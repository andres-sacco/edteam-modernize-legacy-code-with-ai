package com.twa.flights.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.text.Collator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * The catalog service (twa-flights-catalog) replaced by WireMock: GET /catalog/cities?codes= answers the cities of
 * the contract of the service (microcks/catalog-openapi.json) with the same rules of the service, for any combination
 * of codes. Some codes can fail with a 503 to simulate a failure in the middle of the search.
 */
public class CatalogStubs {

    public static final String CATALOG = "/catalog";
    public static final String CITIES = CATALOG + "/cities";

    private static final String UNAVAILABLE = "unavailable";

    private final WireMockServer server;

    public CatalogStubs(WireMockServer server) {
        this.server = server;
    }

    // The catalog answers all the cities of the snapshot
    public void available() {
        available(0);
    }

    public void available(int delayMillis) {
        server.stubFor(WireMock.get(urlPathEqualTo(CITIES)).atPriority(10)
                .willReturn(aResponse().withTransformers(Transformer.NAME).withFixedDelay(delayMillis)));
    }

    // The catalog answers 503 when the request has one of these codes
    public void unavailableFor(String... codes) {
        server.stubFor(WireMock.get(urlPathEqualTo(CITIES)).atPriority(5).willReturn(
                aResponse().withTransformers(Transformer.NAME).withTransformerParameter(UNAVAILABLE, List.of(codes))));
    }

    // The catalog answers 503 to everything
    public void down() {
        server.stubFor(WireMock.get(urlPathEqualTo(CITIES)).atPriority(1).willReturn(unavailable()));
    }

    public List<LoggedRequest> requests() {
        return server.findAll(getRequestedFor(urlPathEqualTo(CITIES)));
    }

    private static ResponseDefinitionBuilder unavailable() {
        return json(503, "{\"code\":50301,\"shortDescription\":\"The catalog is not available, try again later\"}");
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    /**
     * Builds the response of GET /cities?codes= with the cities of the snapshot. It has to be registered in the
     * WireMock server: options().extensions(new CatalogStubs.Transformer()).
     */
    public static class Transformer implements ResponseDefinitionTransformerV2 {

        public static final String NAME = "catalog-cities";

        // The same rules of the collation of MySQL that the service uses: without case, accents or full width letters
        private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

        static {
            COLLATOR.setStrength(Collator.PRIMARY);
            COLLATOR.setDecomposition(Collator.FULL_DECOMPOSITION);
        }

        // code -> JSON of the city, the same JSON of the service
        private static final Map<String, String> CITIES = CatalogContract.cities();

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ResponseDefinition transform(ServeEvent serveEvent) {
            QueryParameter parameter = serveEvent.getRequest().queryParameter("codes");
            List<String> codes = parameter.isPresent()
                    ? Arrays.stream(parameter.firstValue().split(",")).filter(code -> !code.isBlank()).toList()
                    : List.of();

            Parameters parameters = serveEvent.getTransformerParameters();
            List<String> unavailable = parameters != null && parameters.containsKey(UNAVAILABLE)
                    ? (List<String>) parameters.get(UNAVAILABLE)
                    : List.of();

            ResponseDefinitionBuilder response;
            if (codes.isEmpty()) {
                response = json(400, "{\"code\":40001,\"shortDescription\":\"The parameter codes is required\"}");
            } else if (codes.stream().anyMatch(code -> unavailable.stream().anyMatch(u -> COLLATOR.equals(u, code)))) {
                response = unavailable();
            } else {
                Map<String, String> found = new LinkedHashMap<>();
                for (String code : codes) {
                    CITIES.keySet().stream().filter(city -> COLLATOR.equals(code, city)).findFirst()
                            .ifPresent(city -> found.putIfAbsent(city, CITIES.get(city)));
                }
                response = json(200, "[" + String.join(",", found.values()) + "]");
            }
            return response.withFixedDelay(serveEvent.getResponseDefinition().getFixedDelayMilliseconds()).build();
        }
    }
}
