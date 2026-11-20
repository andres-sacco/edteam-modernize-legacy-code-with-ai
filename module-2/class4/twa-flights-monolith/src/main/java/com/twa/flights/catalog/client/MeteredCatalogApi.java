package com.twa.flights.catalog.client;

import java.util.Collection;
import java.util.Map;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.helper.CityCodes;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Measures each call to one of the paths of the catalog (M2 C4, metric catalog.lookup of the extraction plan), so the
 * local and the remote path are compared with the same metric: Timer catalog.lookup with the tags path (local or
 * remote), operation (single or batch) and result (found, not_found or error). It publishes the p95 and the p99 of
 * each combination of tags.
 *
 * It is in front of the implementation and behind the cache: the hits of the cache are not a call to any path.
 */
public class MeteredCatalogApi implements CatalogApi {

    public static final String METRIC = "catalog.lookup";
    public static final String TAG_PATH = "path";
    public static final String TAG_RESULT = "result";
    public static final String RESULT_ERROR = "error";

    private final CatalogApi delegate;
    private final String path;
    private final MeterRegistry registry;

    public MeteredCatalogApi(CatalogApi delegate, String path, MeterRegistry registry) {
        this.delegate = delegate;
        this.path = path;
        this.registry = registry;
    }

    @Override
    public CityDTO getCity(String code) {
        Timer.Sample sample = Timer.start(registry);
        String result = RESULT_ERROR;
        try {
            CityDTO city = delegate.getCity(code);
            result = city == null ? "not_found" : "found";
            return city;
        } finally {
            sample.stop(timer("single", result));
        }
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        Timer.Sample sample = Timer.start(registry);
        String result = RESULT_ERROR;
        try {
            Map<String, CityDTO> cities = delegate.getCities(codes);
            result = cities.size() < CityCodes.distinct(codes).size() ? "not_found" : "found";
            return cities;
        } finally {
            sample.stop(timer("batch", result));
        }
    }

    private Timer timer(String operation, String result) {
        return Timer.builder(METRIC).description("Calls to the catalog by path (local or remote)")
                .tag(TAG_PATH, path).tag("operation", operation).tag(TAG_RESULT, result)
                .publishPercentiles(0.95, 0.99).register(registry);
    }
}
