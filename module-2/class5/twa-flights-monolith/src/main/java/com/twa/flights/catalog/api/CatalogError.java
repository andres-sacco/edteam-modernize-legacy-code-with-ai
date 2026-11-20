package com.twa.flights.catalog.api;

import com.twa.flights.shared.exception.ErrorCode;

/**
 * Errors of the catalog of cities (BC4). It is part of the public interface of the module because other modules
 * validate the cities with {@link CatalogApi}.
 */
public enum CatalogError implements ErrorCode {

    INCOMPLETE_CATALOG_INFORMATION(400_14,
            "The information about one of the city is not complete (code, country or continent is missing)"),

    INVALID_ROLLOUT_PERCENTAGE(400_30, "The remote percentage must be between 0 and 100, or null to use catalog.mode"),
    INVALID_ROLLOUT_UPDATED_BY(400_31,
            "The author of the change (updatedBy) is required and it can have at most 50 characters"),
    INVALID_ROLLOUT_REASON(400_32, "The reason of the change can have at most 255 characters"),
    UNAUTHORIZED(401_02, "The API key of the rollout of the catalog is missing or it is invalid"),

    CATALOG_UNAVAILABLE(503_01, "The catalog of cities is not available, try again later");

    private final int code;
    private final String message;

    CatalogError(int code, String message) {
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
