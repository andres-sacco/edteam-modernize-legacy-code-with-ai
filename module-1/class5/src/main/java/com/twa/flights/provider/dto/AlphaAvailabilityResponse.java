package com.twa.flights.provider.dto;

import java.math.BigDecimal;
import java.util.List;

// Format of the response of the provider Alpha
public record AlphaAvailabilityResponse(List<Offer> results) {

    public record Offer(String offerId, Airline airline, String cabinClass, List<Journey> journeys, Pricing pricing,
            Luggage luggage) {
    }

    public record Airline(String code) {
    }

    public record Journey(List<Flight> flights) {
    }

    // The departure and the arrival have the format yyyy-MM-ddTHH:mm
    public record Flight(String from, String to, String departure, String arrival, String flightNumber) {
    }

    public record Pricing(String currency, List<PassengerFare> passengers) {
    }

    // The type could be ADT, CHD or INF
    public record PassengerFare(String type, BigDecimal base, BigDecimal taxes) {
    }

    public record Luggage(Bag cabin, Bag hold) {
    }

    public record Bag(Integer pieces, Integer maxWeightKg) {
    }
}
