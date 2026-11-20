package com.twa.flights.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// Access to the recorded responses of the providers
public final class Fixtures {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Fixtures() {
        // just to avoid create instances
    }

    public static String read(String path) {
        try (InputStream input = Fixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("The fixture " + path + " does not exist");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The same format that the provider returns, using the plain Jackson configuration like the RestClient does
    public static <T> T read(String path, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(read(path), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // To modify a recorded response before using it
    public static ObjectNode tree(String path) {
        try {
            return (ObjectNode) OBJECT_MAPPER.readTree(read(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return OBJECT_MAPPER.treeToValue(node, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
