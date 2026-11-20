package com.twa.flights.controller;

import static com.twa.flights.support.Json.decimal;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.twa.flights.repository.CommercialPolicyRepository;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Json;

/**
 * Characterization of the administration of the commercial policies (behaviors #5, #7, #25 and #28). The seed
 * policies are 1 General (priority 10), 2 Special Carrier (5) and 3 Ciber Week (1). The tests only modify the
 * policies that they create, with carriers that do not exist in the seed data (ZZ, ZY, ZX, ZW).
 */
class CommercialPolicyControllerTest extends AbstractIntegrationTest {

    private static final String POLICIES = "/pricing/commercial-policies";

    @Autowired
    private CommercialPolicyRepository commercialPolicyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdPolicies = Lists.newArrayList();

    @AfterEach
    void removeTestPolicies() {
        commercialPolicyRepository.deleteAllById(createdPolicies);
        createdPolicies.clear();
    }

    @Test
    @DisplayName("[#5 #7] without the X-Api-Key header the administration responds 401 with the code 40101")
    void withoutApiKeyResponds401() {
        ResponseEntity<String> response = restTemplate.getForEntity(uri(POLICIES, Map.of()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Json.parse(response.getBody())).isEqualTo(Json.parse(
                "{\"code\":40101,\"shortDescription\":\"The API key is missing or it is invalid\",\"reasons\":null}"));
    }

    @Test
    @DisplayName("[#5] with an invalid X-Api-Key the administration responds 401, also in the writes")
    void withInvalidApiKeyResponds401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", "invalid-key");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> list = restTemplate.exchange(uri(POLICIES, Map.of()), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        ResponseEntity<String> delete = restTemplate.exchange(uri(POLICIES + "/1", Map.of()), HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Json.parse(delete.getBody()).get("code").asInt()).isEqualTo(40101);
        // The policy is not deleted
        assertThat(call(HttpMethod.GET, POLICIES + "/1", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("[#28] the list is sorted by priority descending and it can be filtered by active and carrier")
    void listOfPolicies() {
        assertThat(ids(call(HttpMethod.GET, POLICIES, null))).containsExactly(1L, 2L, 3L);
        assertThat(ids(call(HttpMethod.GET, POLICIES + "?carrier=ar", null))).containsExactly(1L, 2L);
        assertThat(ids(call(HttpMethod.GET, POLICIES + "?carrier=DL", null))).containsExactly(1L, 3L);
        assertThat(ids(call(HttpMethod.GET, POLICIES + "?active=true", null))).containsExactly(1L, 2L, 3L);
        assertThat(ids(call(HttpMethod.GET, POLICIES + "?active=false", null))).isEmpty();
    }

    @Test
    @DisplayName("[#28] with the same priority the policies are sorted by id")
    void samePriorityIsSortedById() {
        long first = create(policyJson("Tie A", 10, null, "{\"carrier\":\"ZZ\",\"percentage\":1}"));
        long second = create(policyJson("Tie B", 10, null, "{\"carrier\":\"ZZ\",\"percentage\":1}"));

        assertThat(ids(call(HttpMethod.GET, POLICIES + "?carrier=zz", null))).containsExactly(first, second);
        assertThat(ids(call(HttpMethod.GET, POLICIES, null))).startsWith(1L, first, second, 2L, 3L);
    }

    @Test
    @DisplayName("[#25] the seed policy General has the 15 carriers of the migrations V1.3 and V1.6")
    void seedPolicy() {
        JsonNode general = Json.parse(call(HttpMethod.GET, POLICIES + "/1", null).getBody());

        assertThat(general.get("name").asText()).isEqualTo("General");
        assertThat(general.get("priority").asInt()).isEqualTo(10);
        assertThat(general.get("active").asBoolean()).isTrue();
        assertThat(general.get("startDate").asText()).isEqualTo("2024-01-01T00:00:00");
        assertThat(general.get("expireDate").asText()).isEqualTo("2030-01-01T00:00:00");
        assertThat(percentages(general)).containsOnlyKeys("AR", "AA", "IB", "AL", "AF", "BA", "DL", "KL", "LH", "QF",
                "CM", "AV", "G3", "JA", "H2").containsEntry("AR", "5.00").containsEntry("G3", "2.00");
        // There is not an ORDER BY: MySQL returns the carriers sorted by the unique index of V1.7 (carrier code)
        assertThat(general.get("carriers").get(0).get("carrier").asText()).isEqualTo("AA");
    }

    @Test
    @DisplayName("[#25] POST responds 201 with Location, active by default, the name trimmed and the carriers in upper case")
    void createPolicy() {
        ResponseEntity<String> response = call(HttpMethod.POST, POLICIES, policyJson("  Promo  ", 4, null,
                "{\"carrier\":\" zz \",\"percentage\":2.5}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = Json.parse(response.getBody());
        long id = created.get("id").asLong();
        createdPolicies.add(id);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create(restTemplate.getRootUri()).resolve(CONTEXT_PATH + POLICIES + "/" + id));
        assertThat(created.get("name").asText()).isEqualTo("Promo");
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("carriers").get(0).get("carrier").asText()).isEqualTo("ZZ");
        // The response of the creation has the percentage of the request, the database keeps two decimals
        assertThat(decimal(created.get("carriers").get(0).get("percentage"))).isEqualTo("2.5");
        assertThat(created.get("createdAt").isNull()).isFalse();
        assertThat(created.get("updatedAt").isNull()).isFalse();

        JsonNode saved = Json.parse(call(HttpMethod.GET, POLICIES + "/" + id, null).getBody());
        assertThat(decimal(saved.get("carriers").get(0).get("percentage"))).isEqualTo("2.50");
    }

    @Test
    @DisplayName("[#25] PUT replaces everything: carriers removed, kept with the same id and added; without active it is activated")
    void updatePolicyReplacesEverything() {
        long id = create(policyJson("Before", 4, false, "{\"carrier\":\"ZZ\",\"percentage\":1}",
                "{\"carrier\":\"ZY\",\"percentage\":2}"));
        Long carrierIdOfZY = carrierIds(id).get("ZY");

        ResponseEntity<String> response = call(HttpMethod.PUT, POLICIES + "/" + id, policyJson("After", 6, null,
                "{\"carrier\":\"zy\",\"percentage\":7}", "{\"carrier\":\"ZX\",\"percentage\":3}"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updated = Json.parse(response.getBody());
        assertThat(updated.get("name").asText()).isEqualTo("After");
        assertThat(updated.get("priority").asInt()).isEqualTo(6);
        assertThat(updated.get("active").asBoolean()).isTrue();
        assertThat(carrierIds(id)).containsOnlyKeys("ZY", "ZX").containsEntry("ZY", carrierIdOfZY);
        JsonNode saved = Json.parse(call(HttpMethod.GET, POLICIES + "/" + id, null).getBody());
        assertThat(percentages(saved)).containsEntry("ZY", "7.00").containsEntry("ZX", "3.00");
    }

    @Test
    @DisplayName("[#25] PATCH /status changes only the status")
    void changeStatus() {
        long id = create(policyJson("Status", 4, null, "{\"carrier\":\"ZZ\",\"percentage\":1}"));

        ResponseEntity<String> response = call(HttpMethod.PATCH, POLICIES + "/" + id + "/status",
                "{\"active\":false}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Json.parse(response.getBody()).get("active").asBoolean()).isFalse();
        assertThat(ids(call(HttpMethod.GET, POLICIES + "?active=false", null))).containsExactly(id);
    }

    @Test
    @DisplayName("[#25 #7] DELETE responds 204 and then the policy does not exist (404 with the code 40403)")
    void deletePolicy() {
        long id = create(policyJson("Delete", 4, null, "{\"carrier\":\"ZZ\",\"percentage\":1}"));

        ResponseEntity<String> deleted = call(HttpMethod.DELETE, POLICIES + "/" + id, null);
        ResponseEntity<String> afterDelete = call(HttpMethod.GET, POLICIES + "/" + id, null);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Json.parse(afterDelete.getBody())).isEqualTo(Json.parse(
                "{\"code\":40403,\"shortDescription\":\"The commercial policy does not exist\",\"reasons\":null}"));
        assertThat(carrierIds(id)).isEmpty();
    }

    @Test
    @DisplayName("[#25] PUT /carriers/{carrier} adds a new carrier or modifies the existing one")
    void saveCarrier() {
        long id = create(policyJson("Carriers", 4, null, "{\"carrier\":\"ZZ\",\"percentage\":1}"));

        call(HttpMethod.PUT, POLICIES + "/" + id + "/carriers/zw", "{\"percentage\":4}");
        ResponseEntity<String> response = call(HttpMethod.PUT, POLICIES + "/" + id + "/carriers/ZZ",
                "{\"percentage\":6}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode saved = Json.parse(call(HttpMethod.GET, POLICIES + "/" + id, null).getBody());
        assertThat(percentages(saved)).containsOnlyKeys("ZZ", "ZW").containsEntry("ZZ", "6.00").containsEntry("ZW",
                "4.00");
    }

    @Test
    @DisplayName("[#25] DELETE /carriers/{carrier}: 404 40404 if it does not exist and 400 40026 for the last carrier")
    void deleteCarrier() {
        long id = create(policyJson("Carriers", 4, null, "{\"carrier\":\"ZZ\",\"percentage\":1}",
                "{\"carrier\":\"ZY\",\"percentage\":2}"));

        ResponseEntity<String> deleted = call(HttpMethod.DELETE, POLICIES + "/" + id + "/carriers/zy", null);
        ResponseEntity<String> unknown = call(HttpMethod.DELETE, POLICIES + "/" + id + "/carriers/ZX", null);
        ResponseEntity<String> last = call(HttpMethod.DELETE, POLICIES + "/" + id + "/carriers/ZZ", null);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(percentages(Json.parse(deleted.getBody()))).containsOnlyKeys("ZZ");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Json.parse(unknown.getBody()).get("code").asInt()).isEqualTo(40404);
        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Json.parse(last.getBody()).get("code").asInt()).isEqualTo(40026);
    }

    @Test
    @DisplayName("[#25 #7] a policy that does not exist responds 404 with the code 40403")
    void unknownPolicy() {
        ResponseEntity<String> response = call(HttpMethod.GET, POLICIES + "/999999", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Json.parse(response.getBody()).get("code").asInt()).isEqualTo(40403);
    }

    static Stream<Arguments> invalidPolicies() {
        String carrier = "{\"carrier\":\"ZZ\",\"percentage\":1}";
        return Stream.of(Arguments.of("blank name", policyJson(" ", 1, null, carrier), 40023),
                Arguments.of("name of 51 characters", policyJson("x".repeat(51), 1, null, carrier), 40023),
                Arguments.of("without priority", policyJson("Name", null, null, carrier), 40024),
                Arguments.of("negative priority", policyJson("Name", -1, null, carrier), 40024),
                Arguments.of("start equal to expire",
                        "{\"name\":\"Name\",\"priority\":1,\"startDate\":\"2027-01-01T00:00:00\","
                                + "\"expireDate\":\"2027-01-01T00:00:00\",\"carriers\":[" + carrier + "]}",
                        40025),
                Arguments.of("without dates", "{\"name\":\"Name\",\"priority\":1,\"carriers\":[" + carrier + "]}",
                        40025),
                Arguments.of("without carriers", policyJson("Name", 1, null), 40026),
                Arguments.of("duplicated carriers", policyJson("Name", 1, null, carrier,
                        "{\"carrier\":\"zz\",\"percentage\":2}"), 40026),
                Arguments.of("carrier of three characters",
                        policyJson("Name", 1, null, "{\"carrier\":\"ZZZ\",\"percentage\":1}"), 40027),
                Arguments.of("percentage higher than 100",
                        policyJson("Name", 1, null, "{\"carrier\":\"ZZ\",\"percentage\":100.01}"), 40028),
                Arguments.of("negative percentage",
                        policyJson("Name", 1, null, "{\"carrier\":\"ZZ\",\"percentage\":-1}"), 40028),
                Arguments.of("without percentage", policyJson("Name", 1, null, "{\"carrier\":\"ZZ\"}"), 40028));
    }

    @ParameterizedTest(name = "[#25 #7] {0} -> {2}")
    @MethodSource("invalidPolicies")
    void invalidPolicy(String description, String body, int code) {
        ResponseEntity<String> response = call(HttpMethod.POST, POLICIES, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode error = Json.parse(response.getBody());
        assertThat(error.get("code").asInt()).isEqualTo(code);
        assertThat(error.get("shortDescription").asText()).isNotBlank();
        assertThat(error.get("reasons").isNull()).isTrue();
    }

    @Test
    @DisplayName("[#25] the status is required (40029) and the carrier of the path is validated (40027)")
    void invalidStatusAndCarrier() {
        ResponseEntity<String> status = call(HttpMethod.PATCH, POLICIES + "/1/status", "{}");
        ResponseEntity<String> carrier = call(HttpMethod.PUT, POLICIES + "/1/carriers/ARG", "{\"percentage\":1}");
        ResponseEntity<String> percentage = call(HttpMethod.PUT, POLICIES + "/1/carriers/AR", "{\"percentage\":101}");

        assertThat(Json.parse(status.getBody()).get("code").asInt()).isEqualTo(40029);
        assertThat(Json.parse(carrier.getBody()).get("code").asInt()).isEqualTo(40027);
        assertThat(Json.parse(percentage.getBody()).get("code").asInt()).isEqualTo(40028);
    }

    // private function(s)

    private static String policyJson(String name, Integer priority, Boolean active, String... carriers) {
        return "{\"name\":\"" + name + "\",\"priority\":" + priority + (active != null ? ",\"active\":" + active : "")
                + ",\"startDate\":\"2026-01-01T00:00:00\",\"expireDate\":\"2029-01-01T00:00:00\",\"carriers\":["
                + String.join(",", carriers) + "]}";
    }

    private long create(String body) {
        ResponseEntity<String> response = call(HttpMethod.POST, POLICIES, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = Json.parse(response.getBody()).get("id").asLong();
        createdPolicies.add(id);
        return id;
    }

    private ResponseEntity<String> call(HttpMethod method, String pathAndQuery, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(URI.create(restTemplate.getRootUri()).resolve(CONTEXT_PATH + pathAndQuery),
                method, new HttpEntity<>(body, headers), String.class);
    }

    private List<Long> ids(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = Lists.newArrayList();
        Json.parse(response.getBody()).forEach(policy -> ids.add(policy.get("id").asLong()));
        return ids;
    }

    private Map<String, String> percentages(JsonNode policy) {
        Map<String, String> percentages = new java.util.LinkedHashMap<>();
        policy.get("carriers").forEach(carrier -> percentages.put(carrier.get("carrier").asText(),
                decimal(carrier.get("percentage"))));
        return percentages;
    }

    private Map<String, Long> carrierIds(long policyId) {
        Map<String, Long> ids = new java.util.HashMap<>();
        jdbcTemplate.query("select id, carrier from commercial_policy_carrier where commercial_policy_id = ?",
                rs -> {
                    ids.put(rs.getString("carrier"), rs.getLong("id"));
                }, policyId);
        return ids;
    }
}
