package com.twa.catalog.exception;

import java.util.List;

public class APIException extends RuntimeException {

    private static final long serialVersionUID = 2817310473521006118L;

    private final CatalogServiceError error;
    private final List<String> reasons;

    public APIException(CatalogServiceError error, List<String> reasons) {
        super(error.getMessage());
        this.error = error;
        this.reasons = reasons;
    }

    public CatalogServiceError getError() {
        return error;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
