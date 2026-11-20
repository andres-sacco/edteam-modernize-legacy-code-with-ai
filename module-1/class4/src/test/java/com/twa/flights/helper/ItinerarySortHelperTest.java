package com.twa.flights.helper;

import static com.twa.flights.support.ItineraryTestData.OUTBOUND_DATE;
import static com.twa.flights.support.ItineraryTestData.ids;
import static com.twa.flights.support.ItineraryTestData.itinerary;
import static com.twa.flights.support.ItineraryTestData.leg;
import static com.twa.flights.support.ItineraryTestData.segment;
import static com.twa.flights.support.ItineraryTestData.totalPrice;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.enums.Provider;

// Characterization of the order of the search (behavior #20)
class ItinerarySortHelperTest {

    private final ItinerarySortHelper helper = new ItinerarySortHelper();

    @Test
    @DisplayName("[#20] by default PRICE ASC")
    void defaultOrder() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(of("b", "AR", "200", "01:00"),
                of("a", "AR", "100", "02:00"), of("c", "AR", "300", "03:00"));

        assertThat(ids(helper.sort(itineraries, null, null))).containsExactly("a", "b", "c");
        assertThat(ids(helper.sort(itineraries, null, "desc"))).containsExactly("c", "b", "a");
        assertThat(ids(helper.sort(itineraries, " price ", null))).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("[#20] with CARRIER the tiebreaker is the price ASC, also with DESC")
    void carrierWithPriceAsTiebreaker() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(of("aa-300", "AA", "300", "01:00"),
                of("ar-200", "AR", "200", "01:00"), of("aa-100", "AA", "100", "01:00"),
                of("ar-50", "AR", "50", "01:00"));

        assertThat(ids(helper.sort(itineraries, "CARRIER", "ASC"))).containsExactly("aa-100", "aa-300", "ar-50",
                "ar-200");
        assertThat(ids(helper.sort(itineraries, "carrier", "DESC"))).containsExactly("ar-50", "ar-200", "aa-100",
                "aa-300");
    }

    @Test
    @DisplayName("[#20] CARRIER puts the null carriers at the end with ASC and at the beginning with DESC")
    void nullCarriers() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(of("null", null, "100", "01:00"),
                of("ar", "AR", "100", "01:00"));

        assertThat(ids(helper.sort(itineraries, "CARRIER", "ASC"))).containsExactly("ar", "null");
        assertThat(ids(helper.sort(itineraries, "CARRIER", "DESC"))).containsExactly("null", "ar");
    }

    @Test
    @DisplayName("[#20] DURATION sums all the segments and the tiebreaker is the price ASC")
    void durationOfAllTheSegments() {
        ItineraryDTO roundTrip = itinerary("10h+10h", Provider.ALPHA, "AR", totalPrice("100"),
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)),
                segment("10:00", leg("MIA", "BUE", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 2)));
        List<ItineraryDTO> itineraries = Lists.newArrayList(roundTrip, of("19:59-expensive", "AR", "500", "19:59"),
                of("19:59-cheap", "AR", "400", "19:59"), of("20:01", "AR", "50", "20:01"));

        assertThat(ids(helper.sort(itineraries, "DURATION", null))).containsExactly("19:59-cheap",
                "19:59-expensive", "10h+10h", "20:01");
        assertThat(ids(helper.sort(itineraries, "DURATION", "DESC"))).containsExactly("20:01", "10h+10h",
                "19:59-cheap", "19:59-expensive");
    }

    @Test
    @DisplayName("[#20] with the same price the original order is kept (the sort is stable)")
    void samePriceKeepsTheOrder() {
        List<ItineraryDTO> itineraries = Lists.newArrayList(of("first", "DL", "100", "01:00"),
                of("second", "AA", "100", "01:00"));

        assertThat(ids(helper.sort(itineraries, "PRICE", "ASC"))).containsExactly("first", "second");
    }

    private static ItineraryDTO of(String id, String carrier, String price, String duration) {
        return itinerary(id, Provider.ALPHA, carrier, totalPrice(price),
                segment(duration, leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T18:00", 1)));
    }
}
