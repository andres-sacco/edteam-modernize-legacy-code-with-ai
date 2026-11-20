package com.twa.flights.catalog.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.helper.CityCodes;
import com.twa.flights.catalog.repository.CityReplicaRepository;

/**
 * The catalog read from the replica city_replica (M2 C5), the one that the events CiudadActualizada of the catalog
 * service keep up to date. It is the path of the rollback of the rollout without the tables of the monolith
 * (catalog.fallback=REPLICA): the same answers of CatalogService, with the same collation for the codes.
 *
 * The replica is as fresh as the last event applied: a city created in the service is unknown here until its event
 * arrives.
 */
@Service
public class CityReplicaCatalogService implements CatalogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityReplicaCatalogService.class);

    private final CityReplicaRepository repository;

    @Autowired
    public CityReplicaCatalogService(CityReplicaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CityDTO getCity(String code) {
        LOGGER.debug("Obtain the information of the replica for code: {}", code);
        if (code == null) {
            return null;
        }
        try {
            return repository.findByCode(code);
        } catch (DataAccessException e) {
            LOGGER.error("Error accessing the replica of the catalog for the code {}", code, e);
            throw new CatalogUnavailableException("The replica of the catalog is not available", e);
        }
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        LOGGER.debug("Obtain the information of the replica for codes: {}", codes);
        Set<String> requested = CityCodes.distinct(codes);
        Map<String, CityDTO> result = new LinkedHashMap<>();
        if (requested.isEmpty()) {
            return result;
        }

        List<CityDTO> cities;
        try {
            cities = repository.findByCodeIn(requested);
        } catch (DataAccessException e) {
            LOGGER.error("Error accessing the replica of the catalog for the codes {}", requested, e);
            throw new CatalogUnavailableException("The replica of the catalog is not available", e);
        }

        // MySQL finds the cities with its collation, each one is assigned to the codes that match it in the same way
        for (String code : requested) {
            cities.stream().filter(city -> CityCodes.matches(code, city.getCode())).findFirst()
                    .ifPresent(city -> result.put(code, city));
        }
        return result;
    }
}
