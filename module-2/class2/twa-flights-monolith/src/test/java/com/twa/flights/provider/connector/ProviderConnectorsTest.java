package com.twa.flights.provider.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.shared.exception.TWAException;
import com.twa.flights.support.AbstractIntegrationTest;
import com.twa.flights.support.ProviderStubs;
import com.twa.flights.support.Requests;

/**
 * Characterization of the requests that the connectors send to the providers and of the limit of the amount
 * (behaviors #16 and #23). The three connectors build the same request.
 */
class ProviderConnectorsTest extends AbstractIntegrationTest {

    @Autowired
    private AlphaConnector alphaConnector;

    @Autowired
    private BetaConnector betaConnector;

    @Autowired
    private GammaConnector gammaConnector;

    @Test
    @DisplayName("[#23] round trip: origin, destination and departureDate of the first route, returnDate is the second date")
    void requestOfRoundTrip() {
        providers.allReturnRecordedRoundTrip();
        ClustersAvailabilityRequestDTO request = Requests.roundTrip();
        request.setCabin(" business ");
        request.setAmount(2);

        alphaConnector.availability(request);
        betaConnector.availability(request);
        gammaConnector.availability(request);

        String expected = "/itineraries?origin=BUE&destination=MIA&departureDate=2027-01-14&adults=1&children=1"
                + "&infants=1&returnDate=2027-01-24&amount=2&cabin=BUSINESS";
        assertThat(onlyRequest(ProviderStubs.ALPHA).getUrl()).isEqualTo(ProviderStubs.ALPHA + expected);
        assertThat(onlyRequest(ProviderStubs.BETA).getUrl()).isEqualTo(ProviderStubs.BETA + expected);
        assertThat(onlyRequest(ProviderStubs.GAMMA).getUrl()).isEqualTo(ProviderStubs.GAMMA + expected);
    }

    @Test
    @DisplayName("[#23] one way: without returnDate, without amount and the cabin ECONOMY by default")
    void oneWayRequest() {
        providers.allReturnRecordedRoundTrip();

        alphaConnector.availability(Requests.request("BUE", "MIA", "2027-01-14", 2, 0, 0));

        assertThat(onlyRequest(ProviderStubs.ALPHA).getUrl()).isEqualTo(ProviderStubs.ALPHA
                + "/itineraries?origin=BUE&destination=MIA&departureDate=2027-01-14&adults=2&children=0&infants=0"
                + "&cabin=ECONOMY");
    }

    @Test
    @DisplayName("[#23] multiple destinations: only the first route is sent, the third one is ignored")
    void multipleDestinationsRequest() {
        providers.allReturnRecordedRoundTrip();

        betaConnector.availability(Requests.request("BUE,MIA,SCL", "MIA,SCL,BUE", "2027-01-14,2027-01-20,2027-01-24",
                1, 0, 0));

        assertThat(onlyRequest(ProviderStubs.BETA).getUrl()).isEqualTo(ProviderStubs.BETA
                + "/itineraries?origin=BUE&destination=MIA&departureDate=2027-01-14&adults=1&children=0&infants=0"
                + "&returnDate=2027-01-20&cabin=ECONOMY");
    }

    @Test
    @DisplayName("[#23] without children or infants the parameter is sent without a value")
    void requestWithoutChildrenAndInfants() {
        providers.allReturnRecordedRoundTrip();

        gammaConnector.availability(Requests.request("BUE", "MIA", "2027-01-14", 1, null, null));

        assertThat(onlyRequest(ProviderStubs.GAMMA).getUrl()).isEqualTo(ProviderStubs.GAMMA
                + "/itineraries?origin=BUE&destination=MIA&departureDate=2027-01-14&adults=1&children&infants"
                + "&cabin=ECONOMY");
    }

    @Test
    @DisplayName("[#16] each connector cuts the result to the amount, without amount it returns everything")
    void amountLimitsEachProvider() {
        providers.allReturnRecordedRoundTrip();
        ClustersAvailabilityRequestDTO withAmount = Requests.roundTrip();
        withAmount.setAmount(2);

        assertThat(alphaConnector.availability(Requests.roundTrip())).hasSize(5);
        assertThat(alphaConnector.availability(withAmount)).hasSize(2);
        assertThat(betaConnector.availability(withAmount)).hasSize(2);
        assertThat(gammaConnector.availability(withAmount)).hasSize(2);
    }

    @Test
    @DisplayName("[#10] an error of the provider throws a TWAException in the connector")
    void errorOfTheProvider() {
        providers.respondWithStatus(ProviderStubs.ALPHA, 500);
        providers.respondWithStatus(ProviderStubs.BETA, 404);

        assertThatThrownBy(() -> alphaConnector.availability(Requests.roundTrip())).isInstanceOf(TWAException.class)
                .hasMessage("Error while calling provider alpha endpoint");
        assertThatThrownBy(() -> betaConnector.availability(Requests.roundTrip())).isInstanceOf(TWAException.class)
                .hasMessage("Error while calling provider beta endpoint");
    }

    private LoggedRequest onlyRequest(String provider) {
        List<LoggedRequest> requests = providers.requests(provider);
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }
}
