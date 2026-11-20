package com.twa.flights.catalog.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.twa.flights.catalog.api.CatalogError;
import com.twa.flights.shared.exception.APIException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// The flag of the rollout of the catalog is only available with its own API key, not the one of the pricing
@Component
public class CatalogRolloutApiKeyInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final String apiKey;

    public CatalogRolloutApiKeyInterceptor(@Value("${catalog.rollout.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!apiKey.equals(request.getHeader(API_KEY_HEADER))) {
            throw new APIException(HttpStatus.UNAUTHORIZED, CatalogError.UNAUTHORIZED);
        }
        return true;
    }
}
