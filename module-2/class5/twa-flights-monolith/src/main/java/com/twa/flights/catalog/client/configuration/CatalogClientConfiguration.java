package com.twa.flights.catalog.client.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "catalog.remote")
public class CatalogClientConfiguration {

    private String host;
    private int connectionTimeout;
    private int readTimeout;
    // Total attempts of each request (1 = without retries). Only the transient failures are retried
    private int maxAttempts = 2;
    // Time between two attempts, in milliseconds
    private long retryBackoff = 100;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(long retryBackoff) {
        this.retryBackoff = retryBackoff;
    }
}
