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
import com.twa.flights.catalog.service.CityReplicaCatalogService;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The implementation of {@link CatalogApi} that the other modules receive (Branch by Abstraction). It delegates in the
 * local catalog or in the catalog service according to the flag of the rollout ({@link CatalogRolloutFlag}): each call
 * goes to the catalog service with the remote percentage of the flag, so the consumers do not change and there is only
 * one primary bean of the interface. The flag is read in runtime, a rollback does not need a deploy.
 *
 * With catalog.batch.enabled=false (default) {@link #getCities(Collection)} asks one city at a time, the same calls of
 * before the batch; with true it is one query (or one request of up to 50 codes) for all the cities. With
 * catalog.cache.enabled=true the cities are cached in front of both paths (see {@link CachedCatalogApi}).
 *
 * The calls that the flag does not send to the catalog service go to the fallback (catalog.fallback, M2 C5): LOCAL,
 * the tables of the monolith, or REPLICA, the replica city_replica of the events of the service. With REPLICA a
 * rollback to 0 % remote does not need the local catalog.
 *
 * Each call to the implementation is measured with catalog.lookup and the tag path (see {@link MeteredCatalogApi}).
 */
@Primary
@Component
public class CatalogApiRouter implements CatalogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogApiRouter.class);

    // Values of the tag path of catalog.lookup (MeteredCatalogApi)
    public static final String PATH_LOCAL = "local";
    public static final String PATH_REMOTE = "remote";
    public static final String PATH_REPLICA = "replica";

    private final CatalogApi delegate;
    private final boolean batchEnabled;

    public CatalogApiRouter(CatalogRolloutFlag rolloutFlag, boolean batchEnabled,
            CatalogCacheConfiguration cacheConfiguration, MeterRegistry meterRegistry, CatalogService catalogService,
            CatalogClient catalogClient) {
        this(rolloutFlag, batchEnabled, cacheConfiguration, meterRegistry, CatalogFallback.LOCAL, catalogService, null,
                catalogClient);
    }

    @Autowired
    public CatalogApiRouter(CatalogRolloutFlag rolloutFlag, @Value("${catalog.batch.enabled:false}") boolean batchEnabled,
            CatalogCacheConfiguration cacheConfiguration, MeterRegistry meterRegistry,
            @Value("${catalog.fallback:LOCAL}") CatalogFallback fallback, CatalogService catalogService,
            CityReplicaCatalogService replicaService, CatalogClient catalogClient) {
        CatalogApi fallbackPath = fallback == CatalogFallback.REPLICA
                ? new MeteredCatalogApi(replicaService, PATH_REPLICA, meterRegistry)
                : new MeteredCatalogApi(catalogService, PATH_LOCAL, meterRegistry);
        CatalogApi routed = new RoutedCatalogApi(rolloutFlag, fallbackPath,
                new MeteredCatalogApi(catalogClient, PATH_REMOTE, meterRegistry));
        this.delegate = cacheConfiguration.isEnabled()
                ? new CachedCatalogApi(routed, cacheConfiguration, meterRegistry)
                : routed;
        this.batchEnabled = batchEnabled;
        LOGGER.info("The catalog of cities works with {} % remote and the rest {} (by {}, batch: {}, cache: {})",
                rolloutFlag.getRemotePercentage(), fallback, rolloutFlag.getCurrent().getUpdatedBy(), batchEnabled,
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

    /**
     * Chooses the path of each call with the flag of the rollout.
     */
    private static final class RoutedCatalogApi implements CatalogApi {

        private final CatalogRolloutFlag rolloutFlag;
        private final CatalogApi fallback;
        private final CatalogApi remote;

        RoutedCatalogApi(CatalogRolloutFlag rolloutFlag, CatalogApi fallback, CatalogApi remote) {
            this.rolloutFlag = rolloutFlag;
            this.fallback = fallback;
            this.remote = remote;
        }

        @Override
        public CityDTO getCity(String code) {
            return path().getCity(code);
        }

        @Override
        public Map<String, CityDTO> getCities(Collection<String> codes) {
            return path().getCities(codes);
        }

        private CatalogApi path() {
            return rolloutFlag.useRemote() ? remote : fallback;
        }
    }
}
