package com.fundatechperu.shareway.booking.pricing.domain.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port to Driver Operations & Routing: supplies the data this service does not own.
 * Today it only exposes the route distance used by {@link FarePolicy}; the passenger count
 * is local (seat_inventories.capacity - seat_inventories.available_seats).
 */
public interface GroupInfoProvider {

    /**
     * Route distance in kilometres of a trip group.
     */
    BigDecimal distanceKm(UUID groupId);
}
