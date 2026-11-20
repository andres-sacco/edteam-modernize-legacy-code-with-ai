package com.twa.flights.catalog.dto;

/**
 * A change of the flag of the rollout of the catalog service.
 */
public class CatalogRolloutRequestDTO {

    // From 0 to 100, or null to go back to the value of catalog.mode
    private Integer remotePercentage;
    private String updatedBy;
    private String reason;

    public Integer getRemotePercentage() {
        return remotePercentage;
    }

    public void setRemotePercentage(Integer remotePercentage) {
        this.remotePercentage = remotePercentage;
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
}
