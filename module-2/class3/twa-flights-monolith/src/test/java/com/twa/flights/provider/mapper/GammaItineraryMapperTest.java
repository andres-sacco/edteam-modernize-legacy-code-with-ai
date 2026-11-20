package com.twa.flights.provider.mapper;

import static com.twa.flights.support.ItineraryTestData.ids;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.provider.dto.GammaAvailabilityResponse;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.search.dto.FlightType;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.support.Fixtures;
import com.twa.flights.support.ProviderStubs;
import com.twa.flights.support.Requests;

// Characterization of the transformation of the provider Gamma with its recorded response (behaviors #13 and #24)
@ExtendWith(OutputCaptureExtension.class)
class GammaItineraryMapperTest {

    private static final String JA_WITH_CONNECTIONS = "fa07a8be-bff5-4278-8ec9-0530ca982839";

    private final GammaItineraryMapper mapper = new GammaItineraryMapper(new ProviderMapperHelper());

    @Test
    @DisplayName("[#13] each itinerary of Gamma is an itinerary, the dates lose the seconds")
    void recordedResponse() {
        List<ItineraryDTO> itineraries = map(Fixtures.tree(ProviderStubs.GAMMA_ROUND_TRIP));

        assertThat(ids(itineraries)).containsExactly("3611ff29-66f5-4444-be3e-6e55539f1e9b",
                "9c3d4c18-8e2f-401c-8b7d-5d8cf3677204", "df7f294f-7d88-4165-9625-5e67e7a395b7", JA_WITH_CONNECTIONS,
                "df3a40aa-5aa7-442f-b816-41572f15796b");

        ItineraryDTO first = itineraries.get(0);
        assertThat(first.getCarrier()).isEqualTo("H2");
        assertThat(first.getProvider()).isEqualTo(Provider.GAMMA);
        assertThat(first.getFlightType()).isEqualTo(FlightType.ROUND_TRIP);
        assertThat(first.getCabin()).isEqualTo(CabinClass.ECONOMY);
        assertThat(first.getSegments().get(0).getLegs()).containsExactly(
                new LegDTO("BUE", "MIA", "2027-01-14", "07:15", "2027-01-14", "22:30", 7231, null));
        assertThat(first.getPriceInfo().getAdults().getSubtotal()).hasToString("367.20");
        assertThat(first.getPriceInfo().getChildren().getSubtotal()).hasToString("530.62");
        assertThat(first.getPriceInfo().getInfants().getSubtotal()).hasToString("191.40");
    }

    @Test
    @DisplayName("[#13] the flights are grouped by SegmentIndex and sorted by Sequence, whatever the order of the list")
    void flightsAreGroupedBySegmentIndex() {
        ObjectNode response = Fixtures.tree(ProviderStubs.GAMMA_ROUND_TRIP);
        ArrayNode flights = itinerary(response, 3).withArray("Flights");
        List<JsonNode> reversed = Lists.reverse(Lists.newArrayList(flights.elements()));
        flights.removeAll();
        flights.addAll(reversed);

        ItineraryDTO withConnections = map(response).get(3);

        assertThat(withConnections.getSegments()).hasSize(2);
        assertThat(withConnections.getSegments().get(0).getLegs())
                .extracting(LegDTO::getOrigin, LegDTO::getDestination)
                .containsExactly(tuple("BUE", "SAO"), tuple("SAO", "MIA"));
        assertThat(withConnections.getSegments().get(1).getLegs())
                .extracting(LegDTO::getOrigin, LegDTO::getDestination)
                .containsExactly(tuple("MIA", "SAO"), tuple("SAO", "BUE"));
    }

    @Test
    @DisplayName("[#13] the baggage is a text nPC[mKG], without pieces the weight is null")
    void baggageAsText() {
        ObjectNode response = Fixtures.tree(ProviderStubs.GAMMA_ROUND_TRIP);
        itinerary(response, 2).withObject("/Bags").put("Checked", "2PC");

        List<ItineraryDTO> itineraries = map(response);

        assertThat(itineraries.get(0).getBaggage().getCarryOn()).isEqualTo(new BaggageAllowanceDTO(1, 8));
        assertThat(itineraries.get(0).getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(1, 23));
        assertThat(itineraries.get(1).getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(0, null));
        assertThat(itineraries.get(2).getBaggage().getChecked()).isEqualTo(new BaggageAllowanceDTO(2, null));
    }

    @Test
    @DisplayName("[#13 #24] an unknown format of baggage discards only that itinerary and logs a WARN")
    void unknownBaggageFormat(CapturedOutput output) {
        ObjectNode response = Fixtures.tree(ProviderStubs.GAMMA_ROUND_TRIP);
        itinerary(response, 0).withObject("/Bags").put("Checked", "23KG");
        itinerary(response, 1).withObject("/Bags").put("CarryOn", "1pc8kg");

        assertThat(map(response)).hasSize(3);
        assertThat(output).contains("The itinerary 3611ff29-66f5-4444-be3e-6e55539f1e9b of the provider gamma can not "
                + "be transformed: Unknown format of baggage 23KG");
    }

    @Test
    @DisplayName("[#13] the cabin uses ECO, PEY and BUS, an unknown one discards the itinerary")
    void cabin() {
        ObjectNode response = Fixtures.tree(ProviderStubs.GAMMA_ROUND_TRIP);
        itinerary(response, 0).put("CabinType", "PEY");
        itinerary(response, 1).put("CabinType", "BUS");
        itinerary(response, 2).put("CabinType", "FST");

        List<ItineraryDTO> itineraries = map(response);

        assertThat(itineraries).hasSize(4);
        assertThat(itineraries).extracting(ItineraryDTO::getCabin).startsWith(CabinClass.PREMIUM_ECONOMY,
                CabinClass.BUSINESS, CabinClass.ECONOMY);
    }

    @Test
    @DisplayName("[#24] an empty response or without itineraries returns an empty list")
    void emptyResponses() {
        assertThat(mapper.map(null, Requests.roundTrip())).isEmpty();
        assertThat(mapper.map(new GammaAvailabilityResponse(null), Requests.roundTrip())).isEmpty();
    }

    private List<ItineraryDTO> map(ObjectNode response) {
        return mapper.map(Fixtures.convert(response, GammaAvailabilityResponse.class), Requests.roundTrip());
    }

    private ObjectNode itinerary(ObjectNode response, int index) {
        return (ObjectNode) response.withArray("Itineraries").get(index);
    }
}
