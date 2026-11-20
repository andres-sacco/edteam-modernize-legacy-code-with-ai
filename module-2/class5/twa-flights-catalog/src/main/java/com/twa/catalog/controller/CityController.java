package com.twa.catalog.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twa.catalog.controller.documentation.CityResources;
import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.service.CityService;

@RestController
@RequestMapping("/cities")
public class CityController implements CityResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityController.class);

    private final CityService cityService;

    @Autowired
    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @Override
    public ResponseEntity<CityDTO> getCity(String code) {
        LOGGER.debug("Obtain the city {}", code);
        return new ResponseEntity<>(cityService.getCity(code), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<CityDTO>> getCities(String codes) {
        LOGGER.debug("Obtain the cities {}", codes);
        return new ResponseEntity<>(cityService.getCities(codes), HttpStatus.OK);
    }
}
