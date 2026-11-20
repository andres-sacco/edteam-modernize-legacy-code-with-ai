package com.twa.flights.provider.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.BaggageDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.provider.dto.GammaAvailabilityResponse;
import com.twa.flights.provider.dto.GammaAvailabilityResponse.Flight;
import com.twa.flights.provider.dto.GammaAvailabilityResponse.Itinerary;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.provider.mapper.ProviderMapperHelper.Fare;

@Component
public class GammaItineraryMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(GammaItineraryMapper.class);

    // e.g. 1PC23KG or 0PC
    private static final Pattern BAG_PATTERN = Pattern.compile("(\\d+)PC(?:(\\d+)KG)?");

    private final ProviderMapperHelper helper;

    @Autowired
    public GammaItineraryMapper(ProviderMapperHelper helper) {
        this.helper = helper;
    }

    public List<ItineraryDTO> map(GammaAvailabilityResponse response, AvailabilityRequestDTO availabilityRequest) {
        List<ItineraryDTO> itineraries = Lists.newArrayList();
        if (response == null || response.itineraries() == null) {
            return itineraries;
        }

        for (Itinerary providerItinerary : response.itineraries()) {
            try {
                itineraries.add(map(providerItinerary, availabilityRequest));
            } catch (RuntimeException e) {
                LOGGER.warn("The itinerary {} of the provider gamma can not be transformed: {}",
                        providerItinerary.key(), e.getMessage());
            }
        }
        return itineraries;
    }

    private ItineraryDTO map(Itinerary providerItinerary, AvailabilityRequestDTO availabilityRequest) {
        ItineraryDTO itinerary = new ItineraryDTO();

        itinerary.setId(providerItinerary.key());
        itinerary.setCarrier(providerItinerary.validatingCarrier());
        itinerary.setProvider(Provider.GAMMA);
        itinerary.setFlightType(helper.getFlightType(availabilityRequest));
        itinerary.setCabin(getCabin(providerItinerary.cabinType()));
        itinerary.setSegments(getSegments(providerItinerary.flights()));
        itinerary.setPriceInfo(helper.createPriceInfo(availabilityRequest, getFare(providerItinerary, "ADULT"),
                getFare(providerItinerary, "CHILD"), getFare(providerItinerary, "INFANT")));
        itinerary.setBaggage(new BaggageDTO(getAllowance(providerItinerary.bags().carryOn()),
                getAllowance(providerItinerary.bags().checked())));

        return itinerary;
    }

    private CabinClass getCabin(String cabinType) {
        return switch (cabinType) {
        case "ECO" -> CabinClass.ECONOMY;
        case "PEY" -> CabinClass.PREMIUM_ECONOMY;
        case "BUS" -> CabinClass.BUSINESS;
        default -> throw new IllegalArgumentException("Unknown cabin type " + cabinType);
        };
    }

    // Gamma returns all the flights together, they are grouped by segment and ordered by the sequence
    private List<SegmentDTO> getSegments(List<Flight> flights) {
        Map<Integer, List<Flight>> flightsBySegment = flights.stream()
                .collect(Collectors.groupingBy(Flight::segmentIndex, TreeMap::new, Collectors.toList()));

        return flightsBySegment.values().stream()
                .map(segmentFlights -> new SegmentDTO(segmentFlights.stream()
                        .sorted(Comparator.comparing(Flight::sequence))
                        .map(flight -> helper.createLeg(flight.origin(), flight.destination(),
                                flight.departureDateTime(), flight.arrivalDateTime(), flight.flightNumber()))
                        .collect(Collectors.toList()), null))
                .collect(Collectors.toList());
    }

    private Fare getFare(Itinerary providerItinerary, String paxType) {
        return providerItinerary.fares().stream().filter(fare -> paxType.equals(fare.paxType())).findFirst()
                .map(fare -> new Fare(fare.baseAmount(), fare.taxAmount())).orElse(null);
    }

    private BaggageAllowanceDTO getAllowance(String bag) {
        Matcher matcher = BAG_PATTERN.matcher(bag);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unknown format of baggage " + bag);
        }

        Integer weight = matcher.group(2) != null ? Integer.valueOf(matcher.group(2)) : null;
        return helper.createAllowance(Integer.valueOf(matcher.group(1)), weight);
    }
}
