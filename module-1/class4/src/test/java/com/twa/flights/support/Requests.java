package com.twa.flights.support;

import com.twa.flights.dto.request.ClustersAvailabilityRequestDTO;

// Requests of the search with the same passengers of the recorded responses (1 adult, 1 child and 1 infant)
public final class Requests {

    private Requests() {
        // just to avoid create instances
    }

    public static ClustersAvailabilityRequestDTO roundTrip() {
        return request("BUE,MIA", "MIA,BUE", "2027-01-14,2027-01-24", 1, 1, 1);
    }

    public static ClustersAvailabilityRequestDTO request(String from, String to, String departure, Integer adults,
            Integer children, Integer infants) {
        ClustersAvailabilityRequestDTO request = new ClustersAvailabilityRequestDTO();
        request.setFrom(from);
        request.setTo(to);
        request.setDeparture(departure);
        request.setAdults(adults);
        request.setChildren(children);
        request.setInfants(infants);
        return request;
    }
}
