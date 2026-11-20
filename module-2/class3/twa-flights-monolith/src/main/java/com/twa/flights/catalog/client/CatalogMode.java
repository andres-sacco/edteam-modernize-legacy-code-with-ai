package com.twa.flights.catalog.client;

/**
 * Which implementation of the catalog answers (property catalog.mode).
 */
public enum CatalogMode {

    /** The tables of the monolith (CatalogService). Default value. */
    LOCAL,

    /** The catalog service (CatalogClient). */
    REMOTE

}
