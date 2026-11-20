package com.twa.flights.dto;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class PriceInfoDTO {

    private PaxPriceDTO adults;
    private PaxPriceDTO children;
    private PaxPriceDTO infants;

    public PriceInfoDTO() {
    }

    public PriceInfoDTO(PriceInfoDTO other) {
        this(replicatePaxPriceDTO(other.adults), replicatePaxPriceDTO(other.children),
                replicatePaxPriceDTO(other.infants));
    }

    public PriceInfoDTO(PaxPriceDTO adults, PaxPriceDTO children, PaxPriceDTO infants) {
        this.adults = adults;
        this.children = children;
        this.infants = infants;
    }

    private static PaxPriceDTO replicatePaxPriceDTO(PaxPriceDTO paxPrice) {
        return paxPrice != null ? new PaxPriceDTO(paxPrice) : null;
    }

    public PaxPriceDTO getAdults() {
        return adults;
    }

    public void setAdults(PaxPriceDTO adults) {
        this.adults = adults;
    }

    public PaxPriceDTO getChildren() {
        return children;
    }

    public void setChildren(PaxPriceDTO children) {
        this.children = children;
    }

    public PaxPriceDTO getInfants() {
        return infants;
    }

    public void setInfants(PaxPriceDTO infants) {
        this.infants = infants;
    }

    @JsonIgnore
    public BigDecimal getTotalAmount() {
        BigDecimal totalAmount = BigDecimal.ZERO;

        if (Objects.nonNull(getAdults())) {
            totalAmount = totalAmount.add(getAdults().getTotal());
        }

        if (Objects.nonNull(getChildren())) {
            totalAmount = totalAmount.add(getChildren().getTotal());
        }

        if (Objects.nonNull(getInfants())) {
            totalAmount = totalAmount.add(getInfants().getTotal());
        }

        return totalAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PriceInfoDTO that = (PriceInfoDTO) o;
        return Objects.equals(adults, that.adults) && Objects.equals(children, that.children)
                && Objects.equals(infants, that.infants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adults, children, infants);
    }

    @Override
    public String toString() {
        return "PriceInfoDTO{" + "adults=" + adults + ", children=" + children + ", infants=" + infants + '}';
    }
}
