package com.twa.flights.pricing.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.twa.flights.pricing.dto.CommercialPolicyCarrierDTO;
import com.twa.flights.pricing.dto.CommercialPolicyDTO;
import com.twa.flights.pricing.model.CommercialPolicy;
import com.twa.flights.pricing.model.CommercialPolicyCarrier;

@Mapper(componentModel = "spring")
public interface CommercialPolicyMapper {

    @Mapping(source = "validatingCarriers", target = "carriers")
    CommercialPolicyDTO convert(CommercialPolicy source);

    CommercialPolicyCarrierDTO convert(CommercialPolicyCarrier source);
}
