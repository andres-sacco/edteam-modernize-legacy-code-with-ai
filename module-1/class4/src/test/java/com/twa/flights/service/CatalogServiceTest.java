package com.twa.flights.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.twa.flights.dto.CityDTO;
import com.twa.flights.model.City;
import com.twa.flights.repository.CityRepository;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.Fixtures;

/**
 * Characterization of the catalog of cities (behavior #26). The seed data of the migrations V1.1 and V1.5 is part of
 * the behavior: the validation of the search and the duration of the flights depend on it. The expected cities are in
 * src/test/resources/catalog/cities.csv (code;name;time_zone;country;continent).
 */
class CatalogServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private CityRepository cityRepository;

    @Test
    @DisplayName("[#26] getCity returns the city with its time zone, country and continent")
    void cityWithCountryAndContinent() {
        CityDTO city = catalogService.getCity("BUE");

        assertThat(city.getCode()).isEqualTo("BUE");
        assertThat(city.getName()).isEqualTo("Buenos Aires");
        assertThat(city.getTimeZone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(city.getCountry().getCode()).isEqualTo("AR");
        assertThat(city.getCountry().getName()).isEqualTo("Argentina");
        assertThat(city.getCountry().getContinent().getCode()).isEqualTo("SA");
        assertThat(city.getCountry().getContinent().getName()).isEqualTo("South America");
    }

    @Test
    @DisplayName("[#26] an unknown code returns null (the validator transforms it into the error 40014)")
    void unknownCity() {
        assertThat(catalogService.getCity("XXX")).isNull();
        assertThat(catalogService.getCity("")).isNull();
    }

    @Test
    @DisplayName("[#26] the code is found in any case because of the collation of MySQL")
    void codeInLowerCase() {
        CityDTO city = catalogService.getCity("mia");

        assertThat(city.getCode()).isEqualTo("MIA");
        assertThat(city.getTimeZone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("[#26] the seed data has the 86 cities of the snapshot, with the same name, time zone, country and continent")
    void seedCities() {
        List<String> expected = Arrays.stream(Fixtures.read("catalog/cities.csv").split("\n")).skip(1)
                .map(String::trim).filter(line -> !line.isEmpty()).sorted().collect(Collectors.toList());

        List<String> actual = cityRepository.findAll().stream().map(City::getCode).map(catalogService::getCity)
                .map(city -> String.join(";", city.getCode(), city.getName(), city.getTimeZone(),
                        city.getCountry().getCode(), city.getCountry().getContinent().getCode()))
                .sorted().collect(Collectors.toList());

        assertThat(actual).hasSize(86).isEqualTo(expected);
    }

    @Test
    @DisplayName("[#15 #26] all the time zones of the catalog are valid for Joda-Time")
    void timeZonesAreValid() {
        assertThat(cityRepository.findAll()).allSatisfy(city -> assertThat(DateTimeZone.forID(city.getTimeZone()))
                .as("time zone of %s", city.getCode()).isNotNull());
    }
}
