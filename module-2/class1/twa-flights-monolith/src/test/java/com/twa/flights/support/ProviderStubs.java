package com.twa.flights.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * Stubs of the external providers (airlines). Each provider has its own base path in the same WireMock server and
 * answers with the responses recorded from Microcks in src/test/resources/providers.
 */
public class ProviderStubs {

    public static final String ALPHA = "/alpha";
    public static final String BETA = "/beta";
    public static final String GAMMA = "/gamma";

    public static final String ALPHA_ROUND_TRIP = "providers/alpha-ida-y-vuelta.json";
    public static final String BETA_ROUND_TRIP = "providers/beta-ida-y-vuelta.json";
    public static final String GAMMA_ROUND_TRIP = "providers/gamma-ida-y-vuelta.json";

    private static final String SEARCH = "/itineraries";

    private final WireMockServer server;

    public ProviderStubs(WireMockServer server) {
        this.server = server;
    }

    // The three providers answer with the recorded round trip BUE-MIA
    public void allReturnRecordedRoundTrip() {
        respondWithFixture(ALPHA, ALPHA_ROUND_TRIP);
        respondWithFixture(BETA, BETA_ROUND_TRIP);
        respondWithFixture(GAMMA, GAMMA_ROUND_TRIP);
    }

    // Only one provider has results, the others answer without itineraries
    public void onlyAlphaReturns(String alphaBody) {
        respondWithBody(ALPHA, alphaBody);
        respondWithBody(BETA, "{\"status\":\"OK\",\"data\":{\"flight_options\":[]}}");
        respondWithBody(GAMMA, "{\"Itineraries\":[]}");
    }

    public void respondWithFixture(String provider, String fixture) {
        respondWithBody(provider, Fixtures.read(fixture));
    }

    public void respondWithBody(String provider, String body) {
        stub(provider, json(body));
    }

    public void respondWithStatus(String provider, int status) {
        stub(provider, aResponse().withStatus(status).withBody("{\"error\":\"provider failure\"}"));
    }

    public void respondWithDelay(String provider, String fixture, int delayMillis) {
        stub(provider, json(Fixtures.read(fixture)).withFixedDelay(delayMillis));
    }

    public List<LoggedRequest> requests(String provider) {
        return server.findAll(getRequestedFor(urlPathEqualTo(provider + SEARCH)));
    }

    private void stub(String provider, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo(provider + SEARCH)).willReturn(response));
    }

    private ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
