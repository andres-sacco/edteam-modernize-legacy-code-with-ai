package com.twa.flights.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Json;
import com.twa.flights.support.ProviderStubs;

/**
 * The search with the catalog service (catalog.mode=REMOTE) replaced by WireMock: when the catalog is not available
 * the search answers 503 with the error 50301 and does not save anything. It never answers 40014 (an error of the
 * request) or 200 with fewer itineraries. This is the condition to close the stage 1 of the extraction plan.
 *
 * Without the cache: a city cached by a previous test would hide the failure of the catalog.
 */
class CatalogUnavailableSearchTest extends AbstractIntegrationTest {

    private static final int TIMEOUT = 300;

    @DynamicPropertySource
    static void catalogService(DynamicPropertyRegistry registry) {
        registry.add("catalog.mode", () -> "REMOTE");
        registry.add("catalog.cache.enabled", () -> "false");
        registry.add("catalog.remote.connectionTimeout", () -> TIMEOUT);
        registry.add("catalog.remote.readTimeout", () -> TIMEOUT);
    }

    @BeforeEach
    void setUp() {
        providers.allReturnRecordedRoundTrip();
        clusterSearchJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("the catalog service answers: the search is 200 with itineraries, the same as the local catalog")
    void catalogAvailable() {
        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Json.parse(response.getBody());
        assertThat(body.get("itineraries")).isNotEmpty();
        // The search is saved, and the next test does not find it in the middle of its own check
        awaitSearchSaved(body.get("id").asText());
    }

    @Test
    @DisplayName("[#6] an unknown city is still the error 40014 in remote mode")
    void unknownCity() {
        ResponseEntity<String> response = search(with(roundTripRequest(), "from", "XXX,MIA", "to", "MIA,XXX"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Json.parse(response.getBody()).get("code").asInt()).isEqualTo(400_14);
    }

    @Test
    @DisplayName("a code that is not valid in a path (A/B) is an unknown city (40014), not a failure of the catalog")
    void codeThatIsNotValidInAPath() {
        ResponseEntity<String> response = search(with(roundTripRequest(), "from", "A/B,MIA", "to", "MIA,A/B"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Json.parse(response.getBody()).get("code").asInt()).isEqualTo(400_14);
    }

    @Test
    @DisplayName("the catalog is down during the validation: 503 with the error 50301, not 400 with the error 40014")
    void catalogDownInTheValidation() {
        catalog.down();

        assertUnavailable(search(roundTripRequest()));
        assertThat(providers.requests(ProviderStubs.ALPHA)).isEmpty();
    }

    @Test
    @DisplayName("the catalog does not answer in time during the validation: 503 with the error 50301")
    void catalogTimeoutInTheValidation() {
        catalog.available(TIMEOUT * 3);

        assertUnavailable(search(roundTripRequest()));
    }

    @Test
    @DisplayName("the catalog fails in the middle of the search: 503 and nothing saved, not 200 with fewer itineraries")
    void catalogDownInTheMiddleOfTheSearch() {
        // The validation of BUE and MIA works, the cities of the connections (SAO in alpha and gamma, LIM in beta) fail
        catalog.unavailableFor("SAO", "LIM");

        assertUnavailable(search(roundTripRequest()));
        assertThat(providers.requests(ProviderStubs.ALPHA)).isNotEmpty();
    }

    private void assertUnavailable(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = Json.parse(response.getBody());
        assertThat(body.get("code").asInt()).isEqualTo(503_01);
        assertThat(body.get("shortDescription").asText())
                .isEqualTo("The catalog of cities is not available, try again later");
        assertNothingSaved();
    }

    // The search is saved asynchronously after the response: it waits a moment before checking that there is nothing
    private void assertNothingSaved() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        assertThat(clusterSearchJpaRepository.count()).isZero();
    }
}
