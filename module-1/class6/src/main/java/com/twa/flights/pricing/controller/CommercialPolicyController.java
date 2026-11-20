package com.twa.flights.pricing.controller;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.twa.flights.pricing.controller.documentation.CommercialPolicyResources;
import com.twa.flights.pricing.dto.CommercialPolicyCarrierDTO;
import com.twa.flights.pricing.dto.CommercialPolicyDTO;
import com.twa.flights.pricing.dto.CommercialPolicyRequestDTO;
import com.twa.flights.pricing.dto.CommercialPolicyStatusDTO;
import com.twa.flights.pricing.service.CommercialPolicyService;
import com.twa.flights.pricing.validator.CommercialPolicyValidator;

@RestController
@RequestMapping("/pricing/commercial-policies")
public class CommercialPolicyController implements CommercialPolicyResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommercialPolicyController.class);

    private final CommercialPolicyService commercialPolicyService;
    private final CommercialPolicyValidator validator;

    @Autowired
    public CommercialPolicyController(CommercialPolicyService commercialPolicyService,
            CommercialPolicyValidator validator) {
        this.commercialPolicyService = commercialPolicyService;
        this.validator = validator;
    }

    @Override
    public ResponseEntity<List<CommercialPolicyDTO>> getPolicies(Boolean active, String carrier) {
        LOGGER.debug("Obtain the commercial policies");
        return new ResponseEntity<>(commercialPolicyService.getPolicies(active, carrier), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> getPolicy(Long id) {
        LOGGER.debug("Obtain the commercial policy {}", id);
        return new ResponseEntity<>(commercialPolicyService.getPolicy(id), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> create(CommercialPolicyRequestDTO request) {
        LOGGER.debug("Create a commercial policy");
        validator.validate(request);

        CommercialPolicyDTO response = commercialPolicyService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> update(Long id, CommercialPolicyRequestDTO request) {
        LOGGER.debug("Update the commercial policy {}", id);
        validator.validate(request);

        return new ResponseEntity<>(commercialPolicyService.update(id, request), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> changeStatus(Long id, CommercialPolicyStatusDTO request) {
        LOGGER.debug("Change the status of the commercial policy {}", id);
        validator.validate(request);

        return new ResponseEntity<>(commercialPolicyService.changeStatus(id, request.getActive()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> delete(Long id) {
        LOGGER.debug("Delete the commercial policy {}", id);
        commercialPolicyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> saveCarrier(Long id, String carrier,
            CommercialPolicyCarrierDTO request) {
        LOGGER.debug("Save the carrier {} of the commercial policy {}", carrier, id);
        validator.validateCarrier(carrier, request.getPercentage());

        return new ResponseEntity<>(commercialPolicyService.saveCarrier(id, carrier, request.getPercentage()),
                HttpStatus.OK);
    }

    @Override
    public ResponseEntity<CommercialPolicyDTO> deleteCarrier(Long id, String carrier) {
        LOGGER.debug("Delete the carrier {} of the commercial policy {}", carrier, id);
        return new ResponseEntity<>(commercialPolicyService.deleteCarrier(id, carrier), HttpStatus.OK);
    }
}
