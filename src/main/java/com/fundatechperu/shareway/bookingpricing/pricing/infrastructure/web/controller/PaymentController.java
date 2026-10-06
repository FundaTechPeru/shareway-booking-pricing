package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.web.controller;
import com.fundatechperu.shareway.bookingpricing.pricing.application.dto.request.CreatePaymentRequest;
import com.fundatechperu.shareway.bookingpricing.pricing.application.dto.response.PaymentResponse;
import com.fundatechperu.shareway.bookingpricing.pricing.application.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI; import java.util.UUID;
@RestController @RequestMapping("/api/v1/pricing/payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request, Authentication authentication) {
        UUID payerId = UUID.fromString(((Jwt) authentication.getPrincipal()).getSubject());
        PaymentResponse response = PaymentResponse.from(service.authorize(request.bookingId(), request.groupId(), payerId));
        return ResponseEntity.created(URI.create("/api/v1/pricing/payments/" + response.paymentId())).body(response);
    }
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> find(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(PaymentResponse.from(service.findById(paymentId)));
    }
}
