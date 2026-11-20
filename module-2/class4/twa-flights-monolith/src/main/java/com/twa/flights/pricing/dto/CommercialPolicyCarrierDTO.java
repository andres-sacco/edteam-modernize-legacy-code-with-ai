package com.twa.flights.pricing.dto;

import java.math.BigDecimal;

public class CommercialPolicyCarrierDTO {

    private String carrier;
    private BigDecimal percentage;

    public CommercialPolicyCarrierDTO() {
    }

    public CommercialPolicyCarrierDTO(String carrier, BigDecimal percentage) {
        this.carrier = carrier;
        this.percentage = percentage;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }
}
