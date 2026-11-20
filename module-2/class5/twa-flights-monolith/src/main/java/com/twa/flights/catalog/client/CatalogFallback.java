package com.twa.flights.catalog.client;

/**
 * The path of the calls that the flag of the rollout does not send to the catalog service (property catalog.fallback,
 * M2 C5): the destination of a rollback to 0 % remote.
 */
public enum CatalogFallback {

    /** The tables of the monolith (CatalogService). Default value. */
    LOCAL,

    /** The replica city_replica, kept by the events of the catalog service (CityReplicaCatalogService). */
    REPLICA

}
