package com.twa.flights.catalog.client;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.twa.flights.catalog.client.configuration.CatalogRolloutGuardConfiguration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * The rollback rule of the rollout of the catalog, executable (M2 C4): every catalog.rollout.guard.window (10 s) it
 * compares the remote calls of the window (catalog.lookup{path=remote}) with the thresholds, and on a breach it sets
 * the flag to 0 % remote for all the instances, without a deploy:
 *
 * <ul>
 * <li>CatalogRemoteErrorsCritical: error rate &gt; criticalErrorRate (5 %) in one window, with at least
 * criticalMinErrors (3) errors. Rollback right away.</li>
 * <li>CatalogRemoteErrors: error rate &gt; maxErrorRate (0,5 %) in consecutiveWindows (6) windows in a row.</li>
 * <li>CatalogRemoteLatency: mean of the remote calls &gt; maxMeanLatency (50 ms) in consecutiveWindows windows.</li>
 * </ul>
 *
 * A window with fewer than minCalls (10) remote calls is not evaluated. The gauges catalog.rollout.guard.window and
 * catalog.rollout.guard.min.calls publish the rule, so the alert CatalogRolloutGuardBlind of Prometheus uses the same
 * values. The guard only goes back (never advances a
 * stage) and only acts while the flag has a remote percentage. Each rollback logs an ERROR "ALERT ..." and increments
 * catalog.rollout.rollbacks{rule}.
 */
@Component
@ConditionalOnProperty(name = "catalog.rollout.guard.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogRolloutGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRolloutGuard.class);

    static final String UPDATED_BY = "catalog-rollout-guard";

    private final CatalogRolloutFlag rolloutFlag;
    private final CatalogRolloutGuardConfiguration configuration;
    private final MeterRegistry meterRegistry;

    private long lastCalls;
    private long lastErrors;
    private double lastTotalMillis;
    private int errorBreaches;
    private int latencyBreaches;

    @Autowired
    public CatalogRolloutGuard(CatalogRolloutFlag rolloutFlag, CatalogRolloutGuardConfiguration configuration,
            MeterRegistry meterRegistry) {
        this.rolloutFlag = rolloutFlag;
        this.configuration = configuration;
        this.meterRegistry = meterRegistry;
        Gauge.builder("catalog.rollout.guard.window", configuration, c -> c.getWindow() / 1000.0)
                .description("Seconds of each window of the rollback rule of the catalog").baseUnit("seconds")
                .register(meterRegistry);
        Gauge.builder("catalog.rollout.guard.min.calls", configuration, CatalogRolloutGuardConfiguration::getMinCalls)
                .description("Remote calls that a window needs to be evaluated by the rollback rule of the catalog")
                .register(meterRegistry);
        LOGGER.info(
                "Rollback rule of the catalog: errors > {} % in {} windows or > {} % in one (at least {} errors), mean"
                        + " > {} ms in {} windows (window of {} ms, at least {} remote calls)",
                configuration.getMaxErrorRate(), configuration.getConsecutiveWindows(),
                configuration.getCriticalErrorRate(), configuration.getCriticalMinErrors(),
                configuration.getMaxMeanLatency(), configuration.getConsecutiveWindows(), configuration.getWindow(),
                configuration.getMinCalls());
    }

    @Scheduled(fixedDelayString = "${catalog.rollout.guard.window:10000}", initialDelayString = "${catalog.rollout.guard.window:10000}")
    public void evaluate() {
        long calls = 0;
        long errors = 0;
        double totalMillis = 0;
        for (Timer timer : meterRegistry.find(MeteredCatalogApi.METRIC)
                .tag(MeteredCatalogApi.TAG_PATH, CatalogApiRouter.PATH_REMOTE).timers()) {
            calls += timer.count();
            totalMillis += timer.totalTime(TimeUnit.MILLISECONDS);
            if (MeteredCatalogApi.RESULT_ERROR.equals(timer.getId().getTag(MeteredCatalogApi.TAG_RESULT))) {
                errors += timer.count();
            }
        }

        long windowCalls = calls - lastCalls;
        long windowErrors = errors - lastErrors;
        double windowMillis = totalMillis - lastTotalMillis;
        lastCalls = calls;
        lastErrors = errors;
        lastTotalMillis = totalMillis;

        if (rolloutFlag.getRemotePercentage() == 0) {
            errorBreaches = 0;
            latencyBreaches = 0;
            return;
        }
        if (windowCalls < configuration.getMinCalls()) {
            // A window that is not evaluated breaks the sequence: "consecutive windows" are windows in a row, not two
            // bad windows separated by windows without traffic. The alert CatalogRolloutGuardBlind warns when the
            // windows are skipped for a long time
            errorBreaches = 0;
            latencyBreaches = 0;
            LOGGER.debug("Window without enough remote calls to evaluate the rollout of the catalog: {}", windowCalls);
            return;
        }

        double errorRate = 100.0 * windowErrors / windowCalls;
        double meanMillis = windowMillis / windowCalls;
        errorBreaches = errorRate > configuration.getMaxErrorRate() ? errorBreaches + 1 : 0;
        latencyBreaches = meanMillis > configuration.getMaxMeanLatency() ? latencyBreaches + 1 : 0;
        LOGGER.info("Rollout of the catalog at {} % remote: {} remote calls in the window, errors {} %, mean {} ms",
                rolloutFlag.getRemotePercentage(), windowCalls, String.format("%.2f", errorRate),
                String.format("%.1f", meanMillis));

        String detail = String.format("%d remote calls, errors %.2f %%, mean %.1f ms", windowCalls, errorRate,
                meanMillis);
        if (errorRate > configuration.getCriticalErrorRate() && windowErrors >= configuration.getCriticalMinErrors()) {
            rollback("CatalogRemoteErrorsCritical", detail);
        } else if (errorBreaches >= configuration.getConsecutiveWindows()) {
            rollback("CatalogRemoteErrors", detail);
        } else if (latencyBreaches >= configuration.getConsecutiveWindows()) {
            rollback("CatalogRemoteLatency", detail);
        }
    }

    private void rollback(String rule, String detail) {
        int from = rolloutFlag.getRemotePercentage();
        LOGGER.error("ALERT {}: {}. Rollback of the catalog from {} % to 0 % remote", rule, detail, from);
        meterRegistry.counter("catalog.rollout.rollbacks", "rule", rule).increment();
        errorBreaches = 0;
        latencyBreaches = 0;
        try {
            rolloutFlag.change(0, UPDATED_BY, "Rollback " + rule + ": " + detail);
        } catch (DataAccessException e) {
            // The flag is already 0 in this instance, the others keep the last value until the database answers
            LOGGER.error("The rollback of the catalog could not be saved, it is applied only in this instance", e);
        }
    }
}
