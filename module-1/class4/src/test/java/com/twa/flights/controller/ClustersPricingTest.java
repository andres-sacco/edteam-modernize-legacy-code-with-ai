package com.twa.flights.controller;

import static com.twa.flights.support.Json.decimal;
import static com.twa.flights.support.Json.findById;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Json;

/**
 * Characterization of the prices that the search returns with the recorded responses of the three providers
 * (behaviors #1, #3, #4, #5 and #9). The request has 1 adult, 1 child and 1 infant, like the recorded responses.
 */
class ClustersPricingTest extends AbstractIntegrationTest {

    // AR of the provider Alpha: ADT 322.62 + 64.67, CHD 95.70 + 123.26, INF 119.29 + 29.98
    private static final String ALPHA_AR = "adb49624-bf8c-4da6-904e-2892cba9f9b6";
    // Carriers without a commercial policy
    private static final String BETA_LA = "f8466941-3630-4df4-a495-c617a5fbde15";
    private static final String BETA_FY = "af182674-446e-4048-a3c7-8dd012920f96";

    private static final String GENERAL_POLICY_AR = "/pricing/commercial-policies/1/carriers/AR";

    @AfterEach
    void restoreGeneralPolicy() {
        changeGeneralPercentageOfAR("5");
    }

    @Test
    @DisplayName("[#1 #9] the search returns the markup of each passenger type with the scale of BigDecimal, without rounding")
    void pricesOfTheSearchAreNotRounded() {
        providers.allReturnRecordedRoundTrip();

        ResponseEntity<String> response = search(roundTripRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode priceInfo = findById(Json.parse(response.getBody()).get("itineraries"), ALPHA_AR).get("priceInfo");

        assertPaxPrice(priceInfo.get("adults"), "322.62", "64.67", "387.29", "5.00", "193.6450", "580.9350");
        assertPaxPrice(priceInfo.get("children"), "95.70", "123.26", "218.96", "5.00", "109.4800", "328.4400");
        assertPaxPrice(priceInfo.get("infants"), "119.29", "29.98", "149.27", "5.00", "74.6350", "223.9050");
        // The exact text of the JSON, a client that parses the numbers as text depends on it
        assertThat(response.getBody()).contains("\"total\":580.9350", "\"percentage\":5.00");
    }

    @Test
    @DisplayName("[#3] the itineraries of carriers without a policy (LA, FY) are not in the response")
    void itinerariesWithoutPolicyAreNotReturned() {
        providers.allReturnRecordedRoundTrip();

        JsonNode body = Json.parse(search(roundTripRequest()).getBody());

        List<String> ids = Lists.newArrayList();
        body.get("itineraries").forEach(itinerary -> ids.add(itinerary.get("id").asText()));
        List<String> carriers = Lists.newArrayList();
        body.get("itineraries").forEach(itinerary -> carriers.add(itinerary.get("carrier").asText()));

        // 15 itineraries recorded: 5 Alpha, 5 Beta (LA and FY without policy) and 5 Gamma (the LA one is discarded
        // before the pricing because of its duration, see ProvidersSearchTest)
        assertThat(ids).hasSize(12).doesNotContain(BETA_LA, BETA_FY);
        assertThat(carriers).doesNotContain("LA", "FY");
        assertThat(body.at("/pagination/total").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("[#4] a change of a policy is used by the next search, the saved searches keep the old price")
    void policyChangesAffectOnlyTheNewSearches() {
        providers.allReturnRecordedRoundTrip();

        JsonNode before = Json.parse(search(roundTripRequest()).getBody());
        String savedSearch = before.get("id").asText();
        awaitSearchSaved(savedSearch);

        changeGeneralPercentageOfAR("9");

        JsonNode after = Json.parse(search(roundTripRequest()).getBody());
        assertThat(decimal(findById(after.get("itineraries"), ALPHA_AR).at("/priceInfo/adults/markup/percentage")))
                .isEqualTo("9.00");
        assertThat(decimal(findById(after.get("itineraries"), ALPHA_AR).at("/priceInfo/adults/total")))
                .isEqualTo("735.8510");

        // The pagination of the first search uses the snapshot saved with the old policy
        JsonNode page = Json.parse(
                search(with(roundTripRequest(), "id", savedSearch, "offset", 0)).getBody());
        assertThat(decimal(findById(page.get("itineraries"), ALPHA_AR).at("/priceInfo/adults/markup/percentage")))
                .isEqualTo("5.00");
        assertThat(decimal(findById(page.get("itineraries"), ALPHA_AR).at("/priceInfo/adults/total")))
                .isEqualTo("580.9350");
    }

    @Test
    @DisplayName("[#5] the search is public, it does not need the API key of the pricing")
    void searchDoesNotNeedTheApiKey() {
        providers.allReturnRecordedRoundTrip();

        assertThat(search(roundTripRequest()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // private function(s)

    private void assertPaxPrice(JsonNode paxPrice, String base, String tax, String subtotal, String percentage,
            String markup, String total) {
        assertThat(decimal(paxPrice.get("base"))).isEqualTo(base);
        assertThat(decimal(paxPrice.get("tax"))).isEqualTo(tax);
        assertThat(paxPrice.get("quantity").asInt()).isEqualTo(1);
        assertThat(decimal(paxPrice.get("subtotal"))).isEqualTo(subtotal);
        assertThat(decimal(paxPrice.at("/markup/percentage"))).isEqualTo(percentage);
        assertThat(decimal(paxPrice.at("/markup/amount"))).isEqualTo(markup);
        assertThat(decimal(paxPrice.get("total"))).isEqualTo(total);
    }

    private void changeGeneralPercentageOfAR(String percentage) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(uri(GENERAL_POLICY_AR, Map.of()), HttpMethod.PUT,
                new HttpEntity<>("{\"percentage\":" + percentage + "}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
