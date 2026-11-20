package com.twa.flights.catalog.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.google.common.collect.Lists;
import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.client.configuration.CatalogClientConfiguration;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.helper.CityCodes;

/**
 * Remote implementation of {@link CatalogApi}: calls the catalog service (twa-flights-catalog) and keeps the contract
 * of the local implementation, an unknown city is null (or it is not in the map). Any other problem (error, timeout,
 * an invalid city) is a {@link CatalogUnavailableException}, never a null, so a failure of the service is not confused
 * with an unknown city.
 *
 * Both methods use GET /cities?codes=: the code travels as a query parameter, so codes like "A/B", ".." or "%" are
 * unknown cities, as in the local catalog, and not an error of the server for an invalid path.
 */
@Component
public class CatalogClient implements CatalogApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogClient.class);

    private static final String CITIES = "/cities?codes={codes}";

    // The maximum of codes that the service accepts in one request
    static final int MAX_CODES = 50;

    private final RestClient restClient;

    @Autowired
    public CatalogClient(CatalogClientConfiguration configuration, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(configuration.getConnectionTimeout());
        requestFactory.setReadTimeout(configuration.getReadTimeout());

        this.restClient = restClientBuilder.baseUrl(configuration.getHost()).requestFactory(requestFactory).build();
    }

    @Override
    public CityDTO getCity(String code) {
        LOGGER.debug("Obtain the information for code: {} from the catalog service", code);

        // The local implementation returns null for an empty code
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return getCities(List.of(code)).get(code);
    }

    @Override
    public Map<String, CityDTO> getCities(Collection<String> codes) {
        LOGGER.debug("Obtain the information for codes: {} from the catalog service", codes);
        Map<String, CityDTO> result = new LinkedHashMap<>();

        for (List<String> batch : Lists.partition(List.copyOf(CityCodes.distinct(codes)), MAX_CODES)) {
            List<CityDTO> cities = call(batch);
            // The service matches the codes with the collation of MySQL, the client assigns each city in the same way
            for (String code : batch) {
                cities.stream().filter(city -> CityCodes.matches(code, city.getCode())).findFirst()
                        .ifPresent(city -> result.put(code, city));
            }
        }
        return result;
    }

    private List<CityDTO> call(List<String> codes) {
        CityDTO[] cities;
        try {
            cities = restClient.get().uri(CITIES, String.join(",", codes)).exchange((request, response) -> {
                if (response.getStatusCode().isError()) {
                    LOGGER.error("Error while calling endpoint {} with status code {}", CITIES,
                            response.getStatusCode());
                    throw new CatalogUnavailableException(
                            "The catalog service answered " + response.getStatusCode() + " for the cities " + codes);
                }
                return response.bodyTo(CityDTO[].class);
            });
        } catch (RestClientException e) {
            LOGGER.error("Error while calling endpoint {} for the cities {}", CITIES, codes, e);
            throw new CatalogUnavailableException("The catalog service is not available", e);
        }

        if (cities == null) {
            throw new CatalogUnavailableException("The catalog service answered without a body for " + codes);
        }

        // An empty time zone does not fail: the duration of the flights is calculated with the zone of the JVM
        for (CityDTO city : cities) {
            if (city == null || StringUtils.isBlank(city.getCode()) || StringUtils.isBlank(city.getTimeZone())
                    || city.getCountry() == null) {
                LOGGER.error("The catalog service returned an incomplete city for the codes {}", codes);
                throw new CatalogUnavailableException("The catalog service returned an incomplete city for " + codes);
            }
        }
        return Arrays.asList(cities);
    }
}
