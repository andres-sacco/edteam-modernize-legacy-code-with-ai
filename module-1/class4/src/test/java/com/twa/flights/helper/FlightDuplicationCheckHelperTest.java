package com.twa.flights.helper;

import static com.twa.flights.support.ItineraryTestData.adultsOnly;
import static com.twa.flights.support.ItineraryTestData.ids;
import static com.twa.flights.support.ItineraryTestData.itinerary;
import static com.twa.flights.support.ItineraryTestData.leg;
import static com.twa.flights.support.ItineraryTestData.roundTrip;
import static com.twa.flights.support.ItineraryTestData.segment;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.enums.Provider;

// Characterization of the removal of duplicated itineraries and of the key that identifies them (behavior #18)
class FlightDuplicationCheckHelperTest {

    private final ItineraryKeyGeneratorHelper keyGenerator = new ItineraryKeyGeneratorHelper();
    private final FlightDuplicationCheckHelper helper = new FlightDuplicationCheckHelper(keyGenerator);

    @Test
    @DisplayName("[#18] the key is provider|carrier@ and, for each segment, |duration|origin-destination(number)date_time_@")
    void keyOfTheItinerary() {
        ItineraryDTO withConnection = itinerary("id", Provider.BETA, "AR", adultsOnly("1", "1", 1),
                segment("10:00", leg("BUE", "LIM", "2027-01-14T06:00", "2027-01-14T10:30", 1448),
                        leg("LIM", "MIA", "2027-01-14T12:45", "2027-01-14T18:00", 9500)),
                segment("14:00", leg("MIA", "BUE", "2027-01-24T08:00", "2027-01-24T20:00", 200)));

        assertThat(keyGenerator.generateKeyForItinerary(withConnection)).isEqualTo(
                "BETA|AR@|10:00|BUE-LIM(1448)2027-01-14_06:00_&&LIM-MIA(9500)2027-01-14_12:45_@*"
                        + "|14:00|MIA-BUE(200)2027-01-24_08:00_@");
    }

    @Test
    @DisplayName("[#18] with duplicates of the same provider the cheapest one is kept")
    void cheapestDuplicateIsKept() {
        ItineraryDTO expensive = roundTrip("expensive", "AR", adultsOnly("200.00", "0", 1));
        ItineraryDTO cheap = roundTrip("cheap", "AR", adultsOnly("100.00", "0", 1));
        ItineraryDTO cheaper = roundTrip("cheaper", "AR", adultsOnly("99.99", "0", 1));

        assertThat(ids(helper.removeDuplicateFlights(Lists.newArrayList(expensive, cheap)))).containsExactly("cheap");
        assertThat(ids(helper.removeDuplicateFlights(Lists.newArrayList(cheaper, expensive, cheap))))
                .containsExactly("cheaper");
    }

    @Test
    @DisplayName("[#18] with the same price the first one is kept")
    void samePriceKeepsTheFirst() {
        ItineraryDTO first = roundTrip("first", "AR", adultsOnly("100.00", "0", 1));
        ItineraryDTO second = roundTrip("second", "AR", adultsOnly("100.00", "0", 1));

        assertThat(ids(helper.removeDuplicateFlights(Lists.newArrayList(first, second)))).containsExactly("first");
    }

    @Test
    @DisplayName("[#18] the same flights of different providers are not duplicates")
    void duplicatesBetweenProvidersAreKept() {
        ItineraryDTO alpha = roundTrip("alpha", "AR", adultsOnly("100.00", "0", 1));
        ItineraryDTO beta = roundTrip("beta", "AR", adultsOnly("100.00", "0", 1));
        beta.setProvider(Provider.BETA);

        assertThat(ids(helper.removeDuplicateFlights(Lists.newArrayList(alpha, beta))))
                .containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    @DisplayName("[#18] the cabin, the baggage and the arrival time are not part of the key")
    void attributesOutsideTheKey() {
        ItineraryDTO economy = roundTrip("economy", "AR", adultsOnly("100.00", "0", 1));
        ItineraryDTO otherArrival = roundTrip("other-arrival", "AR", adultsOnly("90.00", "0", 1));
        otherArrival.setCabin(com.twa.flights.enums.CabinClass.BUSINESS);
        otherArrival.setBaggage(null);
        otherArrival.getSegments().get(0).getLegs().get(0).setArrivalTime("23:59");

        assertThat(ids(helper.removeDuplicateFlights(Lists.newArrayList(economy, otherArrival))))
                .containsExactly("other-arrival");
    }
}
