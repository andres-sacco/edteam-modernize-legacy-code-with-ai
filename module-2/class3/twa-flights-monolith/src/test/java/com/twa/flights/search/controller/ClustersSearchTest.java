package com.twa.flights.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.twa.flights.search.job.ClusterSearchCleanupJob;
import com.twa.flights.search.model.ClusterSearch;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Json;

/**
 * Characterization of the search through the public API with the recorded responses of the providers: the contract of
 * the response, the saved search and the pagination (behaviors #8, #14, #16, #17, #27, #29, #30 and #31).
 *
 * With the recorded responses the search has 12 itineraries: 5 of Alpha, 3 of Beta and 4 of Gamma.
 */
class ClustersSearchTest extends AbstractIntegrationTest {

    private static final String ITINERARIES = "/clusters/itineraries";

    @Autowired
    private ClusterSearchCleanupJob cleanupJob;

    @Test
    @DisplayName("[#8] the response has id, pagination and itineraries with the attributes in lowerCamelCase")
    void contractOfTheResponse() {
        providers.allReturnRecordedRoundTrip();

        JsonNode body = Json.parse(search(roundTripRequest()).getBody());

        assertThat(fields(body)).containsExactlyInAnyOrder("id", "pagination", "itineraries");
        assertThat(fields(body.get("pagination"))).containsExactlyInAnyOrder("offset", "limit", "total");
        JsonNode itinerary = body.get("itineraries").get(0);
        assertThat(fields(itinerary)).containsExactlyInAnyOrder("id", "segments", "priceInfo", "provider",
                "flightType", "carrier", "cabin", "baggage");
        assertThat(fields(itinerary.at("/segments/0"))).containsExactlyInAnyOrder("legs", "flightDuration");
        assertThat(fields(itinerary.at("/segments/0/legs/0"))).containsExactlyInAnyOrder("origin", "destination",
                "departureDate", "departureTime", "arrivalDate", "arrivalTime", "number", "flightDuration");
        assertThat(fields(itinerary.get("priceInfo"))).containsExactlyInAnyOrder("adults", "children", "infants");
        assertThat(fields(itinerary.at("/priceInfo/adults"))).containsExactlyInAnyOrder("tax", "base", "quantity",
                "subtotal", "total", "type", "markup");
        assertThat(fields(itinerary.at("/priceInfo/adults/markup"))).containsExactlyInAnyOrder("percentage",
                "amount");
        assertThat(fields(itinerary.get("baggage"))).containsExactlyInAnyOrder("carryOn", "checked");
        assertThat(fields(itinerary.at("/baggage/checked"))).containsExactlyInAnyOrder("pieces", "weightKg");
        assertThat(itinerary.get("flightType").asText()).isEqualTo("ROUND_TRIP");
        assertThat(itinerary.at("/priceInfo/adults/type").asText()).isEqualTo("ADULT");
    }

    @Test
    @DisplayName("[#8] without children or infants their prices are null in the JSON")
    void pricesOfPassengersThatAreNotRequested() {
        providers.allReturnRecordedRoundTrip();

        JsonNode itinerary = Json.parse(search(with(roundTripRequest(), "children", 0, "infants", 0)).getBody())
                .get("itineraries").get(0);

        assertThat(itinerary.at("/priceInfo/children").isNull()).isTrue();
        assertThat(itinerary.at("/priceInfo/infants").isNull()).isTrue();
    }

    @Test
    @DisplayName("[#14] the id has the timestamp and the parameters of the request")
    void idOfTheSearch() {
        providers.allReturnRecordedRoundTrip();

        String id = Json.parse(search(roundTripRequest()).getBody()).get("id").asText();

        assertThat(id).matches("TWA_g=\\d{4}-\\d{2}-\\d{2}T[\\d:.]+_f=BUE,MIA_t=MIA,BUE_d=" + roundTripDates()
                + "_a=1_c=1_i=1_cb=ECONOMY");
    }

    @Test
    @DisplayName("[#16] without amount all the itineraries are in one page: limit = total")
    void withoutAmount() {
        providers.allReturnRecordedRoundTrip();

        JsonNode body = Json.parse(search(roundTripRequest()).getBody());

        assertThat(body.get("pagination")).isEqualTo(Json.parse("{\"offset\":0,\"limit\":12,\"total\":12}"));
        assertThat(body.get("itineraries")).hasSize(12);
    }

    @Test
    @DisplayName("[#16] the amount is applied to each provider: amount=2 asks 2 to each one and the total is 5, not 2")
    void amountByProvider() {
        providers.allReturnRecordedRoundTrip();

        JsonNode body = Json.parse(search(with(roundTripRequest(), "amount", 2)).getBody());

        // Alpha 2 (AA, AR), Beta 1 (the LA option has no policy) and Gamma 2 (H2, H2)
        assertThat(body.get("pagination")).isEqualTo(Json.parse("{\"offset\":0,\"limit\":2,\"total\":5}"));
        assertThat(body.get("itineraries")).hasSize(2);
    }

    @Test
    @DisplayName("[#17 #20] the offset is the number of the page and the pages keep the order by price")
    void pagination() {
        providers.allReturnRecordedRoundTrip();
        Map<String, Object> request = with(roundTripRequest(), "amount", 2);
        JsonNode first = Json.parse(search(request).getBody());
        String id = first.get("id").asText();
        awaitSearchSaved(id);

        JsonNode page0 = Json.parse(search(with(request, "id", id, "offset", 0)).getBody());
        JsonNode page1 = Json.parse(search(with(request, "id", id, "offset", 1)).getBody());
        JsonNode page2 = Json.parse(search(with(request, "id", id, "offset", 2)).getBody());
        JsonNode page3 = Json.parse(search(with(request, "id", id, "offset", 3)).getBody());

        assertThat(ids(page0)).isEqualTo(ids(first));
        assertThat(page1.get("pagination")).isEqualTo(Json.parse("{\"offset\":1,\"limit\":2,\"total\":5}"));
        assertThat(page1.get("id").asText()).isEqualTo(id);
        assertThat(ids(page1)).hasSize(2);
        assertThat(ids(page2)).hasSize(1);
        assertThat(ids(page3)).isEmpty();
        assertThat(page3.get("pagination")).isEqualTo(Json.parse("{\"offset\":3,\"limit\":2,\"total\":5}"));

        List<String> all = Lists.newArrayList(ids(page0));
        all.addAll(ids(page1));
        all.addAll(ids(page2));
        assertThat(Sets.newHashSet(all)).hasSize(5);
        assertThat(totals(page0, page1, page2)).isSorted();
        // The providers are not called again
        assertThat(providers.requests("/alpha")).hasSize(1);
    }

    @Test
    @DisplayName("[#17 #19] each page applies again the filters and the order, total is the amount after the filters")
    void filtersInThePagination() {
        providers.allReturnRecordedRoundTrip();
        JsonNode first = Json.parse(search(roundTripRequest()).getBody());
        String id = first.get("id").asText();
        awaitSearchSaved(id);

        JsonNode onlyAR = Json.parse(search(with(roundTripRequest(), "id", id, "offset", 0, "carriers", "AR",
                "sortBy", "PRICE", "sortOrder", "DESC")).getBody());

        // AR: 2 of Alpha and 3 of Beta
        assertThat(onlyAR.at("/pagination/total").asInt()).isEqualTo(5);
        assertThat(onlyAR.at("/pagination/limit").asInt()).isEqualTo(5);
        Set<String> carriers = Sets.newHashSet();
        onlyAR.get("itineraries").forEach(itinerary -> carriers.add(itinerary.get("carrier").asText()));
        assertThat(carriers).containsExactly("AR");
        assertThat(Lists.reverse(totals(onlyAR))).isSorted();
    }

    @Test
    @DisplayName("[#31] with an id the result comes from the saved search, even if the other parameters are different")
    void paginationIgnoresTheParameters() {
        providers.allReturnRecordedRoundTrip();
        String id = Json.parse(search(roundTripRequest()).getBody()).get("id").asText();
        awaitSearchSaved(id);

        JsonNode other = Json.parse(search(with(roundTripRequest(), "from", "SCL", "to", "LIM", "departure",
                departureDate(), "id", id, "offset", 0)).getBody());

        assertThat(other.at("/pagination/total").asInt()).isEqualTo(12);
        assertThat(other.at("/itineraries/0/segments/0/legs/0/origin").asText()).isEqualTo("BUE");
    }

    @Test
    @DisplayName("[#30] with an id and without offset the response is 500")
    void paginationWithoutOffset() {
        providers.allReturnRecordedRoundTrip();
        String id = Json.parse(search(roundTripRequest()).getBody()).get("id").asText();
        awaitSearchSaved(id);

        assertThat(search(with(roundTripRequest(), "id", id)).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("[#14 #7] the pagination of an unknown search responds 502 with the code 40015 and without description")
    void paginationOfUnknownSearch() {
        ResponseEntity<String> response = search(with(roundTripRequest(), "id", "TWA_unknown", "offset", 0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(Json.parse(response.getBody()))
                .isEqualTo(Json.parse("{\"code\":40015,\"shortDescription\":null,\"reasons\":null}"));
    }

    @Test
    @DisplayName("[#14] an expired search can not be paginated (502) and the job removes it")
    void expiredSearch() {
        String expired = saveSearch("TWA_expired", LocalDateTime.now().minusMinutes(1));
        String valid = saveSearch("TWA_valid", LocalDateTime.now().plusMinutes(5));

        ResponseEntity<String> response = search(with(roundTripRequest(), "id", expired, "offset", 0));
        ResponseEntity<String> itinerary = get("/clusters/" + expired + "/itineraries/ar", Map.of(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(itinerary.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // [#29] the cleanup job removes only the expired searches
        cleanupJob.removeExpiredSearches();
        assertThat(clusterSearchJpaRepository.existsById(expired)).isFalse();
        assertThat(clusterSearchJpaRepository.existsById(valid)).isTrue();
        clusterSearchJpaRepository.deleteById(valid);
    }

    @Test
    @DisplayName("[#14 #27] the search is saved in snake_case with a TTL of 5 minutes")
    void savedSearch() {
        providers.allReturnRecordedRoundTrip();
        String id = Json.parse(search(roundTripRequest()).getBody()).get("id").asText();
        awaitSearchSaved(id);

        ClusterSearch saved = clusterSearchJpaRepository.findById(id).orElseThrow();

        assertThat(Duration.between(saved.getCreatedAt(), saved.getExpireAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(saved.getPayload()).contains("\"price_info\"", "\"flight_type\"", "\"weight_kg\"")
                .doesNotContain("\"priceInfo\"");
    }

    @Test
    @DisplayName("[#14] GET /clusters/{id}/itineraries/{itineraryId} returns the itinerary of the saved search")
    void itineraryOfTheSearch() {
        providers.allReturnRecordedRoundTrip();
        JsonNode body = Json.parse(search(roundTripRequest()).getBody());
        String id = body.get("id").asText();
        JsonNode itinerary = body.get("itineraries").get(0);
        awaitSearchSaved(id);

        // The id has commas, colons and equals: it has to be encoded in the path
        ResponseEntity<String> response = restTemplate.getForEntity(java.net.URI.create(restTemplate.getRootUri())
                .resolve(CONTEXT_PATH + "/clusters/" + UriUtils.encodePathSegment(id, "UTF-8") + "/itineraries/"
                        + itinerary.get("id").asText()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Json.parse(response.getBody())).isEqualTo(itinerary);
    }

    @Test
    @DisplayName("[#14 #7] an unknown search responds 404 40401 and an unknown itinerary 404 40402")
    void unknownSearchOrItinerary() {
        String id = saveSearch("TWA_search", LocalDateTime.now().plusMinutes(5));

        ResponseEntity<String> unknownSearch = get("/clusters/unknown/itineraries/ar", Map.of(), String.class);
        ResponseEntity<String> unknownItinerary = get("/clusters/" + id + "/itineraries/unknown", Map.of(),
                String.class);
        ResponseEntity<String> known = get("/clusters/" + id + "/itineraries/ar", Map.of(), String.class);

        assertThat(unknownSearch.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Json.parse(unknownSearch.getBody())).isEqualTo(Json.parse(
                "{\"code\":40401,\"shortDescription\":\"The search does not exist or it is expired\",\"reasons\":null}"));
        assertThat(unknownItinerary.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Json.parse(unknownItinerary.getBody())).isEqualTo(Json.parse(
                "{\"code\":40402,\"shortDescription\":\"The itinerary does not exist in the search\",\"reasons\":null}"));
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.OK);
        clusterSearchJpaRepository.deleteById(id);
    }

    // private function(s)

    private String saveSearch(String id, LocalDateTime expireAt) {
        ClusterSearch search = new ClusterSearch();
        search.setId(id);
        search.setPayload("{\"id\":\"" + id + "\",\"pagination\":{\"offset\":0,\"limit\":1,\"total\":1},"
                + "\"itineraries\":[{\"id\":\"ar\",\"carrier\":\"AR\",\"segments\":[],\"price_info\":null}]}");
        search.setCreatedAt(expireAt.minusMinutes(5));
        search.setExpireAt(expireAt);
        clusterSearchJpaRepository.saveAndFlush(search);
        return id;
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> fields = Sets.newHashSet();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = Lists.newArrayList();
        page.get("itineraries").forEach(itinerary -> ids.add(itinerary.get("id").asText()));
        return ids;
    }

    private static List<java.math.BigDecimal> totals(JsonNode... pages) {
        List<java.math.BigDecimal> totals = Lists.newArrayList();
        for (JsonNode page : pages) {
            page.get("itineraries").forEach(itinerary -> {
                JsonNode priceInfo = itinerary.get("priceInfo");
                java.math.BigDecimal total = java.math.BigDecimal.ZERO;
                for (String type : List.of("adults", "children", "infants")) {
                    if (!priceInfo.get(type).isNull()) {
                        total = total.add(priceInfo.get(type).get("total").decimalValue());
                    }
                }
                totals.add(total);
            });
        }
        return totals;
    }
}
