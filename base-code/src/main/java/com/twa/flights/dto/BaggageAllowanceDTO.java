package com.twa.flights.dto;

import java.util.Objects;

public class BaggageAllowanceDTO {

    private Integer pieces;
    private Integer weightKg;

    public BaggageAllowanceDTO() {
    }

    public BaggageAllowanceDTO(Integer pieces, Integer weightKg) {
        this.pieces = pieces;
        this.weightKg = weightKg;
    }

    public Integer getPieces() {
        return pieces;
    }

    public void setPieces(Integer pieces) {
        this.pieces = pieces;
    }

    public Integer getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Integer weightKg) {
        this.weightKg = weightKg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BaggageAllowanceDTO that = (BaggageAllowanceDTO) o;
        return Objects.equals(pieces, that.pieces) && Objects.equals(weightKg, that.weightKg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pieces, weightKg);
    }

    @Override
    public String toString() {
        return "BaggageAllowanceDTO{" + "pieces=" + pieces + ", weightKg=" + weightKg + '}';
    }
}
