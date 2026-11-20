package com.twa.flights.catalog.dto;

import com.twa.flights.shared.dto.BaseDTO;

public class CityDTO extends BaseDTO {

    private String timeZone;
    private CountryDTO country;

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public CountryDTO getCountry() {
        return country;
    }

    public void setCountry(CountryDTO country) {
        this.country = country;
    }
}
