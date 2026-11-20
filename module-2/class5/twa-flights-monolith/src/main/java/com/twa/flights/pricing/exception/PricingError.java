package com.twa.flights.pricing.exception;

import com.twa.flights.shared.exception.ErrorCode;

/**
 * Errors of the administration of the commercial policies (BC3).
 */
public enum PricingError implements ErrorCode {

    INVALID_POLICY_NAME(400_23, "The name is required and it can have at most 50 characters"),
    INVALID_POLICY_PRIORITY(400_24, "The priority is required and it must be zero or a positive value"),
    INVALID_POLICY_DATES(400_25, "The start and expire dates are required and the start must be before the expiration"),
    INVALID_POLICY_CARRIERS(400_26, "The policy must have at least one carrier and they can not be duplicated"),
    INVALID_POLICY_CARRIER_CODE(400_27, "The carrier must be a code of two characters"),
    INVALID_POLICY_PERCENTAGE(400_28, "The percentage is required and it must be between 0 and 100"),
    INVALID_POLICY_STATUS(400_29, "The status of the policy is required"),
    UNAUTHORIZED(401_01, "The API key is missing or it is invalid"),
    POLICY_NOT_FOUND(404_03, "The commercial policy does not exist"),
    CARRIER_NOT_FOUND_IN_POLICY(404_04, "The carrier does not exist in the commercial policy");

    private final int code;
    private final String message;

    PricingError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
