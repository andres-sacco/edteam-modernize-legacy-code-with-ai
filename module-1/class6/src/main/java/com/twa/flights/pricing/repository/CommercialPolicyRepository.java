package com.twa.flights.pricing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.twa.flights.pricing.model.CommercialPolicy;

@Repository
public interface CommercialPolicyRepository
        extends JpaRepository<CommercialPolicy, Long>, JpaSpecificationExecutor<CommercialPolicy> {

}
