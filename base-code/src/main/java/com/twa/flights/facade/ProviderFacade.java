package com.twa.flights.facade;

import java.util.List;

import com.twa.flights.enums.Provider;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.request.AvailabilityRequestDTO;

public interface ProviderFacade {
    List<ItineraryDTO> availability(AvailabilityRequestDTO request);

    Provider getProvider();
}