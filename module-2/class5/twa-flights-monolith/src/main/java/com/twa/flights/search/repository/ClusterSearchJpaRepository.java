package com.twa.flights.search.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.twa.flights.search.model.ClusterSearch;

@Repository
public interface ClusterSearchJpaRepository extends JpaRepository<ClusterSearch, String> {

    Optional<ClusterSearch> findByIdAndExpireAtAfter(String id, LocalDateTime now);

    @Modifying
    @Transactional
    @Query("delete from ClusterSearch c where c.expireAt < :now")
    int deleteExpired(LocalDateTime now);
}
