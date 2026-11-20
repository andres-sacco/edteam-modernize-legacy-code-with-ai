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
import com.twa.flights.catalog.mapper.CityMapper;
import com.twa.flights.catalog.model.City;
import com.twa.flights.catalog.repository.CityRepository;

@Service
public class CatalogService implements CatalogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogService.class);

    private final CityRepository cityRepository;
    private final CityMapper mapper;

    @Autowired
    public CatalogService(CityRepository cityRepository, CityMapper mapper) {
        this.cityRepository = cityRepository;
        this.mapper = mapper;
    }

    @Override
    public CityDTO getCity(String code) {
        LOGGER.debug("Obtain the information for code: {}", code);
        City city;
        try {
            city = cityRepository.findByCode(code);
        } catch (DataAccessException e) {
            // The same failure of the remote mode: the database of the catalog is down (or a timeout of Hikari)
            LOGGER.error("Error accessing the database of the catalog for the code {}", code, e);
            throw new CatalogUnavailableException("The database of the catalog is not available", e);
        }
        return mapper.convert(city);
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        LOGGER.debug("Obtain the information for codes: {}", codes);
        Set<String> requested = CityCodes.distinct(codes);
        Map<String, CityDTO> result = new LinkedHashMap<>();
        if (requested.isEmpty()) {
            return result;
        }

        List<City> cities;
        try {
            cities = cityRepository.findByCodeIn(requested);
        } catch (DataAccessException e) {
            LOGGER.error("Error accessing the database of the catalog for the codes {}", requested, e);
            throw new CatalogUnavailableException("The database of the catalog is not available", e);
        }

        // MySQL finds the cities with its collation, each one is assigned to the codes that match it in the same way
        for (String code : requested) {
            cities.stream().filter(city -> CityCodes.matches(code, city.getCode())).findFirst()
                    .ifPresent(city -> result.put(code, mapper.convert(city)));
        }
        return result;
    }

}
