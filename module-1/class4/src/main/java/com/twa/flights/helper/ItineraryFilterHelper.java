package com.twa.flights.helper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.twa.flights.dto.BaggageDTO;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.SegmentDTO;
import com.twa.flights.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.enums.DepartureTimeRange;

@Component
public class ItineraryFilterHelper {

    private static final String COMMA = ",";

    public List<ItineraryDTO> filter(List<ItineraryDTO> itineraries, ClustersAvailabilityRequestDTO request) {
        Predicate<ItineraryDTO> filters = itinerary -> true;

        if (request.getMaxStops() != null) {
            filters = filters.and(itinerary -> getStops(itinerary) <= request.getMaxStops());
        }

        if (request.getMinPrice() != null) {
            filters = filters.and(itinerary -> getPrice(itinerary).compareTo(request.getMinPrice()) >= 0);
        }

        if (request.getMaxPrice() != null) {
            filters = filters.and(itinerary -> getPrice(itinerary).compareTo(request.getMaxPrice()) <= 0);
        }

        if (request.getCarriers() != null) {
            Set<String> carriers = splitCodes(request.getCarriers());
            filters = filters.and(itinerary -> carriers.contains(itinerary.getCarrier()));
        }

        if (request.getExcludedCarriers() != null) {
            Set<String> excludedCarriers = splitCodes(request.getExcludedCarriers());
            filters = filters.and(itinerary -> !excludedCarriers.contains(itinerary.getCarrier()));
        }

        if (request.getDepartureTime() != null) {
            DepartureTimeRange range = DepartureTimeRange
                    .valueOf(ItinerarySortHelper.normalize(request.getDepartureTime()));
            filters = filters.and(itinerary -> range.contains(getDepartureHour(itinerary)));
        }

        if (request.getCheckedBaggage() != null) {
            filters = filters.and(itinerary -> hasCheckedBaggage(itinerary) == request.getCheckedBaggage());
        }

        return itineraries.stream().filter(filters).collect(Collectors.toList());
    }

    public static Set<String> splitCodes(String codes) {
        return Arrays.stream(codes.split(COMMA)).map(ItinerarySortHelper::normalize).filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
    }

    // The number of stops of an itinerary is the one of the segment with more connections
    private int getStops(ItineraryDTO itinerary) {
        return itinerary.getSegments().stream().mapToInt(segment -> segment.getLegs().size() - 1).max().orElse(0);
    }

    private BigDecimal getPrice(ItineraryDTO itinerary) {
        return itinerary.getPriceInfo().getTotalAmount();
    }

    // Only the departure of the outbound flight is considered
    private int getDepartureHour(ItineraryDTO itinerary) {
        SegmentDTO segment = itinerary.firstSegment();
        return Integer.parseInt(segment.firstLeg().getDepartureTime().split(":")[0]);
    }

    private boolean hasCheckedBaggage(ItineraryDTO itinerary) {
        BaggageDTO baggage = itinerary.getBaggage();
        return baggage != null && baggage.getChecked() != null && baggage.getChecked().getPieces() != null
                && baggage.getChecked().getPieces() > 0;
    }
}
