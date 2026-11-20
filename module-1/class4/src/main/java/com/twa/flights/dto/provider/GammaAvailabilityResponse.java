package com.twa.flights.dto.provider;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// Format of the response of the provider Gamma, all the attributes use pascal case
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public record GammaAvailabilityResponse(List<Itinerary> itineraries) {

    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record Itinerary(String key, String validatingCarrier, String cabinType, List<Flight> flights,
            List<Fare> fares, Bags bags) {
    }

    // All the flights of the itinerary are in the same list, the segment index indicates if it is the outbound (0) or
    // the return (1). The dates have the format yyyy-MM-ddTHH:mm:ss
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record Flight(Integer segmentIndex, Integer sequence, String origin, String destination,
            String departureDateTime, String arrivalDateTime, String operatingCarrier, String flightNumber) {
    }

    // The type could be ADULT, CHILD or INFANT
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record Fare(String paxType, BigDecimal baseAmount, BigDecimal taxAmount) {
    }

    // Pieces and weight with the format 1PC23KG, 0PC means without baggage
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public record Bags(String carryOn, String checked) {
    }
}
