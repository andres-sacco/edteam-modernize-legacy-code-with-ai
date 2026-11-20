package com.twa.flights.search.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.twa.flights.enums.ExceptionStatus;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Json;

/**
 * Characterization of the validation of the search through the public API (behaviors #6, #7, #26, #30 and #32). The
 * dates are relative to today because the validator uses LocalDate.now().
 */
class ClustersValidationTest extends AbstractIntegrationTest {

    private static Map<String, Object> valid() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", "BUE");
        params.put("to", "MIA");
        params.put("departure", departureDate());
        params.put("adults", 1);
        params.put("children", 0);
        params.put("infants", 0);
        return params;
    }

    static Stream<Arguments> invalidRequests() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        return Stream.of(
                // Dates
                Arguments.of("without departure", with(valid(), "departure", null), 40003),
                Arguments.of("departure with another format", with(valid(), "departure", "14/01/2027"), 40007),
                Arguments.of("departure in the past", with(valid(), "departure", "2020-01-01"), 40009),
                Arguments.of("departure yesterday", with(valid(), "departure", yesterday), 40009),
                Arguments.of("dates in the wrong order",
                        with(valid(), "from", "BUE,MIA", "to", "MIA,BUE", "departure", returnDate() + ","
                                + departureDate()), 40008),
                // Passengers
                Arguments.of("zero adults", with(valid(), "adults", 0), 40004),
                Arguments.of("negative children", with(valid(), "children", -1), 40005),
                Arguments.of("negative infants", with(valid(), "infants", -1), 40006),
                Arguments.of("more than 9 adults and children", with(valid(), "adults", 5, "children", 5), 40010),
                // Origins and destinations
                Arguments.of("without origin", with(valid(), "from", null), 40001),
                Arguments.of("empty origin", with(valid(), "from", ""), 40001),
                Arguments.of("without destination", with(valid(), "to", null), 40002),
                Arguments.of("code of four characters", with(valid(), "from", "BUEN"), 40011),
                Arguments.of("code outside the catalog", with(valid(), "from", "XXX"), 40014),
                Arguments.of("code of two characters outside the catalog", with(valid(), "to", "MI"), 40014),
                Arguments.of("same origin and destination", with(valid(), "to", "BUE"), 40012),
                Arguments.of("duplicated routes",
                        with(valid(), "from", "BUE,BUE", "to", "MIA,MIA", "departure", departureDate() + ","
                                + returnDate()), 40013),
                // Sort, cabin and filters
                Arguments.of("unknown sort criteria", with(valid(), "sortBy", "NAME"), 40016),
                Arguments.of("unknown sort order", with(valid(), "sortOrder", "UP"), 40017),
                Arguments.of("unknown cabin", with(valid(), "cabin", "FIRST"), 40018),
                Arguments.of("negative maxStops", with(valid(), "maxStops", -1), 40019),
                Arguments.of("negative minPrice", with(valid(), "minPrice", -1), 40020),
                Arguments.of("minPrice higher than maxPrice", with(valid(), "minPrice", 10, "maxPrice", 5), 40020),
                Arguments.of("carrier of three characters", with(valid(), "carriers", "ARG"), 40021),
                Arguments.of("carriers without codes", with(valid(), "carriers", ","), 40021),
                Arguments.of("excluded carrier of one character", with(valid(), "excludedCarriers", "A"), 40021),
                Arguments.of("unknown departure time", with(valid(), "departureTime", "DAWN"), 40022));
    }

    @ParameterizedTest(name = "[#6 #7] {0} -> {2}")
    @MethodSource("invalidRequests")
    void invalidRequest(String description, Map<String, Object> params, int code) {
        ResponseEntity<String> response = search(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Json.parse(response.getBody())).isEqualTo(error(code));
    }

    static Stream<Arguments> severalErrors() {
        return Stream.of(
                Arguments.of("date in the past and zero adults",
                        with(valid(), "departure", "2020-01-01", "adults", 0), 40009),
                Arguments.of("zero adults and without origin", with(valid(), "adults", 0, "from", null), 40004),
                Arguments.of("origin outside the catalog and same origin and destination",
                        with(valid(), "from", "XXX", "to", "XXX"), 40014),
                Arguments.of("origin outside the catalog and unknown sort", with(valid(), "from", "XXX", "sortBy",
                        "NAME"), 40014),
                Arguments.of("unknown sort and unknown cabin", with(valid(), "sortBy", "NAME", "cabin", "FIRST"),
                        40016),
                Arguments.of("unknown cabin and negative maxStops", with(valid(), "cabin", "FIRST", "maxStops", -1),
                        40018));
    }

    @ParameterizedTest(name = "[#6] the first error wins: {0} -> {2}")
    @MethodSource("severalErrors")
    void firstErrorWins(String description, Map<String, Object> params, int code) {
        ResponseEntity<String> response = search(params);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Json.parse(response.getBody()).get("code").asInt()).isEqualTo(code);
    }

    @Test
    @DisplayName("[#6] the enums accept lower case and spaces")
    void enumsInAnyCase() {
        providers.allReturnRecordedRoundTrip();

        ResponseEntity<String> response = search(with(valid(), "sortBy", " duration ", "sortOrder", "desc", "cabin",
                "business", "departureTime", " Morning "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#6] a search for today is valid")
    void searchForToday() {
        providers.allReturnRecordedRoundTrip();

        assertThat(search(with(valid(), "departure", LocalDate.now().toString())).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#26] the codes of the cities are found in any case (collation of MySQL)")
    void cityCodeInLowerCase() {
        providers.allReturnRecordedRoundTrip();

        assertThat(search(with(valid(), "from", "bue", "to", "mia")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#32] the infants do not count in the maximum of 9 and they do not need an adult each")
    void infantsWithoutLimit() {
        providers.allReturnRecordedRoundTrip();

        assertThat(search(with(valid(), "adults", 9, "infants", 9)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search(with(valid(), "adults", 1, "infants", 5)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#30] without children or infants the response is 500 (unboxing of null), not a 400")
    void withoutChildrenOrInfants() {
        assertThat(search(with(valid(), "children", null)).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(search(with(valid(), "infants", null)).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("[#30] with more origins than destinations the response is 500 (IndexOutOfBoundsException)")
    void moreOriginsThanDestinations() {
        ResponseEntity<String> response = search(with(valid(), "from", "BUE,MIA", "to", "MIA", "departure",
                departureDate() + "," + returnDate()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // The default error of Spring Boot, not the ErrorDTO of the application
        JsonNode body = Json.parse(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.has("code")).isFalse();
    }

    @Test
    @DisplayName("[#30] with more destinations than origins only the routes of the origins are validated")
    void moreDestinationsThanOrigins() {
        providers.allReturnRecordedRoundTrip();

        assertThat(search(with(valid(), "from", "BUE", "to", "MIA,BUE")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#6] a parameter with a wrong type is a 400 of Spring, without the ErrorDTO")
    void parameterWithWrongType() {
        ResponseEntity<String> response = search(with(valid(), "adults", "one"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody() == null || !response.getBody().contains("\"code\"")).isTrue();
    }

    private static JsonNode error(int code) {
        ExceptionStatus status = java.util.Arrays.stream(ExceptionStatus.values())
                .filter(value -> value.getCode() == code).findFirst().orElseThrow();
        return Json.parse("{\"code\":" + code + ",\"shortDescription\":\"" + status.getMessage()
                + "\",\"reasons\":null}");
    }
}
