package com.twa.flights.search.job;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.twa.flights.search.repository.ClusterSearchJpaRepository;

@Component
public class ClusterSearchCleanupJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchCleanupJob.class);

    private final ClusterSearchJpaRepository clusterSearchJpaRepository;

    @Autowired
    public ClusterSearchCleanupJob(ClusterSearchJpaRepository clusterSearchJpaRepository) {
        this.clusterSearchJpaRepository = clusterSearchJpaRepository;
    }

    // Remove the searches that are not available anymore to paginate
    @Scheduled(fixedDelayString = "${jobs.cluster-search-cleanup.fixed-delay:60000}")
    public void removeExpiredSearches() {
        int deleted = clusterSearchJpaRepository.deleteExpired(LocalDateTime.now());
        LOGGER.debug("Removed {} expired searches", deleted);
    }
}
