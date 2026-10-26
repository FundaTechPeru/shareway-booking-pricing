package com.fundatechperu.shareway.booking.pricing.application.service;

import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.model.Fare;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRepository;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.booking.pricing.domain.service.FarePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persists the fare of a booking (estimated or final) using the same {@link FarePolicy}
 * strategy that answers GET /fares/estimate, so the recorded amount always matches the quote.
 */
@Service
public class FareService {
    private final FareRuleRepository fareRuleRepository;
    private final FareRepository fareRepository;
    private final FarePolicy farePolicy;

    public FareService(FareRuleRepository fareRuleRepository, FareRepository fareRepository,
                       FarePolicy farePolicy) {
        this.fareRuleRepository = fareRuleRepository;
        this.fareRepository = fareRepository;
        this.farePolicy = farePolicy;
    }

    @Transactional
    public Fare recordFare(UUID bookingId, String fareType, LocalDate date, BigDecimal distanceKm, int passengers) {
        FareRule rule = fareRuleRepository.findActive(date)
                .orElseThrow(() -> new FareNotFoundException("No active fare rule found"));
        FarePolicy.FareQuote quote = farePolicy.quote(rule, distanceKm, passengers);
        return fareRepository.findByBookingIdAndFareType(bookingId, fareType)
                .orElseGet(() -> fareRepository.save(new Fare(UUID.randomUUID(), bookingId, rule.ruleId(), fareType,
                        distanceKm, passengers, quote.totalPerPassenger().amount(),
                        quote.totalPerPassenger().currency())));
    }

    @Transactional(readOnly = true)
    public Fare find(UUID bookingId, String fareType) {
        return fareRepository.findByBookingIdAndFareType(bookingId, fareType)
                .orElseThrow(() -> new FareNotFoundException("No " + fareType + " fare for booking " + bookingId));
    }
}
