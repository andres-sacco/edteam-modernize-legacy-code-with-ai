package com.twa.catalog.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.twa.catalog.dto.ErrorDTO;

@ControllerAdvice
public class ResourceExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceExceptionHandler.class);

    @ExceptionHandler(APIException.class)
    public ResponseEntity<ErrorDTO> apiException(APIException e, WebRequest request) {
        CatalogServiceError error = e.getError();
        return ResponseEntity.status(error.getStatus())
                .body(new ErrorDTO(error.getCode(), error.getMessage(), e.getReasons()));
    }

    // The database is down or the pool has no connections: the client of the monolith must see a 5xx, never a 404,
    // because a 404 means that the city does not exist
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorDTO> dataAccess(DataAccessException e, WebRequest request) {
        LOGGER.error("Error accessing the database of the catalog", e);
        CatalogServiceError error = CatalogServiceError.CATALOG_UNAVAILABLE;
        return ResponseEntity.status(error.getStatus())
                .body(new ErrorDTO(error.getCode(), error.getMessage(), List.of()));
    }
}
