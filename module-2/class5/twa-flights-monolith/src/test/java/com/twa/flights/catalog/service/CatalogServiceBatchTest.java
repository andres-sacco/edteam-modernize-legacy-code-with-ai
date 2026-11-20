package com.twa.flights.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Fixtures;

/**
 * getCities of the local catalog finds, for each code, the same city that getCity finds with the collation of MySQL.
 */
class CatalogServiceBatchTest extends AbstractIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    @DisplayName("getCities returns, by the requested code, the same city than getCity, in one query")
    void sameAsGetCity() {
        List<String> codes = List.of("BUE", "mia", "BUÉ", "ＢＵＥ", "ｌｉｍ", "XXX", "MI", " SCL", "A/B");

        Map<String, CityDTO> cities = catalogService.getCities(codes);

        for (String code : codes) {
            CityDTO expected = catalogService.getCity(code);
            if (expected == null) {
                assertThat(cities).as("code %s", code).doesNotContainKey(code);
            } else {
                assertThat(cities.get(code).getCode()).as("code %s", code).isEqualTo(expected.getCode());
                assertThat(cities.get(code).getTimeZone()).isEqualTo(expected.getTimeZone());
            }
        }
        assertThat(cities).containsOnlyKeys("BUE", "mia", "BUÉ", "ＢＵＥ", "ｌｉｍ");
    }

    @Test
    @DisplayName("getCities with the 86 cities of the snapshot")
    void allTheCities() {
        List<String> codes = Arrays.stream(Fixtures.read("catalog/cities.csv").split("\n")).skip(1).map(String::trim)
                .filter(line -> !line.isEmpty()).map(line -> line.split(";")[0]).collect(Collectors.toList());

        assertThat(catalogService.getCities(codes)).hasSize(86).containsOnlyKeys(codes);
    }
}
