package com.twa.flights.catalog.mapper;

import com.twa.flights.catalog.dto.CityDTO;
import com.twa.flights.catalog.model.City;
import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;

@Mapper(componentModel = "spring")
public interface CityMapper extends Converter<City, CityDTO> {

    @Override
    CityDTO convert(City source);

}