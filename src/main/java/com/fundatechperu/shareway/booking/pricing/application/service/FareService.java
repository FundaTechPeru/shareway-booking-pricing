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
import java.util.Optional;
import java.util.UUID;

/**
 * Persists the fare of a booking (estimated or final) using the same {@link FarePolicy}
 * strategy that answers GET /fares/estimate, so the recorded amount always matches the
 * quote. The UNIQUE (booking_id, fare_type) pair is respected: an existing row is never
 * duplicated, it is only overwritten when the caller explicitly recalculates
 * (overwriteExisting = true, used by RouteUpdated).
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

    /**
     * Records the fare once: a fare that already exists for (bookingId, fareType) is kept.
     */
    @Transactional
    public Fare recordFare(UUID bookingId, String fareType, LocalDate date, BigDecimal distanceKm, int passengers) {
        return priceAndPersist(bookingId, fareType, date, distanceKm, passengers, false).fare();
    }

    /**
     * Prices the fare with the rule valid on {@code date} and persists it. When
     * {@code overwriteExisting} is true the existing row is updated in place (RouteUpdated
     * recalculates the estimate with the new distance); otherwise the first stored fare
     * wins (TripCompleted never rewrites the final fare).
     *
     * @return the persisted fare plus the version of the rule that priced it
     */
    @Transactional
    public PricedFare priceAndPersist(UUID bookingId, String fareType, LocalDate date, BigDecimal distanceKm,
                                      int passengers, boolean overwriteExisting) {
        FareRule rule = fareRuleRepository.findActive(date)
                .orElseThrow(() -> new FareNotFoundException("No active fare rule found"));
        FarePolicy.FareQuote quote = farePolicy.quote(rule, distanceKm, passengers);

        Optional<Fare> existing = fareRepository.findByBookingIdAndFareType(bookingId, fareType);
        if (existing.isPresent() && !overwriteExisting) {
            return new PricedFare(existing.get(), rule.version());
        }
        Fare fare = new Fare(existing.map(Fare::fareId).orElseGet(UUID::randomUUID), bookingId, rule.ruleId(),
                fareType, distanceKm, passengers, quote.totalPerPassenger().amount(),
                quote.totalPerPassenger().currency());
        return new PricedFare(fareRepository.save(fare), rule.version());
    }

    @Transactional(readOnly = true)
    public Fare find(UUID bookingId, String fareType) {
        return fareRepository.findByBookingIdAndFareType(bookingId, fareType)
                .orElseThrow(() -> new FareNotFoundException("No " + fareType + " fare for booking " + bookingId));
    }

    /** A persisted fare together with the version of the rule that produced the amount. */
    public record PricedFare(Fare fare, int ruleVersion) {
    }
}
