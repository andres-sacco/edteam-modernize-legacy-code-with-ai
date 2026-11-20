package com.twa.flights.catalog.client.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The rule of the automatic rollback of the rollout of the catalog (catalog.rollout.guard), see CatalogRolloutGuard.
 */
@Configuration
@ConfigurationProperties(prefix = "catalog.rollout.guard")
public class CatalogRolloutGuardConfiguration {

    private boolean enabled = true;
    // MILLISECONDS between two evaluations, each one looks at the calls of that window. Short, because while the
    // service is down the users see 503 until the rollback (there is no fallback by request, decision 1 of the plan)
    private long window = 10000;
    // A window with fewer remote calls is not evaluated (it breaks the sequence of consecutive windows)
    private long minCalls = 10;
    // Percentage of remote calls with result=error: over maxErrorRate in consecutiveWindows windows, or over
    // criticalErrorRate in one window with at least criticalMinErrors errors (with few calls, one error alone is
    // already over 5 %)
    private double maxErrorRate = 0.5;
    private double criticalErrorRate = 5.0;
    private long criticalMinErrors = 3;
    // MILLISECONDS of the mean of the remote calls of the window, in consecutiveWindows windows
    private double maxMeanLatency = 50;
    // With windows of 10 s, 6 windows are the minute of "sustained" of the plan
    private int consecutiveWindows = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getWindow() {
        return window;
    }

    public void setWindow(long window) {
        this.window = window;
    }

    public long getMinCalls() {
        return minCalls;
    }

    public void setMinCalls(long minCalls) {
        this.minCalls = minCalls;
    }

    public double getMaxErrorRate() {
        return maxErrorRate;
    }

    public void setMaxErrorRate(double maxErrorRate) {
        this.maxErrorRate = maxErrorRate;
    }

    public double getCriticalErrorRate() {
        return criticalErrorRate;
    }

    public void setCriticalErrorRate(double criticalErrorRate) {
        this.criticalErrorRate = criticalErrorRate;
    }

    public long getCriticalMinErrors() {
        return criticalMinErrors;
    }

    public void setCriticalMinErrors(long criticalMinErrors) {
        this.criticalMinErrors = criticalMinErrors;
    }

    public double getMaxMeanLatency() {
        return maxMeanLatency;
    }

    public void setMaxMeanLatency(double maxMeanLatency) {
        this.maxMeanLatency = maxMeanLatency;
    }

    public int getConsecutiveWindows() {
        return consecutiveWindows;
    }

    public void setConsecutiveWindows(int consecutiveWindows) {
        this.consecutiveWindows = consecutiveWindows;
    }
}
