package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.entity.BookingJpaEntity;
import org.springframework.stereotype.Repository;

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
        BookingJpaEntity entity = toEntity(booking);
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Booking> findById(UUID bookingId) {
        return repository.findById(bookingId).map(this::toDomain);
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
        entity.setStatus(booking.getStatus());
        entity.setPinHash(booking.getPinHash());
        entity.setQrTokenHash(booking.getQrTokenHash());
        entity.setVersion(booking.getVersion());
        entity.setCreatedAt(booking.getCreatedAt());
        return entity;
    }

    private Booking toDomain(BookingJpaEntity entity) {
        return Booking.restore(entity.getBookingId(), entity.getRequestId(), entity.getGroupId(),
                entity.getStatus(), entity.getPinHash(), entity.getQrTokenHash(),
                entity.getVersion(), entity.getCreatedAt());
    }
}
