package com.twa.flights.catalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twa.flights.catalog.dto.event.CityUpdatedEventDTO;
import com.twa.flights.catalog.repository.CityReplicaRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Applies the events CiudadActualizada to the replica of the cities (M2 C3). The consumer is idempotent and tolerates
 * the disorder:
 * <ul>
 * <li>the same eventId twice: the second one is discarded (processed_event, in the same transaction as the change);</li>
 * <li>an event with a sequence lower than or equal to the last one applied to the same city: it is discarded, so an
 * old UPSERT never overwrites a newer change or brings back a deleted city.</li>
 * </ul>
 * The metric catalog.events.consumed counts the events by result (applied, duplicate, outdated).
 */
@Service
public class CityReplicaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityReplicaService.class);

    public enum Result {
        APPLIED, DUPLICATE, OUTDATED
    }

    private final CityReplicaRepository repository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public CityReplicaService(CityReplicaRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param event
     *            a valid event of the version 1 (the listener checks it)
     */
    @Transactional
    public Result apply(CityUpdatedEventDTO event) {
        Result result = doApply(event);
        meterRegistry.counter("catalog.events.consumed", "type", CityUpdatedEventDTO.TYPE, "result",
                result.name().toLowerCase()).increment();
        LOGGER.debug("Event {}: {}", event, result);
        return result;
    }

    private Result doApply(CityUpdatedEventDTO event) {
        if (!repository.markProcessed(event.getEventId(), event.getEventType(), event.getSequence())) {
            LOGGER.info("Event {} already processed, it is discarded", event);
            return Result.DUPLICATE;
        }

        Long lastSequence = repository.lockLastSequence(event.getCityId());
        if (lastSequence != null && lastSequence >= event.getSequence()) {
            LOGGER.info("Event {} is older than the last one applied to the city ({}), it is discarded", event,
                    lastSequence);
            return Result.OUTDATED;
        }

        if (CityUpdatedEventDTO.DELETE.equals(event.getOperation())) {
            repository.markDeleted(event.getCityId(), event.getCity().getCode(), event.getSequence());
        } else {
            repository.upsert(event.getCityId(), event.getCity(), event.getSequence());
        }
        return Result.APPLIED;
    }
}
