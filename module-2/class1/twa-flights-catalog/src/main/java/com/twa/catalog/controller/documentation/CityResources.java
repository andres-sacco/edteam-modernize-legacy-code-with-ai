package com.twa.catalog.controller.documentation;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.twa.catalog.dto.CityDTO;
import com.twa.catalog.dto.ErrorDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cities", description = "Operations about the catalog of cities, with their country and continent")
public interface CityResources {

    @Operation(description = "Get one city by its code", responses = {
            @ApiResponse(responseCode = "200", description = "The city with its time zone, country and continent", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CityDTO.class))),
            @ApiResponse(responseCode = "404", description = "The city does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))),
            @ApiResponse(responseCode = "503", description = "The database of the catalog is not available", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) }, tags = {
                    "Cities" }, parameters = {
                            @Parameter(in = ParameterIn.PATH, name = "code", description = "The code of the city, in any case. The format is not validated: an invalid code is an unknown city", required = true, example = "BUE") })
    @GetMapping(value = "/{code}")
    ResponseEntity<CityDTO> getCity(@PathVariable("code") String code);

    @Operation(description = "Get several cities by their codes in one request", responses = {
            @ApiResponse(responseCode = "200", description = "The cities that exist, in the order of the request and without repeated. The unknown codes are not in the list", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = CityDTO.class)))),
            @ApiResponse(responseCode = "400", description = "Without codes or with more codes than the allowed (50 by default)", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))),
            @ApiResponse(responseCode = "503", description = "The database of the catalog is not available", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) }, tags = {
                    "Cities" }, parameters = {
                            @Parameter(in = ParameterIn.QUERY, name = "codes", description = "The codes of the cities comma separated, in any case. (e.g. BUE,MIA)", required = true, example = "BUE,MIA") })
    @GetMapping
    ResponseEntity<List<CityDTO>> getCities(@RequestParam(name = "codes", required = false) String codes);

}
