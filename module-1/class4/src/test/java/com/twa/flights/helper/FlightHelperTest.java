package com.twa.flights.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.twa.flights.exception.TWAException;

/**
 * Characterization of the calculation of the duration with the time zones (behavior #15). Only the methods that do
 * not use the catalog, the calculation with the cities of the database is in ProvidersSearchTest.
 */
class FlightHelperTest {

    private static final String BUENOS_AIRES = "America/Argentina/Buenos_Aires";
    private static final String NEW_YORK = "America/New_York";

    // The catalog is not used by these methods
    private final FlightHelper helper = new FlightHelper(null);

    @Test
    @DisplayName("[#15] the duration uses the time zone of each city (BUE 04:45 to MIA 23:45 is 21:00)")
    void durationWithTimeZones() {
        assertThat(helper.timeBetweenDateTime("2027-01-14", "04:45", BUENOS_AIRES, "2027-01-14", "23:45", NEW_YORK))
                .isEqualTo("21:00");
    }

    @Test
    @DisplayName("[#15] without time zones the local times are compared")
    void durationWithoutTimeZones() {
        assertThat(helper.timeBetweenDateTime("2027-01-14", "04:45", null, "2027-01-14", "23:45", ""))
                .isEqualTo("19:00");
    }

    @Test
    @DisplayName("[#15] the format is HH:mm with two digits, and more than two digits after 99 hours")
    void format() {
        assertThat(helper.timeBetweenDateTime("2027-01-14", "10:00", null, "2027-01-14", "10:05", null))
                .isEqualTo("00:05");
        assertThat(helper.timeBetweenDateTime("2027-01-14", "10:00", null, "2027-01-14", "10:00", null))
                .isEqualTo("00:00");
        assertThat(helper.timeBetweenDateTime("2027-01-10", "00:00", null, "2027-01-14", "04:30", null))
                .isEqualTo("100:30");
    }

    @Test
    @DisplayName("[#15] the daylight saving time of the date changes the duration (New York, 2027-03-14)")
    void daylightSavingTime() {
        // Before the change New York is UTC-5 and after it UTC-4, Buenos Aires does not change (UTC-3)
        assertThat(helper.timeBetweenDateTime("2027-03-13", "22:00", NEW_YORK, "2027-03-14", "10:00", BUENOS_AIRES))
                .isEqualTo("10:00");
        assertThat(helper.timeBetweenDateTime("2027-03-14", "22:00", NEW_YORK, "2027-03-15", "10:00", BUENOS_AIRES))
                .isEqualTo("11:00");
    }

    @Test
    @DisplayName("[#15] an arrival before the departure (with the time zones) throws an exception")
    void arrivalBeforeDeparture() {
        // MIA 07:15 (12:15 UTC) to SAO 09:00 (12:00 UTC), the case of the recorded response of Gamma
        assertThatThrownBy(() -> helper.timeBetweenDateTime("2027-01-24", "07:15", NEW_YORK, "2027-01-24", "09:00",
                "America/Sao_Paulo")).isInstanceOf(TWAException.class)
                .hasMessage("The arrival of the flight is before the departure");
    }
}
