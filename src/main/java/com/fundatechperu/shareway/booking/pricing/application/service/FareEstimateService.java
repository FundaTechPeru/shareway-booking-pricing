package com.fundatechperu.shareway.booking.pricing.application.service;

import com.fundatechperu.shareway.booking.domain.exception.GroupNotFoundException;
import com.fundatechperu.shareway.booking.domain.model.SeatInventory;
import com.fundatechperu.shareway.booking.domain.repository.SeatInventoryRepository;
import com.fundatechperu.shareway.booking.pricing.application.dto.response.FareEstimateResponse;
import com.fundatechperu.shareway.booking.pricing.domain.exception.FareNotFoundException;
import com.fundatechperu.shareway.booking.pricing.domain.model.FareRule;
import com.fundatechperu.shareway.booking.pricing.domain.repository.FareRuleRepository;
import com.fundatechperu.shareway.booking.pricing.domain.service.FarePolicy;
import com.fundatechperu.shareway.booking.pricing.domain.service.GroupInfoProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * GET /fares/estimate: quotes a group without persisting anything.
 * - 404 when the group has no seat inventory (GroupNotFoundException) or no fare rule is
 *   valid today (FareNotFoundException).
 */
@Service
public class FareEstimateService {

    private final SeatInventoryRepository seatInventoryRepository;
    private final FareRuleRepository fareRuleRepository;
    private final GroupInfoProvider groupInfoProvider;
    private final FarePolicy farePolicy;
    private final Clock clock;

    public FareEstimateService(SeatInventoryRepository seatInventoryRepository,
                               FareRuleRepository fareRuleRepository,
                               GroupInfoProvider groupInfoProvider,
                               FarePolicy farePolicy,
                               Clock clock) {
        this.seatInventoryRepository = seatInventoryRepository;
        this.fareRuleRepository = fareRuleRepository;
        this.groupInfoProvider = groupInfoProvider;
        this.farePolicy = farePolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FareEstimateResponse estimate(UUID groupId) {
        SeatInventory inventory = seatInventoryRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        // Passengers of the group are already-held seats; an empty group is quoted as a solo rider.
        int groupedPassengers = Math.max(1, inventory.getCapacity() - inventory.getAvailableSeats());
        BigDecimal distanceKm = groupInfoProvider.distanceKm(groupId);

        LocalDate today = LocalDate.now(clock);
        FareRule rule = fareRuleRepository.findActive(today)
                .orElseThrow(() -> new FareNotFoundException("No fare rule valid on " + today));

        FarePolicy.FareQuote quote = farePolicy.quote(rule, distanceKm, groupedPassengers);
        return new FareEstimateResponse(quote.totalPerPassenger().amount(),
                quote.totalPerPassenger().currency(), quote.ruleVersion());
    }
}
