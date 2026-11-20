package com.twa.flights.catalog.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.dto.ContinentDTO;
import com.twa.flights.catalog.dto.CountryDTO;

/**
 * The replica of the cities built from the events CiudadActualizada (city_replica) and the events already processed
 * (processed_event), V1.13. The methods run in the transaction of the caller (CityReplicaService). The reads
 * (findByCode, findByCodeIn) are the path replica of the catalog (CityReplicaCatalogService, M2 C5).
 */
@Repository
public class CityReplicaRepository {

    private static final String SELECT_CITY = "SELECT code, name, time_zone, country_code, country_name,"
            + " continent_code, continent_name FROM city_replica WHERE deleted = 0";

    // The same CityDTO of the catalog: the city with its country and its continent
    private static final RowMapper<CityDTO> CITY_MAPPER = (rs, rowNum) -> {
        ContinentDTO continent = null;
        if (rs.getString("continent_code") != null) {
            continent = new ContinentDTO();
            continent.setCode(rs.getString("continent_code"));
            continent.setName(rs.getString("continent_name"));
        }
        CountryDTO country = null;
        if (rs.getString("country_code") != null) {
            country = new CountryDTO();
            country.setCode(rs.getString("country_code"));
            country.setName(rs.getString("country_name"));
            country.setContinent(continent);
        }
        CityDTO city = new CityDTO();
        city.setCode(rs.getString("code"));
        city.setName(rs.getString("name"));
        city.setTimeZone(rs.getString("time_zone"));
        city.setCountry(country);
        return city;
    };

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CityReplicaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The city of the code, compared with the collation of the table (like the table city of the catalog).
     *
     * @return null if the replica does not have the city or it was deleted
     */
    public CityDTO findByCode(String code) {
        List<CityDTO> cities = jdbcTemplate.query(SELECT_CITY + " AND code = ? LIMIT 1", CITY_MAPPER, code);
        return cities.isEmpty() ? null : cities.get(0);
    }

    /**
     * The cities of the codes that exist in the replica, in any order.
     */
    public List<CityDTO> findByCodeIn(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", Collections.nCopies(codes.size(), "?"));
        return jdbcTemplate.query(SELECT_CITY + " AND code IN (" + placeholders + ")", CITY_MAPPER, codes.toArray());
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
