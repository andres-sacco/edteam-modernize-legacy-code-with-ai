package com.twa.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;

import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.service.CityService;

/**
 * A failure of the database must be a 5xx: the client of the monolith transforms a 404 into an unknown city. The
 * service is a stub and not a mock of Mockito, so the test does not depend on the version of the JDK.
 */
@WebMvcTest(CityController.class)
@Import(CityControllerErrorsTest.DatabaseDown.class)
class CityControllerErrorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the database of the catalog is down: GET /cities/{code} is a 503 with the error 50301, never a 404")
    void databaseDownForOneCity() throws Exception {
        mockMvc.perform(get("/cities/BUE")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @Test
    @DisplayName("the database of the catalog is down: GET /cities?codes= is a 503 with the error 50301")
    void databaseDownForSeveralCities() throws Exception {
        mockMvc.perform(get("/cities").param("codes", "BUE,MIA")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }

    @TestConfiguration
    static class DatabaseDown {

        @Bean
        CityService cityService() {
            return new CityService(null, null, 50) {

                @Override
                public CityDTO getCity(String code) {
                    throw new DataAccessResourceFailureException("The database is down");
                }

                @Override
                public List<CityDTO> getCities(String codes) {
                    throw new DataAccessResourceFailureException("The database is down");
                }
            };
        }
    }
}
