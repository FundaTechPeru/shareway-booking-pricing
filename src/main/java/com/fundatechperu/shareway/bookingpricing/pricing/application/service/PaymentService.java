package com.fundatechperu.shareway.bookingpricing.pricing.application.service;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Payment;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.PaymentRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.service.PaymentGateway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class PaymentService {
    private final PaymentRepository repository; private final FareService fareService; private final PaymentGateway gateway;
    public PaymentService(PaymentRepository repository, FareService fareService, PaymentGateway gateway) {
        this.repository = repository; this.fareService = fareService; this.gateway = gateway;
    }
    @Transactional
    public Payment authorize(UUID bookingId, UUID groupId, UUID payerId) {
        var active = repository.findActiveByBookingId(bookingId);
        if (active.isPresent()) return active.get();
        var fare = fareService.findCurrentByGroupId(groupId);
        try { return repository.save(gateway.authorize(bookingId, payerId, fare.amountPerPassenger())); }
        catch (DataIntegrityViolationException e) {
            return repository.findActiveByBookingId(bookingId).orElseThrow(() -> e);
        }
    }
    @Transactional(readOnly = true)
    public Payment findById(UUID id) { return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id)); }
}
