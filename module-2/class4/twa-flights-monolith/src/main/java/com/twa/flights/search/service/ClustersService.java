package com.twa.flights.search.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.twa.flights.search.dto.ClusterSearchDTO;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import com.twa.flights.search.exception.SearchError;
import com.twa.flights.shared.exception.APIException;
import com.twa.flights.search.dto.response.PaginationDTO;
import com.twa.flights.search.helper.ItineraryFilterHelper;
import com.twa.flights.search.helper.ItinerarySortHelper;
import com.twa.flights.search.repository.ClustersRepository;
import com.twa.flights.search.dto.ItineraryDTO;

@Service
public class ClustersService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClustersService.class);

    private final ItinerariesSearchService itinerariesSearchService;
    private final ItineraryPricingAdapter itineraryPricingAdapter;
    private final ClustersRepository repository;
    private final ItinerarySortHelper itinerarySortHelper;
    private final ItineraryFilterHelper itineraryFilterHelper;

    @Autowired
    public ClustersService(ItinerariesSearchService itinerariesSearchService,
            ItineraryPricingAdapter itineraryPricingAdapter, ClustersRepository repository,
            ItinerarySortHelper itinerarySortHelper, ItineraryFilterHelper itineraryFilterHelper) {
        this.itinerariesSearchService = itinerariesSearchService;
        this.itineraryPricingAdapter = itineraryPricingAdapter;
        this.repository = repository;
        this.itinerarySortHelper = itinerarySortHelper;
        this.itineraryFilterHelper = itineraryFilterHelper;
    }

    public ClusterSearchDTO availability(ClustersAvailabilityRequestDTO request) {
        LOGGER.debug("begin the search");

        ClusterSearchDTO response = null;

        if (StringUtils.isEmpty(request.getId())) { // New search
            response = availabilityFromProviders(request);
        } else { // Pagination old search
            response = availabilityFromDatabase(request);
        }

        return response;
    }

    public ItineraryDTO getItinerary(String id, String itineraryId) {
        LOGGER.debug("Obtain the itinerary {} of the search {}", itineraryId, id);

        ClusterSearchDTO clusterSearch = repository.get(id);

        if (clusterSearch == null) {
            throw new APIException(HttpStatus.NOT_FOUND, SearchError.SEARCH_NOT_FOUND);
        }

        return clusterSearch.getItineraries().stream().filter(itinerary -> itinerary.getId().equals(itineraryId))
                .findFirst()
                .orElseThrow(() -> new APIException(HttpStatus.NOT_FOUND, SearchError.ITINERARY_NOT_FOUND));
    }

    private ClusterSearchDTO availabilityFromProviders(ClustersAvailabilityRequestDTO request) {
        ClusterSearchDTO response;
        List<ItineraryDTO> itineraries = itinerariesSearchService.availability(request);

        itineraries = itineraryPricingAdapter.priceItineraries(itineraries);

        // All the itineraries are saved, the filters and the order are applied in each request
        response = repository.insert(request, itineraries);

        return buildPage(response, request, 0);
    }

    private ClusterSearchDTO availabilityFromDatabase(ClustersAvailabilityRequestDTO request) {
        ClusterSearchDTO response;
        response = repository.get(request.getId());

        if (response == null) {
            throw new APIException(HttpStatus.BAD_GATEWAY, SearchError.SEARCH_NOT_FOUND_IN_REPOSITORY.getCode(),
                    SearchError.SEARCH_NOT_FOUND_IN_REPOSITORY.getMessage());
        }

        return buildPage(response, request, request.getOffset());
    }

    // Each page could be requested with different filters and order
    private ClusterSearchDTO buildPage(ClusterSearchDTO clusterSearch, ClustersAvailabilityRequestDTO request,
            int offset) {
        List<ItineraryDTO> itineraries = itineraryFilterHelper.filter(clusterSearch.getItineraries(), request);
        itineraries = itinerarySortHelper.sort(itineraries, request.getSortBy(), request.getSortOrder());

        int pageSize = getPageSize(request, itineraries);
        long skip = (long) offset * pageSize;

        // Limit the size
        List<ItineraryDTO> page = itineraries.stream().skip(skip).limit(pageSize).collect(Collectors.toList());

        return new ClusterSearchDTO(clusterSearch.getId(), page,
                new PaginationDTO(offset, pageSize, itineraries.size()));
    }

    // Without an amount in the request, all the itineraries are returned in one page
    private int getPageSize(ClustersAvailabilityRequestDTO request, List<ItineraryDTO> itineraries) {
        return request.getAmount() != null ? request.getAmount() : itineraries.size();
    }

}
