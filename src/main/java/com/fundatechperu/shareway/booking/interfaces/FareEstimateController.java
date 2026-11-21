package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse;
import com.fundatechperu.shareway.booking.pricing.application.dto.response.FareEstimateResponse;
import com.fundatechperu.shareway.booking.pricing.application.service.FareEstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fares/estimate")
@Tag(name = "Fares", description = "Fare estimation for shared trip groups. Read-only: nothing is persisted.")
public class FareEstimateController {

    private final FareEstimateService fareEstimateService;

    public FareEstimateController(FareEstimateService fareEstimateService) {
        this.fareEstimateService = fareEstimateService;
    }

    @Operation(
            summary = "Estimate the fare of a trip group",
            description = "Prices the group with the fare rule that is valid today (highest version whose "
                    + "valid_from/valid_to range includes today): "
                    + "amount = ((base_amount + amount_per_km * distance_km) / passengers) + platform fee, "
                    + "where passengers = capacity - available_seats of the group (minimum 1) and the distance "
                    + "comes from Driver Operations & Routing (currently a fake 12 km). "
                    + "The estimate does not create a fares row.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fare estimate computed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FareEstimateResponse.class),
                            examples = @ExampleObject(value = "{\"amount\":21.34,\"currency\":\"PEN\",\"ruleVersion\":1}"))),
            @ApiResponse(responseCode = "400", description = "groupId is missing or is not a UUID",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Type mismatch for request parameter 'groupId'\",\"path\":\"/api/v1/fares/estimate\"}"))),
            @ApiResponse(responseCode = "404", description = "Group not found in seat inventories, or no fare rule is valid today",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":404,\"error\":\"Not Found\",\"message\":\"No fare rule valid on 2026-11-20\",\"path\":\"/api/v1/fares/estimate\"}")))
    })
    @GetMapping
    public FareEstimateResponse estimate(
            @Parameter(description = "Trip group identifier (seat_inventories.group_id)", required = true,
                    example = "11111111-1111-1111-1111-111111111111")
            @RequestParam UUID groupId) {
        return fareEstimateService.estimate(groupId);
    }
}
