package com.twa.flights.support;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The contract of the catalog service (microcks/catalog-openapi.json, generated from the seed data of the catalog).
 * The service is tested against the same examples, so the client of the monolith reads the real JSON of the service
 * and not a copy written by hand.
 */
public final class CatalogContract {

    private static final JsonNode CONTRACT = Json.parse(Fixtures.read("contracts/catalog-openapi.json"));

    private CatalogContract() {
        // just to avoid create instances
    }

    // code -> JSON of GET /cities/{code}
    public static Map<String, String> cities() {
        Map<String, String> cities = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> examples = CONTRACT.path("paths").path("/cities/{code}").path("get")
                .path("responses").path("200").path("content").path("application/json").path("examples").fields();
        examples.forEachRemaining(example -> cities.put(example.getKey(), example.getValue().get("value").toString()));
        return cities;
    }

    public static String city(String code) {
        String city = cities().get(code);
        if (city == null) {
            throw new IllegalArgumentException("The city " + code + " is not in the contract");
        }
        return city;
    }
}
