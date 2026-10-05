package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence;

import com.fundatechperu.shareway.bookingpricing.booking.application.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.application.repository.BookingRepository;
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
        Booking booking = new Booking(entity.getRequestId(), entity.getGroupId());
        booking.setBookingId(entity.getBookingId());
        booking.setStatus(entity.getStatus());
        booking.setPinHash(entity.getPinHash());
        booking.setQrTokenHash(entity.getQrTokenHash());
        booking.setVersion(entity.getVersion());
        booking.setCreatedAt(entity.getCreatedAt());
        return booking;
    }
}
