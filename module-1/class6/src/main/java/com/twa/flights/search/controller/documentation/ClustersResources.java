package com.twa.flights.search.controller.documentation;

import com.twa.flights.search.dto.ClusterSearchDTO;
import com.twa.flights.shared.dto.ErrorDTO;
import com.twa.flights.search.dto.ItineraryDTO;
import com.twa.flights.search.dto.request.ClustersAvailabilityRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Clusters", description = "Operations about obtain itineraries with price")
public interface ClustersResources {

    @Operation(description = "Get flights availability", responses = {
            @ApiResponse(responseCode = "200", description = "The itineraries with the conditions of search", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ClusterSearchDTO.class))) }, tags = {
                    "Clusters" }, parameters = {
                            @Parameter(in = ParameterIn.QUERY, name = "from", description = "Every segment's origin comma separated. (e.g. BUE,MIA)", required = true, example = "BUE,MIA"),
                            @Parameter(in = ParameterIn.QUERY, name = "to", description = "Every segment's destination comma separated. (e.g. BUE,MIA)", required = true, example = "MIA,BUE"),
                            @Parameter(in = ParameterIn.QUERY, name = "departure", description = "Every segment's departure date comma separated. (e.g. 2026-12-10,2026-12-20)", required = true, example = "2026-12-10,2026-12-20"),
                            @Parameter(in = ParameterIn.QUERY, name = "adults", description = "Adults quantity. (e.g. 1)", required = true, example = "1"),
                            @Parameter(in = ParameterIn.QUERY, name = "children", description = "Children quantity. (e.g. 1))", required = true, example = "1"),
                            @Parameter(in = ParameterIn.QUERY, name = "infants", description = "Infants quantity. (e.g. 1)", required = true, example = "1"),
                            @Parameter(in = ParameterIn.QUERY, name = "amount", description = "The number of the itineraries to return. Without it, all the itineraries of the providers are returned", required = false, example = "10"),
                            @Parameter(in = ParameterIn.QUERY, name = "id", description = "The id of a previous search to obtain another page of results", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "offset", description = "The number of the page to return when the id is present", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "sortBy", description = "The criteria to sort the itineraries: PRICE (total price), CARRIER (airline code) or DURATION (total flight time). By default PRICE", required = false, example = "PRICE"),
                            @Parameter(in = ParameterIn.QUERY, name = "sortOrder", description = "The order of the sort: ASC or DESC. By default ASC", required = false, example = "ASC"),
                            @Parameter(in = ParameterIn.QUERY, name = "cabin", description = "The cabin class: ECONOMY, PREMIUM_ECONOMY or BUSINESS. By default ECONOMY", required = false, example = "ECONOMY"),
                            @Parameter(in = ParameterIn.QUERY, name = "maxStops", description = "Maximum number of stops in each segment (0 = only direct flights)", required = false, example = "0"),
                            @Parameter(in = ParameterIn.QUERY, name = "minPrice", description = "Minimum total price of the itinerary", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "maxPrice", description = "Maximum total price of the itinerary", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "carriers", description = "Only these carriers comma separated. (e.g. AR,AA)", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "excludedCarriers", description = "Exclude these carriers comma separated. (e.g. DL)", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "departureTime", description = "Departure time of the outbound flight: NIGHT (00-06), MORNING (06-12), AFTERNOON (12-18) or EVENING (18-24)", required = false),
                            @Parameter(in = ParameterIn.QUERY, name = "checkedBaggage", description = "true to return only itineraries with checked baggage included, false only without it", required = false)

    })
    @GetMapping(value = "/itineraries")
    ResponseEntity<ClusterSearchDTO> availability(@ParameterObject ClustersAvailabilityRequestDTO availabilityRequest);

    @Operation(description = "Get one itinerary of a previous search", responses = {
            @ApiResponse(responseCode = "200", description = "The itinerary with the final price", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ItineraryDTO.class))),
            @ApiResponse(responseCode = "404", description = "The search does not exist, it is expired or it does not contain the itinerary", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) }, tags = {
                    "Clusters" }, parameters = {
                            @Parameter(in = ParameterIn.PATH, name = "id", description = "The id of the search returned by the availability", required = true),
                            @Parameter(in = ParameterIn.PATH, name = "itineraryId", description = "The id of the itinerary inside the search", required = true) })
    @GetMapping(value = "/{id}/itineraries/{itineraryId}")
    ResponseEntity<ItineraryDTO> getItinerary(@PathVariable("id") String id,
            @PathVariable("itineraryId") String itineraryId);

}
