package com.twa.catalog.dto;

/**
 * Same JSON of the CityDTO of the monolith (code, name, timeZone and country), without the id of the table. The
 * client of the monolith reads this JSON with its own CityDTO, so the names of the fields are part of the contract.
 * The time zone is the IANA id (for example America/Argentina/Buenos_Aires), never an offset.
 */
public class CityDTO {

    private String code;
    private String name;
    private String timeZone;
    private CountryDTO country;

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
