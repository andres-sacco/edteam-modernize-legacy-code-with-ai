package com.twa.flights.pricing.dto;

import java.time.LocalDateTime;

/**
 * Information of an itinerary that the quoting needs: the carrier and the dates choose the commercial policy, and the
 * prices of each type of passenger are used to calculate the markup.
 */
public class QuoteRequest {

    private String key;
    private String carrier;
    private LocalDateTime departure;
    private LocalDateTime arrival;
    private QuotePaxPrice adults;
    private QuotePaxPrice children;
    private QuotePaxPrice infants;

    /**
     * Identifies the request in the results (the id of the itinerary).
     */
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    /**
     * Departure of the first leg of the first segment. It is compared with the start date of the policy.
     */
    public LocalDateTime getDeparture() {
        return departure;
    }

    public void setDeparture(LocalDateTime departure) {
        this.departure = departure;
    }

    /**
     * Arrival of the FIRST leg of the last segment (not of the last leg). It is compared with the expire date of the
     * policy.
     */
    public LocalDateTime getArrival() {
        return arrival;
    }

    public void setArrival(LocalDateTime arrival) {
        this.arrival = arrival;
    }

    public QuotePaxPrice getAdults() {
        return adults;
    }

    public void setAdults(QuotePaxPrice adults) {
        this.adults = adults;
    }

    /**
     * Null when the itinerary does not have children.
     */
    public QuotePaxPrice getChildren() {
        return children;
    }

    public void setChildren(QuotePaxPrice children) {
        this.children = children;
    }

    /**
     * Null when the itinerary does not have infants.
     */
    public QuotePaxPrice getInfants() {
        return infants;
    }

    public void setInfants(QuotePaxPrice infants) {
        this.infants = infants;
    }
}
