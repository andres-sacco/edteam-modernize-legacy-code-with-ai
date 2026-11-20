package com.twa.flights.catalog.client.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "catalog.cache")
public class CatalogCacheConfiguration {

    private boolean enabled;
    private Duration ttl = Duration.ofSeconds(60);
    private long maximumSize = 1000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }
}
