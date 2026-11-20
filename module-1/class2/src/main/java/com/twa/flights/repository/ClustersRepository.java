package com.twa.flights.repository;

import java.util.List;

import com.twa.flights.dto.ClusterSearchDTO;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.request.AvailabilityRequestDTO;

public interface ClustersRepository {

    ClusterSearchDTO insert(AvailabilityRequestDTO query, List<ItineraryDTO> itineraries);

    ClusterSearchDTO get(String id);
}
