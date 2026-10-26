package com.fundatechperu.shareway.booking.interfaces;

import com.fundatechperu.shareway.booking.pricing.application.dto.response.FareRuleResponse;
import com.fundatechperu.shareway.booking.pricing.application.service.FareRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the fare-rule catalogue. The contract of the report exposes only GET:
 * rules are seeded by Flyway (V2) and are not created or modified through this API.
 */
@RestController
@RequestMapping("/fare-rules")
@Tag(name = "Fare Rules", description = "Fare-rule catalogue used to price trips. Read-only: GET only.")
public class FareRuleController {

    private final FareRuleService service;

    public FareRuleController(FareRuleService service) {
        this.service = service;
    }

    @Operation(summary = "List fare rules",
            description = "Returns every fare rule known to the service, ordered by id.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Rules listed",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = FareRuleResponse.class))))
    @GetMapping
    public List<FareRuleResponse> find() {
        return service.findAll().stream().map(FareRuleResponse::from).toList();
    }

    @Operation(summary = "Get a fare rule by id",
            description = "Returns one rule with its base amount, amount per km, platform fee and validity range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule found",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = FareRuleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown rule id",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(
                                    implementation = com.fundatechperu.shareway.booking.infrastructure.web.ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<FareRuleResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(FareRuleResponse.from(service.findById(id)));
    }
}
