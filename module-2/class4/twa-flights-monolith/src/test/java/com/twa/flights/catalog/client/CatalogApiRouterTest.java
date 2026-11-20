package com.twa.flights.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.client.configuration.CatalogCacheConfiguration;
import com.twa.flights.catalog.client.configuration.CatalogClientConfiguration;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.service.CatalogService;
import com.twa.flights.support.AbstractIntegrationTest;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * The other modules receive the router as the only CatalogApi, and by default it uses the local catalog: the monolith
 * does not call the catalog service until catalog.mode=REMOTE.
 */
class CatalogApiRouterTest extends AbstractIntegrationTest {

    private static final CatalogCacheConfiguration NO_CACHE = new CatalogCacheConfiguration();
    private static final SimpleMeterRegistry METRICS = new SimpleMeterRegistry();

    @Autowired
    private CatalogApi catalogApi;

    @Test
    @DisabledIfSystemProperty(named = "catalog.mode", matches = "REMOTE", disabledReason = "the suite is in remote mode")
    @DisplayName("the CatalogApi of the application is the router, in mode LOCAL by default")
    void localByDefault() {
        assertThat(catalogApi).isInstanceOf(CatalogApiRouter.class);
        assertThat(catalogApi.getCity("BUE").getTimeZone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(catalog.requests()).isEmpty();
    }

    @Test
    @DisplayName("getCities returns the same cities than getCity, by the code as it was requested")
    void severalCities() {
        Map<String, CityDTO> cities = catalogApi.getCities(List.of("BUE", "mia", "XXX", "", "BUE"));

        assertThat(cities).containsOnlyKeys("BUE", "mia");
        assertThat(cities.get("mia").getCode()).isEqualTo("MIA");
        assertThat(cities.get("mia").getTimeZone()).isEqualTo(catalogApi.getCity("mia").getTimeZone());
    }

    @Test
    @DisplayName("each mode delegates in its implementation")
    void delegates() {
        FakeCatalog local = new FakeCatalog();
        FakeCatalog remote = new FakeCatalog();

        assertThat(router(CatalogMode.LOCAL, false, local, remote).getCity("BUE")).isSameAs(local.city);
        assertThat(router(CatalogMode.REMOTE, false, local, remote).getCity("BUE")).isSameAs(remote.city);
    }

    @Test
    @DisplayName("with the batch disabled getCities asks one city at a time, with the batch enabled it is one call")
    void batch() {
        FakeCatalog withoutBatch = new FakeCatalog();
        Map<String, CityDTO> cities = router(CatalogMode.LOCAL, false, withoutBatch, withoutBatch)
                .getCities(List.of("BUE", "MIA", "BUE", "XXX"));

        assertThat(cities).containsOnlyKeys("BUE", "MIA");
        assertThat(withoutBatch.calls).containsExactly("getCity BUE", "getCity MIA", "getCity XXX");

        FakeCatalog withBatch = new FakeCatalog();
        router(CatalogMode.LOCAL, true, withBatch, withBatch).getCities(List.of("BUE", "MIA"));

        assertThat(withBatch.calls).containsExactly("getCities [BUE, MIA]");
    }

    private static CatalogApiRouter router(CatalogMode mode, boolean batch, FakeCatalog local, FakeCatalog remote) {
        return new CatalogApiRouter(CatalogRolloutFlag.fixed(mode), batch, NO_CACHE, METRICS, local.service(),
                remote.client());
    }

    // XXX does not exist, any other code is the same city
    private static class FakeCatalog {

        private final CityDTO city = new CityDTO();
        private final List<String> calls = new ArrayList<>();

        CatalogService service() {
            return new CatalogService(null, null) {
                @Override
                public CityDTO getCity(String code) {
                    return FakeCatalog.this.getCity(code);
                }

                @Override
                public Map<String, CityDTO> getCities(Collection<String> codes) {
                    return FakeCatalog.this.getCities(codes);
                }
            };
        }

        CatalogClient client() {
            return new CatalogClient(new CatalogClientConfiguration(), RestClient.builder()) {
                @Override
                public CityDTO getCity(String code) {
                    return FakeCatalog.this.getCity(code);
                }

                @Override
                public Map<String, CityDTO> getCities(Collection<String> codes) {
                    return FakeCatalog.this.getCities(codes);
                }
            };
        }

        private CityDTO getCity(String code) {
            calls.add("getCity " + code);
            return "XXX".equals(code) ? null : city;
        }

        private Map<String, CityDTO> getCities(Collection<String> codes) {
            calls.add("getCities " + codes);
            return Map.of();
        }
    }
}
