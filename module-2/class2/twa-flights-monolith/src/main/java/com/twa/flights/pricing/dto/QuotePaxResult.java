package com.twa.flights.pricing.dto;

import java.math.BigDecimal;

/**
 * Markup of one type of passenger. The three values are null when the policy does not have the carrier of the
 * request.
 */
public class QuotePaxResult {

    private BigDecimal percentage;
    private BigDecimal markup;
    private BigDecimal total;

    /**
     * Percentage of the carrier in the policy.
     */
    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    /**
     * Amount of the markup.
     */
    public BigDecimal getMarkup() {
        return markup;
    }

    public void setMarkup(BigDecimal markup) {
        this.markup = markup;
    }

    /**
     * Total of the passenger with the markup.
     */
    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
