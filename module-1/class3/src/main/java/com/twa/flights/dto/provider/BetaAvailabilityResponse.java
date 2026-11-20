package com.twa.flights.dto.provider;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

// Format of the response of the provider Beta, all the attributes use snake case
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BetaAvailabilityResponse(String status, Data data) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Data(List<FlightOption> flightOptions) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlightOption(String optionId, String marketingCarrier, String cabin, List<Bound> bounds,
            FareBreakdown fareBreakdown, BaggageAllowance baggageAllowance) {
    }

    // The direction could be OUTBOUND or INBOUND
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Bound(String direction, List<Leg> legs) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Leg(String depAirport, String arrAirport, String depDate, String depTime, String arrDate,
            String arrTime, String flightNo) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FareBreakdown(Fare adult, Fare child, Fare infant) {
    }

    // The amounts are in cents
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Fare(Long baseFareCents, Long taxCents) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BaggageAllowance(Integer handPieces, Integer handKg, Integer checkedPieces, Integer checkedKg) {
    }
}
