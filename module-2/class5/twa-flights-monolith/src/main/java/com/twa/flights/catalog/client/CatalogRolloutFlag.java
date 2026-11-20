package com.twa.flights.catalog.client;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.twa.flights.catalog.dto.CatalogRolloutDTO;
import com.twa.flights.catalog.repository.CatalogRolloutRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * The flag of the rollout of the catalog service (M2 C4): the percentage of the calls to the catalog that go to the
 * remote path. It is read from the table catalog_rollout every catalog.rollout.refresh (5 s by default), so a change
 * or a rollback is applied in all the instances without a deploy or a restart.
 *
 * Without a value in the table (remote_percentage NULL) the property catalog.mode decides, as before: LOCAL is 0 %
 * and REMOTE is 100 %. If the table can not be read, the instance keeps the last value that it read.
 *
 * The gauge catalog.rollout.remote.percentage has the percentage that this instance applies.
 */
@Component
public class CatalogRolloutFlag {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRolloutFlag.class);

    private final CatalogMode mode;
    private final CatalogRolloutRepository repository;

    private volatile CatalogRolloutDTO current;

    @Autowired
    public CatalogRolloutFlag(@Value("${catalog.mode:LOCAL}") CatalogMode mode, CatalogRolloutRepository repository,
            MeterRegistry meterRegistry) {
        this.mode = mode;
        this.repository = repository;
        this.current = fromProperty();
        refresh();
        Gauge.builder("catalog.rollout.remote.percentage", this, CatalogRolloutFlag::getRemotePercentage)
                .description("Percentage of the calls to the catalog that go to the catalog service")
                .register(meterRegistry);
    }

    private CatalogRolloutFlag(CatalogMode mode) {
        this.mode = mode;
        this.repository = null;
        this.current = fromProperty();
    }

    /**
     * A flag that is not read from the database: always the value of catalog.mode.
     */
    public static CatalogRolloutFlag fixed(CatalogMode mode) {
        return new CatalogRolloutFlag(mode);
    }

    @Scheduled(fixedDelayString = "${catalog.rollout.refresh:5000}", initialDelayString = "${catalog.rollout.refresh:5000}")
    public void refresh() {
        if (repository == null) {
            return;
        }

        CatalogRolloutDTO read;
        try {
            read = repository.find();
        } catch (DataAccessException e) {
            LOGGER.warn("The flag of the rollout of the catalog could not be read, it keeps {} % remote: {}",
                    current.getEffectiveRemotePercentage(), e.getMessage());
            return;
        }

        CatalogRolloutDTO next = read == null ? fromProperty() : withEffectivePercentage(read);
        if (next.getEffectiveRemotePercentage() != current.getEffectiveRemotePercentage()
                || !Objects.equals(next.getRemotePercentage(), current.getRemotePercentage())) {
            LOGGER.info("The rollout of the catalog changed from {} % to {} % remote (by {}: {})",
                    current.getEffectiveRemotePercentage(), next.getEffectiveRemotePercentage(), next.getUpdatedBy(),
                    next.getReason());
        }
        current = next;
    }

    /**
     * Changes the flag in the database (for all the instances) and applies it in this instance right away. If the
     * database fails, it is applied only in this instance and the exception goes to the caller.
     *
     * @param remotePercentage
     *            from 0 to 100, or null to go back to the value of catalog.mode
     */
    public CatalogRolloutDTO change(Integer remotePercentage, String updatedBy, String reason) {
        CatalogRolloutDTO changed = new CatalogRolloutDTO();
        changed.setRemotePercentage(remotePercentage);
        changed.setUpdatedBy(updatedBy);
        changed.setReason(reason);
        withEffectivePercentage(changed);

        LOGGER.info("The rollout of the catalog changes from {} % to {} % remote (by {}: {})",
                current.getEffectiveRemotePercentage(), changed.getEffectiveRemotePercentage(), updatedBy, reason);
        current = changed;
        if (repository != null) {
            repository.update(remotePercentage, updatedBy, reason);
            refresh();
        }
        return current;
    }

    public CatalogRolloutDTO getCurrent() {
        return current;
    }

    public int getRemotePercentage() {
        return current.getEffectiveRemotePercentage();
    }

    /**
     * Decides the path of one call to the catalog.
     */
    public boolean useRemote() {
        int percentage = getRemotePercentage();
        return percentage >= 100 || (percentage > 0 && ThreadLocalRandom.current().nextInt(100) < percentage);
    }

    private CatalogRolloutDTO fromProperty() {
        CatalogRolloutDTO rollout = new CatalogRolloutDTO();
        rollout.setUpdatedBy("catalog.mode");
        rollout.setReason("The property catalog.mode decides");
        return withEffectivePercentage(rollout);
    }

    private CatalogRolloutDTO withEffectivePercentage(CatalogRolloutDTO rollout) {
        Integer percentage = rollout.getRemotePercentage();
        if (percentage == null) {
            rollout.setEffectiveRemotePercentage(mode == CatalogMode.REMOTE ? 100 : 0);
        } else {
            rollout.setEffectiveRemotePercentage(Math.max(0, Math.min(100, percentage)));
        }
        return rollout;
    }
}
