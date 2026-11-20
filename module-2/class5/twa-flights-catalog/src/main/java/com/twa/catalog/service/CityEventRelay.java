package com.twa.catalog.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.twa.catalog.dto.event.CityUpdatedEventDTO;
import com.twa.catalog.mapper.CityEventMapper;
import com.twa.catalog.model.CatalogOutboxEvent;
import com.twa.catalog.repository.CatalogCopyRepository;
import com.twa.catalog.repository.CatalogOutboxRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Relay of the outbox to RabbitMQ (M2 C3, flow F3 of the decision table): publishes each change of a city of
 * flights.catalog_outbox as CiudadActualizada, in the order of the outbox.
 *
 * Each event is published inside a transaction of the database catalog that locks event_relay_checkpoint, and the
 * checkpoint moves only after the confirm of the broker. If the broker is down or does not route the event, the
 * relay stops there and tries again in the next run: it never skips an event. If the service stops after the confirm
 * and before the commit, the event is published again with the same eventId, and the consumer discards it.
 *
 * Only when catalog.events.enabled=true and catalog.copy.enabled=true (the checkpoint is in the database catalog).
 */
@Service
@ConditionalOnProperty(name = { "catalog.events.enabled", "catalog.copy.enabled" }, havingValue = "true")
public class CityEventRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityEventRelay.class);

    static final String RELAY = "ciudad-actualizada";

    private final CatalogOutboxRepository outboxRepository;
    private final CatalogCopyRepository copyRepository;
    private final CityEventMapper mapper;
    private final CityEventPublisher publisher;
    private final long settleMillis;
    private final int batchSize;
    private final AtomicLong lastPublished = new AtomicLong();
    private final Counter published;

    @Autowired
    public CityEventRelay(CatalogOutboxRepository outboxRepository, CatalogCopyRepository copyRepository,
            CityEventMapper mapper, CityEventPublisher publisher, MeterRegistry meterRegistry,
            @Value("${catalog.copy.publisher.settle-ms:2000}") long settleMillis,
            @Value("${catalog.events.relay.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.copyRepository = copyRepository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.settleMillis = settleMillis;
        this.batchSize = batchSize;
        Gauge.builder("catalog.events.relay.checkpoint", lastPublished, AtomicLong::get)
                .description("Last event of flights.catalog_outbox published as CiudadActualizada")
                .register(meterRegistry);
        this.published = Counter.builder("catalog.events.published").tag("type", CityUpdatedEventDTO.TYPE)
                .description("Events CiudadActualizada confirmed by RabbitMQ").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${catalog.events.relay.fixed-delay-ms:1000}")
    public void relay() {
        try {
            List<CatalogOutboxEvent> events = outboxRepository
                    .findCityEventsAfter(copyRepository.readRelayCheckpoint(RELAY), settleMillis, batchSize);
            int count = 0;
            for (CatalogOutboxEvent event : events) {
                if (publish(event)) {
                    count++;
                }
            }
            lastPublished.set(copyRepository.readRelayCheckpoint(RELAY));
            if (count > 0) {
                LOGGER.info("Published {} events {} in RabbitMQ, checkpoint {}", count, CityUpdatedEventDTO.TYPE,
                        lastPublished.get());
            }
        } catch (RuntimeException e) {
            // The event stays in the outbox and the next run tries again from the checkpoint
            LOGGER.error("Error publishing the events {}, it is retried in the next run: {}",
                    CityUpdatedEventDTO.TYPE, e.getMessage());
            LOGGER.debug("Error publishing the events", e);
        }
    }

    // Returns false if another instance already published the event
    private boolean publish(CatalogOutboxEvent event) {
        CityUpdatedEventDTO dto = mapper.convert(event);
        Boolean sent = copyRepository.inTransaction(status -> {
            if (copyRepository.lockRelayCheckpoint(RELAY) >= event.getId()) {
                return false;
            }
            publisher.publish(dto);
            copyRepository.saveRelayCheckpoint(RELAY, event.getId());
            return true;
        });
        if (Boolean.TRUE.equals(sent)) {
            published.increment();
        }
        return Boolean.TRUE.equals(sent);
    }
}
