package com.twa.flights.enums;

/**
 * @deprecated the errors are split by module: {@link com.twa.flights.search.exception.SearchError},
 *             {@link com.twa.flights.pricing.exception.PricingError} and
 *             {@link com.twa.flights.catalog.api.CatalogError}, with the same codes and messages. It is not used by
 *             the application, it is kept only as the reference of the codes and messages of the characterization
 *             tests.
 */
@Deprecated
public enum ExceptionStatus {

    EMPTY_ORIGINS(400_01, "From must have a value"), EMPTY_DESTINATIONS(400_02, "To must have a value"),
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
    INCOMPLETE_CATALOG_INFORMATION(400_14,
            "The information about one of the city is not complete (code, country or continent is missing)"),
    SEARCH_NOT_FOUND_IN_REPOSITORY(400_15, "The information about the search is not available"),
    INVALID_SORT_CRITERIA(400_16, "The sort criteria must be one of PRICE, CARRIER or DURATION"),
    INVALID_SORT_ORDER(400_17, "The sort order must be ASC or DESC"),
    INVALID_CABIN(400_18, "The cabin must be one of ECONOMY, PREMIUM_ECONOMY or BUSINESS"),
    INVALID_MAX_STOPS(400_19, "The maximum number of stops must be zero or a positive value"),
    INVALID_PRICE_RANGE(400_20,
            "The prices must be positive values and the minimum can not be higher than the maximum"),
    INVALID_CARRIERS(400_21, "The carriers must be codes of two characters comma separated"),
    INVALID_DEPARTURE_TIME(400_22, "The departure time must be one of NIGHT, MORNING, AFTERNOON or EVENING"),
    INVALID_POLICY_NAME(400_23, "The name is required and it can have at most 50 characters"),
    INVALID_POLICY_PRIORITY(400_24, "The priority is required and it must be zero or a positive value"),
    INVALID_POLICY_DATES(400_25, "The start and expire dates are required and the start must be before the expiration"),
    INVALID_POLICY_CARRIERS(400_26, "The policy must have at least one carrier and they can not be duplicated"),
    INVALID_POLICY_CARRIER_CODE(400_27, "The carrier must be a code of two characters"),
    INVALID_POLICY_PERCENTAGE(400_28, "The percentage is required and it must be between 0 and 100"),
    INVALID_POLICY_STATUS(400_29, "The status of the policy is required"),
    UNAUTHORIZED(401_01, "The API key is missing or it is invalid"),
    SEARCH_NOT_FOUND(404_01, "The search does not exist or it is expired"),
    ITINERARY_NOT_FOUND(404_02, "The itinerary does not exist in the search"),
    POLICY_NOT_FOUND(404_03, "The commercial policy does not exist"),
    CARRIER_NOT_FOUND_IN_POLICY(404_04, "The carrier does not exist in the commercial policy");

    private final int code;
    private final String message;

    ExceptionStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
