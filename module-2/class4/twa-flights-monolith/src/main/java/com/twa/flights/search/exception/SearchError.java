package com.twa.flights.search.exception;

import com.twa.flights.shared.exception.ErrorCode;

/**
 * Errors of the search of itineraries (BC1).
 */
public enum SearchError implements ErrorCode {

    EMPTY_ORIGINS(400_01, "From must have a value"),
    EMPTY_DESTINATIONS(400_02, "To must have a value"),
    EMPTY_DEPARTURES(400_03, "Departures must have a value"),
    INVALID_ADULTS_QUANTITY(400_04, "Adults value must be higher than zero"),
    INVALID_CHILDREN_QUANTITY(400_05, "Children must have a positive value"),
    INVALID_INFANT_QUANTITY(400_06, "Infants must have a positive value"),
    INVALID_DEPARTURES(400_07, "Departure must have a valid format"),
    ARRIVAL_BEFORE_DEPARTURE(400_08, "Arrival date es before departure date"),
    SEARCH_IN_THE_PAST(400_09, "you cannot make a search in the past"),
    INVALID_NUMBER_OF_PASSENGERS(400_10, "Invalid number of passengers. Maximum 9"),
    INVALID_ORIGIN_OR_DESTINATION(400_11, "At least one origin or destination have an invalid code"),
    ORIGIN_AND_DESTINATION_ARE_THE_SAME(400_12, "Origin and destination are the same"),
    DUPLICATED_ROUTES(400_13, "Some of the routes are duplicated"),
    SEARCH_NOT_FOUND_IN_REPOSITORY(400_15, "The information about the search is not available"),
    INVALID_SORT_CRITERIA(400_16, "The sort criteria must be one of PRICE, CARRIER or DURATION"),
    INVALID_SORT_ORDER(400_17, "The sort order must be ASC or DESC"),
    INVALID_CABIN(400_18, "The cabin must be one of ECONOMY, PREMIUM_ECONOMY or BUSINESS"),
    INVALID_MAX_STOPS(400_19, "The maximum number of stops must be zero or a positive value"),
    INVALID_PRICE_RANGE(400_20,
            "The prices must be positive values and the minimum can not be higher than the maximum"),
    INVALID_CARRIERS(400_21, "The carriers must be codes of two characters comma separated"),
    INVALID_DEPARTURE_TIME(400_22, "The departure time must be one of NIGHT, MORNING, AFTERNOON or EVENING"),
    SEARCH_NOT_FOUND(404_01, "The search does not exist or it is expired"),
    ITINERARY_NOT_FOUND(404_02, "The itinerary does not exist in the search");

    private final int code;
    private final String message;

    SearchError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
