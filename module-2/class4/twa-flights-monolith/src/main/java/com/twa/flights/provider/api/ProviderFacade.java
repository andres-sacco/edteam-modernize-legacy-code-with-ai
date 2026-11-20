package com.twa.flights.provider.api;

import java.util.List;

import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;

public interface ProviderFacade {
    List<ItineraryDTO> availability(AvailabilityRequestDTO request);

    Provider getProvider();
}