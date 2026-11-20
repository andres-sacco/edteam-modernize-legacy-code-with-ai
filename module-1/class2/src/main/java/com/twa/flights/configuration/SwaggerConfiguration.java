package com.twa.flights.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twa.flights.service.AppInfoService;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfiguration {

    public static final String PRICING_API_KEY = "pricingApiKey";

    AppInfoService appInfoService;

    @Autowired
    public SwaggerConfiguration(AppInfoService appInfoService) {
        this.appInfoService = appInfoService;
    }

    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder().group("default").packagesToScan("com.twa.flights.controller")
                .pathsToMatch("/clusters/**").build();
    }

    @Bean
    public GroupedOpenApi pricingApi() {
        return GroupedOpenApi.builder().group("pricing").packagesToScan("com.twa.flights.controller")
                .pathsToMatch("/pricing/**").build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("TWA Flights")
                .description("This is the documentation of the API to obtain all the itineraries with price")
                .version(appInfoService.getVersion()))
                .components(new Components().addSecuritySchemes(PRICING_API_KEY,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(PricingApiKeyInterceptor.API_KEY_HEADER)));
    }
}
