package com.twa.catalog.service;

import java.text.Collator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.exception.APIException;
import com.twa.catalog.exception.CatalogServiceError;
import com.twa.catalog.mapper.CityMapper;
import com.twa.catalog.model.City;
import com.twa.catalog.repository.CityRepository;

/**
 * Same logic of the CatalogService of the monolith. The codes are normalized to upper case: the monolith finds "mia"
 * only because of the collation of MySQL, the service does not depend on it. The format of the code is not
 * validated: "MI" or "ABCD" are unknown cities, like in the monolith.
 */
@Service
@Transactional(readOnly = true)
public class CityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityService.class);

    // The same rules of the collation of MySQL (utf8mb4_0900_ai_ci): without case, accents or full width letters
    private static final Collator COLLATOR = Collator.getInstance(Locale.ROOT);

    static {
        COLLATOR.setStrength(Collator.PRIMARY);
        // The compatibility decomposition (NFKD) makes ＢＵＥ equal to BUE, like MySQL
        COLLATOR.setDecomposition(Collator.FULL_DECOMPOSITION);
    }

    private final CityRepository cityRepository;
    private final CityMapper mapper;
    private final int maxCodes;

    @Autowired
    public CityService(CityRepository cityRepository, CityMapper mapper,
            @Value("${catalog.cities.max-codes:50}") int maxCodes) {
        this.cityRepository = cityRepository;
        this.mapper = mapper;
        this.maxCodes = maxCodes;
    }

    /**
     * Obtain the information of a city.
     *
     * @param code
     *            the code of the city, in any case
     * @return the city with its country and continent
     * @throws APIException
     *             CITY_NOT_FOUND if the code does not exist
     */
    public CityDTO getCity(String code) {
        LOGGER.debug("Obtain the information for code: {}", code);
        City city = cityRepository.findByCode(normalize(code));
        if (city == null) {
            throw new APIException(CatalogServiceError.CITY_NOT_FOUND, List.of(code));
        }
        return mapper.convert(city);
    }

    /**
     * Obtain the information of several cities in one query.
     *
     * @param codes
     *            the codes separated by comma, in any case. The repeated and empty codes are ignored
     * @return the cities that exist, in the order of the request and without repeated. The unknown codes are not in
     *         the list (it is not an error). Each code finds the same city than {@link #getCity(String)}: MySQL compares
     *         without distinguishing case or accents and without trimming the spaces
     * @throws APIException
     *             INVALID_CODES if there is no code, TOO_MANY_CODES if there are more than the maximum
     */
    public List<CityDTO> getCities(String codes) {
        LOGGER.debug("Obtain the information for codes: {}", codes);
        Set<String> requested = codes == null ? Set.of()
                : Arrays.stream(codes.split(",")).filter(code -> !code.isBlank()).map(this::normalize)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requested.isEmpty()) {
            throw new APIException(CatalogServiceError.INVALID_CODES, List.of());
        }
        if (requested.size() > maxCodes) {
            throw new APIException(CatalogServiceError.TOO_MANY_CODES,
                    List.of("The maximum is " + maxCodes + " codes and the request has " + requested.size()));
        }

        List<City> found = cityRepository.findByCodeIn(requested);

        Map<String, City> cities = new LinkedHashMap<>();
        for (String code : requested) {
            found.stream().filter(city -> COLLATOR.equals(code, city.getCode())).findFirst()
                    .ifPresent(city -> cities.putIfAbsent(city.getCode(), city));
        }
        return cities.values().stream().map(mapper::convert).collect(Collectors.toList());
    }

    private String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }
}
