package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.domain.model.BookingSaga;
import com.fundatechperu.shareway.booking.domain.repository.BookingSagaRepository;
import com.fundatechperu.shareway.booking.infrastructure.persistence.entity.BookingSagaJpaEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BookingSagaRepositoryAdapter implements BookingSagaRepository {

    private final SpringDataBookingSagaRepository repository;

    public BookingSagaRepositoryAdapter(SpringDataBookingSagaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BookingSaga> findById(UUID sagaId) {
        return repository.findById(sagaId).map(this::toDomain);
    }

    @Override
    public Optional<BookingSaga> findByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId).map(this::toDomain);
    }

    @Override
    public BookingSaga save(BookingSaga saga) {
        return toDomain(repository.save(toEntity(saga)));
    }

    @Override
    public List<BookingSaga> findByStatusAndDeadlineAtBefore(BookingSaga.Status status, LocalDateTime deadlineAt) {
        return repository.findByStatusAndDeadlineAtBefore(status.name().toLowerCase(Locale.ROOT), deadlineAt)
                .stream().map(this::toDomain).toList();
    }

    private BookingSagaJpaEntity toEntity(BookingSaga saga) {
        BookingSagaJpaEntity entity = new BookingSagaJpaEntity();
        entity.setSagaId(saga.getSagaId());
        entity.setBookingId(saga.getBookingId());
        entity.setCurrentStep(saga.getCurrentStep());
        entity.setStatus(saga.getStatus().name().toLowerCase(Locale.ROOT));
        entity.setDeadlineAt(saga.getDeadlineAt());
        entity.setUpdatedAt(saga.getUpdatedAt());
        return entity;
    }

    private BookingSaga toDomain(BookingSagaJpaEntity entity) {
        return BookingSaga.restore(entity.getSagaId(), entity.getBookingId(), entity.getCurrentStep(),
                BookingSaga.Status.valueOf(entity.getStatus().toUpperCase(Locale.ROOT)),
                entity.getDeadlineAt(), entity.getUpdatedAt());
    }
}
