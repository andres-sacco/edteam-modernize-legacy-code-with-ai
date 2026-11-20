package com.twa.flights.search.dto;

import java.math.BigDecimal;
import java.util.Objects;

import com.twa.flights.shared.enums.PassengerType;

public class PaxPriceDTO {

    private BigDecimal tax;
    private BigDecimal base;
    private Integer quantity;
    private BigDecimal subtotal;
    private BigDecimal total;
    private PassengerType type;
    private MarkupDTO markup;

    public PaxPriceDTO() {
    }

    public PaxPriceDTO(PaxPriceDTO other) {
        this(other.tax, other.base, other.quantity, other.subtotal, other.type,
                other.markup != null ? new MarkupDTO(other.markup.getPercentage(), other.markup.getAmount()) : null,
                other.total);
    }

    public PaxPriceDTO(BigDecimal tax, BigDecimal base, Integer quantity, BigDecimal subtotal, PassengerType type,
            MarkupDTO markup, BigDecimal total) {
        this.tax = tax;
        this.base = base;
        this.quantity = quantity;
        this.subtotal = subtotal;
        this.type = type;
        this.markup = markup;
        this.total = total;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getBase() {
        return base;
    }

    public void setBase(BigDecimal base) {
        this.base = base;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public PassengerType getType() {
        return type;
    }

    public void setType(PassengerType type) {
        this.type = type;
    }

    public MarkupDTO getMarkup() {
        return markup;
    }

    public void setMarkup(MarkupDTO markup) {
        this.markup = markup;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PaxPriceDTO that = (PaxPriceDTO) o;
        return Objects.equals(tax, that.tax) && Objects.equals(base, that.base)
                && Objects.equals(quantity, that.quantity) && Objects.equals(subtotal, that.subtotal)
                && Objects.equals(total, that.total) && type == that.type && Objects.equals(markup, that.markup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tax, base, quantity, subtotal, total, type, markup);
    }

    @Override
    public String toString() {
        return "PaxPriceDTO{" + "tax=" + tax + ", base=" + base + ", quantity=" + quantity + ", subtotal=" + subtotal
                + ", total=" + total + ", type=" + type + ", markup=" + markup + '}';
    }
}
