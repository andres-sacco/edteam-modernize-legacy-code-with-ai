package com.twa.flights.catalog.dto;

import java.time.LocalDateTime;

/**
 * The flag of the rollout of the catalog service (table catalog_rollout, V1.14).
 */
public class CatalogRolloutDTO {

    // The value of the table, null when the property catalog.mode decides
    private Integer remotePercentage;
    // The percentage that the monolith applies: the value of the table, or 0/100 by catalog.mode
    private int effectiveRemotePercentage;
    private String updatedBy;
    private String reason;
    private LocalDateTime updatedAt;

    public Integer getRemotePercentage() {
        return remotePercentage;
    }

    public void setRemotePercentage(Integer remotePercentage) {
        this.remotePercentage = remotePercentage;
    }

    public int getEffectiveRemotePercentage() {
        return effectiveRemotePercentage;
    }

    public void setEffectiveRemotePercentage(int effectiveRemotePercentage) {
        this.effectiveRemotePercentage = effectiveRemotePercentage;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
