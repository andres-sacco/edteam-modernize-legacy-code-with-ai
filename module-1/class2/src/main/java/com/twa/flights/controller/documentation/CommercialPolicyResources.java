package com.twa.flights.controller.documentation;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.twa.flights.configuration.SwaggerConfiguration;
import com.twa.flights.dto.ErrorDTO;
import com.twa.flights.dto.pricing.CommercialPolicyCarrierDTO;
import com.twa.flights.dto.pricing.CommercialPolicyDTO;
import com.twa.flights.dto.pricing.CommercialPolicyRequestDTO;
import com.twa.flights.dto.pricing.CommercialPolicyStatusDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Commercial policies", description = "Administration of the markup that is applied to each carrier")
@SecurityRequirement(name = SwaggerConfiguration.PRICING_API_KEY)
@ApiResponse(responseCode = "401", description = "The API key is missing or it is invalid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class)))
public interface CommercialPolicyResources {

    String POLICY_EXAMPLE = "{\"name\":\"Summer 2027\",\"priority\":20,\"active\":true,\"startDate\":\"2026-12-01T00:00:00\",\"expireDate\":\"2027-03-01T00:00:00\",\"carriers\":[{\"carrier\":\"AR\",\"percentage\":3},{\"carrier\":\"LA\",\"percentage\":2}]}";

    @Operation(description = "Get all the commercial policies ordered by priority", responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policies") })
    @GetMapping
    ResponseEntity<List<CommercialPolicyDTO>> getPolicies(
            @Parameter(description = "Only the active (true) or inactive (false) policies") @RequestParam(required = false) Boolean active,
            @Parameter(description = "Only the policies that include this carrier (e.g. AR)") @RequestParam(required = false) String carrier);

    @Operation(description = "Get a commercial policy", responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policy"),
            @ApiResponse(responseCode = "404", description = "The policy does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @GetMapping("/{id}")
    ResponseEntity<CommercialPolicyDTO> getPolicy(@PathVariable("id") Long id);

    @Operation(description = "Create a commercial policy, by default it is active", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Policy", value = POLICY_EXAMPLE))), responses = {
            @ApiResponse(responseCode = "201", description = "The commercial policy created"),
            @ApiResponse(responseCode = "400", description = "The information of the policy is not valid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @PostMapping
    ResponseEntity<CommercialPolicyDTO> create(@RequestBody CommercialPolicyRequestDTO request);

    @Operation(description = "Replace all the information of a commercial policy, including the carriers", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Policy", value = POLICY_EXAMPLE))), responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policy updated"),
            @ApiResponse(responseCode = "400", description = "The information of the policy is not valid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))),
            @ApiResponse(responseCode = "404", description = "The policy does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @PutMapping("/{id}")
    ResponseEntity<CommercialPolicyDTO> update(@PathVariable("id") Long id,
            @RequestBody CommercialPolicyRequestDTO request);

    @Operation(description = "Activate or deactivate a commercial policy", responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policy updated"),
            @ApiResponse(responseCode = "404", description = "The policy does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @PatchMapping("/{id}/status")
    ResponseEntity<CommercialPolicyDTO> changeStatus(@PathVariable("id") Long id,
            @RequestBody CommercialPolicyStatusDTO request);

    @Operation(description = "Delete a commercial policy and all its carriers", responses = {
            @ApiResponse(responseCode = "204", description = "The commercial policy was deleted"),
            @ApiResponse(responseCode = "404", description = "The policy does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable("id") Long id);

    @Operation(description = "Add a carrier to a commercial policy or change its percentage", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(name = "Carrier", value = "{\"percentage\":4}"))), responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policy updated"),
            @ApiResponse(responseCode = "400", description = "The percentage or the carrier are not valid", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))),
            @ApiResponse(responseCode = "404", description = "The policy does not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @PutMapping("/{id}/carriers/{carrier}")
    ResponseEntity<CommercialPolicyDTO> saveCarrier(@PathVariable("id") Long id,
            @PathVariable("carrier") String carrier, @RequestBody CommercialPolicyCarrierDTO request);

    @Operation(description = "Remove a carrier of a commercial policy, the policy must have at least one carrier", responses = {
            @ApiResponse(responseCode = "200", description = "The commercial policy updated"),
            @ApiResponse(responseCode = "400", description = "It is the last carrier of the policy", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))),
            @ApiResponse(responseCode = "404", description = "The policy or the carrier do not exist", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorDTO.class))) })
    @DeleteMapping("/{id}/carriers/{carrier}")
    ResponseEntity<CommercialPolicyDTO> deleteCarrier(@PathVariable("id") Long id,
            @PathVariable("carrier") String carrier);
}
