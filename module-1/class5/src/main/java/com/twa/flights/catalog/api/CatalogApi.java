package com.twa.flights.catalog.api;

import com.twa.flights.catalog.dto.CityDTO;

/**
 * Public interface of the catalog module (BC4). The other modules must use this interface instead of the classes of
 * the packages service, repository or model of the catalog.
 */
public interface CatalogApi {

    /**
     * Obtain the information of a city.
     *
     * @param code
     *            the code of the city
     * @return the city with its country and continent, or null if the code does not exist
     */
    CityDTO getCity(String code);

}
