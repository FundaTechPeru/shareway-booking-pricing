package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.*;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.PaymentRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepositoryAdapter implements PaymentRepository {
    private final SpringDataPaymentRepository repository;
    public PaymentRepositoryAdapter(SpringDataPaymentRepository repository) { this.repository = repository; }
    @Override public Payment save(Payment payment) { return toDomain(repository.save(toEntity(payment))); }
    @Override public Optional<Payment> findById(UUID id) { return repository.findById(id).map(this::toDomain); }
    @Override public Optional<Payment> findByBookingId(UUID bookingId) { return repository.findByBookingId(bookingId).map(this::toDomain); }
    @Override public Optional<Payment> findActiveByBookingId(UUID bookingId) {
        return repository.findByBookingIdAndStatusIn(bookingId, List.of(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURED))
                .map(this::toDomain);
    }
    private PaymentJpaEntity toEntity(Payment p) {
        PaymentJpaEntity e = new PaymentJpaEntity();
        e.setPaymentId(p.getPaymentId()); e.setBookingId(p.getBookingId()); e.setPayerId(p.getPayerId());
        e.setAmount(p.getAmount()); e.setCurrency(p.getCurrency()); e.setStatus(p.getStatus());
        e.setProviderRef(p.getProviderRef()); e.setAuthorizedAt(p.getAuthorizedAt()); e.setCapturedAt(p.getCapturedAt());
        e.setVersion(p.getVersion()); return e;
    }
    private Payment toDomain(PaymentJpaEntity e) {
        return Payment.restore(e.getPaymentId(), e.getBookingId(), e.getPayerId(), e.getAmount(), e.getCurrency(),
                e.getStatus(), e.getProviderRef(), e.getAuthorizedAt(), e.getCapturedAt(), e.getVersion());
    }
}
