package com.twa.flights.connector;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.twa.flights.connector.configuration.ProviderAlphaConnectorConfiguration;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.provider.AlphaAvailabilityResponse;
import com.twa.flights.dto.request.AvailabilityRequestDTO;
import com.twa.flights.enums.CabinClass;
import com.twa.flights.exception.TWAException;
import com.twa.flights.mapper.provider.AlphaItineraryMapper;

@Component
public class AlphaConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlphaConnector.class);

    private static final String SEARCH = "/itineraries";

    private final RestClient restClient;
    private final AlphaItineraryMapper mapper;

    @Autowired
    public AlphaConnector(ProviderAlphaConnectorConfiguration configuration, RestClient.Builder restClientBuilder,
            AlphaItineraryMapper mapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(configuration.getConnectionTimeout());
        requestFactory.setReadTimeout(configuration.getReadTimeout());

        this.restClient = restClientBuilder.baseUrl(configuration.getHost()).requestFactory(requestFactory).build();
        this.mapper = mapper;
    }

    public List<ItineraryDTO> availability(AvailabilityRequestDTO availabilityRequest) {
        String[] departureDate = availabilityRequest.getDeparture().split(",");
        String[] origin = availabilityRequest.getFrom().split(",");
        String[] destination = availabilityRequest.getTo().split(",");

        AlphaAvailabilityResponse response = restClient.get().uri(uriBuilder -> {
            uriBuilder.path(SEARCH).queryParam("origin", origin[0]).queryParam("destination", destination[0])
                    .queryParam("departureDate", departureDate[0]).queryParam("adults", availabilityRequest.getAdults())
                    .queryParam("children", availabilityRequest.getChildren())
                    .queryParam("infants", availabilityRequest.getInfants());

            if (departureDate.length > 1) {
                uriBuilder.queryParam("returnDate", departureDate[1]);
            }

            if (availabilityRequest.getAmount() != null) {
                uriBuilder.queryParam("amount", availabilityRequest.getAmount());
            }

            uriBuilder.queryParam("cabin", availabilityRequest.getCabin() != null
                    ? availabilityRequest.getCabin().trim().toUpperCase(Locale.ROOT)
                    : CabinClass.ECONOMY.name());
            return uriBuilder.build();
        }).retrieve().onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
            LOGGER.error("Error while calling endpoint {} with status code {}", SEARCH, clientResponse.getStatusCode());
            throw new TWAException("Error while calling provider alpha endpoint");
        }).body(AlphaAvailabilityResponse.class);

        List<ItineraryDTO> itineraries = mapper.map(response, availabilityRequest);

        // Without an amount in the request, all the itineraries of the provider are used
        if (availabilityRequest.getAmount() != null) {
            itineraries = itineraries.stream().limit(availabilityRequest.getAmount()).collect(Collectors.toList());
        }

        return itineraries;
    }
}
