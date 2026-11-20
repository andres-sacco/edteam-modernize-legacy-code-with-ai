package com.twa.flights.search.repository;

import java.util.List;

import com.twa.flights.search.dto.ClusterSearchDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.request.AvailabilityRequestDTO;

public interface ClustersRepository {

    ClusterSearchDTO insert(AvailabilityRequestDTO query, List<ItineraryDTO> itineraries);

    ClusterSearchDTO get(String id);
}
