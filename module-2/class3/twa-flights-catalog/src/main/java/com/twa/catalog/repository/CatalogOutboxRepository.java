package com.twa.catalog.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.twa.catalog.model.CatalogOutboxEvent;

/**
 * Reads flights.catalog_outbox with the read-only datasource of the service (the user catalog_reader has SELECT over
 * the table). The service never writes the outbox: what was published is in the checkpoint of the copy.
 */
@Repository
public class CatalogOutboxRepository {

    private static final RowMapper<CatalogOutboxEvent> ROW_MAPPER = (rs, rowNum) -> {
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setId(rs.getLong("id"));
        event.setAggregateType(rs.getString("aggregate_type"));
        event.setAggregateId(rs.getLong("aggregate_id"));
        event.setOperation(rs.getString("operation"));
        event.setPayload(rs.getString("payload"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        event.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        event.setAgeMillis(rs.getLong("age_ms"));
        return event;
    };

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CatalogOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The events after the last one applied, in the order of the outbox.
     *
     * @param lastEventId
     *            the last event applied in the copy
     * @param settleMillis
     *            the events younger than this are not returned yet: the id is assigned at the insert and not at the
     *            commit, so a transaction that commits later can leave a lower id behind one that was already read
     * @param limit
     *            maximum number of events
     */
    public List<CatalogOutboxEvent> findAfter(long lastEventId, long settleMillis, int limit) {
        return jdbcTemplate.query("SELECT id, aggregate_type, aggregate_id, operation, payload, created_at,"
                + " TIMESTAMPDIFF(MICROSECOND, created_at, NOW(3)) DIV 1000 AS age_ms"
                + " FROM catalog_outbox WHERE id > ? AND created_at <= NOW(3) - INTERVAL ? MICROSECOND"
                + " ORDER BY id LIMIT ?", ROW_MAPPER, lastEventId, settleMillis * 1000, limit);
    }

    /**
     * The events of cities after the last one published, in the order of the outbox, with the name of the country and
     * the code and name of its continent added to the payload (countryName, continentCode and continentName). They are
     * the ones of the moment of the query, not of the change: a change of a country does not write an event of city.
     *
     * @param lastEventId
     *            the last event published
     * @param settleMillis
     *            the same as {@link #findAfter(long, long, int)}
     * @param limit
     *            maximum number of events
     */
    public List<CatalogOutboxEvent> findCityEventsAfter(long lastEventId, long settleMillis, int limit) {
        // The code of the payload is utf8mb4_bin: compared with the collation of the tables, like the rest of the codes
        return jdbcTemplate.query("SELECT o.id, o.aggregate_type, o.aggregate_id, o.operation,"
                + " JSON_SET(o.payload, '$.countryName', co.name, '$.continentCode', ct.code,"
                + " '$.continentName', ct.name) AS payload, o.created_at,"
                + " TIMESTAMPDIFF(MICROSECOND, o.created_at, NOW(3)) DIV 1000 AS age_ms"
                + " FROM catalog_outbox o"
                + " LEFT JOIN country co ON co.code = CONVERT(JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.countryCode'))"
                + " USING utf8mb4) COLLATE utf8mb4_0900_ai_ci"
                + " LEFT JOIN continent ct ON ct.code = co.continent_code"
                + " WHERE o.aggregate_type = 'city' AND o.id > ? AND o.created_at <= NOW(3) - INTERVAL ? MICROSECOND"
                + " ORDER BY o.id LIMIT ?", ROW_MAPPER, lastEventId, settleMillis * 1000, limit);
    }

    /**
     * Number of events after the last one applied, without the settle time (metric catalog.outbox.pending).
     */
    public long countAfter(long lastEventId) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM catalog_outbox WHERE id > ?", Long.class,
                lastEventId);
        return count == null ? 0 : count;
    }
}
