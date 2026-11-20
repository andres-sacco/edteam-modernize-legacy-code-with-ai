package com.twa.catalog.configuration;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Events of the catalog in RabbitMQ (M2 C3): the exchange topic where CityEventRelay publishes CiudadActualizada. The
 * service declares only the exchange; each consumer declares and binds its own queue.
 *
 * Only when catalog.events.enabled=true. The relay also needs catalog.copy.enabled=true: its checkpoint is in the own
 * database of the service.
 */
@Configuration
@ConditionalOnProperty(name = "catalog.events.enabled", havingValue = "true")
public class CatalogEventsConfiguration {

    @Bean
    public TopicExchange catalogEventsExchange(@Value("${catalog.events.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }
}
