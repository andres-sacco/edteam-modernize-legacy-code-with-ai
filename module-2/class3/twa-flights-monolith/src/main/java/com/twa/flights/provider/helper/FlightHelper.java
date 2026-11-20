package com.twa.flights.provider.helper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.google.common.collect.Iterables;
import com.twa.flights.shared.exception.TWAException;
import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.SegmentDTO;

@Component
public class FlightHelper {

    private final CatalogApi catalogApi;

    private static final DateTimeFormatter LOCATION_DATETIME_FORMATTER = DateTimeFormat
            .forPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    public FlightHelper(CatalogApi catalogApi) {
        this.catalogApi = catalogApi;
    }

    public String timeBetweenDateTime(String fromDate, String fromTime, String fromTimezone, String toDate,
            String toTime, String toTimezone) {

        DateTime from = LOCATION_DATETIME_FORMATTER.parseDateTime(fromDate + "T" + fromTime);
        DateTime to = LOCATION_DATETIME_FORMATTER.parseDateTime(toDate + "T" + toTime);

        if (!ObjectUtils.isEmpty(fromTimezone) && !ObjectUtils.isEmpty(toTimezone)) {
            from = from.withZoneRetainFields(DateTimeZone.forID(fromTimezone));
            to = to.withZoneRetainFields(DateTimeZone.forID(toTimezone));
        }

        return timeBetweenDateTime(from, to);
    }

    public String timeBetweenDateTime(DateTime from, DateTime to) {
        Duration duration = new Duration(from.getMillis(), to.getMillis());
        long val = duration.getStandardMinutes();

        // The information of the provider is not consistent, the flight arrives before the departure
        if (val < 0) {
            throw new TWAException("The arrival of the flight is before the departure");
        }

        long hours = val / 60;
        long minutes = val % 60;

        return (hours < 10 ? "0" + hours : hours) + ":" + (minutes < 10 ? "0" + minutes : minutes);
    }

    public String calculateFlightDuration(SegmentDTO segment) {
        LegDTO firstLeg = Iterables.getFirst(segment.getLegs(), null);
        LegDTO lastLeg = Iterables.getLast(segment.getLegs(), null);

        CityDTO originCity = catalogApi.getCity(firstLeg.getOrigin());
        CityDTO destinationCity = catalogApi.getCity(lastLeg.getDestination());

        return timeBetweenDateTime(firstLeg.getDepartureDate(), firstLeg.getDepartureTime(), originCity.getTimeZone(),
                lastLeg.getArrivalDate(), lastLeg.getArrivalTime(), destinationCity.getTimeZone());
    }

    /**
     * The cities of all the legs of the itineraries in one call to the catalog. The itineraries without segments or
     * legs are skipped here: each one is discarded later when its duration is calculated, as before.
     */
    public Map<String, CityDTO> getCities(Collection<ItineraryDTO> itineraries) {
        Set<String> codes = new LinkedHashSet<>();
        for (ItineraryDTO itinerary : itineraries) {
            if (itinerary == null || itinerary.getSegments() == null) {
                continue;
            }
            for (SegmentDTO segment : itinerary.getSegments()) {
                if (segment == null || segment.getLegs() == null) {
                    continue;
                }
                for (LegDTO leg : segment.getLegs()) {
                    if (leg != null) {
                        codes.add(leg.getOrigin());
                        codes.add(leg.getDestination());
                    }
                }
            }
        }
        codes.remove(null);
        return codes.isEmpty() ? Map.of() : catalogApi.getCities(codes);
    }

    // The same calculation of calculateFlightDuration(SegmentDTO) with the cities already obtained. A city that is not
    // in the map is a NullPointerException, the same as a city that the catalog returns as null
    public String calculateFlightDuration(SegmentDTO segment, Map<String, CityDTO> cities) {
        LegDTO firstLeg = Iterables.getFirst(segment.getLegs(), null);
        LegDTO lastLeg = Iterables.getLast(segment.getLegs(), null);

        CityDTO originCity = cities.get(firstLeg.getOrigin());
        CityDTO destinationCity = cities.get(lastLeg.getDestination());

        return timeBetweenDateTime(firstLeg.getDepartureDate(), firstLeg.getDepartureTime(), originCity.getTimeZone(),
                lastLeg.getArrivalDate(), lastLeg.getArrivalTime(), destinationCity.getTimeZone());
    }

    public String calculateFlightDuration(LegDTO leg, Map<String, CityDTO> cities) {
        CityDTO originCity = cities.get(leg.getOrigin());
        CityDTO destinationCity = cities.get(leg.getDestination());

        return timeBetweenDateTime(leg.getDepartureDate(), leg.getDepartureTime(), originCity.getTimeZone(),
                leg.getArrivalDate(), leg.getArrivalTime(), destinationCity.getTimeZone());
    }

    public String calculateFlightDuration(LegDTO leg) {
        CityDTO originCity = catalogApi.getCity(leg.getOrigin());
        CityDTO destinationCity = catalogApi.getCity(leg.getDestination());

        return timeBetweenDateTime(leg.getDepartureDate(), leg.getDepartureTime(), originCity.getTimeZone(),
                leg.getArrivalDate(), leg.getArrivalTime(), destinationCity.getTimeZone());
    }
}
