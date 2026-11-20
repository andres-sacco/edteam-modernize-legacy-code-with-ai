package com.twa.flights.search.serializer;

import static com.twa.flights.support.ItineraryTestData.roundTrip;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.twa.flights.search.dto.ClusterSearchDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.MarkupDTO;
import com.twa.flights.search.dto.PaxPriceDTO;
import com.twa.flights.search.dto.PriceInfoDTO;
import com.twa.flights.search.dto.response.PaginationDTO;
import com.twa.flights.shared.enums.PassengerType;
import com.twa.flights.support.ItineraryTestData;

// Characterization of the format of the saved searches (behavior #27)
class JsonSerializerTest {

    @Test
    @DisplayName("[#27] the payload is saved in snake_case, with the nulls and the scale of the decimals")
    void payloadInSnakeCase() {
        String payload = new String(JsonSerializer.serialize(search()), StandardCharsets.UTF_8);

        assertThat(payload).startsWith("{\"id\":\"TWA_test\",\"pagination\":{\"offset\":0,\"limit\":1,\"total\":1},"
                + "\"itineraries\":[{\"id\":\"ar\",\"segments\":[{\"legs\":[{\"origin\":\"BUE\"");
        assertThat(payload).contains("\"price_info\":", "\"flight_type\":\"ROUND_TRIP\"", "\"departure_date\":",
                "\"departure_time\":", "\"flight_duration\":\"10:00\"", "\"carry_on\":", "\"weight_kg\":null",
                "\"children\":null", "\"infants\":null", "\"markup\":{\"percentage\":5.00,\"amount\":365.1100}",
                "\"total\":1095.3300");
        assertThat(payload).doesNotContain("priceInfo", "flightType", "firstSegment", "totalAmount");
    }

    @Test
    @DisplayName("[#27] the payload is read back into the same search")
    void roundTripOfTheSerialization() {
        ClusterSearchDTO search = search();

        ClusterSearchDTO read = JsonSerializer.deserialize(JsonSerializer.serialize(search), ClusterSearchDTO.class);

        assertThat(read.getId()).isEqualTo(search.getId());
        assertThat(read.getItineraries()).isEqualTo(search.getItineraries());
        assertThat(read.getItineraries().get(0).getPriceInfo().getAdults().getTotal()).hasToString("1095.3300");
        assertThat(read.getPagination().getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("[#27] an invalid payload is read as null, without an exception")
    void invalidPayload() {
        assertThat(JsonSerializer.deserialize("not json".getBytes(StandardCharsets.UTF_8), ClusterSearchDTO.class))
                .isNull();
        assertThat(JsonSerializer.deserialize(null, ClusterSearchDTO.class)).isNull();
    }

    private ClusterSearchDTO search() {
        PaxPriceDTO adults = ItineraryTestData.pax(PassengerType.ADULT, "339.39", "25.72", 2);
        adults.setMarkup(new MarkupDTO(new BigDecimal("5.00"), new BigDecimal("365.1100")));
        adults.setTotal(new BigDecimal("1095.3300"));
        ItineraryDTO itinerary = roundTrip("ar", "AR", new PriceInfoDTO(adults, null, null));

        return new ClusterSearchDTO("TWA_test", Lists.newArrayList(itinerary), new PaginationDTO(0, 1, 1));
    }
}
