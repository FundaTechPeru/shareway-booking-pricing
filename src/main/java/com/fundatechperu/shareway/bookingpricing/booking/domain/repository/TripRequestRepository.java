package com.fundatechperu.shareway.bookingpricing.booking.domain.repository;

import java.util.UUID;
import java.util.Optional;

public interface TripRequestRepository {
    boolean existsById(UUID requestId);

    Optional<UUID> findPassengerIdById(UUID requestId);
}
