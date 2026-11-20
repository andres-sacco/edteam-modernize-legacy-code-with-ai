package com.twa.flights.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.twa.flights.model.City;

@Repository
public interface CityRepository extends JpaRepository<City, Long> {

    City findByCode(String code);

}
