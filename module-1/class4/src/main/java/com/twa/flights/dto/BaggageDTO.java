package com.twa.flights.dto;

import java.util.Objects;

public class BaggageDTO {

    private BaggageAllowanceDTO carryOn;
    private BaggageAllowanceDTO checked;

    public BaggageDTO() {
    }

    public BaggageDTO(BaggageAllowanceDTO carryOn, BaggageAllowanceDTO checked) {
        this.carryOn = carryOn;
        this.checked = checked;
    }

    public BaggageDTO(BaggageDTO other) {
        this(copy(other.carryOn), copy(other.checked));
    }

    private static BaggageAllowanceDTO copy(BaggageAllowanceDTO allowance) {
        return allowance != null ? new BaggageAllowanceDTO(allowance.getPieces(), allowance.getWeightKg()) : null;
    }

    public BaggageAllowanceDTO getCarryOn() {
        return carryOn;
    }

    public void setCarryOn(BaggageAllowanceDTO carryOn) {
        this.carryOn = carryOn;
    }

    public BaggageAllowanceDTO getChecked() {
        return checked;
    }

    public void setChecked(BaggageAllowanceDTO checked) {
        this.checked = checked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BaggageDTO that = (BaggageDTO) o;
        return Objects.equals(carryOn, that.carryOn) && Objects.equals(checked, that.checked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carryOn, checked);
    }

    @Override
    public String toString() {
        return "BaggageDTO{" + "carryOn=" + carryOn + ", checked=" + checked + '}';
    }
}
