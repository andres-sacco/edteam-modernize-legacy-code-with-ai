package com.twa.flights.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.mysql.MySQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.twa.flights.search.repository.ClusterSearchJpaRepository;

/**
 * Base of the integration tests: the whole application with a real MySQL (the same Flyway migrations and seed data of
 * production) and the external providers (the airlines) replaced by WireMock with recorded responses.
 *
 * The containers are started only once and the Spring context is shared by all the subclasses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    public static final String CONTEXT_PATH = "/api/flights";
    public static final String API_KEY = "twa-pricing-local-key";

    // The user of the container is not root: with the binary log on, MySQL only lets it create the triggers of the
    // catalog (V1.9, V1.11) with log_bin_trust_function_creators. In production Flyway runs with root
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0").withDatabaseName("flights")
            .withCommand("--log-bin-trust-function-creators=1");
    // The providers and the catalog service (used when catalog.mode=REMOTE)
    protected static final WireMockServer PROVIDERS = new WireMockServer(
            options().dynamicPort().extensions(new CatalogStubs.Transformer()));

    static {
        MYSQL.start();
        PROVIDERS.start();
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ClusterSearchJpaRepository clusterSearchJpaRepository;

    protected final ProviderStubs providers = new ProviderStubs(PROVIDERS);

    protected final CatalogStubs catalog = new CatalogStubs(PROVIDERS);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("connector.provider-alpha.host", () -> PROVIDERS.baseUrl() + ProviderStubs.ALPHA);
        registry.add("connector.provider-beta.host", () -> PROVIDERS.baseUrl() + ProviderStubs.BETA);
        registry.add("connector.provider-gamma.host", () -> PROVIDERS.baseUrl() + ProviderStubs.GAMMA);

        // The mode comes from catalog.mode (LOCAL by default): with REMOTE the catalog is WireMock with the snapshot
        registry.add("catalog.remote.host", () -> PROVIDERS.baseUrl() + CatalogStubs.CATALOG);

        registry.add("pricing.api-key", () -> API_KEY);

        // The tests call the cleanup job directly, it must not run in the middle of them
        registry.add("jobs.cluster-search-cleanup.fixed-delay", () -> "3600000");
    }

    @BeforeEach
    void resetProviders() {
        PROVIDERS.resetAll();
        catalog.available();
        // The client of the JDK supports PATCH and reads the body of the 401 responses
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    // The validator compares the dates of the request with the current date (LocalDate.now() without a Clock), so the
    // dates of the requests are relative to today. The recorded responses keep their own fixed dates.
    protected static String departureDate() {
        return LocalDate.now().plusDays(60).toString();
    }

    protected static String returnDate() {
        return LocalDate.now().plusDays(70).toString();
    }

    protected static String roundTripDates() {
        return departureDate() + "," + returnDate();
    }

    // The query parameters are encoded, the id of a search has commas, colons and equals
    protected URI uri(String path, Map<String, ?> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(CONTEXT_PATH + path);
        queryParams.forEach((name, value) -> builder.queryParam(name, value));
        return URI.create(restTemplate.getRootUri()).resolve(builder.encode().build().toUri());
    }

    protected <T> ResponseEntity<T> get(String path, Map<String, ?> queryParams, Class<T> type) {
        return restTemplate.getForEntity(uri(path, queryParams), type);
    }

    // Parameters of the round trip BUE-MIA with the same passengers of the recorded responses
    protected static Map<String, Object> roundTripRequest() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", "BUE,MIA");
        params.put("to", "MIA,BUE");
        params.put("departure", roundTripDates());
        params.put("adults", 1);
        params.put("children", 1);
        params.put("infants", 1);
        return params;
    }

    protected static Map<String, Object> with(Map<String, Object> params, Object... nameAndValues) {
        Map<String, Object> copy = new LinkedHashMap<>(params);
        for (int i = 0; i < nameAndValues.length; i += 2) {
            if (nameAndValues[i + 1] == null) {
                copy.remove((String) nameAndValues[i]);
            } else {
                copy.put((String) nameAndValues[i], nameAndValues[i + 1]);
            }
        }
        return copy;
    }

    protected ResponseEntity<String> search(Map<String, ?> params) {
        return get("/clusters/itineraries", params, String.class);
    }

    // The search is saved asynchronously after the response, the pagination has to wait for it
    protected void awaitSearchSaved(String id) {
        long limit = System.currentTimeMillis() + 10_000;
        while (!clusterSearchJpaRepository.existsById(id)) {
            if (System.currentTimeMillis() > limit) {
                throw new AssertionError("The search " + id + " was not saved");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
