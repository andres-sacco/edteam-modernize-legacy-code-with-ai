package com.twa.flights.configuration;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final PricingApiKeyInterceptor pricingApiKeyInterceptor;

    public WebConfiguration(PricingApiKeyInterceptor pricingApiKeyInterceptor) {
        this.pricingApiKeyInterceptor = pricingApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pricingApiKeyInterceptor).addPathPatterns("/pricing/**");
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
