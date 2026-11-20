package com.twa.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import org.hibernate.annotations.Immutable;

/**
 * Table continent of the database of the monolith. The service only reads it: the monolith owns the schema and the
 * data until the service has its own database.
 */
@Entity
@Immutable
public class Continent {

    @Id
    private Long id;

    @Column(length = 4, nullable = false)
    private String code;

    @Column(length = 50, nullable = false)
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
