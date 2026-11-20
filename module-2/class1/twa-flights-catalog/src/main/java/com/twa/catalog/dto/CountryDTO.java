package com.twa.catalog.dto;

/**
 * Same JSON of the CountryDTO of the monolith (code, name and continent), without the id of the table.
 */
public class CountryDTO {

    private String code;
    private String name;
    private ContinentDTO continent;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ContinentDTO getContinent() {
        return continent;
    }

    public void setContinent(ContinentDTO continent) {
        this.continent = continent;
    }
}
