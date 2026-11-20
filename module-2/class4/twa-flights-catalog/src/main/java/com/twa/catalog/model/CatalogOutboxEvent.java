package com.twa.catalog.model;

import java.time.LocalDateTime;

/**
 * A row of flights.catalog_outbox: a change of continent, country or city, written by a trigger of the monolith in
 * the same transaction as the change (V1.11 of the monolith).
 */
public class CatalogOutboxEvent {

    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";

    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private String operation;
    private String payload;
    private LocalDateTime createdAt;
    // Calculated by MySQL when the event is read: it does not depend on the time zone of the service
    private long ageMillis;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(Long aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getAgeMillis() {
        return ageMillis;
    }

    public void setAgeMillis(long ageMillis) {
        this.ageMillis = ageMillis;
    }

    @Override
    public String toString() {
        return "CatalogOutboxEvent [id=" + id + ", aggregateType=" + aggregateType + ", aggregateId=" + aggregateId
                + ", operation=" + operation + "]";
    }
}
