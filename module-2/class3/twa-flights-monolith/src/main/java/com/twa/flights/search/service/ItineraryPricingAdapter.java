package com.twa.flights.search.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.twa.flights.pricing.api.QuotingApi;
import com.twa.flights.pricing.dto.QuotePaxPrice;
import com.twa.flights.pricing.dto.QuotePaxResult;
import com.twa.flights.pricing.dto.QuoteRequest;
import com.twa.flights.pricing.dto.QuoteResult;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.MarkupDTO;
import com.twa.flights.search.dto.PaxPriceDTO;
import com.twa.flights.search.dto.PriceInfoDTO;
import com.twa.flights.search.dto.SegmentDTO;

/**
 * Prices the itineraries of the search with the quoting of the module pricing: it converts each itinerary to a quote
 * request, applies the markup of the results and discards the itineraries without a policy.
 */
@Component
public class ItineraryPricingAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItineraryPricingAdapter.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm";

    private final QuotingApi quotingApi;

    @Autowired
    public ItineraryPricingAdapter(QuotingApi quotingApi) {
        this.quotingApi = quotingApi;
    }

    public List<ItineraryDTO> priceItineraries(List<ItineraryDTO> itineraries) {
        LOGGER.debug("Pricing itineraries");

        List<QuoteRequest> requests = itineraries.stream().map(this::toQuoteRequest).collect(Collectors.toList());
        List<QuoteResult> results = quotingApi.quote(requests);

        for (QuoteResult result : results) {
            if (!result.isWithPolicy()) {
                continue;
            }

            Optional<ItineraryDTO> itinerary = itineraries.stream()
                    .filter(iti -> iti.getId().equals(result.getKey())).findFirst();

            if (itinerary.isPresent()) {
                updatePriceInfo(result, itinerary.get());
            }
        }

        // The itineraries without a policy do not have markup and they are discarded
        return itineraries.stream().filter(iti -> iti.getPriceInfo().getAdults().getMarkup() != null)
                .collect(Collectors.toList());
    }

    private QuoteRequest toQuoteRequest(ItineraryDTO itinerary) {
        QuoteRequest request = new QuoteRequest();
        request.setKey(itinerary.getId());
        request.setCarrier(itinerary.getCarrier());
        request.setDeparture(convertDeparture(itinerary.firstSegment()));
        request.setArrival(convertArrival(itinerary.lastSegment()));

        PriceInfoDTO priceInfo = itinerary.getPriceInfo();
        request.setAdults(toQuotePaxPrice(priceInfo.getAdults()));

        if (priceInfo.getChildren() != null) {
            request.setChildren(toQuotePaxPrice(priceInfo.getChildren()));
        }

        if (priceInfo.getInfants() != null) {
            request.setInfants(toQuotePaxPrice(priceInfo.getInfants()));
        }

        return request;
    }

    private QuotePaxPrice toQuotePaxPrice(PaxPriceDTO paxPrice) {
        return new QuotePaxPrice(paxPrice.getSubtotal(), paxPrice.getTotal());
    }

    private LocalDateTime convertDeparture(SegmentDTO segment) {
        String dateTime = segment.firstLeg().getDepartureDate().concat(" ")
                .concat(segment.firstLeg().getDepartureTime());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        return LocalDateTime.parse(dateTime, formatter);
    }

    // The arrival of the FIRST leg of the last segment is the one that is compared with the expire date of the policy
    private LocalDateTime convertArrival(SegmentDTO segment) {
        String dateTime = segment.firstLeg().getArrivalDate().concat(" ").concat(segment.firstLeg().getArrivalTime());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
        return LocalDateTime.parse(dateTime, formatter);
    }

    private void updatePriceInfo(QuoteResult result, ItineraryDTO itinerary) {
        PriceInfoDTO priceInfo = itinerary.getPriceInfo();
        priceInfo.getAdults().setMarkup(toMarkup(result.getAdults()));
        priceInfo.getAdults().setTotal(result.getAdults().getTotal());

        if (priceInfo.getChildren() != null) {
            priceInfo.getChildren().setMarkup(toMarkup(result.getChildren()));
            priceInfo.getChildren().setTotal(result.getChildren().getTotal());
        }

        if (priceInfo.getInfants() != null) {
            priceInfo.getInfants().setMarkup(toMarkup(result.getInfants()));
            priceInfo.getInfants().setTotal(result.getInfants().getTotal());
        }
    }

    private MarkupDTO toMarkup(QuotePaxResult paxResult) {
        return paxResult.getMarkup() != null ? new MarkupDTO(paxResult.getPercentage(), paxResult.getMarkup()) : null;
    }
}
