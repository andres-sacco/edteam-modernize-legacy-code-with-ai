package com.twa.flights.dto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.twa.flights.enums.CabinClass;
import com.twa.flights.enums.FlightType;
import com.twa.flights.enums.Provider;

public class ItineraryDTO {

    private String id;
    private List<SegmentDTO> segments;
    private PriceInfoDTO priceInfo;
    private Provider provider;
    private FlightType flightType;
    private String carrier;
    private CabinClass cabin;
    private BaggageDTO baggage;

    public ItineraryDTO() {
    }

    public ItineraryDTO(List<SegmentDTO> segments, PriceInfoDTO priceInfo, Provider provider, FlightType flightType,
            String carrier, String id) {
        this.segments = segments;
        this.priceInfo = priceInfo;
        this.provider = provider;
        this.flightType = flightType;
        this.carrier = carrier;
        this.id = id;
    }

    public ItineraryDTO(ItineraryDTO other) {
        this(copySegments(other.segments), other.priceInfo != null ? new PriceInfoDTO(other.priceInfo) : null,
                other.provider, other.flightType, other.carrier, other.id);
        this.cabin = other.cabin;
        this.baggage = other.baggage != null ? new BaggageDTO(other.baggage) : null;
    }

    private static List<SegmentDTO> copySegments(List<SegmentDTO> segments) {
        return segments != null ? segments.stream().map(SegmentDTO::new).collect(Collectors.toList()) : null;
    }

    public List<SegmentDTO> getSegments() {
        return segments;
    }

    public void setSegments(List<SegmentDTO> segments) {
        this.segments = segments;
    }

    public PriceInfoDTO getPriceInfo() {
        return priceInfo;
    }

    public void setPriceInfo(PriceInfoDTO priceInfo) {
        this.priceInfo = priceInfo;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public FlightType getFlightType() {
        return flightType;
    }

    public void setFlightType(FlightType flightType) {
        this.flightType = flightType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public CabinClass getCabin() {
        return cabin;
    }

    public void setCabin(CabinClass cabin) {
        this.cabin = cabin;
    }

    public BaggageDTO getBaggage() {
        return baggage;
    }

    public void setBaggage(BaggageDTO baggage) {
        this.baggage = baggage;
    }

    @JsonIgnore
    public SegmentDTO firstSegment() {
        return !getSegments().isEmpty() ? getSegments().get(0) : null;
    }

    @JsonIgnore
    public SegmentDTO lastSegment() {
        return !getSegments().isEmpty() ? getSegments().get(getSegments().size() - 1) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ItineraryDTO that = (ItineraryDTO) o;
        return Objects.equals(id, that.id) && Objects.equals(segments, that.segments)
                && Objects.equals(priceInfo, that.priceInfo) && provider == that.provider
                && flightType == that.flightType && Objects.equals(carrier, that.carrier) && cabin == that.cabin
                && Objects.equals(baggage, that.baggage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, segments, priceInfo, provider, flightType, carrier, cabin, baggage);
    }

    @Override
    public String toString() {
        return "ItineraryDTO{" + "id='" + id + '\'' + ", segments=" + segments + ", priceInfo=" + priceInfo
                + ", provider=" + provider + ", flightType=" + flightType + ", carrier='" + carrier + '\'' + ", cabin=" + cabin
                + ", baggage=" + baggage + '}';
    }
}
