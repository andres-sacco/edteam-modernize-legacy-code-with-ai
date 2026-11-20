package com.twa.flights.catalog.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.client.configuration.CatalogCacheConfiguration;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.helper.CityCodes;
import com.twa.flights.catalog.service.CatalogService;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The implementation of {@link CatalogApi} that the other modules receive (Branch by Abstraction). It delegates in the
 * local catalog or in the catalog service according to catalog.mode, so the consumers do not change and there is
 * only one primary bean of the interface.
 *
 * With catalog.batch.enabled=false (default) {@link #getCities(Collection)} asks one city at a time, the same calls of
 * before the batch; with true it is one query (or one request of up to 50 codes) for all the cities. With
 * catalog.cache.enabled=true the cities are cached in front of the implementation (see {@link CachedCatalogApi}).
 */
@Primary
@Component
public class CatalogApiRouter implements CatalogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogApiRouter.class);

    private final CatalogApi delegate;
    private final boolean batchEnabled;

    @Autowired
    public CatalogApiRouter(@Value("${catalog.mode:LOCAL}") CatalogMode mode,
            @Value("${catalog.batch.enabled:false}") boolean batchEnabled, CatalogCacheConfiguration cacheConfiguration,
            MeterRegistry meterRegistry, CatalogService catalogService, CatalogClient catalogClient) {
        CatalogApi implementation = mode == CatalogMode.REMOTE ? catalogClient : catalogService;
        this.delegate = cacheConfiguration.isEnabled()
                ? new CachedCatalogApi(implementation, cacheConfiguration, meterRegistry)
                : implementation;
        this.batchEnabled = batchEnabled;
        LOGGER.info("The catalog of cities works in mode {} (batch: {}, cache: {})", mode, batchEnabled,
                cacheConfiguration.isEnabled());
    }

    @Override
    public CityDTO getCity(String code) {
        return delegate.getCity(code);
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        if (batchEnabled) {
            return delegate.getCities(codes);
        }

        Map<String, CityDTO> cities = new LinkedHashMap<>();
        for (String code : CityCodes.distinct(codes)) {
            CityDTO city = delegate.getCity(code);
            if (city != null) {
                cities.put(code, city);
            }
        }
        return cities;
    }
}
