package com.fundatechperu.shareway.bookingpricing.pricing.domain.repository;

import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Penalty;
import java.util.List;
import java.util.UUID;
public interface PenaltyRepository { Penalty save(Penalty penalty); List<Penalty> findByBookingId(UUID bookingId); }
