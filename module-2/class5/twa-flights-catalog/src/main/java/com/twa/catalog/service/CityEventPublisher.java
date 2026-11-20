package com.twa.catalog.service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.catalog.dto.event.CityUpdatedEventDTO;

/**
 * Sends CiudadActualizada to the exchange of the catalog and waits for the confirm of the broker. The message is
 * persistent and mandatory: if the broker does not confirm it, or no queue receives it (the consumer did not declare
 * its queue yet), it is an exception and the relay does not move its checkpoint. So an event is never lost, but it can
 * be sent more than once (at least once).
 */
@Component
@ConditionalOnProperty(name = "catalog.events.enabled", havingValue = "true")
public class CityEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityEventPublisher.class);

    public static final String VERSION_HEADER = "eventVersion";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;
    private final Duration confirmTimeout;

    @Autowired
    public CityEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
            @Value("${catalog.events.exchange}") String exchange,
            @Value("${catalog.events.ciudad-actualizada.routing-key}") String routingKey,
            @Value("${catalog.events.confirm-timeout:5s}") Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        // The null fields are not written: a DELETE only has the code of the city
        this.objectMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.confirmTimeout = confirmTimeout;
    }

    public void publish(CityUpdatedEventDTO event) {
        Message message = MessageBuilder.withBody(serialize(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setContentEncoding("UTF-8")
                .setMessageId(event.getEventId()).setType(event.getEventType())
                .setHeader(VERSION_HEADER, event.getEventVersion()).setAppId(event.getSource())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();

        CorrelationData correlation = new CorrelationData(event.getEventId());
        rabbitTemplate.send(exchange, routingKey, message, correlation);

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting the confirm of " + event, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("RabbitMQ did not confirm " + event, e);
        }

        if (correlation.getReturned() != null) {
            throw new IllegalStateException("No queue is bound to " + exchange + " with " + routingKey
                    + ", the event is published again later: " + event);
        }
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ rejected " + event + ": " + confirm.getReason());
        }
        LOGGER.debug("Published {}", event);
    }

    private byte[] serialize(CityUpdatedEventDTO event) {
        try {
            return objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The event can not be written as JSON: " + event, e);
        }
    }
}
