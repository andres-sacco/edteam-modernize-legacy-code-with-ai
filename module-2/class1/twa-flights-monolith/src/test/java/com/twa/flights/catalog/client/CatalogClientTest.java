package com.twa.flights.catalog.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.client.configuration.CatalogClientConfiguration;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.support.CatalogContract;
import com.twa.flights.support.CatalogStubs;
import com.twa.flights.support.Json;

/**
 * The client of the catalog service keeps the contract of the local catalog: an unknown city is null. Any other
 * answer of the service is a CatalogUnavailableException, never a null, so a failure of the service is not confused
 * with an unknown city (the error 40014 of the search or a discarded itinerary).
 */
class CatalogClientTest {

    // The JSON of BUE in the contract of twa-flights-catalog, the same that the service returns
    private static final String BUE = CatalogContract.city("BUE");

    private static final String CITIES = "/api/catalog/cities";

    private static final int TIMEOUT = 300;

    private static final WireMockServer CATALOG = new WireMockServer(
            options().dynamicPort().extensions(new CatalogStubs.Transformer()));

    private CatalogClient client;

    @BeforeAll
    static void start() {
        CATALOG.start();
    }

    @AfterAll
    static void stop() {
        CATALOG.stop();
    }

    @BeforeEach
    void setUp() {
        CATALOG.resetAll();
        client = client(CATALOG.baseUrl() + "/api/catalog");
    }

    @Test
    @DisplayName("200: the JSON of the service is the CityDTO of the monolith, with its country and continent")
    void city() {
        stub(okJson("[" + BUE + "]"));

        CityDTO city = client.getCity("BUE");

        assertThat(city.getCode()).isEqualTo("BUE");
        assertThat(city.getName()).isEqualTo("Buenos Aires");
        assertThat(city.getTimeZone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(city.getCountry().getCode()).isEqualTo("AR");
        assertThat(city.getCountry().getName()).isEqualTo("Argentina");
        assertThat(city.getCountry().getContinent().getCode()).isEqualTo("SA");
        assertThat(city.getCountry().getContinent().getName()).isEqualTo("South America");
        assertThat(onlyRequest().queryParameter("codes").firstValue()).isEqualTo("BUE");
    }

    @Test
    @DisplayName("200: the fields that the monolith does not know are ignored")
    void unknownFields() {
        stub(okJson("[" + BUE.replace("\"code\":\"BUE\"", "\"id\":1,\"code\":\"BUE\",\"active\":true") + "]"));

        assertThat(client.getCity("BUE").getCode()).isEqualTo("BUE");
    }

    @ParameterizedTest
    @ValueSource(strings = { "bue", "BUÉ", "Bue", "ＢＵＥ", "ｂｕｅ" })
    @DisplayName("the code is sent as it is and the city is found like in MySQL: without case, accents or width")
    void codeLikeTheCollation(String code) {
        stub(okJson("[" + BUE + "]"));

        assertThat(client.getCity(code).getCode()).isEqualTo("BUE");
        assertThat(onlyRequest().queryParameter("codes").firstValue()).isEqualTo(code);
    }

    @Test
    @DisplayName("200 with an empty list: an unknown city is null, the same as the local catalog")
    void unknownCity() {
        stub(okJson("[]"));

        assertThat(client.getCity("XXX")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "A/B", "..", "%", "B;x", " BU" })
    @DisplayName("a code that is not valid in a path travels encoded in the query: it is an unknown city, not an error")
    void codesThatAreNotValidInAPath(String code) {
        CATALOG.stubFor(get(urlPathEqualTo(CITIES)).willReturn(aResponse().withTransformers(
                CatalogStubs.Transformer.NAME)));

        assertThat(client.getCity(code)).isNull();
        assertThat(onlyRequest().queryParameter("codes").firstValue()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    @DisplayName("an empty code is null without calling the service, the same as the local catalog")
    void emptyCode(String code) {
        assertThat(client.getCity(code)).isNull();
        assertThat(client.getCity(null)).isNull();
        assertThat(client.getCities(List.of(code))).isEmpty();

        CATALOG.verify(0, getRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 404, 500, 502, 503 })
    @DisplayName("an error of the service is a CatalogUnavailableException, never null")
    void errorOfTheService(int status) {
        stub(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":50301}"));

        assertThatThrownBy(() -> client.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
        assertThatThrownBy(() -> client.getCities(List.of("BUE", "MIA")))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    @DisplayName("a timeout of the service is a CatalogUnavailableException")
    void timeout() {
        stub(okJson("[" + BUE + "]").withFixedDelay(TIMEOUT * 3));

        assertThatThrownBy(() -> client.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    @DisplayName("the service is down (connection refused) is a CatalogUnavailableException")
    void serviceDown() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        CatalogClient down = client("http://localhost:" + port + "/api/catalog");

        assertThatThrownBy(() -> down.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    @DisplayName("200 with an invalid JSON is a CatalogUnavailableException")
    void invalidJson() {
        stub(okJson("[{\"code\":"));

        assertThatThrownBy(() -> client.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"time_zone\":\"America/Argentina/Buenos_Aires\"", "\"timeZone\":\"\"" })
    @DisplayName("200 without the time zone (or with a renamed field) is an error: the duration would use the zone of the JVM")
    void withoutTimeZone(String timeZone) {
        stub(okJson("[" + BUE.replace("\"timeZone\":\"America/Argentina/Buenos_Aires\"", timeZone) + "]"));

        assertThatThrownBy(() -> client.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    @DisplayName("200 without the country is an error")
    void withoutCountry() {
        stub(okJson("[{\"code\":\"BUE\",\"name\":\"Buenos Aires\",\"timeZone\":\"America/Argentina/Buenos_Aires\"}]"));

        assertThatThrownBy(() -> client.getCity("BUE")).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    @DisplayName("consumer side of the contract: the 86 cities of the contract are read with all their fields")
    void everyCityOfTheContract() {
        CATALOG.stubFor(get(urlPathEqualTo(CITIES))
                .willReturn(aResponse().withTransformers(CatalogStubs.Transformer.NAME)));

        CatalogContract.cities().forEach((code, json) -> {
            JsonNode expected = Json.parse(json);
            CityDTO city = client.getCity(code);
            assertThat(city.getCode()).isEqualTo(expected.get("code").asText());
            assertThat(city.getName()).isEqualTo(expected.get("name").asText());
            assertThat(city.getTimeZone()).as("time zone of %s", code).isEqualTo(expected.get("timeZone").asText());
            assertThat(city.getCountry().getCode()).isEqualTo(expected.at("/country/code").asText());
            assertThat(city.getCountry().getName()).isEqualTo(expected.at("/country/name").asText());
            assertThat(city.getCountry().getContinent().getCode())
                    .isEqualTo(expected.at("/country/continent/code").asText());
            assertThat(city.getCountry().getContinent().getName())
                    .isEqualTo(expected.at("/country/continent/name").asText());
        });
    }

    @Test
    @DisplayName("getCities: the cities by the code as it was requested, without the unknown ones")
    void severalCities() {
        CATALOG.stubFor(get(urlPathEqualTo(CITIES))
                .willReturn(aResponse().withTransformers(CatalogStubs.Transformer.NAME)));

        Map<String, CityDTO> cities = client.getCities(List.of("BUE", "mia", "XXX", "BUE", ""));

        assertThat(cities).containsOnlyKeys("BUE", "mia");
        assertThat(cities.get("mia").getCode()).isEqualTo("MIA");
        assertThat(onlyRequest().queryParameter("codes").firstValue()).isEqualTo("BUE,mia,XXX");
    }

    @Test
    @DisplayName("getCities: more than 50 codes are split in requests of up to 50, the maximum of the service")
    void batchesOfFifty() {
        CATALOG.stubFor(get(urlPathEqualTo(CITIES))
                .willReturn(aResponse().withTransformers(CatalogStubs.Transformer.NAME)));
        List<String> codes = new ArrayList<>(IntStream.range(0, 118).mapToObj(i -> String.format("X%02d", i))
                .collect(Collectors.toList()));
        codes.add("BUE");
        codes.add("MIA");

        Map<String, CityDTO> cities = client.getCities(codes);

        assertThat(cities).containsOnlyKeys("BUE", "MIA");
        assertThat(CATALOG.findAll(getRequestedFor(urlPathEqualTo(CITIES))))
                .extracting(request -> request.queryParameter("codes").firstValue().split(",").length)
                .containsExactlyInAnyOrder(50, 50, 20);
    }

    private void stub(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
        CATALOG.stubFor(get(urlPathEqualTo(CITIES)).willReturn(response));
    }

    private LoggedRequest onlyRequest() {
        List<LoggedRequest> requests = CATALOG.findAll(getRequestedFor(urlPathEqualTo(CITIES)));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    // The same builder that Spring Boot injects: Jackson ignores the unknown properties
    private static CatalogClient client(String host) {
        CatalogClientConfiguration configuration = new CatalogClientConfiguration();
        configuration.setHost(host);
        configuration.setConnectionTimeout(TIMEOUT);
        configuration.setReadTimeout(TIMEOUT);

        RestClient.Builder builder = RestClient.builder().messageConverters(converters -> {
            converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
            converters.add(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()));
        });
        return new CatalogClient(configuration, builder);
    }
}
