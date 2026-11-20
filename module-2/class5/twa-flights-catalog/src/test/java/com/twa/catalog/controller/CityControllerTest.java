package com.twa.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.catalog.support.AbstractIntegrationTest;
import com.twa.catalog.support.Fixtures;

/**
 * The service answers the same as the CatalogService of the monolith (characterization #26 in the monolith): the same
 * JSON of CityDTO, an unknown city is a 404 instead of a null, and the same 86 cities of the seed data.
 */
class CityControllerTest extends AbstractIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("[#26] GET /cities/{code} returns the same JSON of the CityDTO of the monolith, without the id")
    void cityWithCountryAndContinent() throws Exception {
        ResponseEntity<String> response = get("/cities/BUE", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(OBJECT_MAPPER.readTree(response.getBody())).isEqualTo(OBJECT_MAPPER.readTree("""
                {"code":"BUE","name":"Buenos Aires","timeZone":"America/Argentina/Buenos_Aires",
                 "country":{"code":"AR","name":"Argentina","continent":{"code":"SA","name":"South America"}}}
                """));
    }

    @Test
    @DisplayName("[#26] the code is found in any case, the service normalizes it to upper case")
    void codeInLowerCase() {
        JsonNode city = get("/cities/mia", JsonNode.class).getBody();

        assertThat(city.path("code").asText()).isEqualTo("MIA");
        assertThat(city.path("timeZone").asText()).isEqualTo("America/New_York");
    }

    @ParameterizedTest
    @ValueSource(strings = { "XXX", "MI", "ABCD" })
    @DisplayName("[#26] an unknown code is a 404 with the error 40401; the format of the code is not validated")
    void unknownCity(String code) {
        ResponseEntity<JsonNode> response = get("/cities/" + code, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(40401);
        assertThat(response.getBody().path("shortDescription").asText()).isEqualTo("The city does not exist");
        assertThat(response.getBody().path("reasons").get(0).asText()).isEqualTo(code);
    }

    @Test
    @DisplayName("GET /cities?codes= returns the cities that exist, in the order of the request and without repeated")
    void severalCities() {
        ResponseEntity<JsonNode> response = get("/cities?codes=mia,XXX,BUE,MIA,SCL", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codes(response.getBody())).containsExactly("MIA", "BUE", "SCL");
    }

    @Test
    @DisplayName("GET /cities?codes= finds each code like GET /cities/{code}: without case, accents or width")
    void sameMatchThanOneCity() {
        assertThat(get("/cities/BUÉ", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codes(get("/cities?codes=BUÉ,bue", JsonNode.class).getBody())).containsExactly("BUE");

        // Full width letters (Asian keyboards): MySQL finds BUE, the batch has to find it too
        assertThat(get("/cities/ＢＵＥ", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codes(get("/cities?codes=ＢＵＥ,ｍｉａ", JsonNode.class).getBody())) //
                .containsExactly("BUE", "MIA");

        assertThat(get("/cities/ SCL", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/cities?codes= SCL,MIA ", JsonNode.class).getBody()).isEmpty();
    }

    @Test
    @DisplayName("GET /cities?codes= with only unknown codes returns an empty list, it is not an error")
    void onlyUnknownCities() {
        ResponseEntity<JsonNode> response = get("/cities?codes=XXX,MI", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/cities", "/cities?codes=", "/cities?codes=,, ," })
    @DisplayName("GET /cities without codes is a 400 with the error 40001")
    void withoutCodes(String path) {
        ResponseEntity<JsonNode> response = get(path, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(40001);
    }

    @Test
    @DisplayName("GET /cities accepts up to 50 different codes, more is a 400 with the error 40002")
    void maximumOfCodes() {
        String fifty = IntStream.range(0, 50).mapToObj(i -> String.format("C%02d", i))
                .collect(Collectors.joining(","));

        assertThat(get("/cities?codes=" + fifty, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // The repeated codes count once
        assertThat(get("/cities?codes=" + fifty + ",C00,c01", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> response = get("/cities?codes=" + fifty + ",BUE", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(40002);
    }

    @Test
    @DisplayName("[#26] the service has the 86 cities of the snapshot, with the same name, time zone, country and continent")
    void seedCities() {
        List<String> expected = Fixtures.cities().stream().sorted().collect(Collectors.toList());

        List<String> actual = expected.stream().map(line -> line.split(";")[0])
                .map(code -> get("/cities/" + code, JsonNode.class).getBody()).map(CityControllerTest::line)
                .sorted().collect(Collectors.toList());

        assertThat(actual).hasSize(86).isEqualTo(expected);
    }

    @Test
    @DisplayName("[#26] GET /cities?codes= returns the same 86 cities than GET /cities/{code}")
    void seedCitiesInBatches() {
        List<String> codes = Fixtures.cities().stream().map(line -> line.split(";")[0]).collect(Collectors.toList());

        List<String> actual = IntStream.range(0, (codes.size() + 49) / 50)
                .mapToObj(batch -> codes.subList(batch * 50, Math.min(codes.size(), (batch + 1) * 50)))
                .map(batch -> get("/cities?codes=" + String.join(",", batch), JsonNode.class).getBody())
                .flatMap(cities -> StreamSupport.stream(cities.spliterator(), false)).map(CityControllerTest::line)
                .sorted().collect(Collectors.toList());

        assertThat(actual).isEqualTo(Fixtures.cities().stream().sorted().collect(Collectors.toList()));
    }

    @Test
    @DisplayName("[#15 #26] all the time zones are IANA ids (the monolith validates them with Joda-Time)")
    void timeZonesAreIanaIds() {
        assertThat(Fixtures.cities()).allSatisfy(line -> {
            String timeZone = get("/cities/" + line.split(";")[0], JsonNode.class).getBody().path("timeZone").asText();
            assertThat(ZoneId.of(timeZone).getId()).as("time zone of %s", line).isEqualTo(timeZone);
        });
    }

    private static String line(JsonNode city) {
        return String.join(";", city.path("code").asText(), city.path("name").asText(),
                city.path("timeZone").asText(), city.path("country").path("code").asText(),
                city.path("country").path("continent").path("code").asText());
    }

    private static List<String> codes(JsonNode cities) {
        Iterator<JsonNode> iterator = cities.elements();
        return StreamSupport.stream(((Iterable<JsonNode>) () -> iterator).spliterator(), false)
                .map(city -> city.path("code").asText()).collect(Collectors.toList());
    }
}
