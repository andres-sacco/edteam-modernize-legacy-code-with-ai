package com.twa.catalog.mapper;

import org.mapstruct.Mapper;
import org.springframework.core.convert.converter.Converter;

import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.model.City;

@Mapper(componentModel = "spring")
public interface CityMapper extends Converter<City, CityDTO> {

    @Override
    CityDTO convert(City source);

}
