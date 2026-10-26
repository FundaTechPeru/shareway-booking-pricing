package com.fundatechperu.shareway.booking.pricing;

import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.service.DistanceBasedFarePolicy;
import com.fundatechperu.shareway.booking.pricing.domain.service.FarePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FarePolicy under test: the rule seeded by V2 (base 5.00, 1.20 per km, 10 % platform fee).
 * amount = ((base_amount + amount_per_km * distance_km) / passengers) + platform fee
 */
class FarePolicyTest {

    private static final BigDecimal TWELVE_KM = new BigDecimal("12");

    private final FareRule rule = new FareRule(1L, 1, new BigDecimal("5.00"), new BigDecimal("1.20"),
            new BigDecimal("10"), LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31));
    private final FarePolicy policy = new DistanceBasedFarePolicy();

    @Test
    void soloRiderPaysTheWholeTripCostPlusFee() {
        // 5.00 + 1.20 * 12 = 19.40, no split, + 10 % = 21.34
        FarePolicy.FareQuote quote = policy.quote(rule, TWELVE_KM, 1);
        assertEquals(new BigDecimal("19.40"), quote.farePerPassenger().amount());
        assertEquals(new BigDecimal("1.94"), quote.platformFee().amount());
        assertEquals(new BigDecimal("21.34"), quote.totalPerPassenger().amount());
        assertEquals("PEN", quote.totalPerPassenger().currency());
        assertEquals(1, quote.ruleVersion());
    }

    @Test
    void splitsTheTripCostAmongGroupedPassengers() {
        // 19.40 / 4 = 4.85, + 10 % (0.485 -> 0.49) = 5.34
        FarePolicy.FareQuote quote = policy.quote(rule, TWELVE_KM, 4);
        assertEquals(new BigDecimal("4.85"), quote.farePerPassenger().amount());
        assertEquals(new BigDecimal("0.49"), quote.platformFee().amount());
        assertEquals(new BigDecimal("5.34"), quote.totalPerPassenger().amount());
    }

    @Test
    void treatsZeroOrNegativePassengersAsOne() {
        FarePolicy.FareQuote zero = policy.quote(rule, TWELVE_KM, 0);
        FarePolicy.FareQuote negative = policy.quote(rule, TWELVE_KM, -3);
        FarePolicy.FareQuote solo = policy.quote(rule, TWELVE_KM, 1);
        assertEquals(solo.totalPerPassenger().amount(), zero.totalPerPassenger().amount());
        assertEquals(solo.totalPerPassenger().amount(), negative.totalPerPassenger().amount());
        assertEquals(new BigDecimal("21.34"), zero.totalPerPassenger().amount());
    }

    @Test
    void roundsEveryMoneyStepHalfUpToTwoDecimals() {
        // 11.00 / 3 = 3.666... -> 3.67; fee 0.367 -> 0.37; total 4.04
        FareRule threePassengersRule = new FareRule(2L, 3, new BigDecimal("5.00"), new BigDecimal("1.20"),
                new BigDecimal("10"), LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31));
        FarePolicy.FareQuote quote = policy.quote(threePassengersRule, new BigDecimal("5"), 3);
        assertEquals(new BigDecimal("3.67"), quote.farePerPassenger().amount());
        assertEquals(new BigDecimal("0.37"), quote.platformFee().amount());
        assertEquals(new BigDecimal("4.04"), quote.totalPerPassenger().amount());
    }

    @Test
    void zeroPlatformFeeAddsNothing() {
        FareRule freeRule = new FareRule(3L, 5, new BigDecimal("5.00"), new BigDecimal("1.20"),
                BigDecimal.ZERO, LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31));
        FarePolicy.FareQuote quote = policy.quote(freeRule, TWELVE_KM, 2);
        assertEquals(new BigDecimal("9.70"), quote.farePerPassenger().amount());
        assertEquals(new BigDecimal("0.00"), quote.platformFee().amount());
        assertEquals(new BigDecimal("9.70"), quote.totalPerPassenger().amount());
    }

    @Test
    void returnsTheVersionOfTheRuleUsed() {
        FareRule versionSeven = new FareRule(4L, 7, new BigDecimal("5.00"), new BigDecimal("1.20"),
                new BigDecimal("10"), LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31));
        assertEquals(7, policy.quote(versionSeven, TWELVE_KM, 1).ruleVersion());
    }

    @Test
    void rejectsNegativeOrNullDistance() {
        assertThrows(IllegalArgumentException.class, () -> policy.quote(rule, new BigDecimal("-1"), 1));
        assertThrows(IllegalArgumentException.class, () -> policy.quote(rule, null, 1));
    }

    @Test
    void rejectsMissingRule() {
        assertThrows(IllegalArgumentException.class, () -> policy.quote(null, TWELVE_KM, 1));
    }
}
