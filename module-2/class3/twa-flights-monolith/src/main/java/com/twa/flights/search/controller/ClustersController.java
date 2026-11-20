package com.twa.flights.search.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twa.flights.search.controller.documentation.ClustersResources;
import com.twa.flights.search.dto.ClusterSearchDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.search.service.ClustersService;
import com.twa.flights.search.validator.AvailabilityRequestValidator;

@RestController
@RequestMapping("/clusters")
public class ClustersController implements ClustersResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClustersController.class);

    private final ClustersService clustersService;

    private final AvailabilityRequestValidator requestValidator;

    @Autowired
    public ClustersController(ClustersService clustersService, AvailabilityRequestValidator requestValidator) {
        this.clustersService = clustersService;
        this.requestValidator = requestValidator;
    }

    @Override
    public ResponseEntity<ClusterSearchDTO> availability(ClustersAvailabilityRequestDTO request) {
        LOGGER.debug("Obtain all the itineraries with price");
        requestValidator.validate(request);

        ClusterSearchDTO response = clustersService.availability(request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<ItineraryDTO> getItinerary(String id, String itineraryId) {
        LOGGER.debug("Obtain the itinerary {} of the search {}", itineraryId, id);

        ItineraryDTO response = clustersService.getItinerary(id, itineraryId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
