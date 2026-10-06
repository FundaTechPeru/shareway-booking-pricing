package com.fundatechperu.shareway.bookingpricing.booking.domain.repository;

import java.util.UUID;

public interface TripRequestRepository {
    boolean existsById(UUID requestId);
}
