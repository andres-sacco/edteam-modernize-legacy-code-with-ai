package com.twa.flights.search.helper;

import static com.twa.flights.support.ItineraryTestData.INBOUND_DATE;
import static com.twa.flights.support.ItineraryTestData.OUTBOUND_DATE;
import static com.twa.flights.support.ItineraryTestData.ids;
import static com.twa.flights.support.ItineraryTestData.itinerary;
import static com.twa.flights.support.ItineraryTestData.leg;
import static com.twa.flights.support.ItineraryTestData.pax;
import static com.twa.flights.support.ItineraryTestData.segment;
import static com.twa.flights.support.ItineraryTestData.totalPrice;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.BaggageDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.PriceInfoDTO;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.shared.enums.PassengerType;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.support.Requests;

// Characterization of the filters of the search (behavior #19)
class ItineraryFilterHelperTest {

    private final ItineraryFilterHelper helper = new ItineraryFilterHelper();

    @Test
    @DisplayName("[#19] without filters all the itineraries are returned in the same order")
    void withoutFilters() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(departingAt("b", "10:00"), departingAt("a", "09:00"));

        assertThat(ids(helper.filter(itineraries, request()))).containsExactly("b", "a");
    }

    @Test
    @DisplayName("[#19] maxStops uses the segment with more stops (legs - 1)")
    void maxStops() {
        ItineraryDTO direct = itinerary("direct", Provider.ALPHA, "AR", totalPrice("100"),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)),
                segment("10:00", leg("MIA", "BUE", INBOUND_DATE + "T08:00", INBOUND_DATE + "T18:00", 2)));
        // Direct outbound, but one stop in the return
        ItineraryDTO oneStop = itinerary("one-stop", Provider.ALPHA, "AR", totalPrice("100"),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)),
                segment("10:00", leg("MIA", "LIM", INBOUND_DATE + "T08:00", INBOUND_DATE + "T12:00", 2),
                        leg("LIM", "BUE", INBOUND_DATE + "T13:00", INBOUND_DATE + "T18:00", 3)));
        ClustersAvailabilityRequestDTO request = request();

        request.setMaxStops(0);
        assertThat(ids(helper.filter(Lists.newArrayList(direct, oneStop), request))).containsExactly("direct");
        request.setMaxStops(1);
        assertThat(ids(helper.filter(Lists.newArrayList(direct, oneStop), request))).containsExactly("direct",
                "one-stop");
    }

    @Test
    @DisplayName("[#19] minPrice and maxPrice are inclusive and use the total of all the passengers")
    void priceRange() {
        // 2 adults of 50 + 1 child of 20 = total 120
        PriceInfoDTO withChild = new PriceInfoDTO(pax(PassengerType.ADULT, "50", "0", 2),
                pax(PassengerType.CHILD, "20", "0", 1), null);
        ItineraryDTO family = itinerary("family", Provider.ALPHA, "AR", withChild,
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)));
        ItineraryDTO cheap = withPrice("cheap", "100.00");
        ItineraryDTO expensive = withPrice("expensive", "200.01");
        ClustersAvailabilityRequestDTO request = request();
        request.setMinPrice(new BigDecimal("100"));
        request.setMaxPrice(new BigDecimal("120.00"));

        assertThat(ids(helper.filter(Lists.newArrayList(family, cheap, expensive), request)))
                .containsExactly("family", "cheap");
    }

    @Test
    @DisplayName("[#19] the price filter uses the total with markup")
    void priceWithMarkup() {
        ItineraryDTO itinerary = withPrice("marked-up", "100.00");
        itinerary.getPriceInfo().getAdults().setTotal(new BigDecimal("150.0000"));
        ClustersAvailabilityRequestDTO request = request();
        request.setMaxPrice(new BigDecimal("120"));

        assertThat(helper.filter(Lists.newArrayList(itinerary), request)).isEmpty();
    }

    @Test
    @DisplayName("[#19] carriers and excludedCarriers are lists, in any case and with spaces")
    void carriers() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(withCarrier("AR"), withCarrier("AA"), withCarrier("DL"));
        ClustersAvailabilityRequestDTO only = request();
        only.setCarriers(" ar, aa ,");
        ClustersAvailabilityRequestDTO excluded = request();
        excluded.setExcludedCarriers("dl");
        ClustersAvailabilityRequestDTO both = request();
        both.setCarriers("AR,AA");
        both.setExcludedCarriers("AR");

        assertThat(ids(helper.filter(itineraries, only))).containsExactly("AR", "AA");
        assertThat(ids(helper.filter(itineraries, excluded))).containsExactly("AR", "AA");
        assertThat(ids(helper.filter(itineraries, both))).containsExactly("AA");
    }

    @Test
    @DisplayName("[#19] departureTime uses the hour of the first leg of the outbound, the start inclusive and the end exclusive")
    void departureTime() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(departingAt("00:00", "00:00"),
                departingAt("05:59", "05:59"), departingAt("06:00", "06:00"), departingAt("11:59", "11:59"),
                departingAt("12:00", "12:00"), departingAt("18:00", "18:00"), departingAt("23:59", "23:59"));

        assertThat(ids(helper.filter(itineraries, withDepartureTime("NIGHT")))).containsExactly("00:00", "05:59");
        assertThat(ids(helper.filter(itineraries, withDepartureTime(" morning ")))).containsExactly("06:00",
                "11:59");
        assertThat(ids(helper.filter(itineraries, withDepartureTime("AFTERNOON")))).containsExactly("12:00");
        assertThat(ids(helper.filter(itineraries, withDepartureTime("EVENING")))).containsExactly("18:00", "23:59");
    }

    @Test
    @DisplayName("[#19] checkedBaggage looks if checked.pieces > 0, without baggage it is false")
    void checkedBaggage() {
        ItineraryDTO withBag = withCarrier("with-bag");
        withBag.setBaggage(new BaggageDTO(null, new BaggageAllowanceDTO(1, 23)));
        ItineraryDTO withoutPieces = withCarrier("without-pieces");
        withoutPieces.setBaggage(new BaggageDTO(null, new BaggageAllowanceDTO(0, null)));
        ItineraryDTO withoutBaggage = withCarrier("without-baggage");
        withoutBaggage.setBaggage(null);
        List<ItineraryDTO> itineraries = Lists.newArrayList(withBag, withoutPieces, withoutBaggage);
        ClustersAvailabilityRequestDTO yes = request();
        yes.setCheckedBaggage(true);
        ClustersAvailabilityRequestDTO no = request();
        no.setCheckedBaggage(false);

        assertThat(ids(helper.filter(itineraries, yes))).containsExactly("with-bag");
        assertThat(ids(helper.filter(itineraries, no))).containsExactly("without-pieces", "without-baggage");
    }

    @Test
    @DisplayName("[#19] splitCodes normalizes the codes and ignores the empty ones")
    void splitCodes() {
        assertThat(ItineraryFilterHelper.splitCodes(" ar ,,Aa, ")).containsExactlyInAnyOrder("AR", "AA");
        assertThat(ItineraryFilterHelper.splitCodes(",")).isEmpty();
    }

    // private function(s)

    private static ClustersAvailabilityRequestDTO request() {
        return Requests.request("BUE", "MIA", "2027-01-14", 1, 0, 0);
    }

    private static ClustersAvailabilityRequestDTO withDepartureTime(String range) {
        ClustersAvailabilityRequestDTO request = request();
        request.setDepartureTime(range);
        return request;
    }

    private static ItineraryDTO withPrice(String id, String total) {
        return itinerary(id, Provider.ALPHA, "AR", totalPrice(total),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)));
    }

    // The id is the carrier, to read the assertions easily
    private static ItineraryDTO withCarrier(String carrier) {
        return itinerary(carrier, Provider.ALPHA, carrier, totalPrice("100"),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)));
    }

    private static ItineraryDTO departingAt(String id, String time) {
        return itinerary(id, Provider.ALPHA, "AR", totalPrice("100"),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T" + time, OUTBOUND_DATE + "T23:59", 1)),
                // The return departs at night, it is not considered
                segment("10:00", leg("MIA", "BUE", INBOUND_DATE + "T02:00", INBOUND_DATE + "T12:00", 2)));
    }
}
