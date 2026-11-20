package com.twa.flights.search.validator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;
import com.twa.flights.catalog.api.CatalogError;
import com.twa.flights.catalog.api.CatalogUnavailableException;
import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.enums.DepartureTimeRange;
import com.twa.flights.enums.SortCriteria;
import com.twa.flights.enums.SortOrder;
import com.twa.flights.search.exception.SearchError;
import com.twa.flights.shared.exception.BadRequestException;
import com.twa.flights.search.helper.ItineraryFilterHelper;
import com.twa.flights.search.helper.ItinerarySortHelper;
import com.twa.flights.catalog.api.CatalogApi;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;

@Component
public class AvailabilityRequestValidator {

    private static final String COMMA = ",";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    private final CatalogApi catalogService;

    public AvailabilityRequestValidator(CatalogApi catalogService) {
        this.catalogService = catalogService;
    }

    public void validate(final AvailabilityRequestDTO request) {

        validateDeparture(request.getDeparture());

        validatePassengerCount(request);

        validateFromTo(request);
    }

    public void validate(final ClustersAvailabilityRequestDTO request) {

        validate((AvailabilityRequestDTO) request);

        validateSort(request);

        validateCabin(request);

        validateFilters(request);
    }

    // private function(s)
    private void validateSort(final ClustersAvailabilityRequestDTO request) {
        if (request.getSortBy() != null && !isValidValue(SortCriteria.class, request.getSortBy())) {
            throw new BadRequestException(SearchError.INVALID_SORT_CRITERIA);
        }

        if (request.getSortOrder() != null && !isValidValue(SortOrder.class, request.getSortOrder())) {
            throw new BadRequestException(SearchError.INVALID_SORT_ORDER);
        }
    }

    private void validateCabin(final ClustersAvailabilityRequestDTO request) {
        if (request.getCabin() != null && !isValidValue(CabinClass.class, request.getCabin())) {
            throw new BadRequestException(SearchError.INVALID_CABIN);
        }
    }

    private void validateFilters(final ClustersAvailabilityRequestDTO request) {
        if (request.getMaxStops() != null && request.getMaxStops() < 0) {
            throw new BadRequestException(SearchError.INVALID_MAX_STOPS);
        }

        BigDecimal minPrice = request.getMinPrice();
        BigDecimal maxPrice = request.getMaxPrice();
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)) {
            throw new BadRequestException(SearchError.INVALID_PRICE_RANGE);
        }

        validateCarrierCodes(request.getCarriers());
        validateCarrierCodes(request.getExcludedCarriers());

        if (request.getDepartureTime() != null
                && !isValidValue(DepartureTimeRange.class, request.getDepartureTime())) {
            throw new BadRequestException(SearchError.INVALID_DEPARTURE_TIME);
        }
    }

    private void validateCarrierCodes(final String carriers) {
        if (carriers == null) {
            return;
        }

        Set<String> codes = ItineraryFilterHelper.splitCodes(carriers);
        if (codes.isEmpty() || codes.stream().anyMatch(code -> !code.matches("[A-Z0-9]{2}"))) {
            throw new BadRequestException(SearchError.INVALID_CARRIERS);
        }
    }

    private <E extends Enum<E>> boolean isValidValue(Class<E> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                .anyMatch(constant -> constant.name().equals(ItinerarySortHelper.normalize(value)));
    }

    private void validateDeparture(final String departure) {

        if (departure == null) {
            throw new BadRequestException(SearchError.EMPTY_DEPARTURES);
        }

        final List<String> stringDates = Arrays.asList(departure.split(COMMA));

        final List<LocalDate> dates = Lists.newLinkedList();

        for (final String stringDate : stringDates) {
            try {
                dates.add(LocalDate.parse(stringDate, DateTimeFormatter.ofPattern(DATE_PATTERN)));
            } catch (final DateTimeParseException e) {
                throw new BadRequestException(SearchError.INVALID_DEPARTURES);
            }
        }

        dates.forEach(date -> {
            if (date.isBefore(LocalDate.now())) {
                throw new BadRequestException(SearchError.SEARCH_IN_THE_PAST);
            }
        });

        if (dates.size() > 1) {
            for (int i = 1; i < dates.size(); i++) {
                if (dates.get(i - 1).compareTo(dates.get(i)) > 0) {
                    throw new BadRequestException(SearchError.ARRIVAL_BEFORE_DEPARTURE);
                }
            }
        }
    }

    private void validatePassengerCount(final AvailabilityRequestDTO request) {
        if (request.getAdults() <= 0) {
            throw new BadRequestException(SearchError.INVALID_ADULTS_QUANTITY);
        }

        if (request.getChildren() < 0) {
            throw new BadRequestException(SearchError.INVALID_CHILDREN_QUANTITY);
        }

        if (request.getInfants() < 0) {
            throw new BadRequestException(SearchError.INVALID_INFANT_QUANTITY);
        }

        if (request.getAdults() + request.getChildren() > 9) {
            throw new BadRequestException(SearchError.INVALID_NUMBER_OF_PASSENGERS);
        }
    }

    private void validateFromTo(AvailabilityRequestDTO request) {

        validateFromToEmptyRequestFields(request);

        final List<String> list = Lists.newArrayList();
        final List<String> origins = Lists.newArrayList(request.getFrom().split(","));
        final List<String> destinations = Lists.newArrayList(request.getTo().split(","));

        origins.removeIf(ObjectUtils::isEmpty);
        destinations.removeIf(ObjectUtils::isEmpty);

        list.addAll(origins);
        list.addAll(destinations);

        validateFromToValidCodes(list);
        validateFromToRoutes(origins, destinations);
    }

    private void validateFromToEmptyRequestFields(AvailabilityRequestDTO request) {
        if (!StringUtils.hasLength(request.getFrom())) {
            throw new BadRequestException(SearchError.EMPTY_ORIGINS);
        }

        if (!StringUtils.hasLength(request.getTo())) {
            throw new BadRequestException(SearchError.EMPTY_DESTINATIONS);
        }
    }

    private void validateFromToRoutes(final List<String> origins, final List<String> destinations) {
        List<String> routes = Lists.newArrayList();
        for (int i = 0; i < origins.size(); i++) {
            if (origins.get(i).equals(destinations.get(i))) {
                throw new BadRequestException(SearchError.ORIGIN_AND_DESTINATION_ARE_THE_SAME);
            }
            final String route = origins.get(i).concat(destinations.get(i));
            if (routes.contains(route)) {
                throw new BadRequestException(SearchError.DUPLICATED_ROUTES);
            }
            routes.add(route);
        }
    }

    private void validateFromToValidCodes(final List<String> list) {
        // The cities are obtained in one call when the first code reaches the catalog, so the errors keep their order
        // (a code with more than 3 characters before it is still the error 40011 without calling the catalog)
        Map<String, CityDTO> cities = null;
        for (String code : list) {
            if (code.length() > 3) {
                throw new BadRequestException(SearchError.INVALID_ORIGIN_OR_DESTINATION);
            } else {
                if (cities == null) {
                    cities = obtainCities(list);
                }
                validateCodeWithCatalog(code, cities);
            }
        }
    }

    private Map<String, CityDTO> obtainCities(final List<String> list) {
        try {
            return catalogService.getCities(list.stream().filter(code -> code.length() <= 3).toList());
        } catch (CatalogUnavailableException e) {
            // The catalog is down: it is not an error of the request
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(CatalogError.INCOMPLETE_CATALOG_INFORMATION);
        }
    }

    private void validateCodeWithCatalog(String code, Map<String, CityDTO> cities) {
        try {
            CityDTO city = cities.get(code);

            if (!StringUtils.hasLength(city.getCode()) || city.getCountry() == null
                    || !StringUtils.hasLength(city.getCountry().getCode()) || city.getCountry().getContinent() == null
                    || !StringUtils.hasLength(city.getCountry().getContinent().getCode())) {
                throw new BadRequestException(CatalogError.INCOMPLETE_CATALOG_INFORMATION);
            }
        } catch (CatalogUnavailableException e) {
            // The catalog is down: it is not an error of the request
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(CatalogError.INCOMPLETE_CATALOG_INFORMATION);
        }
    }
}
