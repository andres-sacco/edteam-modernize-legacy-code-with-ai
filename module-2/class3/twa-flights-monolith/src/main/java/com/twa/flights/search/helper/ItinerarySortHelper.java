package com.twa.flights.search.helper;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.enums.SortCriteria;
import com.twa.flights.enums.SortOrder;

@Component
public class ItinerarySortHelper {

    public static final SortCriteria DEFAULT_SORT_CRITERIA = SortCriteria.PRICE;
    public static final SortOrder DEFAULT_SORT_ORDER = SortOrder.ASC;

    private static final Comparator<ItineraryDTO> BY_PRICE = Comparator
            .comparing(itinerary -> itinerary.getPriceInfo().getTotalAmount());

    public List<ItineraryDTO> sort(List<ItineraryDTO> itineraries, String sortBy, String sortOrder) {
        SortCriteria criteria = sortBy == null ? DEFAULT_SORT_CRITERIA : SortCriteria.valueOf(normalize(sortBy));
        SortOrder order = sortOrder == null ? DEFAULT_SORT_ORDER : SortOrder.valueOf(normalize(sortOrder));

        Comparator<ItineraryDTO> comparator = getComparator(criteria);
        if (SortOrder.DESC.equals(order)) {
            comparator = comparator.reversed();
        }

        // The cheapest itinerary is always the tiebreaker
        if (!SortCriteria.PRICE.equals(criteria)) {
            comparator = comparator.thenComparing(BY_PRICE);
        }

        return itineraries.stream().sorted(comparator).collect(Collectors.toList());
    }

    public static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Comparator<ItineraryDTO> getComparator(SortCriteria criteria) {
        return switch (criteria) {
        case CARRIER -> Comparator.comparing(ItineraryDTO::getCarrier, Comparator.nullsLast(String::compareTo));
        case DURATION -> Comparator.comparingLong(this::getTotalDurationInMinutes);
        default -> BY_PRICE;
        };
    }

    // Sum of the duration of all the segments, the format of each one is HH:mm
    private long getTotalDurationInMinutes(ItineraryDTO itinerary) {
        long total = 0;
        for (SegmentDTO segment : itinerary.getSegments()) {
            String[] duration = segment.getFlightDuration().split(":");
            total += Long.parseLong(duration[0]) * 60 + Long.parseLong(duration[1]);
        }
        return total;
    }
}
