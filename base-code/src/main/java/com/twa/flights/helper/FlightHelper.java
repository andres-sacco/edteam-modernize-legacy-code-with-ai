package com.twa.flights.helper;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.google.common.collect.Iterables;
import com.twa.flights.exception.TWAException;
import com.twa.flights.model.City;
import com.twa.flights.repository.CityRepository;
import com.twa.flights.dto.LegDTO;
import com.twa.flights.dto.SegmentDTO;

@Component
public class FlightHelper {

    private final CityRepository cityRepository;

    private static final DateTimeFormatter LOCATION_DATETIME_FORMATTER = DateTimeFormat
            .forPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    public FlightHelper(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
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

        City originCity = cityRepository.findByCode(firstLeg.getOrigin());
        City destinationCity = cityRepository.findByCode(lastLeg.getDestination());

        return timeBetweenDateTime(firstLeg.getDepartureDate(), firstLeg.getDepartureTime(), originCity.getTimeZone(),
                lastLeg.getArrivalDate(), lastLeg.getArrivalTime(), destinationCity.getTimeZone());
    }

    public String calculateFlightDuration(LegDTO leg) {
        City originCity = cityRepository.findByCode(leg.getOrigin());
        City destinationCity = cityRepository.findByCode(leg.getDestination());

        return timeBetweenDateTime(leg.getDepartureDate(), leg.getDepartureTime(), originCity.getTimeZone(),
                leg.getArrivalDate(), leg.getArrivalTime(), destinationCity.getTimeZone());
    }
}
