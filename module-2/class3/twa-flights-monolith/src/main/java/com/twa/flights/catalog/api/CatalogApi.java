package com.twa.flights.catalog.api;

import java.util.Collection;
import java.util.Map;

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
     * @throws CatalogUnavailableException
     *             if the catalog can not answer (the search answers 503, it is not an unknown city)
     */
    CityDTO getCity(String code);

    /**
     * Obtain the information of several cities in one call, instead of one call by city.
     *
     * @param codes
     *            the codes of the cities, the repeated and empty ones are ignored
     * @return the cities by the code as it was requested (the same city that {@link #getCity(String)} returns for that
     *         code). The codes that do not exist are not in the map
     * @throws CatalogUnavailableException
     *             if the catalog can not answer
     */
    Map<String, CityDTO> getCities(Collection<String> codes);

}
