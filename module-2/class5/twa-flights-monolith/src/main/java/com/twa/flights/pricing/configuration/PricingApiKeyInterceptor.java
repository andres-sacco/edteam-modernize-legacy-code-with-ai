package com.twa.flights.pricing.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.twa.flights.pricing.exception.PricingError;
import com.twa.flights.shared.exception.APIException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// The administration of the pricing is only available with the API key
@Component
public class PricingApiKeyInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final String apiKey;

    public PricingApiKeyInterceptor(@Value("${pricing.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!apiKey.equals(request.getHeader(API_KEY_HEADER))) {
            throw new APIException(HttpStatus.UNAUTHORIZED, PricingError.UNAUTHORIZED);
        }
        return true;
    }
}
