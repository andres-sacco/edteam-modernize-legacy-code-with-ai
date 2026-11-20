package com.twa.flights.provider.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.PaxPriceDTO;
import com.twa.flights.search.dto.PriceInfoDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;
import com.twa.flights.search.dto.FlightType;
import com.twa.flights.shared.enums.PassengerType;

// Common logic to transform the information of the providers into the format of the application
@Component
public class ProviderMapperHelper {

    private static final String DATE_TIME_SEPARATOR = "T";
    private static final int TIME_LENGTH = 5; // HH:mm

    public record Fare(BigDecimal base, BigDecimal tax) {
    }

    public FlightType getFlightType(AvailabilityRequestDTO availabilityRequest) {
        return availabilityRequest.getTo().split(",").length > 1 ? FlightType.ROUND_TRIP : FlightType.ONE_WAY;
    }

    public PriceInfoDTO createPriceInfo(AvailabilityRequestDTO availabilityRequest, Fare adult, Fare child,
            Fare infant) {
        PriceInfoDTO priceInfo = new PriceInfoDTO();

        priceInfo.setAdults(createPaxPrice(adult, availabilityRequest.getAdults(), PassengerType.ADULT));

        if (availabilityRequest.getChildren() != null && availabilityRequest.getChildren().intValue() != 0) {
            priceInfo.setChildren(createPaxPrice(child, availabilityRequest.getChildren(), PassengerType.CHILD));
        }

        if (availabilityRequest.getInfants() != null && availabilityRequest.getInfants().intValue() != 0) {
            priceInfo.setInfants(createPaxPrice(infant, availabilityRequest.getInfants(), PassengerType.INFANT));
        }

        return priceInfo;
    }

    public LegDTO createLeg(String origin, String destination, String departureDateTime, String arrivalDateTime,
            String flightNumber) {
        String[] departure = departureDateTime.split(DATE_TIME_SEPARATOR);
        String[] arrival = arrivalDateTime.split(DATE_TIME_SEPARATOR);

        return new LegDTO(origin, destination, departure[0], departure[1].substring(0, TIME_LENGTH), arrival[0],
                arrival[1].substring(0, TIME_LENGTH), getFlightNumber(flightNumber), null);
    }

    // Without pieces the weight is not relevant, some providers send it anyway
    public BaggageAllowanceDTO createAllowance(Integer pieces, Integer weightKg) {
        boolean withoutPieces = pieces == null || pieces == 0;
        return new BaggageAllowanceDTO(pieces, withoutPieces ? null : weightKg);
    }

    // The providers could send the number with the code of the carrier (e.g. AR1234)
    private Integer getFlightNumber(String flightNumber) {
        return Integer.valueOf(flightNumber.replaceAll("\\D", ""));
    }

    private PaxPriceDTO createPaxPrice(Fare fare, int quantity, PassengerType type) {
        if (fare == null) {
            throw new IllegalArgumentException("The provider does not return the fare for " + type);
        }

        PaxPriceDTO paxPrice = new PaxPriceDTO();

        BigDecimal base = fare.base();
        BigDecimal tax = fare.tax();
        BigDecimal subtotal = (base.add(tax)).multiply(BigDecimal.valueOf(quantity));

        paxPrice.setBase(base);
        paxPrice.setTax(tax);
        paxPrice.setQuantity(quantity);
        paxPrice.setSubtotal(subtotal);
        paxPrice.setTotal(subtotal);
        paxPrice.setType(type);

        return paxPrice;
    }
}
