package com.twa.flights.catalog.controller;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.twa.flights.catalog.api.CatalogError;
import com.twa.flights.catalog.client.CatalogRolloutFlag;
import com.twa.flights.catalog.controller.documentation.CatalogRolloutResources;
import com.twa.flights.catalog.dto.CatalogRolloutDTO;
import com.twa.flights.catalog.dto.CatalogRolloutRequestDTO;
import com.twa.flights.shared.exception.APIException;

@RestController
@RequestMapping("/catalog/rollout")
public class CatalogRolloutController implements CatalogRolloutResources {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogRolloutController.class);

    private final CatalogRolloutFlag rolloutFlag;

    @Autowired
    public CatalogRolloutController(CatalogRolloutFlag rolloutFlag) {
        this.rolloutFlag = rolloutFlag;
    }

    @Override
    public ResponseEntity<CatalogRolloutDTO> getRollout() {
        LOGGER.debug("Obtain the flag of the rollout of the catalog");
        return new ResponseEntity<>(rolloutFlag.getCurrent(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<CatalogRolloutDTO> changeRollout(CatalogRolloutRequestDTO request) {
        LOGGER.debug("Change the flag of the rollout of the catalog");
        validate(request);
        return new ResponseEntity<>(
                rolloutFlag.change(request.getRemotePercentage(), request.getUpdatedBy(), request.getReason()),
                HttpStatus.OK);
    }

    private void validate(CatalogRolloutRequestDTO request) {
        if (request.getRemotePercentage() != null
                && (request.getRemotePercentage() < 0 || request.getRemotePercentage() > 100)) {
            throw new APIException(HttpStatus.BAD_REQUEST, CatalogError.INVALID_ROLLOUT_PERCENTAGE);
        }
        if (StringUtils.isBlank(request.getUpdatedBy()) || request.getUpdatedBy().length() > 50) {
            throw new APIException(HttpStatus.BAD_REQUEST, CatalogError.INVALID_ROLLOUT_UPDATED_BY);
        }
        if (request.getReason() != null && request.getReason().length() > 255) {
            throw new APIException(HttpStatus.BAD_REQUEST, CatalogError.INVALID_ROLLOUT_REASON);
        }
    }
}
