package com.twa.flights.catalog.api;

import org.springframework.http.HttpStatus;

import com.twa.flights.shared.exception.APIException;

/**
 * The catalog cannot answer: the database of the catalog (local mode) or the catalog service (remote mode) is down,
 * answers with an error or with an invalid city, or there is a timeout. It is different from an unknown city, that is
 * a null in {@link CatalogApi#getCity(String)}.
 *
 * The consumers of the catalog let it pass, so the search answers 503 with the error 50301 (the ResourceExceptionHandler
 * of the kernel handles the APIException) instead of a 40014 or a 200 with fewer itineraries.
 */
public class CatalogUnavailableException extends APIException {

    private static final long serialVersionUID = 4021938476120035817L;

    public CatalogUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CatalogError.CATALOG_UNAVAILABLE.getCode(), message, null,
                CatalogError.CATALOG_UNAVAILABLE.getMessage());
    }

    public CatalogUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CatalogError.CATALOG_UNAVAILABLE.getCode(), message, cause, null,
                CatalogError.CATALOG_UNAVAILABLE.getMessage());
    }
}
