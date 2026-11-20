package com.twa.flights.catalog.dto;

import com.twa.flights.shared.dto.BaseDTO;

public class CountryDTO extends BaseDTO {

    private ContinentDTO continent;

    public ContinentDTO getContinent() {
        return continent;
    }

    public void setContinent(ContinentDTO continent) {
        this.continent = continent;
    }
}
