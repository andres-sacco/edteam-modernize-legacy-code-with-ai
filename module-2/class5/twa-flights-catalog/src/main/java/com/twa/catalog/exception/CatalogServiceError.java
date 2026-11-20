package com.twa.catalog.exception;

import org.springframework.http.HttpStatus;

/**
 * Errors of the catalog service. The codes follow the convention of the monolith (HTTP status * 100 + number). The
 * service does not know the error 40014 of the search: it is the search that decides that an unknown city is an
 * invalid request.
 */
public enum CatalogServiceError {

    INVALID_CODES(HttpStatus.BAD_REQUEST, 400_01, "The parameter codes is required and must have at least one code"),

    TOO_MANY_CODES(HttpStatus.BAD_REQUEST, 400_02, "The parameter codes has more codes than the allowed"),

    CITY_NOT_FOUND(HttpStatus.NOT_FOUND, 404_01, "The city does not exist"),

    CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 503_01, "The catalog is not available, try again later");

    private final HttpStatus status;
    private final int code;
    private final String message;

    CatalogServiceError(HttpStatus status, int code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
