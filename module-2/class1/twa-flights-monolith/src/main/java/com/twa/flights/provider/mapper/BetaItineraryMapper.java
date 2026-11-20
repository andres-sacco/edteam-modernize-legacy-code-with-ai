package com.twa.flights.provider.mapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.twa.flights.search.dto.BaggageDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.provider.dto.BetaAvailabilityResponse;
import com.twa.flights.provider.dto.BetaAvailabilityResponse.BaggageAllowance;
import com.twa.flights.provider.dto.BetaAvailabilityResponse.Bound;
import com.twa.flights.provider.dto.BetaAvailabilityResponse.FlightOption;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.provider.api.Provider;
import com.twa.flights.provider.mapper.ProviderMapperHelper.Fare;

@Component
public class BetaItineraryMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetaItineraryMapper.class);

    private static final String STATUS_OK = "OK";
    private static final String OUTBOUND = "OUTBOUND";
    private static final String DATE_TIME_SEPARATOR = "T";
    private static final int CENTS_SCALE = 2;

    private final ProviderMapperHelper helper;

    @Autowired
    public BetaItineraryMapper(ProviderMapperHelper helper) {
        this.helper = helper;
    }

    public List<ItineraryDTO> map(BetaAvailabilityResponse response, AvailabilityRequestDTO availabilityRequest) {
        List<ItineraryDTO> itineraries = Lists.newArrayList();
        if (response == null || response.data() == null || response.data().flightOptions() == null) {
            return itineraries;
        }

        if (!STATUS_OK.equals(response.status())) {
            LOGGER.warn("The provider beta returns the status {}", response.status());
            return itineraries;
        }

        for (FlightOption option : response.data().flightOptions()) {
            try {
                itineraries.add(map(option, availabilityRequest));
            } catch (RuntimeException e) {
                LOGGER.warn("The option {} of the provider beta can not be transformed: {}", option.optionId(),
                        e.getMessage());
            }
        }
        return itineraries;
    }

    private ItineraryDTO map(FlightOption option, AvailabilityRequestDTO availabilityRequest) {
        ItineraryDTO itinerary = new ItineraryDTO();

        itinerary.setId(option.optionId());
        itinerary.setCarrier(option.marketingCarrier());
        itinerary.setProvider(Provider.BETA);
        itinerary.setFlightType(helper.getFlightType(availabilityRequest));
        itinerary.setCabin(CabinClass.valueOf(option.cabin().toUpperCase()));

        // The outbound always has to be the first segment
        itinerary.setSegments(option.bounds().stream()
                .sorted(Comparator.comparing(bound -> OUTBOUND.equals(bound.direction()) ? 0 : 1))
                .map(this::getSegment).collect(Collectors.toList()));

        itinerary.setPriceInfo(helper.createPriceInfo(availabilityRequest, getFare(option.fareBreakdown().adult()),
                getFare(option.fareBreakdown().child()), getFare(option.fareBreakdown().infant())));
        itinerary.setBaggage(getBaggage(option.baggageAllowance()));

        return itinerary;
    }

    private SegmentDTO getSegment(Bound bound) {
        return new SegmentDTO(bound.legs().stream()
                .map(leg -> helper.createLeg(leg.depAirport(), leg.arrAirport(),
                        leg.depDate() + DATE_TIME_SEPARATOR + leg.depTime(),
                        leg.arrDate() + DATE_TIME_SEPARATOR + leg.arrTime(), leg.flightNo()))
                .collect(Collectors.toList()), null);
    }

    private Fare getFare(BetaAvailabilityResponse.Fare fare) {
        if (fare == null) {
            return null;
        }
        return new Fare(BigDecimal.valueOf(fare.baseFareCents(), CENTS_SCALE),
                BigDecimal.valueOf(fare.taxCents(), CENTS_SCALE));
    }

    private BaggageDTO getBaggage(BaggageAllowance allowance) {
        return new BaggageDTO(helper.createAllowance(allowance.handPieces(), allowance.handKg()),
                helper.createAllowance(allowance.checkedPieces(), allowance.checkedKg()));
    }
}
