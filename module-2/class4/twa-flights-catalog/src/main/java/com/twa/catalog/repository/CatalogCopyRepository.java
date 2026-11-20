package com.twa.catalog.repository;

import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Writes the copy of the catalog in the own database of the service (catalog). It has its own transactions: they
 * never include the database flights, that the JPA repositories of the service read.
 *
 * It is created by CatalogCopyConfiguration only when catalog.copy.enabled=true, and it owns the pool of that database.
 */
public class CatalogCopyRepository {

    // One statement by table. The upsert is by id (the same ids as flights, ADR-002 D2): applying an event twice
    // gives the same row
    private static final Map<String, String> UPSERTS = Map.of(
            "continent", "INSERT INTO continent (id, code, name) VALUES (?, ?, ?) AS new"
                    + " ON DUPLICATE KEY UPDATE code = new.code, name = new.name",
            "country", "INSERT INTO country (id, code, name, continent_id, continent_code) VALUES (?, ?, ?, ?, ?) AS new"
                    + " ON DUPLICATE KEY UPDATE code = new.code, name = new.name, continent_id = new.continent_id,"
                    + " continent_code = new.continent_code",
            "city", "INSERT INTO city (id, code, name, time_zone, country_id, country_code) VALUES (?, ?, ?, ?, ?, ?)"
                    + " AS new ON DUPLICATE KEY UPDATE code = new.code, name = new.name, time_zone = new.time_zone,"
                    + " country_id = new.country_id, country_code = new.country_code");

    private static final Map<String, String> DELETES = Map.of(
            "continent", "DELETE FROM continent WHERE id = ?",
            "country", "DELETE FROM country WHERE id = ?",
            "city", "DELETE FROM city WHERE id = ?");

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public CatalogCopyRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void close() {
        dataSource.close();
    }

    public <T> T inTransaction(TransactionCallback<T> callback) {
        return transactionTemplate.execute(callback);
    }

    /**
     * The last event applied, locking the row until the end of the transaction: another instance waits and then
     * sees the new value.
     */
    public long lockCheckpoint() {
        return queryCheckpoint("SELECT last_event_id FROM outbox_checkpoint WHERE id = 1 FOR UPDATE");
    }

    public long readCheckpoint() {
        return queryCheckpoint("SELECT last_event_id FROM outbox_checkpoint WHERE id = 1");
    }

    public void saveCheckpoint(long eventId) {
        jdbcTemplate.update("UPDATE outbox_checkpoint SET last_event_id = ? WHERE id = 1", eventId);
    }

    /**
     * The last event that a relay of events published (event_relay_checkpoint, M2 C3), locking the row until the end
     * of the transaction.
     */
    public long lockRelayCheckpoint(String relay) {
        return queryRelayCheckpoint("SELECT last_event_id FROM event_relay_checkpoint WHERE relay = ? FOR UPDATE",
                relay);
    }

    public long readRelayCheckpoint(String relay) {
        return queryRelayCheckpoint("SELECT last_event_id FROM event_relay_checkpoint WHERE relay = ?", relay);
    }

    public void saveRelayCheckpoint(String relay, long eventId) {
        jdbcTemplate.update("UPDATE event_relay_checkpoint SET last_event_id = ? WHERE relay = ?", eventId, relay);
    }

    public void upsert(String aggregateType, JsonNode row) {
        String sql = statement(UPSERTS, aggregateType);
        switch (aggregateType) {
        case "continent" -> jdbcTemplate.update(sql, row.get("id").asLong(), text(row, "code"), text(row, "name"));
        case "country" -> jdbcTemplate.update(sql, row.get("id").asLong(), text(row, "code"), text(row, "name"),
                number(row, "continentId"), text(row, "continentCode"));
        default -> jdbcTemplate.update(sql, row.get("id").asLong(), text(row, "code"), text(row, "name"),
                text(row, "timeZone"), number(row, "countryId"), text(row, "countryCode"));
        }
    }

    public void delete(String aggregateType, long id) {
        jdbcTemplate.update(statement(DELETES, aggregateType), id);
    }

    private long queryCheckpoint(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0 : value;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("The table outbox_checkpoint of the database catalog has no row", e);
        }
    }

    private long queryRelayCheckpoint(String sql, String relay) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, relay);
            return value == null ? 0 : value;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("The table event_relay_checkpoint of the database catalog has no row for "
                    + relay, e);
        }
    }

    private static String statement(Map<String, String> statements, String aggregateType) {
        String sql = statements.get(aggregateType);
        if (sql == null) {
            throw new IllegalArgumentException("Unknown aggregate type of the outbox: " + aggregateType);
        }
        return sql;
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long number(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }
}
