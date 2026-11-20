package com.twa.catalog.dto.event;

import java.time.Instant;

import com.twa.catalog.dto.CityDTO;

/**
 * The event CiudadActualizada, version 1 (microcks/events/ciudad-actualizada-v1.schema.json): a city of the catalog was
 * created, changed (UPSERT) or deleted (DELETE). The city has the same JSON as GET /cities/{code}; in a DELETE only the
 * code (the event is written without the null fields).
 */
public class CityUpdatedEventDTO {

    public static final String TYPE = "CiudadActualizada";
    public static final int VERSION = 1;
    public static final String SOURCE = "twa-flights-catalog";

    private String eventId;
    private String eventType = TYPE;
    private int eventVersion = VERSION;
    private Instant occurredAt;
    private String source = SOURCE;
    private long sequence;
    private String operation;
    private long cityId;
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

    public int getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(int eventVersion) {
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

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public long getCityId() {
        return cityId;
    }

    public void setCityId(long cityId) {
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
        return "CityUpdatedEventDTO [eventId=" + eventId + ", sequence=" + sequence + ", operation=" + operation
                + ", cityId=" + cityId + "]";
    }
}
