package com.fundatechperu.shareway.bookingpricing.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.TripRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public class TripRequestRepositoryAdapter implements TripRequestRepository {
    private final SpringDataTripRequestRepository repository;

    public TripRequestRepositoryAdapter(SpringDataTripRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsById(UUID requestId) {
        return repository.existsById(requestId);
    }

    @Override
    public Optional<UUID> findPassengerIdById(UUID requestId) {
        return repository.findPassengerIdById(requestId);
    }
}
