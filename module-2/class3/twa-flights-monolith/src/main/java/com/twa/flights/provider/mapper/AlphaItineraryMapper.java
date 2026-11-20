package com.twa.flights.provider.mapper;

import java.util.List;
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
import com.twa.flights.provider.dto.AlphaAvailabilityResponse;
import com.twa.flights.provider.dto.AlphaAvailabilityResponse.Bag;
import com.twa.flights.provider.dto.AlphaAvailabilityResponse.Journey;
import com.twa.flights.provider.dto.AlphaAvailabilityResponse.Offer;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.provider.mapper.ProviderMapperHelper.Fare;

@Component
public class AlphaItineraryMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlphaItineraryMapper.class);

    private final ProviderMapperHelper helper;

    @Autowired
    public AlphaItineraryMapper(ProviderMapperHelper helper) {
        this.helper = helper;
    }

    public List<ItineraryDTO> map(AlphaAvailabilityResponse response, AvailabilityRequestDTO availabilityRequest) {
        List<ItineraryDTO> itineraries = Lists.newArrayList();
        if (response == null || response.results() == null) {
            return itineraries;
        }

        for (Offer offer : response.results()) {
            try {
                itineraries.add(map(offer, availabilityRequest));
            } catch (RuntimeException e) {
                LOGGER.warn("The offer {} of the provider alpha can not be transformed: {}", offer.offerId(),
                        e.getMessage());
            }
        }
        return itineraries;
    }

    private ItineraryDTO map(Offer offer, AvailabilityRequestDTO availabilityRequest) {
        ItineraryDTO itinerary = new ItineraryDTO();

        itinerary.setId(offer.offerId());
        itinerary.setCarrier(offer.airline().code());
        itinerary.setProvider(Provider.ALPHA);
        itinerary.setFlightType(helper.getFlightType(availabilityRequest));
        itinerary.setCabin(getCabin(offer.cabinClass()));
        itinerary.setSegments(offer.journeys().stream().map(this::getSegment).collect(Collectors.toList()));
        itinerary.setPriceInfo(helper.createPriceInfo(availabilityRequest, getFare(offer, "ADT"),
                getFare(offer, "CHD"), getFare(offer, "INF")));
        itinerary.setBaggage(new BaggageDTO(getAllowance(offer.luggage().cabin()),
                getAllowance(offer.luggage().hold())));

        return itinerary;
    }

    // Alpha uses the codes of the booking classes
    private CabinClass getCabin(String cabinClass) {
        return switch (cabinClass) {
        case "Y" -> CabinClass.ECONOMY;
        case "W" -> CabinClass.PREMIUM_ECONOMY;
        case "C" -> CabinClass.BUSINESS;
        default -> throw new IllegalArgumentException("Unknown cabin class " + cabinClass);
        };
    }

    private SegmentDTO getSegment(Journey journey) {
        return new SegmentDTO(journey.flights().stream()
                .map(flight -> helper.createLeg(flight.from(), flight.to(), flight.departure(), flight.arrival(),
                        flight.flightNumber()))
                .collect(Collectors.toList()), null);
    }

    private Fare getFare(Offer offer, String type) {
        return offer.pricing().passengers().stream().filter(passenger -> type.equals(passenger.type())).findFirst()
                .map(passenger -> new Fare(passenger.base(), passenger.taxes())).orElse(null);
    }

    private BaggageAllowanceDTO getAllowance(Bag bag) {
        return bag != null ? helper.createAllowance(bag.pieces(), bag.maxWeightKg()) : null;
    }
}
