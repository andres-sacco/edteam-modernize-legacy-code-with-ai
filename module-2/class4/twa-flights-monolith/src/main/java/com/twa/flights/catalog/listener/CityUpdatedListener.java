package com.twa.flights.catalog.listener;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.flights.catalog.dto.event.CityUpdatedEventDTO;
import com.twa.flights.catalog.service.CityReplicaService;

/**
 * Consumer of the events CiudadActualizada of the catalog service (M2 C3). It reads the message, checks that it is an
 * event of the version 1 with the fields that the replica needs, and delegates in {@link CityReplicaService}, that
 * discards the duplicates and the old events.
 *
 * An invalid message (not JSON, another type, another version or without required fields) is rejected without
 * retries and goes to the dead letter queue: retrying it would give the same result. A failure of the database is
 * retried by the listener (spring.rabbitmq.listener.simple.retry) and then goes to the dead letter queue too.
 *
 * Only when catalog.events.enabled=true. One consumer (concurrency 1), so the events arrive in the order of the queue.
 */
@Component
@ConditionalOnProperty(name = "catalog.events.enabled", havingValue = "true")
public class CityUpdatedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityUpdatedListener.class);

    private static final Set<String> OPERATIONS = Set.of(CityUpdatedEventDTO.UPSERT, CityUpdatedEventDTO.DELETE);

    private final CityReplicaService cityReplicaService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CityUpdatedListener(CityReplicaService cityReplicaService, ObjectMapper objectMapper) {
        this.cityReplicaService = cityReplicaService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${catalog.events.ciudad-actualizada.queue}", concurrency = "1")
    public void onMessage(Message message) {
        CityUpdatedEventDTO event = read(message);
        validate(event);
        cityReplicaService.apply(event);
    }

    private CityUpdatedEventDTO read(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), CityUpdatedEventDTO.class);
        } catch (IOException e) {
            LOGGER.error("The message {} is not a valid event {}", message.getMessageProperties().getMessageId(),
                    CityUpdatedEventDTO.TYPE, e);
            throw new AmqpRejectAndDontRequeueException("The message is not a valid JSON event", e);
        }
    }

    private static void validate(CityUpdatedEventDTO event) {
        if (!CityUpdatedEventDTO.TYPE.equals(event.getEventType())) {
            reject(event, "the type is not " + CityUpdatedEventDTO.TYPE);
        }
        if (event.getEventVersion() == null || event.getEventVersion() != CityUpdatedEventDTO.VERSION) {
            reject(event, "the version " + event.getEventVersion() + " is not supported");
        }
        if (StringUtils.isBlank(event.getEventId()) || event.getSequence() == null || event.getCityId() == null
                || !OPERATIONS.contains(event.getOperation()) || event.getCity() == null
                || StringUtils.isBlank(event.getCity().getCode())) {
            reject(event, "a required field is missing");
        }
        if (CityUpdatedEventDTO.UPSERT.equals(event.getOperation())
                && (StringUtils.isBlank(event.getCity().getTimeZone()) || event.getCity().getCountry() == null)) {
            reject(event, "the city of an UPSERT has no time zone or country");
        }
    }

    private static void reject(CityUpdatedEventDTO event, String reason) {
        LOGGER.error("The event {} is rejected: {}", event, reason);
        throw new AmqpRejectAndDontRequeueException("Invalid event " + event + ": " + reason);
    }
}
