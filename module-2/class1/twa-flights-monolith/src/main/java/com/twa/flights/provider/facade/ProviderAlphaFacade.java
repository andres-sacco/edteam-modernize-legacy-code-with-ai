package com.twa.flights.provider.facade;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.provider.api.ProviderFacade;
import com.twa.flights.provider.service.ProviderAlphaService;

@Component
public class ProviderAlphaFacade implements ProviderFacade {

    static final Logger LOGGER = LoggerFactory.getLogger(ProviderAlphaFacade.class);

    ProviderAlphaService providerAlphaService;

    @Autowired
    public ProviderAlphaFacade(ProviderAlphaService providerAlphaService) {
        this.providerAlphaService = providerAlphaService;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO request) {
        LOGGER.debug("Obtain the information about the flights");
        return providerAlphaService.availability(request);
    }

    @Override
    public Provider getProvider() {
        return Provider.ALPHA;
    }

}
