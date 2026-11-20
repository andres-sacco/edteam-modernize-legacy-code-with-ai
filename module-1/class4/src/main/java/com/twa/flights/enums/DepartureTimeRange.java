package com.twa.flights.enums;

public enum DepartureTimeRange {

    NIGHT(0, 6), MORNING(6, 12), AFTERNOON(12, 18), EVENING(18, 24);

    private final int fromHour;
    private final int toHour;

    DepartureTimeRange(int fromHour, int toHour) {
        this.fromHour = fromHour;
        this.toHour = toHour;
    }

    // The start of the range is inclusive and the end exclusive
    public boolean contains(int hour) {
        return hour >= fromHour && hour < toHour;
    }
}
