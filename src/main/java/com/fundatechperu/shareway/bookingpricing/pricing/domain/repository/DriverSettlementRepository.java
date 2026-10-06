package com.fundatechperu.shareway.bookingpricing.pricing.domain.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.DriverSettlement;
import java.util.List;
import java.util.UUID;
public interface DriverSettlementRepository { DriverSettlement save(DriverSettlement settlement); List<DriverSettlement> findByTripId(UUID tripId); }
