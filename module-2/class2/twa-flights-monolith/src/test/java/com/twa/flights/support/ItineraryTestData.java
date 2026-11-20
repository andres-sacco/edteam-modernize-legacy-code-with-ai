package com.twa.flights.support;

import java.math.BigDecimal;
import java.util.List;

import com.google.common.collect.Lists;
import com.twa.flights.search.dto.BaggageAllowanceDTO;
import com.twa.flights.search.dto.BaggageDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.LegDTO;
import com.twa.flights.search.dto.PaxPriceDTO;
import com.twa.flights.search.dto.PriceInfoDTO;
import com.twa.flights.search.dto.SegmentDTO;
import com.twa.flights.shared.enums.CabinClass;
import com.twa.flights.search.dto.FlightType;
import com.twa.flights.shared.enums.PassengerType;
import com.twa.flights.provider.api.Provider;

// Itineraries with fixed dates and prices, built like the mappers of the providers do it
public final class ItineraryTestData {

    // Fixed dates inside the validity of the seed policies (General: 2024-01-01 to 2030-01-01)
    public static final String OUTBOUND_DATE = "2027-01-14";
    public static final String INBOUND_DATE = "2027-01-24";

    private ItineraryTestData() {
        // just to avoid create instances
    }

    public static LegDTO leg(String origin, String destination, String departure, String arrival, int number) {
        String[] departureParts = departure.split("T");
        String[] arrivalParts = arrival.split("T");
        return new LegDTO(origin, destination, departureParts[0], departureParts[1], arrivalParts[0], arrivalParts[1],
                number, null);
    }

    public static SegmentDTO segment(String flightDuration, LegDTO... legs) {
        return new SegmentDTO(Lists.newArrayList(legs), flightDuration);
    }

    // The same calculation of the providers: subtotal = (base + tax) x quantity and total = subtotal
    public static PaxPriceDTO pax(PassengerType type, String base, String tax, int quantity) {
        BigDecimal subtotal = new BigDecimal(base).add(new BigDecimal(tax)).multiply(BigDecimal.valueOf(quantity));
        return new PaxPriceDTO(new BigDecimal(tax), new BigDecimal(base), quantity, subtotal, type, null, subtotal);
    }

    public static PriceInfoDTO adultsOnly(String base, String tax, int quantity) {
        return new PriceInfoDTO(pax(PassengerType.ADULT, base, tax, quantity), null, null);
    }

    // An itinerary of one adult with this total price
    public static PriceInfoDTO totalPrice(String total) {
        return adultsOnly(total, "0", 1);
    }

    public static ItineraryDTO itinerary(String id, Provider provider, String carrier, PriceInfoDTO priceInfo,
            SegmentDTO... segments) {
        ItineraryDTO itinerary = new ItineraryDTO(Lists.newArrayList(segments), priceInfo, provider,
                segments.length > 1 ? FlightType.ROUND_TRIP : FlightType.ONE_WAY, carrier, id);
        itinerary.setCabin(CabinClass.ECONOMY);
        itinerary.setBaggage(new BaggageDTO(new BaggageAllowanceDTO(1, 8), new BaggageAllowanceDTO(0, null)));
        return itinerary;
    }

    // Direct round trip BUE-MIA-BUE on the fixed dates
    public static ItineraryDTO roundTrip(String id, String carrier, PriceInfoDTO priceInfo) {
        return itinerary(id, Provider.ALPHA, carrier, priceInfo,
                segment("10:00", leg("BUE", "MIA", OUTBOUND_DATE + "T08:00", OUTBOUND_DATE + "T16:00", 100)),
                segment("14:00", leg("MIA", "BUE", INBOUND_DATE + "T08:00", INBOUND_DATE + "T20:00", 200)));
    }

    public static List<String> ids(List<ItineraryDTO> itineraries) {
        return itineraries.stream().map(ItineraryDTO::getId).toList();
    }
}
