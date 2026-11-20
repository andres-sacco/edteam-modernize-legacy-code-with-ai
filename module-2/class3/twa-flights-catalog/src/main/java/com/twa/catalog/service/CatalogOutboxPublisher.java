package com.twa.catalog.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.catalog.model.CatalogOutboxEvent;
import com.twa.catalog.repository.CatalogCopyRepository;
import com.twa.catalog.repository.CatalogOutboxRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Publisher of the outbox of the catalog (M2 C2, stage 5): applies the events of flights.catalog_outbox in the copy of
 * the catalog in the database catalog, in the order of the outbox.
 *
 * Each event is applied in its own transaction of the database catalog, together with the checkpoint: if the service
 * stops in the middle, the event is applied again from the start, and the upsert by id gives the same row. If an event
 * fails, the publisher stops there and tries again in the next run: it never skips an event.
 *
 * Only when catalog.copy.enabled=true. With the flag off nothing reads the outbox and the copy stays as it was.
 */
@Service
@ConditionalOnProperty(name = "catalog.copy.enabled", havingValue = "true")
public class CatalogOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogOutboxPublisher.class);

    private final CatalogOutboxRepository outboxRepository;
    private final CatalogCopyRepository copyRepository;
    private final ObjectMapper objectMapper;
    private final long settleMillis;
    private final int batchSize;
    private final AtomicLong pending = new AtomicLong();
    private final Timer lag;

    @Autowired
    public CatalogOutboxPublisher(CatalogOutboxRepository outboxRepository, CatalogCopyRepository copyRepository,
            ObjectMapper objectMapper, MeterRegistry meterRegistry,
            @Value("${catalog.copy.publisher.settle-ms:2000}") long settleMillis,
            @Value("${catalog.copy.publisher.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.copyRepository = copyRepository;
        this.objectMapper = objectMapper;
        this.settleMillis = settleMillis;
        this.batchSize = batchSize;
        Gauge.builder("catalog.outbox.pending", pending, AtomicLong::get)
                .description("Events of flights.catalog_outbox not applied in the copy of the catalog")
                .register(meterRegistry);
        this.lag = Timer.builder("catalog.outbox.lag")
                .description("Time from the change in flights to the change in the copy of the catalog")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${catalog.copy.publisher.fixed-delay-ms:1000}")
    public void publish() {
        try {
            long lastEventId = copyRepository.readCheckpoint();
            List<CatalogOutboxEvent> events = outboxRepository.findAfter(lastEventId, settleMillis, batchSize);
            int applied = 0;
            for (CatalogOutboxEvent event : events) {
                if (apply(event)) {
                    applied++;
                }
            }
            pending.set(outboxRepository.countAfter(copyRepository.readCheckpoint()));
            if (applied > 0) {
                LOGGER.info("Applied {} events of the outbox in the copy of the catalog, {} pending", applied,
                        pending.get());
            }
        } catch (RuntimeException e) {
            // The event stays in the outbox and the next run tries again from the checkpoint
            LOGGER.error("Error publishing the outbox of the catalog, it is retried in the next run", e);
        }
    }

    // Returns false if another instance already applied the event
    private boolean apply(CatalogOutboxEvent event) {
        JsonNode row = parse(event);
        Boolean applied = copyRepository.inTransaction(status -> {
            if (copyRepository.lockCheckpoint() >= event.getId()) {
                return false;
            }
            if (CatalogOutboxEvent.DELETE.equals(event.getOperation())) {
                copyRepository.delete(event.getAggregateType(), event.getAggregateId());
            } else if (CatalogOutboxEvent.UPSERT.equals(event.getOperation())) {
                copyRepository.upsert(event.getAggregateType(), row);
            } else {
                throw new IllegalArgumentException("Unknown operation of the outbox: " + event);
            }
            copyRepository.saveCheckpoint(event.getId());
            return true;
        });
        if (Boolean.TRUE.equals(applied)) {
            lag.record(event.getAgeMillis(), TimeUnit.MILLISECONDS);
        }
        LOGGER.debug("Event {} applied: {}", event, applied);
        return Boolean.TRUE.equals(applied);
    }

    private JsonNode parse(CatalogOutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The payload of the event is not valid JSON: " + event, e);
        }
    }
}
