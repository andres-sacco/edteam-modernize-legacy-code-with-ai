package com.twa.flights.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.twa.flights.dto.pricing.CommercialPolicyCarrierDTO;
import com.twa.flights.dto.pricing.CommercialPolicyDTO;
import com.twa.flights.dto.pricing.CommercialPolicyRequestDTO;
import com.twa.flights.enums.ExceptionStatus;
import com.twa.flights.exception.APIException;
import com.twa.flights.exception.BadRequestException;
import com.twa.flights.mapper.CommercialPolicyMapper;
import com.twa.flights.model.CommercialPolicy;
import com.twa.flights.model.CommercialPolicyCarrier;
import com.twa.flights.repository.CommercialPolicyRepository;
import com.twa.flights.validator.CommercialPolicyValidator;

// Administration of the commercial policies that the pricing uses to calculate the markup
@Service
public class CommercialPolicyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommercialPolicyService.class);

    private final CommercialPolicyRepository commercialPolicyRepository;
    private final CommercialPolicyMapper mapper;

    @Autowired
    public CommercialPolicyService(CommercialPolicyRepository commercialPolicyRepository,
            CommercialPolicyMapper mapper) {
        this.commercialPolicyRepository = commercialPolicyRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CommercialPolicyDTO> getPolicies(Boolean active, String carrier) {
        return commercialPolicyRepository.findAll().stream()
                .filter(policy -> active == null || policy.isActive() == active)
                .filter(policy -> carrier == null || findCarrier(policy, carrier).isPresent())
                .sorted(Comparator.comparing(CommercialPolicy::getPriority).reversed()
                        .thenComparing(CommercialPolicy::getId))
                .map(mapper::convert).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CommercialPolicyDTO getPolicy(Long id) {
        return mapper.convert(getPolicyEntity(id));
    }

    @Transactional
    public CommercialPolicyDTO create(CommercialPolicyRequestDTO request) {
        CommercialPolicy policy = new CommercialPolicy();
        policy.setValidatingCarriers(Lists.newArrayList());
        update(policy, request);

        CommercialPolicy saved = commercialPolicyRepository.saveAndFlush(policy);
        LOGGER.info("Commercial policy {} created", saved.getId());
        return mapper.convert(saved);
    }

    @Transactional
    public CommercialPolicyDTO update(Long id, CommercialPolicyRequestDTO request) {
        CommercialPolicy policy = getPolicyEntity(id);
        update(policy, request);

        LOGGER.info("Commercial policy {} updated", id);
        return mapper.convert(commercialPolicyRepository.saveAndFlush(policy));
    }

    @Transactional
    public CommercialPolicyDTO changeStatus(Long id, boolean active) {
        CommercialPolicy policy = getPolicyEntity(id);
        policy.setActive(active);

        LOGGER.info("Commercial policy {} {}", id, active ? "activated" : "deactivated");
        return mapper.convert(commercialPolicyRepository.saveAndFlush(policy));
    }

    @Transactional
    public void delete(Long id) {
        commercialPolicyRepository.delete(getPolicyEntity(id));
        LOGGER.info("Commercial policy {} deleted", id);
    }

    @Transactional
    public CommercialPolicyDTO saveCarrier(Long id, String carrier, BigDecimal percentage) {
        CommercialPolicy policy = getPolicyEntity(id);
        String code = CommercialPolicyValidator.normalize(carrier);

        Optional<CommercialPolicyCarrier> existing = findCarrier(policy, code);
        if (existing.isPresent()) {
            existing.get().setPercentage(percentage);
        } else {
            policy.getValidatingCarriers().add(createCarrier(code, percentage));
        }
        touch(policy);

        LOGGER.info("Carrier {} of the commercial policy {} saved", code, id);
        return mapper.convert(commercialPolicyRepository.saveAndFlush(policy));
    }

    @Transactional
    public CommercialPolicyDTO deleteCarrier(Long id, String carrier) {
        CommercialPolicy policy = getPolicyEntity(id);
        CommercialPolicyCarrier policyCarrier = findCarrier(policy, CommercialPolicyValidator.normalize(carrier))
                .orElseThrow(() -> notFound(ExceptionStatus.CARRIER_NOT_FOUND_IN_POLICY));

        // A policy without carriers is never used by the pricing
        if (policy.getValidatingCarriers().size() == 1) {
            throw new BadRequestException(ExceptionStatus.INVALID_POLICY_CARRIERS);
        }

        policy.getValidatingCarriers().remove(policyCarrier);
        touch(policy);

        LOGGER.info("Carrier {} of the commercial policy {} deleted", policyCarrier.getCarrier(), id);
        return mapper.convert(commercialPolicyRepository.saveAndFlush(policy));
    }

    // private function(s)

    private CommercialPolicy getPolicyEntity(Long id) {
        return commercialPolicyRepository.findById(id).orElseThrow(() -> notFound(ExceptionStatus.POLICY_NOT_FOUND));
    }

    private void update(CommercialPolicy policy, CommercialPolicyRequestDTO request) {
        policy.setName(request.getName().trim());
        policy.setPriority(request.getPriority());
        policy.setActive(request.getActive() == null || request.getActive());
        policy.setStartDate(request.getStartDate());
        policy.setExpireDate(request.getExpireDate());
        syncCarriers(policy, request.getCarriers());
    }

    // The existing carriers are updated instead of recreated to keep the ids and the unique constraint
    private void syncCarriers(CommercialPolicy policy, List<CommercialPolicyCarrierDTO> carriers) {
        Map<String, BigDecimal> percentages = carriers.stream().collect(Collectors.toMap(
                carrier -> CommercialPolicyValidator.normalize(carrier.getCarrier()),
                CommercialPolicyCarrierDTO::getPercentage));

        policy.getValidatingCarriers().removeIf(carrier -> !percentages.containsKey(carrier.getCarrier()));

        Map<String, CommercialPolicyCarrier> existing = policy.getValidatingCarriers().stream()
                .collect(Collectors.toMap(CommercialPolicyCarrier::getCarrier, Function.identity()));

        percentages.forEach((code, percentage) -> {
            if (existing.containsKey(code)) {
                existing.get(code).setPercentage(percentage);
            } else {
                policy.getValidatingCarriers().add(createCarrier(code, percentage));
            }
        });
        touch(policy);
    }

    // The changes in the carriers do not modify the policy itself, so the update date is changed manually
    private void touch(CommercialPolicy policy) {
        policy.setUpdatedAt(LocalDateTime.now());
    }

    private CommercialPolicyCarrier createCarrier(String code, BigDecimal percentage) {
        CommercialPolicyCarrier carrier = new CommercialPolicyCarrier();
        carrier.setCarrier(code);
        carrier.setPercentage(percentage);
        return carrier;
    }

    private Optional<CommercialPolicyCarrier> findCarrier(CommercialPolicy policy, String carrier) {
        String code = CommercialPolicyValidator.normalize(carrier);
        return policy.getValidatingCarriers().stream().filter(item -> item.getCarrier().equals(code)).findFirst();
    }

    private APIException notFound(ExceptionStatus status) {
        return new APIException(HttpStatus.NOT_FOUND, status.getCode(), status.getMessage(), null,
                status.getMessage());
    }
}
