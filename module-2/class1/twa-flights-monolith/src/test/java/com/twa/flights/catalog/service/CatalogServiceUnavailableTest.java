package com.twa.flights.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.repository.CityRepository;

/**
 * The local catalog fails like the remote one: a problem with the database is a CatalogUnavailableException (503),
 * never a null (an unknown city). The repository is a proxy that fails, the database of the integration tests is
 * shared and can not be stopped.
 */
class CatalogServiceUnavailableTest {

    @Test
    @DisplayName("the database of the catalog is down: CatalogUnavailableException with 503 and the error 50301")
    void databaseDown() {
        CityRepository repository = (CityRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { CityRepository.class }, (proxy, method, args) -> {
                    throw new DataAccessResourceFailureException("Communications link failure");
                });
        CatalogService catalogService = new CatalogService(repository, null);

        assertThatThrownBy(() -> catalogService.getCity("BUE")).isInstanceOfSatisfying(
                CatalogUnavailableException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo(503_01);
                });
    }
}
