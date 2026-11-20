package com.twa.flights.dto.pricing;

import java.time.LocalDateTime;
import java.util.List;

public class CommercialPolicyRequestDTO {

    private String name;
    private Integer priority;
    private Boolean active;
    private LocalDateTime startDate;
    private LocalDateTime expireDate;
    private List<CommercialPolicyCarrierDTO> carriers;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDateTime expireDate) {
        this.expireDate = expireDate;
    }

    public List<CommercialPolicyCarrierDTO> getCarriers() {
        return carriers;
    }

    public void setCarriers(List<CommercialPolicyCarrierDTO> carriers) {
        this.carriers = carriers;
    }
}
