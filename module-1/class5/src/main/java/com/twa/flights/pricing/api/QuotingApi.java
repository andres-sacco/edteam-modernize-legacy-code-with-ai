package com.twa.flights.pricing.api;

import java.util.List;

import com.twa.flights.pricing.dto.QuoteRequest;
import com.twa.flights.pricing.dto.QuoteResult;

/**
 * Public interface of the quoting of the module pricing (BC3). It calculates the markup of the commercial policies
 * without knowing the model of the search.
 */
public interface QuotingApi {

    /**
     * Calculate the markup of each request with the commercial policy that applies to it.
     *
     * @param requests
     *            the information of each itinerary that is needed to choose the policy and to calculate the markup
     * @return one result for each request, in the same order. A request without an applicable policy has a result
     *         without policy
     */
    List<QuoteResult> quote(List<QuoteRequest> requests);

}
