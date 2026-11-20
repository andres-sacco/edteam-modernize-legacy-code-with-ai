package com.twa.flights.provider.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.client.CatalogLookupPath;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.provider.connector.BetaConnector;
import com.twa.flights.provider.helper.FlightHelper;
import com.twa.flights.provider.helper.ProviderMetricsHelper;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;

@Service
public class ProviderBetaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderBetaService.class);

    private final BetaConnector betaConnector;
    private static final String PROVIDER = "beta";

    private final FlightHelper flightHelper;
    private final ProviderMetricsHelper providerMetricsHelper;

    @Autowired
    public ProviderBetaService(BetaConnector betaConnector, FlightHelper flightHelper,
            ProviderMetricsHelper providerMetricsHelper) {
        this.betaConnector = betaConnector;
        this.flightHelper = flightHelper;
        this.providerMetricsHelper = providerMetricsHelper;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO availabilityRequest) {
        List<ItineraryDTO> itineraries = betaConnector.availability(availabilityRequest);

        return calculateFlightDuration(itineraries);
    }

    private List<ItineraryDTO> calculateFlightDuration(List<ItineraryDTO> itineraries) {
        List<ItineraryDTO> filteredItineraries = Lists.newArrayList();
        // One call to the catalog for the cities of all the itineraries of the provider
        CatalogLookupPath.begin();
        Map<String, CityDTO> cities = flightHelper.getCities(itineraries);
        String catalogPath = CatalogLookupPath.end();
        providerMetricsHelper.received(PROVIDER, catalogPath, itineraries.size());

        for (ItineraryDTO itinerary : itineraries) {
            if (calculateFlightDuration(itinerary, cities, catalogPath)) {
                filteredItineraries.add(itinerary);
            }
        }

        return filteredItineraries;
    }

    private boolean calculateFlightDuration(ItineraryDTO itinerary, Map<String, CityDTO> cities,
            String catalogPath) {
        boolean completedItinerary = Boolean.FALSE;
        try {
            for (SegmentDTO segment : itinerary.getSegments()) {
                calculateFlightDuration(segment, cities);
            }
            completedItinerary = Boolean.TRUE;
        } catch (CatalogUnavailableException e) {
            // Without the catalog the search fails, it does not return fewer itineraries
            throw e;
        } catch (Exception e) {
            providerMetricsHelper.discarded(PROVIDER, catalogPath, itinerary, cities, e);
            LOGGER.error("Occur an error calculating the flight duration of id {}", itinerary.getId(), e);
        }
        return completedItinerary;
    }

    private void calculateFlightDuration(SegmentDTO seg, Map<String, CityDTO> cities) {

        for (LegDTO leg : seg.getLegs()) {
            leg.setFlightDuration(flightHelper.calculateFlightDuration(leg, cities));
        }

        seg.setFlightDuration(flightHelper.calculateFlightDuration(seg, cities));
    }
}
