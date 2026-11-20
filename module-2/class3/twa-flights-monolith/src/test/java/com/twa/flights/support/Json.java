package com.twa.flights.support;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Reads the JSON of the responses keeping the exact text of the decimals (e.g. 1095.3300 and not 1095.33), because
 * the scale of the amounts is part of the contract.
 */
public final class Json {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    private Json() {
        // just to avoid create instances
    }

    public static JsonNode parse(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The text of a number as it is in the JSON
    public static String decimal(JsonNode node) {
        return node.decimalValue().toPlainString();
    }

    public static JsonNode findById(JsonNode array, String id) {
        for (JsonNode item : array) {
            if (id.equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("There is not an element with the id " + id + " in " + array);
    }
}
