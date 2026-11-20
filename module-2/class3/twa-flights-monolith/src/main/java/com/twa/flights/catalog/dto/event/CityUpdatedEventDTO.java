package com.twa.flights.catalog.dto.event;

import java.time.Instant;

import com.twa.flights.catalog.dto.CityDTO;

/**
 * The event CiudadActualizada of the catalog service, version 1 (microcks/events/ciudad-actualizada-v1.schema.json).
 * The city has the same JSON as GET /cities/{code}, so it is read with the CityDTO of the monolith. The fields that
 * the monolith does not know are ignored (a new optional field of v1 does not break the consumer).
 */
public class CityUpdatedEventDTO {

    public static final String TYPE = "CiudadActualizada";
    public static final int VERSION = 1;
    public static final String UPSERT = "UPSERT";
    public static final String DELETE = "DELETE";

    private String eventId;
    private String eventType;
    private Integer eventVersion;
    private Instant occurredAt;
    private String source;
    private Long sequence;
    private String operation;
    private Long cityId;
    private CityDTO city;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public CityDTO getCity() {
        return city;
    }

    public void setCity(CityDTO city) {
        this.city = city;
    }

    @Override
    public String toString() {
        return "CityUpdatedEventDTO [eventId=" + eventId + ", eventVersion=" + eventVersion + ", sequence=" + sequence
                + ", operation=" + operation + ", cityId=" + cityId + "]";
    }
}
