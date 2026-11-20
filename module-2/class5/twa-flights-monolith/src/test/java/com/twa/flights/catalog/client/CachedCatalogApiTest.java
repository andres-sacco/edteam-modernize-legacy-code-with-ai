package com.twa.flights.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.client.configuration.CatalogCacheConfiguration;
import com.twa.flights.catalog.dto.CityDTO;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * The cache of the cities (ADR-002, D5): TTL, the unknown codes and the failures are not cached.
 */
class CachedCatalogApiTest {

    private final AtomicLong nanos = new AtomicLong();
    private final List<String> calls = new ArrayList<>();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private boolean down;
    private CachedCatalogApi cache;

    @BeforeEach
    void setUp() {
        CatalogCacheConfiguration configuration = new CatalogCacheConfiguration();
        configuration.setTtl(Duration.ofSeconds(60));
        cache = new CachedCatalogApi(new FakeCatalog(), configuration, registry, nanos::get);
    }

    @Test
    @DisplayName("a city is obtained once until the TTL expires, in any case of the code")
    void ttl() {
        cache.getCity("BUE");
        cache.getCity("bue");
        assertThat(calls).containsExactly("getCity BUE");

        nanos.addAndGet(Duration.ofSeconds(61).toNanos());
        cache.getCity("BUE");
        assertThat(calls).containsExactly("getCity BUE", "getCity BUE");
    }

    @Test
    @DisplayName("an unknown code is not cached: a new city does not wait the TTL to exist")
    void unknownCodesAreNotCached() {
        assertThat(cache.getCity("XXX")).isNull();
        assertThat(cache.getCity("XXX")).isNull();
        assertThat(cache.getCities(List.of("XXX"))).isEmpty();

        assertThat(calls).containsExactly("getCity XXX", "getCity XXX", "getCities [XXX]");
    }

    @Test
    @DisplayName("getCities only asks the cities that are not in the cache, and keeps the order of the request")
    void onlyTheMissingCities() {
        cache.getCity("MIA");

        Map<String, CityDTO> cities = cache.getCities(List.of("BUE", "MIA", "XXX", "SCL"));

        assertThat(cities).containsOnlyKeys("BUE", "MIA", "SCL");
        assertThat(List.copyOf(cities.keySet())).containsExactly("BUE", "MIA", "SCL");
        assertThat(calls).containsExactly("getCity MIA", "getCities [BUE, XXX, SCL]");

        cache.getCities(List.of("SCL", "BUE"));
        assertThat(calls).hasSize(2);
    }

    @Test
    @DisplayName("a failure of the catalog is not cached and goes to the caller")
    void failuresAreNotCached() {
        down = true;
        assertThatThrownBy(() -> cache.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);

        down = false;
        assertThat(cache.getCity("BUE").getCode()).isEqualTo("BUE");
    }

    @Test
    @DisplayName("the cache publishes its metrics with the name catalog-cities")
    void metrics() {
        cache.getCity("BUE");
        cache.getCity("BUE");

        assertThat(registry.get("cache.gets").tag("cache", "catalog-cities").tag("result", "hit")
                .functionCounter().count()).isEqualTo(1);
    }

    // XXX does not exist, any other code is a city with that code in upper case
    private class FakeCatalog implements CatalogApi {

        @Override
        public CityDTO getCity(String code) {
            calls.add("getCity " + code);
            return find(code);
        }

        @Override
        public Map<String, CityDTO> getCities(Collection<String> codes) {
            calls.add("getCities " + codes);
            Map<String, CityDTO> cities = new LinkedHashMap<>();
            codes.forEach(code -> {
                CityDTO city = find(code);
                if (city != null) {
                    cities.put(code, city);
                }
            });
            return cities;
        }

        private CityDTO find(String code) {
            if (down) {
                throw new CatalogUnavailableException("down");
            }
            if ("XXX".equals(code)) {
                return null;
            }
            CityDTO city = new CityDTO();
            city.setCode(code.toUpperCase());
            return city;
        }
    }
}
