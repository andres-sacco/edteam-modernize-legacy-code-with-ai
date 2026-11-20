package com.twa.flights.service;

import static com.twa.flights.support.Json.findById;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Fixtures;
import com.twa.flights.support.Json;
import com.twa.flights.support.ProviderStubs;

/**
 * Characterization of the search with the providers through the public API: the failures of a provider, the duration
 * of the flights with the catalog and the itineraries that are discarded (behaviors #10, #15 and #24).
 */
@ExtendWith(OutputCaptureExtension.class)
class ProvidersSearchTest extends AbstractIntegrationTest {

    private static final String ALPHA_AR = "adb49624-bf8c-4da6-904e-2892cba9f9b6";
    private static final String ALPHA_AR_WITH_CONNECTION = "e485161b-e5e1-44fa-a786-3aaab579c6a6";
    private static final String GAMMA_LA = "df3a40aa-5aa7-442f-b816-41572f15796b";

    @Test
    @DisplayName("[#10] if a provider responds with an error the search continues with the others")
    void providerWithError(CapturedOutput output) {
        providers.allReturnRecordedRoundTrip();
        providers.respondWithStatus(ProviderStubs.ALPHA, 500);

        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(providersOf(response)).containsExactlyInAnyOrder("BETA", "GAMMA");
        assertThat(output).contains("Error while calling endpoint /itineraries with status code 500");
    }

    @Test
    @DisplayName("[#10] if a provider takes more than 5 seconds the search continues without it")
    void providerWithTimeout() {
        providers.allReturnRecordedRoundTrip();
        providers.respondWithDelay(ProviderStubs.BETA, ProviderStubs.BETA_ROUND_TRIP, 6000);

        long start = System.currentTimeMillis();
        ResponseEntity<String> response = search(roundTripRequest());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(providersOf(response)).containsExactlyInAnyOrder("ALPHA", "GAMMA");
        // The read timeout of the connector is 5 seconds
        assertThat(elapsed).isBetween(5000L, 6000L);
    }

    @Test
    @DisplayName("[#10] if a provider returns a body that is not JSON the search continues without it")
    void providerWithInvalidBody() {
        providers.allReturnRecordedRoundTrip();
        providers.respondWithBody(ProviderStubs.GAMMA, "this is not json");

        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(providersOf(response)).containsExactlyInAnyOrder("ALPHA", "BETA");
    }

    @Test
    @DisplayName("[#10] if the three providers fail the response is 200 without itineraries and the search is saved anyway")
    void allProvidersFail() {
        providers.respondWithStatus(ProviderStubs.ALPHA, 500);
        providers.respondWithStatus(ProviderStubs.BETA, 503);
        providers.respondWithStatus(ProviderStubs.GAMMA, 400);

        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = Json.parse(response.getBody());
        assertThat(body.get("itineraries")).isEmpty();
        assertThat(body.get("pagination")).isEqualTo(Json.parse("{\"offset\":0,\"limit\":0,\"total\":0}"));
        awaitSearchSaved(body.get("id").asText());
    }

    @Test
    @DisplayName("[#15] the duration of each leg and segment uses the time zones of the cities of the catalog")
    void durationOfTheFlights() {
        providers.onlyAlphaReturns(Fixtures.read(ProviderStubs.ALPHA_ROUND_TRIP));

        JsonNode itineraries = Json.parse(search(roundTripRequest()).getBody()).get("itineraries");

        // BUE 11:00 (UTC-3) to MIA 18:45 (UTC-5) and MIA 09:45 to BUE 21:45
        JsonNode direct = findById(itineraries, ALPHA_AR);
        assertThat(direct.at("/segments/0/flightDuration").asText()).isEqualTo("09:45");
        assertThat(direct.at("/segments/0/legs/0/flightDuration").asText()).isEqualTo("09:45");
        assertThat(direct.at("/segments/1/flightDuration").asText()).isEqualTo("10:00");

        // BUE 02:15 to SAO 08:30 and SAO 12:45 to MIA 23:00: the segment goes from the first departure to the last
        // arrival, including the connection
        JsonNode withConnection = findById(itineraries, ALPHA_AR_WITH_CONNECTION);
        assertThat(withConnection.at("/segments/0/legs/0/flightDuration").asText()).isEqualTo("06:15");
        assertThat(withConnection.at("/segments/0/legs/1/flightDuration").asText()).isEqualTo("12:15");
        assertThat(withConnection.at("/segments/0/flightDuration").asText()).isEqualTo("22:45");
    }

    @Test
    @DisplayName("[#15] an itinerary that arrives before its departure is discarded and an ERROR is logged")
    void arrivalBeforeDepartureDiscardsTheItinerary(CapturedOutput output) {
        providers.allReturnRecordedRoundTrip();

        ResponseEntity<String> response = search(roundTripRequest());

        // The LA itinerary of Gamma: MIA 07:15 (UTC-5) to SAO 09:00 (UTC-3)
        assertThat(response.getBody()).doesNotContain(GAMMA_LA);
        assertThat(output).contains("Occur an error calculating the flight duration of id " + GAMMA_LA);
    }

    @Test
    @DisplayName("[#15] an itinerary with a city that is not in the catalog is discarded and an ERROR is logged")
    void cityOutsideTheCatalogDiscardsTheItinerary(CapturedOutput output) {
        ObjectNode alpha = Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP);
        ((ObjectNode) alpha.at("/results/1/journeys/0/flights/0")).put("to", "XYZ");
        providers.onlyAlphaReturns(alpha.toString());

        List<String> ids = idsOf(search(roundTripRequest()));

        assertThat(ids).hasSize(4).doesNotContain(ALPHA_AR);
        assertThat(output).contains("Occur an error calculating the flight duration of id " + ALPHA_AR);
    }

    @Test
    @DisplayName("[#24] an offer that can not be transformed is discarded with a WARN, the search continues")
    void malformedOfferIsDiscarded(CapturedOutput output) {
        ObjectNode alpha = Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP);
        ((ObjectNode) alpha.at("/results/1")).put("cabinClass", "X");
        providers.onlyAlphaReturns(alpha.toString());

        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idsOf(response)).hasSize(4).doesNotContain(ALPHA_AR);
        assertThat(output).contains("The offer " + ALPHA_AR + " of the provider alpha can not be transformed");
    }

    private Set<String> providersOf(ResponseEntity<String> response) {
        Set<String> providers = Sets.newHashSet();
        Json.parse(response.getBody()).get("itineraries")
                .forEach(itinerary -> providers.add(itinerary.get("provider").asText()));
        return providers;
    }

    private List<String> idsOf(ResponseEntity<String> response) {
        List<String> ids = Lists.newArrayList();
        Json.parse(response.getBody()).get("itineraries").forEach(itinerary -> ids.add(itinerary.get("id").asText()));
        return ids;
    }
}
