package com.twa.flights.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.MarkupDTO;
import com.twa.flights.dto.PaxPriceDTO;
import com.twa.flights.dto.PriceInfoDTO;
import com.twa.flights.dto.UpdatedPaxPriceDTO;
import com.twa.flights.dto.UpdatedPriceInfoDTO;
import com.twa.flights.model.CommercialPolicy;
import com.twa.flights.model.CommercialPolicyCarrier;
import com.twa.flights.repository.CommercialPolicyRepository;
import com.twa.flights.specification.CommercialPolicyCarrierSpecification;

@Service
public class PricingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PricingService.class);

    private final CommercialPolicyRepository commercialPolicyRepository;

    @Autowired
    public PricingService(CommercialPolicyRepository commercialPolicyRepository) {
        this.commercialPolicyRepository = commercialPolicyRepository;
    }

    @Transactional(readOnly = true)
    public List<ItineraryDTO> priceItineraries(List<ItineraryDTO> itineraries) {
        LOGGER.debug("Pricing itineraries");

        List<UpdatedPriceInfoDTO> updatedPrices = calculatePrices(itineraries);

        for (UpdatedPriceInfoDTO updatedPriceInfo : updatedPrices) {
            Optional<ItineraryDTO> itinerary = itineraries.stream()
                    .filter(iti -> iti.getId().equals(updatedPriceInfo.getItineraryId())).findFirst();

            if (itinerary.isPresent()) {
                updatePriceInfo(updatedPriceInfo, itinerary.get());
            }
        }

        return itineraries.stream().filter(iti -> iti.getPriceInfo().getAdults().getMarkup() != null)
                .collect(Collectors.toList());
    }

    public List<UpdatedPriceInfoDTO> calculatePrices(List<ItineraryDTO> itineraries) {
        List<UpdatedPriceInfoDTO> response = Lists.newArrayList();

        for (ItineraryDTO itinerary : itineraries) {

            CommercialPolicyCarrierSpecification specification = new CommercialPolicyCarrierSpecification(itinerary);

            List<CommercialPolicy> policies = commercialPolicyRepository.findAll(specification);

            Optional<CommercialPolicy> optionalPolicy = policies.stream().findFirst();

            if (optionalPolicy.isPresent()) {
                CommercialPolicy policy = optionalPolicy.get();
                Optional<CommercialPolicyCarrier> optionalPolicyCarrier = policy.getValidatingCarriers().stream()
                        .filter(carrier -> carrier.getCarrier().equals(itinerary.getCarrier())).findAny();
                response.add(calculateMarkup(itinerary, optionalPolicyCarrier));
            }
        }

        return response;
    }

    private void updatePriceInfo(UpdatedPriceInfoDTO updatedPriceInfo, ItineraryDTO itinerary) {
        PriceInfoDTO priceInfo = itinerary.getPriceInfo();
        priceInfo.getAdults().setMarkup(updatedPriceInfo.getAdults().getMarkup());
        priceInfo.getAdults().setTotal(updatedPriceInfo.getAdults().getTotal());

        if (priceInfo.getChildren() != null) {
            priceInfo.getChildren().setMarkup(updatedPriceInfo.getChildren().getMarkup());
            priceInfo.getChildren().setTotal(updatedPriceInfo.getChildren().getTotal());
        }

        if (priceInfo.getInfants() != null) {
            priceInfo.getInfants().setMarkup(updatedPriceInfo.getInfants().getMarkup());
            priceInfo.getInfants().setTotal(updatedPriceInfo.getInfants().getTotal());
        }
    }

    private UpdatedPriceInfoDTO calculateMarkup(ItineraryDTO itinerary,
            Optional<CommercialPolicyCarrier> optionalPolicyCarrier) {

        UpdatedPriceInfoDTO updatedPriceInfo = new UpdatedPriceInfoDTO();
        updatedPriceInfo.setItineraryId(itinerary.getId());

        updatedPriceInfo.setAdults(calculateMarkupByType(optionalPolicyCarrier, itinerary.getPriceInfo().getAdults()));

        if (itinerary.getPriceInfo().getChildren() != null) {
            updatedPriceInfo
                    .setChildren(calculateMarkupByType(optionalPolicyCarrier, itinerary.getPriceInfo().getChildren()));
        }

        if (itinerary.getPriceInfo().getInfants() != null) {
            updatedPriceInfo
                    .setInfants(calculateMarkupByType(optionalPolicyCarrier, itinerary.getPriceInfo().getInfants()));
        }

        return updatedPriceInfo;
    }

    private UpdatedPaxPriceDTO calculateMarkupByType(Optional<CommercialPolicyCarrier> optionalPolicyCarrier,
            PaxPriceDTO paxPrice) {
        UpdatedPaxPriceDTO updatedPaxPrice = new UpdatedPaxPriceDTO();
        if (optionalPolicyCarrier.isPresent()) {
            CommercialPolicyCarrier policyCarrier = optionalPolicyCarrier.get();

            BigDecimal amount = paxPrice.getSubtotal()
                    .multiply(policyCarrier.getPercentage().divide(BigDecimal.valueOf(10)));
            updatedPaxPrice.setTotal(paxPrice.getTotal().add(amount));
            updatedPaxPrice.setMarkup(new MarkupDTO(policyCarrier.getPercentage(), amount));
        }

        return updatedPaxPrice;
    }
}
