package com.twa.flights.facade;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.request.AvailabilityRequestDTO;
import com.twa.flights.enums.Provider;
import com.twa.flights.service.ProviderGammaService;

@Component
public class ProviderGammaFacade implements ProviderFacade {

    static final Logger LOGGER = LoggerFactory.getLogger(ProviderGammaFacade.class);

    ProviderGammaService providerGammaService;

    @Autowired
    public ProviderGammaFacade(ProviderGammaService providerGammaService) {
        this.providerGammaService = providerGammaService;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO request) {
        LOGGER.debug("Obtain the information about the flights");
        return providerGammaService.availability(request);
    }

    @Override
    public Provider getProvider() {
        return Provider.GAMMA;
    }

}
