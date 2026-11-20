package com.twa.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.catalog.support.AbstractIntegrationTest;
import com.twa.catalog.support.Fixtures;

/**
 * Provider side of the contract of the catalog (microcks/catalog-openapi.json, generated from the seed data of the
 * monolith): the service answers exactly the JSON of every example. The client of the monolith is tested against the
 * same examples, so a change of a field breaks one of the two sides.
 */
class CatalogContractTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JsonNode contract = parse(Fixtures.read("contracts/catalog-openapi.json"));

    @Test
    @DisplayName("GET /cities/{code} answers the JSON of each example of the contract (the 86 cities)")
    void everyCity() {
        JsonNode examples = examples("/cities/{code}", "200");

        assertThat(examples.size()).isEqualTo(86);
        forEach(examples, (code, example) -> {
            ResponseEntity<JsonNode> response = get("/cities/" + code, JsonNode.class);
            assertThat(response.getStatusCode()).as("city %s", code).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as("city %s", code).isEqualTo(example.get("value"));
        });
    }

    @Test
    @DisplayName("GET /cities/{code} with an unknown code answers the 404 of the contract")
    void unknownCity() {
        JsonNode example = examples("/cities/{code}", "404").get("unknown").get("value");
        String code = parameterExample("/cities/{code}", "unknown");

        ResponseEntity<JsonNode> response = get("/cities/" + code, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(example);
    }

    @Test
    @DisplayName("GET /cities?codes= answers the JSON of each example of the contract, also for an unknown code")
    void everyBatch() {
        forEach(examples("/cities", "200"), (name, example) -> {
            String codes = parameterExample("/cities", name);
            ResponseEntity<JsonNode> response = get("/cities?codes=" + codes, JsonNode.class);
            assertThat(response.getStatusCode()).as("codes %s", codes).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as("codes %s", codes).isEqualTo(example.get("value"));
        });
    }

    private JsonNode examples(String path, String status) {
        return contract.path("paths").path(path).path("get").path("responses").path(status).path("content")
                .path("application/json").path("examples");
    }

    private String parameterExample(String path, String name) {
        return contract.path("paths").path(path).path("get").path("parameters").get(0).path("examples").path(name)
                .path("value").asText();
    }

    private static void forEach(JsonNode node, java.util.function.BiConsumer<String, JsonNode> consumer) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            consumer.accept(field.getKey(), field.getValue());
        }
    }

    private static JsonNode parse(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
