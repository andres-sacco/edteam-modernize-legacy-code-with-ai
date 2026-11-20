package com.twa.flights.search.dto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class SegmentDTO {

    private List<LegDTO> legs;
    private String flightDuration;

    public SegmentDTO() {
    }

    public SegmentDTO(List<LegDTO> legs, String flightDuration) {
        this.legs = legs;
        this.flightDuration = flightDuration;
    }

    public SegmentDTO(SegmentDTO other) {
        this(copyLegs(other.legs), other.flightDuration);
    }

    private static List<LegDTO> copyLegs(List<LegDTO> legs) {
        return legs != null ? legs.stream().map(LegDTO::new).collect(Collectors.toList()) : null;
    }

    public List<LegDTO> getLegs() {
        return legs;
    }

    public void setLegs(List<LegDTO> legs) {
        this.legs = legs;
    }

    public String getFlightDuration() {
        return flightDuration;
    }

    public void setFlightDuration(String flightDuration) {
        this.flightDuration = flightDuration;
    }

    @JsonIgnore
    public LegDTO firstLeg() {
        return !getLegs().isEmpty() ? getLegs().get(0) : null;
    }

    @JsonIgnore
    public LegDTO lastLeg() {
        return !getLegs().isEmpty() ? getLegs().get(getLegs().size() - 1) : null;
    }

    @JsonIgnore
    public String getOrigin() {
        return firstLeg() != null ? firstLeg().getOrigin() : null;
    }

    @JsonIgnore
    public String getDestination() {
        return lastLeg() != null ? lastLeg().getDestination() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SegmentDTO that = (SegmentDTO) o;
        return Objects.equals(legs, that.legs) && Objects.equals(flightDuration, that.flightDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(legs, flightDuration);
    }

    @Override
    public String toString() {
        return "SegmentDTO{" + "legs=" + legs + ", flightDuration='" + flightDuration + '\'' + '}';
    }
}
