package com.twa.flights.facade;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.request.AvailabilityRequestDTO;
import com.twa.flights.enums.Provider;
import com.twa.flights.service.ProviderBetaService;

@Component
public class ProviderBetaFacade implements ProviderFacade {

    static final Logger LOGGER = LoggerFactory.getLogger(ProviderBetaFacade.class);

    ProviderBetaService providerBetaService;

    @Autowired
    public ProviderBetaFacade(ProviderBetaService providerBetaService) {
        this.providerBetaService = providerBetaService;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO request) {
        LOGGER.debug("Obtain the information about the flights");
        return providerBetaService.availability(request);
    }

    @Override
    public Provider getProvider() {
        return Provider.BETA;
    }

}
