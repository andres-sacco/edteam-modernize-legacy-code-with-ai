package com.twa.flights.dto;

import java.math.BigDecimal;


public class UpdatedPaxPriceDTO {

    private MarkupDTO markup;
    private BigDecimal total;

    public MarkupDTO getMarkup() {
        return markup;
    }

    public void setMarkup(MarkupDTO markup) {
        this.markup = markup;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
