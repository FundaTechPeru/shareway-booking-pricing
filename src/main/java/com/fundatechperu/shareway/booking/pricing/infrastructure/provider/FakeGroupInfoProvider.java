package com.fundatechperu.shareway.booking.pricing.infrastructure.provider;

import com.fundatechperu.shareway.booking.pricing.domain.service.GroupInfoProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fake {@link GroupInfoProvider} used until Driver Operations & Routing exposes a real
 * route-distance endpoint. Every group is quoted with a fixed 12 km distance.
 */
@Component
public class FakeGroupInfoProvider implements GroupInfoProvider {

    static final BigDecimal FAKE_DISTANCE_KM = new BigDecimal("12");

    @Override
    public BigDecimal distanceKm(UUID groupId) {
        return FAKE_DISTANCE_KM;
    }
}
