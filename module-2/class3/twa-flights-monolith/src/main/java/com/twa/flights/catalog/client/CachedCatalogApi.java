package com.twa.flights.catalog.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.client.configuration.CatalogCacheConfiguration;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.helper.CityCodes;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

/**
 * Cache of the cities in front of the catalog, local or remote (ADR-002, D5): TTL of 60 seconds and up to 1.000
 * cities by default. The unknown codes are not cached, so a new city does not wait the TTL to stop being the error
 * 40014. The failures are not cached either: a CatalogUnavailableException goes to the caller.
 *
 * The key is the code in upper case: "mia" and "MIA" are the same city in MySQL. The metrics are cache.gets,
 * cache.size and cache.evictions with the tag cache=catalog-cities.
 */
public class CachedCatalogApi implements CatalogApi {

    public static final String CACHE_NAME = "catalog-cities";

    private final CatalogApi delegate;
    private final Cache<String, CityDTO> cache;

    public CachedCatalogApi(CatalogApi delegate, CatalogCacheConfiguration configuration, MeterRegistry registry) {
        this(delegate, configuration, registry, Ticker.systemTicker());
    }

    CachedCatalogApi(CatalogApi delegate, CatalogCacheConfiguration configuration, MeterRegistry registry,
            Ticker ticker) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder().expireAfterWrite(configuration.getTtl())
                .maximumSize(configuration.getMaximumSize()).ticker(ticker).recordStats().build();
        CaffeineCacheMetrics.monitor(registry, cache, CACHE_NAME);
    }

    @Override
    public CityDTO getCity(String code) {
        if (StringUtils.isBlank(code)) {
            return delegate.getCity(code);
        }

        CityDTO city = cache.getIfPresent(key(code));
        if (city == null) {
            city = delegate.getCity(code);
            if (city != null) {
                cache.put(key(code), city);
            }
        }
        return city;
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        Map<String, CityDTO> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (String code : CityCodes.distinct(codes)) {
            CityDTO city = cache.getIfPresent(key(code));
            if (city != null) {
                result.put(code, city);
            } else {
                missing.add(code);
            }
        }

        if (!missing.isEmpty()) {
            delegate.getCities(missing).forEach((code, city) -> {
                cache.put(key(code), city);
                result.put(code, city);
            });
        }

        // The same order of the request
        Map<String, CityDTO> ordered = new LinkedHashMap<>();
        for (String code : CityCodes.distinct(codes)) {
            if (result.containsKey(code)) {
                ordered.put(code, result.get(code));
            }
        }
        return ordered;
    }

    private static String key(String code) {
        return code.toUpperCase(Locale.ROOT);
    }
}
