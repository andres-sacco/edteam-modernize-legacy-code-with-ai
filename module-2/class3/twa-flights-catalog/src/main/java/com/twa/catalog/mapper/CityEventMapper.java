package com.twa.catalog.mapper;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.dto.ContinentDTO;
import com.twa.catalog.dto.CountryDTO;
import com.twa.catalog.dto.event.CityUpdatedEventDTO;
import com.twa.catalog.model.CatalogOutboxEvent;

/**
 * Translates an event of a city of flights.catalog_outbox (with the country and continent that
 * CatalogOutboxRepository#findCityEventsAfter adds) to the event CiudadActualizada v1. It only copies fields: the
 * decisions of what to apply are of the consumer.
 */
@Component
public class CityEventMapper {

    private final ObjectMapper objectMapper;

    @Autowired
    public CityEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CityUpdatedEventDTO convert(CatalogOutboxEvent event) {
        JsonNode row = parse(event);

        CityUpdatedEventDTO dto = new CityUpdatedEventDTO();
        dto.setEventId(eventId(event.getId()));
        // MySQL of the stack runs in UTC, created_at is the time of the server
        dto.setOccurredAt(event.getCreatedAt() == null ? null : event.getCreatedAt().toInstant(ZoneOffset.UTC));
        dto.setSequence(event.getId());
        dto.setOperation(event.getOperation());
        dto.setCityId(event.getAggregateId());

        CityDTO city = new CityDTO();
        city.setCode(text(row, "code"));
        if (CatalogOutboxEvent.UPSERT.equals(event.getOperation())) {
            city.setName(text(row, "name"));
            city.setTimeZone(text(row, "timeZone"));
            city.setCountry(country(row));
        }
        dto.setCity(city);
        return dto;
    }

    /**
     * The same outbox event always has the same id, so a republished event (the relay stopped between the publication
     * and the checkpoint) is a duplicate that the consumer can detect.
     */
    public static String eventId(long outboxId) {
        return UUID.nameUUIDFromBytes(("catalog_outbox:" + outboxId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static CountryDTO country(JsonNode row) {
        String code = text(row, "countryCode");
        if (code == null) {
            return null;
        }
        CountryDTO country = new CountryDTO();
        country.setCode(code);
        country.setName(text(row, "countryName"));
        String continentCode = text(row, "continentCode");
        if (continentCode != null) {
            ContinentDTO continent = new ContinentDTO();
            continent.setCode(continentCode);
            continent.setName(text(row, "continentName"));
            country.setContinent(continent);
        }
        return country;
    }

    private JsonNode parse(CatalogOutboxEvent event) {
        try {
            return objectMapper.readTree(event.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The payload of the event is not valid JSON: " + event, e);
        }
    }

    private static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
