package com.twa.flights.pricing.validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.twa.flights.pricing.dto.CommercialPolicyCarrierDTO;
import com.twa.flights.pricing.dto.CommercialPolicyRequestDTO;
import com.twa.flights.pricing.dto.CommercialPolicyStatusDTO;
import com.twa.flights.pricing.exception.PricingError;
import com.twa.flights.shared.exception.BadRequestException;

@Component
public class CommercialPolicyValidator {

    private static final int MAX_NAME_LENGTH = 50;
    private static final String CARRIER_PATTERN = "[A-Z0-9]{2}";
    private static final BigDecimal MAX_PERCENTAGE = BigDecimal.valueOf(100);

    public void validate(final CommercialPolicyRequestDTO request) {
        if (!StringUtils.hasText(request.getName()) || request.getName().trim().length() > MAX_NAME_LENGTH) {
            throw new BadRequestException(PricingError.INVALID_POLICY_NAME);
        }

        if (request.getPriority() == null || request.getPriority() < 0) {
            throw new BadRequestException(PricingError.INVALID_POLICY_PRIORITY);
        }

        if (request.getStartDate() == null || request.getExpireDate() == null
                || !request.getStartDate().isBefore(request.getExpireDate())) {
            throw new BadRequestException(PricingError.INVALID_POLICY_DATES);
        }

        validateCarriers(request.getCarriers());
    }

    public void validate(final CommercialPolicyStatusDTO request) {
        if (request == null || request.getActive() == null) {
            throw new BadRequestException(PricingError.INVALID_POLICY_STATUS);
        }
    }

    public void validateCarrier(final String carrier, final BigDecimal percentage) {
        if (carrier == null || !normalize(carrier).matches(CARRIER_PATTERN)) {
            throw new BadRequestException(PricingError.INVALID_POLICY_CARRIER_CODE);
        }

        if (percentage == null || percentage.signum() < 0 || percentage.compareTo(MAX_PERCENTAGE) > 0) {
            throw new BadRequestException(PricingError.INVALID_POLICY_PERCENTAGE);
        }
    }

    public static String normalize(String carrier) {
        return carrier.trim().toUpperCase(Locale.ROOT);
    }

    private void validateCarriers(final List<CommercialPolicyCarrierDTO> carriers) {
        if (carriers == null || carriers.isEmpty()) {
            throw new BadRequestException(PricingError.INVALID_POLICY_CARRIERS);
        }

        carriers.forEach(carrier -> validateCarrier(carrier.getCarrier(), carrier.getPercentage()));

        Set<String> codes = carriers.stream().map(carrier -> normalize(carrier.getCarrier()))
                .collect(Collectors.toSet());
        if (codes.size() != carriers.size()) {
            throw new BadRequestException(PricingError.INVALID_POLICY_CARRIERS);
        }
    }
}
