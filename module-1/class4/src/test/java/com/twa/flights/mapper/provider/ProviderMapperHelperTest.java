package com.twa.flights.mapper.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.twa.flights.dto.BaggageAllowanceDTO;
import com.twa.flights.dto.LegDTO;
import com.twa.flights.dto.PriceInfoDTO;
import com.twa.flights.enums.FlightType;
import com.twa.flights.enums.PassengerType;
import com.twa.flights.mapper.provider.ProviderMapperHelper.Fare;
import com.twa.flights.support.Requests;

// Characterization of the common rules of the mappers of the providers (behaviors #21 and #22)
class ProviderMapperHelperTest {

    private final ProviderMapperHelper helper = new ProviderMapperHelper();

    @Test
    @DisplayName("[#21] subtotal = (base + tax) x quantity and total = subtotal, without markup")
    void priceByPassengerType() {
        PriceInfoDTO priceInfo = helper.createPriceInfo(Requests.request("BUE", "MIA", "2027-01-14", 2, 1, 1),
                fare("339.39", "25.72"), fare("100.00", "20.50"), fare("10.00", "1.00"));

        assertThat(priceInfo.getAdults().getType()).isEqualTo(PassengerType.ADULT);
        assertThat(priceInfo.getAdults().getQuantity()).isEqualTo(2);
        assertThat(priceInfo.getAdults().getBase()).hasToString("339.39");
        assertThat(priceInfo.getAdults().getTax()).hasToString("25.72");
        assertThat(priceInfo.getAdults().getSubtotal()).hasToString("730.22");
        assertThat(priceInfo.getAdults().getTotal()).hasToString("730.22");
        assertThat(priceInfo.getAdults().getMarkup()).isNull();
        assertThat(priceInfo.getChildren().getSubtotal()).hasToString("120.50");
        assertThat(priceInfo.getChildren().getType()).isEqualTo(PassengerType.CHILD);
        assertThat(priceInfo.getInfants().getSubtotal()).hasToString("11.00");
        assertThat(priceInfo.getInfants().getType()).isEqualTo(PassengerType.INFANT);
    }

    @Test
    @DisplayName("[#21] children and infants only exist when the quantity is higher than zero")
    void childrenAndInfantsOnlyWithQuantity() {
        PriceInfoDTO withZero = helper.createPriceInfo(Requests.request("BUE", "MIA", "2027-01-14", 1, 0, 0),
                fare("1", "1"), fare("1", "1"), fare("1", "1"));
        PriceInfoDTO withNull = helper.createPriceInfo(Requests.request("BUE", "MIA", "2027-01-14", 1, null, null),
                fare("1", "1"), null, null);

        assertThat(withZero.getChildren()).isNull();
        assertThat(withZero.getInfants()).isNull();
        assertThat(withNull.getChildren()).isNull();
        assertThat(withNull.getInfants()).isNull();
    }

    @Test
    @DisplayName("[#21] without the fare of a requested passenger type the itinerary can not be created")
    void missingFareOfRequestedPassenger() {
        assertThatThrownBy(() -> helper.createPriceInfo(Requests.request("BUE", "MIA", "2027-01-14", 1, 1, 0),
                fare("1", "1"), null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The provider does not return the fare for CHILD");
    }

    @Test
    @DisplayName("[#22] the flight number keeps only the digits and the time is cut to HH:mm")
    void legWithNumberAndTime() {
        LegDTO leg = helper.createLeg("BUE", "MIA", "2027-01-14T07:15:00", "2027-01-14T22:30", "AR01234");

        assertThat(leg.getOrigin()).isEqualTo("BUE");
        assertThat(leg.getDestination()).isEqualTo("MIA");
        assertThat(leg.getDepartureDate()).isEqualTo("2027-01-14");
        assertThat(leg.getDepartureTime()).isEqualTo("07:15");
        assertThat(leg.getArrivalDate()).isEqualTo("2027-01-14");
        assertThat(leg.getArrivalTime()).isEqualTo("22:30");
        assertThat(leg.getNumber()).isEqualTo(1234);
        assertThat(leg.getFlightDuration()).isNull();
    }

    @Test
    @DisplayName("[#22] a flight number without digits can not be transformed")
    void flightNumberWithoutDigits() {
        assertThatThrownBy(() -> helper.createLeg("BUE", "MIA", "2027-01-14T07:15", "2027-01-14T22:30", "AR"))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("[#22] without pieces of baggage the weight is null")
    void allowanceWithoutPieces() {
        assertThat(helper.createAllowance(1, 23)).isEqualTo(new BaggageAllowanceDTO(1, 23));
        assertThat(helper.createAllowance(0, 23)).isEqualTo(new BaggageAllowanceDTO(0, null));
        assertThat(helper.createAllowance(null, 23)).isEqualTo(new BaggageAllowanceDTO(null, null));
        assertThat(helper.createAllowance(2, null)).isEqualTo(new BaggageAllowanceDTO(2, null));
    }

    @Test
    @DisplayName("[#22] the flight type is ROUND_TRIP when there is more than one destination, also for multiple destinations")
    void flightType() {
        assertThat(helper.getFlightType(Requests.request("BUE", "MIA", "2027-01-14", 1, 0, 0)))
                .isEqualTo(FlightType.ONE_WAY);
        assertThat(helper.getFlightType(Requests.request("BUE,MIA", "MIA,BUE", "2027-01-14,2027-01-24", 1, 0, 0)))
                .isEqualTo(FlightType.ROUND_TRIP);
        assertThat(helper.getFlightType(Requests.request("BUE,MIA,SCL", "MIA,SCL,BUE", "2027-01-14", 1, 0, 0)))
                .isEqualTo(FlightType.ROUND_TRIP);
    }

    private static Fare fare(String base, String tax) {
        return new Fare(new BigDecimal(base), new BigDecimal(tax));
    }
}
