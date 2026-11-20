package com.twa.flights.pricing.dto;

import java.math.BigDecimal;

/**
 * Price of one type of passenger that the quoting needs to calculate the markup.
 */
public class QuotePaxPrice {

    private BigDecimal subtotal;
    private BigDecimal total;

    public QuotePaxPrice() {

    }

    public QuotePaxPrice(BigDecimal subtotal, BigDecimal total) {
        this.subtotal = subtotal;
        this.total = total;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
