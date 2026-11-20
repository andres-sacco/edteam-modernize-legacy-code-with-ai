package com.twa.catalog.dto;

/**
 * Same JSON of the ContinentDTO of the monolith (code and name), without the id of the table.
 */
public class ContinentDTO {

    private String code;
    private String name;

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
}
