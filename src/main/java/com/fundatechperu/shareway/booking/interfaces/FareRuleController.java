package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.infrastructure.OpenApiConfiguration;
import com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse;
import com.fundatechperu.shareway.booking.pricing.application.dto.response.FareRuleResponse;
import com.fundatechperu.shareway.booking.pricing.application.service.FareRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the fare-rule catalogue. The contract of the report exposes only GET:
 * rules are seeded by Flyway (V3 baseline) and are not created or modified through this API.
 */
@RestController
@SecurityRequirement(name = OpenApiConfiguration.BEARER_SCHEME)
@RequestMapping("/api/v1/fare-rules")
@Tag(name = "Fare Rules", description = "Fare-rule catalogue used to price trips. Read-only: GET only.")
public class FareRuleController {

    private static final String RULE_EXAMPLE =
            "{\"ruleId\":1,\"version\":1,\"baseAmount\":5.00,\"amountPerKm\":1.20,"
                    + "\"platformFeePct\":10.00,\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\"}";

    private final FareRuleService service;

    public FareRuleController(FareRuleService service) {
        this.service = service;
    }

    @Operation(summary = "List fare rules",
            description = "Returns every fare rule known to the service, ordered by id.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Rules listed",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = FareRuleResponse.class),
                    examples = @ExampleObject(value = "[" + RULE_EXAMPLE + "]"))))
    @GetMapping
    public List<FareRuleResponse> find() {
        return service.findAll().stream().map(FareRuleResponse::from).toList();
    }

    @Operation(summary = "Get a fare rule by id",
            description = "Returns one rule with its base amount, amount per km, platform fee and validity range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FareRuleResponse.class),
                            examples = @ExampleObject(value = RULE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "id is not a number",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"Type mismatch for path variable 'id'\",\"path\":\"/api/v1/fare-rules/abc\"}"))),
            @ApiResponse(responseCode = "404", description = "Unknown rule id",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-20T10:15:30\",\"status\":404,\"error\":\"Not Found\",\"message\":\"Fare rule not found: 99\",\"path\":\"/api/v1/fare-rules/99\"}")))
    })
    @GetMapping("/{id}")
    public ResponseEntity<FareRuleResponse> findById(
            @Parameter(description = "Fare rule identifier (surrogate Long key)", required = true, example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(FareRuleResponse.from(service.findById(id)));
    }
}
