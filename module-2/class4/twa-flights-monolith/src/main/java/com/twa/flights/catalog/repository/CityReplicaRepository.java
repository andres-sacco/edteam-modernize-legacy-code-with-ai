package com.twa.flights.catalog.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.twa.flights.catalog.dto.CityDTO;

/**
 * The replica of the cities built from the events CiudadActualizada (city_replica) and the events already processed
 * (processed_event), V1.13. The methods run in the transaction of the caller (CityReplicaService).
 */
@Repository
public class CityReplicaRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CityReplicaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Registers the event as processed.
     *
     * @return false if the event was already processed (the same eventId)
     */
    public boolean markProcessed(String eventId, String eventType, long sequence) {
        return jdbcTemplate.update("INSERT IGNORE INTO processed_event (event_id, event_type, sequence) VALUES (?, ?, ?)",
                eventId, eventType, sequence) == 1;
    }

    /**
     * The sequence of the last event applied to the city, locking its row until the end of the transaction.
     *
     * @return null if the replica does not have the city
     */
    public Long lockLastSequence(long cityId) {
        List<Long> sequences = jdbcTemplate.queryForList(
                "SELECT last_sequence FROM city_replica WHERE city_id = ? FOR UPDATE", Long.class, cityId);
        return sequences.isEmpty() ? null : sequences.get(0);
    }

    public void upsert(long cityId, CityDTO city, long sequence) {
        String countryCode = city.getCountry() == null ? null : city.getCountry().getCode();
        String countryName = city.getCountry() == null ? null : city.getCountry().getName();
        String continentCode = null;
        String continentName = null;
        if (city.getCountry() != null && city.getCountry().getContinent() != null) {
            continentCode = city.getCountry().getContinent().getCode();
            continentName = city.getCountry().getContinent().getName();
        }
        jdbcTemplate.update("INSERT INTO city_replica (city_id, code, name, time_zone, country_code, country_name,"
                + " continent_code, continent_name, deleted, last_sequence) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?) AS new"
                + " ON DUPLICATE KEY UPDATE code = new.code, name = new.name, time_zone = new.time_zone,"
                + " country_code = new.country_code, country_name = new.country_name,"
                + " continent_code = new.continent_code, continent_name = new.continent_name, deleted = 0,"
                + " last_sequence = new.last_sequence", cityId, city.getCode(), city.getName(), city.getTimeZone(),
                countryCode, countryName, continentCode, continentName, sequence);
    }

    /**
     * Marks the city as deleted (tombstone), so an older UPSERT that arrives later is discarded.
     */
    public void markDeleted(long cityId, String code, long sequence) {
        jdbcTemplate.update("INSERT INTO city_replica (city_id, code, deleted, last_sequence) VALUES (?, ?, 1, ?) AS new"
                + " ON DUPLICATE KEY UPDATE deleted = 1, last_sequence = new.last_sequence", cityId, code, sequence);
    }
}
