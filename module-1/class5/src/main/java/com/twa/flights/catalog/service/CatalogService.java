package com.twa.flights.catalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.dto.CityDTO;
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
        City city = cityRepository.findByCode(code);
        return mapper.convert(city);
    }

}
