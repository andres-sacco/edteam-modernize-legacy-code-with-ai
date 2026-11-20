package com.twa.flights.catalog.listener.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue of the monolith for the events CiudadActualizada of the catalog service (M2 C3). The monolith declares its own
 * queue and binds it to the exchange of the catalog (it declares the exchange too, with the same arguments, so it
 * does not depend on which one starts first). A message that can not be processed after the retries of the listener
 * goes to the dead letter queue, and the next ones are not blocked.
 *
 * Only when catalog.events.enabled=true.
 */
@Configuration
@ConditionalOnProperty(name = "catalog.events.enabled", havingValue = "true")
public class CatalogEventsConfiguration {

    @Bean
    public TopicExchange catalogEventsExchange(@Value("${catalog.events.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue cityUpdatedQueue(@Value("${catalog.events.ciudad-actualizada.queue}") String queue) {
        return QueueBuilder.durable(queue).deadLetterExchange("").deadLetterRoutingKey(queue + ".dlq").build();
    }

    @Bean
    public Queue cityUpdatedDeadLetterQueue(@Value("${catalog.events.ciudad-actualizada.queue}") String queue) {
        return QueueBuilder.durable(queue + ".dlq").build();
    }

    @Bean
    public Binding cityUpdatedBinding(Queue cityUpdatedQueue, TopicExchange catalogEventsExchange,
            @Value("${catalog.events.ciudad-actualizada.routing-key}") String routingKey) {
        return BindingBuilder.bind(cityUpdatedQueue).to(catalogEventsExchange).with(routingKey);
    }
}
