package com.twa.flights.dto.request;

import java.math.BigDecimal;
import java.util.Objects;

public class ClustersAvailabilityRequestDTO extends AvailabilityRequestDTO {

    private String id;
    private Integer offset;
    private String sortBy;
    private String sortOrder;
    private Integer maxStops;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String carriers;
    private String excludedCarriers;
    private String departureTime;
    private Boolean checkedBaggage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getMaxStops() {
        return maxStops;
    }

    public void setMaxStops(Integer maxStops) {
        this.maxStops = maxStops;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public String getCarriers() {
        return carriers;
    }

    public void setCarriers(String carriers) {
        this.carriers = carriers;
    }

    public String getExcludedCarriers() {
        return excludedCarriers;
    }

    public void setExcludedCarriers(String excludedCarriers) {
        this.excludedCarriers = excludedCarriers;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public Boolean getCheckedBaggage() {
        return checkedBaggage;
    }

    public void setCheckedBaggage(Boolean checkedBaggage) {
        this.checkedBaggage = checkedBaggage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClustersAvailabilityRequestDTO that = (ClustersAvailabilityRequestDTO) o;
        return Objects.equals(getFrom(), that.getFrom()) && Objects.equals(getTo(), that.getTo())
                && Objects.equals(getDeparture(), that.getDeparture()) && Objects.equals(getAdults(), that.getAdults())
                && Objects.equals(getChildren(), that.getChildren()) && Objects.equals(getInfants(), that.getInfants())
                && Objects.equals(getAmount(), that.getAmount()) && Objects.equals(id, that.id)
                && Objects.equals(offset, that.offset) && Objects.equals(sortBy, that.sortBy)
                && Objects.equals(sortOrder, that.sortOrder) && Objects.equals(getCabin(), that.getCabin())
                && Objects.equals(maxStops, that.maxStops) && Objects.equals(minPrice, that.minPrice)
                && Objects.equals(maxPrice, that.maxPrice) && Objects.equals(carriers, that.carriers)
                && Objects.equals(excludedCarriers, that.excludedCarriers)
                && Objects.equals(departureTime, that.departureTime)
                && Objects.equals(checkedBaggage, that.checkedBaggage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFrom(), getTo(), getDeparture(), getAdults(), getChildren(), getInfants(), getAmount(),
                id, offset, sortBy, sortOrder, getCabin(), maxStops, minPrice, maxPrice, carriers, excludedCarriers,
                departureTime, checkedBaggage);
    }

    @Override
    public String toString() {
        return "ClustersAvailabilityRequestDTO{" + "from='" + getFrom() + '\'' + ", to='" + getTo() + '\''
                + ", departure='" + getDeparture() + '\'' + ", adults=" + getAdults() + ", children=" + getChildren()
                + ", infants=" + getInfants() + ", amount=" + getAmount() + ", id=" + getId() + ", offset="
                + getOffset() + ", sortBy=" + getSortBy() + ", sortOrder=" + getSortOrder() + ", cabin=" + getCabin() + ", maxStops=" + maxStops + ", minPrice="
                + minPrice + ", maxPrice=" + maxPrice + ", carriers=" + carriers + ", excludedCarriers="
                + excludedCarriers + ", departureTime=" + departureTime + ", checkedBaggage=" + checkedBaggage + '}';
    }

}
