package com.twa.flights.shared.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.twa.flights.catalog.configuration.CatalogRolloutApiKeyInterceptor;
import com.twa.flights.pricing.configuration.PricingApiKeyInterceptor;
import com.twa.flights.shared.service.AppInfoService;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfiguration {

    public static final String PRICING_API_KEY = "pricingApiKey";
    public static final String CATALOG_ROLLOUT_API_KEY = "catalogRolloutApiKey";

    AppInfoService appInfoService;

    @Autowired
    public SwaggerConfiguration(AppInfoService appInfoService) {
        this.appInfoService = appInfoService;
    }

    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder().group("default").packagesToScan("com.twa.flights.search.controller")
                .pathsToMatch("/clusters/**").build();
    }

    @Bean
    public GroupedOpenApi pricingApi() {
        return GroupedOpenApi.builder().group("pricing").packagesToScan("com.twa.flights.pricing.controller")
                .pathsToMatch("/pricing/**").build();
    }

    @Bean
    public GroupedOpenApi catalogRolloutApi() {
        return GroupedOpenApi.builder().group("catalog-rollout").packagesToScan("com.twa.flights.catalog.controller")
                .pathsToMatch("/catalog/rollout/**", "/catalog/rollout").build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("TWA Flights")
                .description("This is the documentation of the API to obtain all the itineraries with price")
                .version(appInfoService.getVersion()))
                .components(new Components().addSecuritySchemes(PRICING_API_KEY,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(PricingApiKeyInterceptor.API_KEY_HEADER))
                        .addSecuritySchemes(CATALOG_ROLLOUT_API_KEY,
                                new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                        .name(CatalogRolloutApiKeyInterceptor.API_KEY_HEADER)));
    }
}
