package com.twa.flights.catalog.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.twa.flights.shared.model.Base;

@Entity
public class Country extends Base {

    @JoinColumn(name = "continent_id")
    @ManyToOne(cascade = CascadeType.ALL)
    private Continent continent;

    public Continent getContinent() {
        return continent;
    }

    public void setContinent(Continent continent) {
        this.continent = continent;
    }
}
