package com.twa.flights.shared.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.twa.flights.catalog.configuration.CatalogRolloutApiKeyInterceptor;
import com.twa.flights.pricing.configuration.PricingApiKeyInterceptor;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final PricingApiKeyInterceptor pricingApiKeyInterceptor;
    private final CatalogRolloutApiKeyInterceptor catalogRolloutApiKeyInterceptor;

    public WebConfiguration(PricingApiKeyInterceptor pricingApiKeyInterceptor,
            CatalogRolloutApiKeyInterceptor catalogRolloutApiKeyInterceptor) {
        this.pricingApiKeyInterceptor = pricingApiKeyInterceptor;
        this.catalogRolloutApiKeyInterceptor = catalogRolloutApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pricingApiKeyInterceptor).addPathPatterns("/pricing/**");
        registry.addInterceptor(catalogRolloutApiKeyInterceptor).addPathPatterns("/catalog/rollout", "/catalog/rollout/**");
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter messageConverter) {
                ObjectMapper objectMapper = messageConverter.getObjectMapper();
                objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            }
        }
    }
}
