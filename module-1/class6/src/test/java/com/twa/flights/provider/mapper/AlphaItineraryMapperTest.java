package com.twa.flights.provider.mapper;

import static com.twa.flights.support.ItineraryTestData.ids;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.provider.dto.AlphaAvailabilityResponse;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.search.dto.FlightType;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.support.Fixtures;
import com.twa.flights.support.ProviderStubs;
import com.twa.flights.support.Requests;

// Characterization of the transformation of the provider Alpha with its recorded response (behaviors #11 and #24)
@ExtendWith(OutputCaptureExtension.class)
class AlphaItineraryMapperTest {

    private final AlphaItineraryMapper mapper = new AlphaItineraryMapper(new ProviderMapperHelper());

    @Test
    @DisplayName("[#11] each offer is an itinerary and each journey is a segment")
    void recordedResponse() {
        List<ItineraryDTO> itineraries = map(Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP));

        assertThat(ids(itineraries)).containsExactly("d15896b6-d0f0-4808-a420-82ea5650421b",
                "adb49624-bf8c-4da6-904e-2892cba9f9b6", "99f1ff5c-53dc-4a70-8784-bf8e5da5d3c9",
                "e485161b-e5e1-44fa-a786-3aaab579c6a6", "7e211600-69f4-48d0-b068-ddf019f5b6d2");

        ItineraryDTO first = itineraries.get(0);
        assertThat(first.getCarrier()).isEqualTo("AA");
        assertThat(first.getProvider()).isEqualTo(Provider.ALPHA);
        assertThat(first.getFlightType()).isEqualTo(FlightType.ROUND_TRIP);
        assertThat(first.getCabin()).isEqualTo(CabinClass.ECONOMY);
        assertThat(first.getSegments()).hasSize(2);
        assertThat(first.getSegments().get(0).getFlightDuration()).isNull();
        assertThat(first.getSegments().get(0).getLegs()).containsExactly(
                new LegDTO("BUE", "MIA", "2027-01-14", "00:45", "2027-01-14", "20:15", 3381, null));
        assertThat(first.getSegments().get(1).getLegs()).containsExactly(
                new LegDTO("MIA", "BUE", "2027-01-24", "02:15", "2027-01-24", "23:30", 2340, null));
    }

    @Test
    @DisplayName("[#11 #21] the fares come by type ADT, CHD and INF")
    void faresByPassengerType() {
        ItineraryDTO first = map(Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP)).get(0);

        assertThat(first.getPriceInfo().getAdults().getSubtotal()).hasToString("561.30");
        assertThat(first.getPriceInfo().getChildren().getSubtotal()).hasToString("809.76");
        assertThat(first.getPriceInfo().getInfants().getSubtotal()).hasToString("160.21");
    }

    @Test
    @DisplayName("[#11 #22] the baggage comes from luggage.cabin and luggage.hold, without pieces the weight is null")
    void baggage() {
        ItineraryDTO first = map(Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP)).get(0);

        assertThat(first.getBaggage().getCarryOn()).isEqualTo(new BaggageAllowanceDTO(1, 8));
        assertThat(first.getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(0, null));
    }

    @Test
    @DisplayName("[#11] the flights of a journey are the legs of the segment, in the same order")
    void connections() {
        ItineraryDTO withConnection = map(Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP)).get(3);

        assertThat(withConnection.getSegments().get(0).getLegs()).extracting(LegDTO::getOrigin, LegDTO::getNumber)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BUE", 4843),
                        org.assertj.core.groups.Tuple.tuple("SAO", 361));
    }

    @Test
    @DisplayName("[#11] the cabin uses the booking classes Y, W and C")
    void cabinWithBookingClasses() {
        ObjectNode response = Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP);
        offer(response, 0).put("cabinClass", "W");
        offer(response, 1).put("cabinClass", "C");

        List<ItineraryDTO> itineraries = map(response);

        assertThat(itineraries).extracting(ItineraryDTO::getCabin).startsWith(CabinClass.PREMIUM_ECONOMY,
                CabinClass.BUSINESS, CabinClass.ECONOMY);
    }

    @Test
    @DisplayName("[#11 #24] an unknown cabin discards only that offer and logs a WARN")
    void unknownCabinDiscardsTheOffer(CapturedOutput output) {
        ObjectNode response = Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP);
        offer(response, 0).put("cabinClass", "F");

        List<ItineraryDTO> itineraries = map(response);

        assertThat(itineraries).hasSize(4);
        assertThat(ids(itineraries)).doesNotContain("d15896b6-d0f0-4808-a420-82ea5650421b");
        assertThat(output).contains("The offer d15896b6-d0f0-4808-a420-82ea5650421b of the provider alpha can not be "
                + "transformed: Unknown cabin class F");
    }

    @Test
    @DisplayName("[#21 #24] without the fare of a requested passenger type the offer is discarded")
    void missingFareDiscardsTheOffer(CapturedOutput output) {
        ObjectNode response = Fixtures.tree(ProviderStubs.ALPHA_ROUND_TRIP);
        offer(response, 1).withObject("/pricing").withArray("passengers").remove(1);

        assertThat(map(response)).hasSize(4);
        assertThat(output).contains("can not be transformed: The provider does not return the fare for CHILD");
        // Without children in the request the same offer is valid
        assertThat(mapper.map(Fixtures.convert(response, AlphaAvailabilityResponse.class),
                Requests.request("BUE,MIA", "MIA,BUE", "2027-01-14,2027-01-24", 1, 0, 1))).hasSize(5);
    }

    @Test
    @DisplayName("[#24] an empty response or without results returns an empty list")
    void emptyResponses() {
        assertThat(mapper.map(null, Requests.roundTrip())).isEmpty();
        assertThat(mapper.map(new AlphaAvailabilityResponse(null), Requests.roundTrip())).isEmpty();
    }

    private List<ItineraryDTO> map(ObjectNode response) {
        return mapper.map(Fixtures.convert(response, AlphaAvailabilityResponse.class), Requests.roundTrip());
    }

    private ObjectNode offer(ObjectNode response, int index) {
        return (ObjectNode) response.withArray("results").get(index);
    }
}
