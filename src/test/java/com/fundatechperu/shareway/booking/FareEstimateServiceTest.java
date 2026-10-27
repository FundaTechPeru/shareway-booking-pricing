package com.fundatechperu.shareway.booking;

import com.fundatechperu.shareway.booking.domain.exception.GroupNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.Money;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.pricing.application.dto.response.FareEstimateResponse;
import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.booking.pricing.domain.service.FarePolicy;
import com.fundatechperu.shareway.booking.pricing.application.service.FareEstimateService;
import com.fundatechperu.shareway.booking.pricing.infrastructure.provider.FakeGroupInfoProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FareEstimateService wiring: fake GroupInfoProvider (12 km), passengers from the seat
 * inventory, 404 exceptions, and no persistence at all.
 */
class FareEstimateServiceTest {

    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private final SeatInventoryRepository seatInventoryRepository = mock(SeatInventoryRepository.class);
    private final FareRuleRepository fareRuleRepository = mock(FareRuleRepository.class);
    private final FarePolicy farePolicy = mock(FarePolicy.class);

    private FareEstimateService service() {
        return new FareEstimateService(seatInventoryRepository, fareRuleRepository,
                new FakeGroupInfoProvider(), farePolicy, FIXED_CLOCK);
    }

    @Test
    void estimatesAnEmptyGroupAsSoloRiderWithTheFake12Km() {
        when(seatInventoryRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(SeatInventory.restore(GROUP_ID, 4, 4, 0L, null)));
        when(fareRuleRepository.findActive(TODAY)).thenReturn(Optional.of(activeRule()));
        when(farePolicy.quote(any(), any(), anyInt()))
                .thenReturn(new FarePolicy.FareQuote(Money.pen(new BigDecimal("19.40")),
                        Money.pen(new BigDecimal("1.94")), Money.pen(new BigDecimal("21.34")), 1));

        FareEstimateResponse response = service().estimate(GROUP_ID);

        assertEquals(new BigDecimal("21.34"), response.amount());
        assertEquals("PEN", response.currency());
        assertEquals(1, response.ruleVersion());
        // 4 capacity - 4 available = 0 seats taken -> minimum 1 passenger, distance from the fake = 12 km
        verify(farePolicy).quote(any(), eq(new BigDecimal("12")), eq(1));
        verify(fareRuleRepository).findActive(TODAY);
    }

    @Test
    void passesTheHeldSeatsAsGroupedPassengers() {
        when(seatInventoryRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(SeatInventory.restore(GROUP_ID, 4, 2, 0L, null)));
        when(fareRuleRepository.findActive(TODAY)).thenReturn(Optional.of(activeRule()));
        when(farePolicy.quote(any(), any(), anyInt()))
                .thenReturn(new FarePolicy.FareQuote(Money.pen(new BigDecimal("9.70")),
                        Money.pen(new BigDecimal("0.97")), Money.pen(new BigDecimal("10.67")), 1));

        FareEstimateResponse response = service().estimate(GROUP_ID);

        assertEquals(new BigDecimal("10.67"), response.amount());
        verify(farePolicy).quote(any(), any(), eq(2));
    }

    @Test
    void unknownGroupIsReportedAsNotFound() {
        when(seatInventoryRepository.findById(GROUP_ID)).thenReturn(Optional.empty());
        assertThrows(GroupNotFoundException.class, () -> service().estimate(GROUP_ID));
        Mockito.verifyNoInteractions(farePolicy);
    }

    @Test
    void missingActiveFareRuleIsReportedAsNotFound() {
        when(seatInventoryRepository.findById(GROUP_ID))
                .thenReturn(Optional.of(SeatInventory.restore(GROUP_ID, 4, 4, 0L, null)));
        when(fareRuleRepository.findActive(TODAY)).thenReturn(Optional.empty());
        assertThrows(FareNotFoundException.class, () -> service().estimate(GROUP_ID));
    }

    private FareRule activeRule() {
        return new FareRule(1L, 1, new BigDecimal("5.00"), new BigDecimal("1.20"), new BigDecimal("10"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31));
    }
}
