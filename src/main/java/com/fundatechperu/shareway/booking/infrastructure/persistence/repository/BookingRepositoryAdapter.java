package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.domain.model.Booking;
import com.fundatechperu.shareway.booking.domain.model.BookingStatus;
import com.fundatechperu.shareway.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.BookingJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingRepositoryAdapter implements BookingRepository {

    private final SpringDataBookingRepository repository;

    public BookingRepositoryAdapter(SpringDataBookingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Booking save(Booking booking) {
        return toDomain(repository.save(toEntity(booking)));
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return repository.findById(bookingId).map(this::toDomain);
    }

    @Override
    public Optional<Booking> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public boolean existsByRequestId(UUID requestId) {
        return repository.existsByRequestId(requestId);
    }

    private BookingJpaEntity toEntity(Booking booking) {
        BookingJpaEntity entity = new BookingJpaEntity();
        entity.setBookingId(booking.getBookingId());
        entity.setRequestId(booking.getRequestId());
        entity.setGroupId(booking.getGroupId());
        entity.setPassengerId(booking.getPassengerId());
        entity.setStatus(booking.getStatus().name().toLowerCase(Locale.ROOT));
        entity.setPinHash(booking.getPinHash());
        entity.setQrTokenHash(booking.getQrTokenHash());
        entity.setIdempotencyKey(booking.getIdempotencyKey());
        entity.setVersion(booking.getVersion());
        entity.setCreatedAt(booking.getCreatedAt());
        return entity;
    }

    private Booking toDomain(BookingJpaEntity entity) {
        return Booking.restore(entity.getBookingId(), entity.getRequestId(), entity.getGroupId(),
                entity.getPassengerId(), entity.getIdempotencyKey(),
                BookingStatus.valueOf(entity.getStatus().toUpperCase(Locale.ROOT)),
                entity.getPinHash(), entity.getQrTokenHash(), entity.getVersion(), entity.getCreatedAt());
    }
}
