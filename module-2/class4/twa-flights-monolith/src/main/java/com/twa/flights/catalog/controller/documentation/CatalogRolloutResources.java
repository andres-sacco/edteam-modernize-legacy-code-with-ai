package com.twa.flights.catalog.controller.documentation;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.twa.flights.catalog.dto.CatalogRolloutDTO;
import com.twa.flights.catalog.dto.CatalogRolloutRequestDTO;
import com.twa.flights.shared.configuration.SwaggerConfiguration;
import com.twa.flights.shared.dto.ErrorDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Rollout of the catalog", description = "Percentage of the calls to the catalog that go to the catalog service")
@SecurityRequirement(name = SwaggerConfiguration.CATALOG_ROLLOUT_API_KEY)
@ApiResponse(responseCode = "401", description = "The API key is missing or it is invalid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class)))
public interface CatalogRolloutResources {

    String ROLLOUT_EXAMPLE = "{\"remotePercentage\":5,\"updatedBy\":\"equipo-busqueda\",\"reason\":\"Stage 4a: canary 5 %\"}";
    String ROLLBACK_EXAMPLE = "{\"remotePercentage\":0,\"updatedBy\":\"guardia\",\"reason\":\"Rollback: CatalogUnavailable503\"}";

    @Operation(description = "Get the flag of the rollout and the percentage that this instance applies", responses = {
            @ApiResponse(responseCode = "200", description = "The flag of the rollout") })
    @GetMapping
    ResponseEntity<CatalogRolloutDTO> getRollout();

    @Operation(description = "Change the remote percentage for all the instances, without a deploy. They apply it in at most catalog.rollout.refresh (5 s). 0 is the rollback, null goes back to catalog.mode", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "Canary", value = ROLLOUT_EXAMPLE),
            @ExampleObject(name = "Rollback", value = ROLLBACK_EXAMPLE) })), responses = {
                    @ApiResponse(responseCode = "200", description = "The flag changed"),
                    @ApiResponse(responseCode = "400", description = "The change is not valid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @PutMapping
    ResponseEntity<CatalogRolloutDTO> changeRollout(@RequestBody CatalogRolloutRequestDTO request);
}
