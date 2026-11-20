package com.twa.flights.shared.exception;

public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = -8999197576638181441L;

    private final ErrorCode status;

    public BadRequestException(ErrorCode status) {
        this.status = status;
    }

    public int getCode() {
        return status.getCode();
    }

    @Override
    public String getMessage() {
        return status.getMessage();
    }

}
