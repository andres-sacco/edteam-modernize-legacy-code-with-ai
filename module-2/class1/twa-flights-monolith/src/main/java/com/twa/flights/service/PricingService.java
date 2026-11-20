package com.twa.flights.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.twa.flights.pricing.api.QuotingApi;
import com.twa.flights.pricing.dto.QuotePaxPrice;
import com.twa.flights.pricing.dto.QuotePaxResult;
import com.twa.flights.pricing.dto.QuoteRequest;
import com.twa.flights.pricing.dto.QuoteResult;
import com.twa.flights.pricing.model.CommercialPolicy;
import com.twa.flights.pricing.model.CommercialPolicyCarrier;
import com.twa.flights.pricing.repository.CommercialPolicyRepository;
import com.twa.flights.specification.CommercialPolicyCarrierSpecification;

@Service
public class PricingService implements QuotingApi {

    private final CommercialPolicyRepository commercialPolicyRepository;

    @Autowired
    public PricingService(CommercialPolicyRepository commercialPolicyRepository) {
        this.commercialPolicyRepository = commercialPolicyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuoteResult> quote(List<QuoteRequest> requests) {
        List<QuoteResult> response = Lists.newArrayList();

        for (QuoteRequest request : requests) {

            CommercialPolicyCarrierSpecification specification = new CommercialPolicyCarrierSpecification(
                    request.getCarrier(), request.getDeparture(), request.getArrival());

            List<CommercialPolicy> policies = commercialPolicyRepository.findAll(specification);

            Optional<CommercialPolicy> optionalPolicy = policies.stream().findFirst();

            if (optionalPolicy.isPresent()) {
                CommercialPolicy policy = optionalPolicy.get();
                Optional<CommercialPolicyCarrier> optionalPolicyCarrier = policy.getValidatingCarriers().stream()
                        .filter(carrier -> carrier.getCarrier().equals(request.getCarrier())).findAny();
                response.add(calculateMarkup(request, optionalPolicyCarrier));
            } else {
                response.add(QuoteResult.withoutPolicy(request.getKey()));
            }
        }

        return response;
    }

    private QuoteResult calculateMarkup(QuoteRequest request, Optional<CommercialPolicyCarrier> optionalPolicyCarrier) {

        QuoteResult result = new QuoteResult();
        result.setKey(request.getKey());
        result.setWithPolicy(true);

        result.setAdults(calculateMarkupByType(optionalPolicyCarrier, request.getAdults()));

        if (request.getChildren() != null) {
            result.setChildren(calculateMarkupByType(optionalPolicyCarrier, request.getChildren()));
        }

        if (request.getInfants() != null) {
            result.setInfants(calculateMarkupByType(optionalPolicyCarrier, request.getInfants()));
        }

        return result;
    }

    private QuotePaxResult calculateMarkupByType(Optional<CommercialPolicyCarrier> optionalPolicyCarrier,
            QuotePaxPrice paxPrice) {
        QuotePaxResult paxResult = new QuotePaxResult();
        if (optionalPolicyCarrier.isPresent()) {
            CommercialPolicyCarrier policyCarrier = optionalPolicyCarrier.get();

            BigDecimal amount = paxPrice.getSubtotal()
                    .multiply(policyCarrier.getPercentage().divide(BigDecimal.valueOf(10)));
            paxResult.setTotal(paxPrice.getTotal().add(amount));
            paxResult.setPercentage(policyCarrier.getPercentage());
            paxResult.setMarkup(amount);
        }

        return paxResult;
    }
}
