package com.twa.flights.repository.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.twa.flights.dto.ClusterSearchDTO;
import com.twa.flights.dto.ItineraryDTO;
import com.twa.flights.dto.request.AvailabilityRequestDTO;
import com.twa.flights.dto.response.PaginationDTO;
import com.twa.flights.helper.FlightIdGeneratorHelper;
import com.twa.flights.model.ClusterSearch;
import com.twa.flights.repository.ClusterSearchJpaRepository;
import com.twa.flights.repository.ClustersRepository;
import com.twa.flights.serializer.JsonSerializer;

@Repository
public class ClustersRepositoryImpl implements ClustersRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClustersRepositoryImpl.class);
    private static final long TOKEN_TTL = 5; // 5 Minutes

    private final ClusterSearchJpaRepository clusterSearchJpaRepository;
    private final ExecutorService executorService;
    private final FlightIdGeneratorHelper flightIdGeneratorHelper;

    @Autowired
    public ClustersRepositoryImpl(ClusterSearchJpaRepository clusterSearchJpaRepository,
            FlightIdGeneratorHelper flightIdGeneratorHelper) {
        this.flightIdGeneratorHelper = flightIdGeneratorHelper;
        this.executorService = Executors.newFixedThreadPool(10);
        this.clusterSearchJpaRepository = clusterSearchJpaRepository;
    }

    @Override
    public ClusterSearchDTO insert(AvailabilityRequestDTO availabilityRequest, List<ItineraryDTO> itineraries) {
        LOGGER.debug("Insert all the information in the database");

        PaginationDTO pagination = new PaginationDTO(0,
                availabilityRequest.getAmount() != null ? availabilityRequest.getAmount() : itineraries.size(),
                itineraries.size());
        ClusterSearchDTO dataToInsert = new ClusterSearchDTO(flightIdGeneratorHelper.generate(availabilityRequest),
                itineraries, pagination);

        executorService.submit(() -> {
            LocalDateTime now = LocalDateTime.now();

            ClusterSearch clusterSearch = new ClusterSearch();
            clusterSearch.setId(dataToInsert.getId());
            clusterSearch.setPayload(new String(JsonSerializer.serialize(dataToInsert), StandardCharsets.UTF_8));
            clusterSearch.setCreatedAt(now);
            clusterSearch.setExpireAt(now.plusMinutes(TOKEN_TTL));

            clusterSearchJpaRepository.save(clusterSearch);
        });
        return new ClusterSearchDTO(dataToInsert.getId(), itineraries, pagination);
    }

    @Override
    public ClusterSearchDTO get(String id) {

        Optional<ClusterSearch> clusterSearch = clusterSearchJpaRepository.findByIdAndExpireAtAfter(id,
                LocalDateTime.now());
        if (clusterSearch.isEmpty()) {
            LOGGER.error("clusterSearch information with id: {}' not found in repository", id);
            return null;
        }

        return JsonSerializer.deserialize(clusterSearch.get().getPayload().getBytes(StandardCharsets.UTF_8),
                ClusterSearchDTO.class);
    }

}
