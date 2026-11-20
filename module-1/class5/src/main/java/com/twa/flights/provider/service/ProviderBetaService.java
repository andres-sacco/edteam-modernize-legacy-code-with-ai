package com.twa.flights.provider.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.twa.flights.provider.connector.BetaConnector;
import com.twa.flights.provider.helper.FlightHelper;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;

@Service
public class ProviderBetaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderBetaService.class);

    private final BetaConnector betaConnector;
    private final FlightHelper flightHelper;

    @Autowired
    public ProviderBetaService(BetaConnector betaConnector, FlightHelper flightHelper) {
        this.betaConnector = betaConnector;
        this.flightHelper = flightHelper;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO availabilityRequest) {
        List<ItineraryDTO> itineraries = betaConnector.availability(availabilityRequest);

        return calculateFlightDuration(itineraries);
    }

    private List<ItineraryDTO> calculateFlightDuration(List<ItineraryDTO> itineraries) {
        List<ItineraryDTO> filteredItineraries = Lists.newArrayList();

        for (ItineraryDTO itinerary : itineraries) {
            if (calculateFlightDuration(itinerary)) {
                filteredItineraries.add(itinerary);
            }
        }

        return filteredItineraries;
    }

    private boolean calculateFlightDuration(ItineraryDTO itinerary) {
        boolean completedItinerary = Boolean.FALSE;
        try {
            for (SegmentDTO segment : itinerary.getSegments()) {
                calculateFlightDuration(segment);
            }
            completedItinerary = Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.error("Occur an error calculating the flight duration of id {}", itinerary.getId(), e);
        }
        return completedItinerary;
    }

    private void calculateFlightDuration(SegmentDTO seg) {

        for (LegDTO leg : seg.getLegs()) {
            leg.setFlightDuration(flightHelper.calculateFlightDuration(leg));
        }

        seg.setFlightDuration(flightHelper.calculateFlightDuration(seg));
    }
}
