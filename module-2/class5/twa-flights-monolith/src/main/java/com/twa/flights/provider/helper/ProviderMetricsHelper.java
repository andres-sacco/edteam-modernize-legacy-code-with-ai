package com.twa.flights.provider.helper;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.shared.exception.TWAException;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The itineraries that each provider returns and the ones that the search discards when it calculates the duration of
 * the flights (M2 C5, metrics of the extraction plan), with the path of the catalog that gave the cities
 * (CatalogLookupPath), so the rate of discards can be compared between the paths:
 * <ul>
 * <li>provider.itineraries.received{provider, path}: the denominator;</li>
 * <li>provider.itineraries.discarded{provider, path, reason}: city_not_found (a city of a leg is not in the catalog),
 * duration_negative (the arrival is before the departure) or other.</li>
 * </ul>
 * There is no reason catalog_unavailable: without the catalog the search fails with 503, it does not discard.
 */
@Component
public class ProviderMetricsHelper {

    public static final String METRIC_RECEIVED = "provider.itineraries.received";
    public static final String METRIC_DISCARDED = "provider.itineraries.discarded";

    private final MeterRegistry meterRegistry;

    @Autowired
    public ProviderMetricsHelper(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void received(String provider, String path, int itineraries) {
        meterRegistry.counter(METRIC_RECEIVED, "provider", provider, "path", path).increment(itineraries);
    }

    public void discarded(String provider, String path, ItineraryDTO itinerary, Map<String, CityDTO> cities,
            Exception exception) {
        meterRegistry.counter(METRIC_DISCARDED, "provider", provider, "path", path, "reason",
                reason(itinerary, cities, exception)).increment();
    }

    private String reason(ItineraryDTO itinerary, Map<String, CityDTO> cities, Exception exception) {
        if (hasCityNotFound(itinerary, cities)) {
            return "city_not_found";
        }
        if (exception instanceof TWAException) {
            return "duration_negative";
        }
        return "other";
    }

    private boolean hasCityNotFound(ItineraryDTO itinerary, Map<String, CityDTO> cities) {
        if (itinerary == null || itinerary.getSegments() == null) {
            return false;
        }
        for (SegmentDTO segment : itinerary.getSegments()) {
            if (segment == null || segment.getLegs() == null) {
                continue;
            }
            for (LegDTO leg : segment.getLegs()) {
                if (leg != null && (cities.get(leg.getOrigin()) == null || cities.get(leg.getDestination()) == null)) {
                    return true;
                }
            }
        }
        return false;
    }
}
