package com.twa.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.Immutable;

/**
 * Table country of the database of the monolith. Without cascade: the service never writes the continent.
 */
@Entity
@Immutable
public class Country {

    @Id
    private Long id;

    @Column(length = 4, nullable = false)
    private String code;

    @Column(length = 50, nullable = false)
    private String name;

    @JoinColumn(name = "continent_id")
    @ManyToOne
    private Continent continent;

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

    public Continent getContinent() {
        return continent;
    }

    public void setContinent(Continent continent) {
        this.continent = continent;
    }
}
