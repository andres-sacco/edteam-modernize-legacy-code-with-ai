package com.twa.flights.provider.mapper;

import static com.twa.flights.support.ItineraryTestData.ids;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.provider.dto.BetaAvailabilityResponse;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.search.dto.FlightType;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.support.Fixtures;
import com.twa.flights.support.ProviderStubs;
import com.twa.flights.support.Requests;

// Characterization of the transformation of the provider Beta with its recorded response (behaviors #12 and #24)
@ExtendWith(OutputCaptureExtension.class)
class BetaItineraryMapperTest {

    private final BetaItineraryMapper mapper = new BetaItineraryMapper(new ProviderMapperHelper());

    @Test
    @DisplayName("[#12] each flight option is an itinerary and each bound is a segment")
    void recordedResponse() {
        List<ItineraryDTO> itineraries = map(Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP));

        assertThat(ids(itineraries)).containsExactly("f8466941-3630-4df4-a495-c617a5fbde15",
                "e432414e-a318-4e34-8cde-26ef07b2f041", "26797bdf-b072-4874-9eb7-1329e6715ad7",
                "af182674-446e-4048-a3c7-8dd012920f96", "c3fdbd74-6e3b-4df2-b9c2-dbf7558f1fd0");

        ItineraryDTO first = itineraries.get(0);
        assertThat(first.getCarrier()).isEqualTo("LA");
        assertThat(first.getProvider()).isEqualTo(Provider.BETA);
        assertThat(first.getFlightType()).isEqualTo(FlightType.ROUND_TRIP);
        assertThat(first.getCabin()).isEqualTo(CabinClass.ECONOMY);
        assertThat(first.getSegments().get(0).getLegs()).containsExactly(
                new LegDTO("BUE", "MIA", "2027-01-14", "08:30", "2027-01-14", "20:45", 1347, null));
        assertThat(first.getSegments().get(1).getLegs()).containsExactly(
                new LegDTO("MIA", "BUE", "2027-01-24", "05:45", "2027-01-24", "21:15", 4216, null));
    }

    @Test
    @DisplayName("[#12] the prices come in cents and they are divided by 100 with two decimals")
    void pricesInCents() {
        ItineraryDTO first = map(Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP)).get(0);

        // adult: base_fare_cents 10812 and tax_cents 13960
        assertThat(first.getPriceInfo().getAdults().getBase()).hasToString("108.12");
        assertThat(first.getPriceInfo().getAdults().getTax()).hasToString("139.60");
        assertThat(first.getPriceInfo().getAdults().getSubtotal()).hasToString("247.72");
        assertThat(first.getPriceInfo().getChildren().getSubtotal()).hasToString("640.02");
        assertThat(first.getPriceInfo().getInfants().getSubtotal()).hasToString("322.58");
    }

    @Test
    @DisplayName("[#12 #22] the baggage comes from hand and checked, without pieces the weight is null")
    void baggage() {
        List<ItineraryDTO> itineraries = map(Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP));

        assertThat(itineraries.get(0).getBaggage().getCarryOn()).isEqualTo(new BaggageAllowanceDTO(1, 8));
        assertThat(itineraries.get(0).getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(1, 23));
        assertThat(itineraries.get(1).getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(0, null));
    }

    @Test
    @DisplayName("[#12] the OUTBOUND bound is always the first segment")
    void outboundIsTheFirstSegment() {
        ObjectNode response = Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP);
        ArrayNode bounds = option(response, 0).withArray("bounds");
        JsonNode outbound = bounds.remove(0);
        bounds.add(outbound);
        assertThat(bounds.get(0).get("direction").asText()).isEqualTo("INBOUND");

        ItineraryDTO first = map(response).get(0);

        assertThat(first.getSegments().get(0).firstLeg().getOrigin()).isEqualTo("BUE");
        assertThat(first.getSegments().get(1).firstLeg().getOrigin()).isEqualTo("MIA");
    }

    @Test
    @DisplayName("[#12] the cabin is the name of the enum in any case, an unknown one discards the option")
    void cabin(CapturedOutput output) {
        ObjectNode response = Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP);
        option(response, 0).put("cabin", "premium_economy");
        option(response, 1).put("cabin", "Business");
        option(response, 2).put("cabin", "first");

        List<ItineraryDTO> itineraries = map(response);

        assertThat(itineraries).hasSize(4);
        assertThat(itineraries).extracting(ItineraryDTO::getCabin).startsWith(CabinClass.PREMIUM_ECONOMY,
                CabinClass.BUSINESS, CabinClass.ECONOMY);
        assertThat(output).contains(
                "The option 26797bdf-b072-4874-9eb7-1329e6715ad7 of the provider beta can not be transformed");
    }

    @Test
    @DisplayName("[#12] with a status different from OK the response is ignored")
    void statusDifferentFromOk(CapturedOutput output) {
        ObjectNode response = Fixtures.tree(ProviderStubs.BETA_ROUND_TRIP);
        response.put("status", "ERROR");

        assertThat(map(response)).isEmpty();
        assertThat(output).contains("The provider beta returns the status ERROR");
    }

    @Test
    @DisplayName("[#12] an empty response, without data or without options returns an empty list")
    void emptyResponses() {
        assertThat(mapper.map(null, Requests.roundTrip())).isEmpty();
        assertThat(mapper.map(new BetaAvailabilityResponse("OK", null), Requests.roundTrip())).isEmpty();
        assertThat(mapper.map(new BetaAvailabilityResponse("OK", new BetaAvailabilityResponse.Data(null)),
                Requests.roundTrip())).isEmpty();
    }

    private List<ItineraryDTO> map(ObjectNode response) {
        return mapper.map(Fixtures.convert(response, BetaAvailabilityResponse.class), Requests.roundTrip());
    }

    private ObjectNode option(ObjectNode response, int index) {
        return (ObjectNode) response.withObject("/data").withArray("flight_options").get(index);
    }
}
