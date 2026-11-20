package com.twa.flights.catalog.api;

import com.twa.flights.shared.exception.ErrorCode;

/**
 * Errors of the catalog of cities (BC4). It is part of the public interface of the module because other modules
 * validate the cities with {@link CatalogApi}.
 */
public enum CatalogError implements ErrorCode {

    INCOMPLETE_CATALOG_INFORMATION(400_14,
            "The information about one of the city is not complete (code, country or continent is missing)");

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
