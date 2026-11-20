package com.twa.flights.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.twa.flights.dto.pricing.CommercialPolicyCarrierDTO;
import com.twa.flights.dto.pricing.CommercialPolicyDTO;
import com.twa.flights.model.CommercialPolicy;
import com.twa.flights.model.CommercialPolicyCarrier;

@Mapper(componentModel = "spring")
public interface CommercialPolicyMapper {

    @Mapping(source = "validatingCarriers", target = "carriers")
    CommercialPolicyDTO convert(CommercialPolicy source);

    CommercialPolicyCarrierDTO convert(CommercialPolicyCarrier source);
}
