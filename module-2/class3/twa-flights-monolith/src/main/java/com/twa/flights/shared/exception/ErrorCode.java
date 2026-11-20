package com.twa.flights.shared.exception;

/**
 * Code and message of an error of the API. Each module has its own enum with its errors (SearchError, PricingError and
 * CatalogError), and the exceptions of the kernel receive this interface.
 */
public interface ErrorCode {

    int getCode();

    String getMessage();

}
