package com.twa.flights.catalog.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.twa.flights.catalog.dto.CatalogRolloutDTO;

/**
 * The flag of the rollout of the catalog service (catalog_rollout, one row) and its history (catalog_rollout_history),
 * V1.14.
 */
@Repository
public class CatalogRolloutRepository {

    private static final long FLAG_ID = 1;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CatalogRolloutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return the flag, or null if the row does not exist
     */
    public CatalogRolloutDTO find() {
        List<CatalogRolloutDTO> rows = jdbcTemplate.query(
                "SELECT remote_percentage, updated_by, reason, updated_at FROM catalog_rollout WHERE id = ?",
                (rs, rowNum) -> {
                    CatalogRolloutDTO rollout = new CatalogRolloutDTO();
                    rollout.setRemotePercentage(rs.getObject("remote_percentage", Integer.class));
                    rollout.setUpdatedBy(rs.getString("updated_by"));
                    rollout.setReason(rs.getString("reason"));
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    rollout.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                    return rollout;
                }, FLAG_ID);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Changes the flag and registers the change in the history, in the same transaction.
     */
    @Transactional
    public void update(Integer remotePercentage, String updatedBy, String reason) {
        jdbcTemplate.update("INSERT INTO catalog_rollout (id, remote_percentage, updated_by, reason) VALUES (?, ?, ?, ?)"
                + " AS new ON DUPLICATE KEY UPDATE remote_percentage = new.remote_percentage,"
                + " updated_by = new.updated_by, reason = new.reason", FLAG_ID, remotePercentage, updatedBy, reason);
        jdbcTemplate.update(
                "INSERT INTO catalog_rollout_history (remote_percentage, updated_by, reason) VALUES (?, ?, ?)",
                remotePercentage, updatedBy, reason);
    }
}
