package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.DriverSettlement;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.DriverSettlementRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.DriverSettlementJpaEntity;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public class DriverSettlementRepositoryAdapter implements DriverSettlementRepository {
    private final SpringDataDriverSettlementRepository repository;
    public DriverSettlementRepositoryAdapter(SpringDataDriverSettlementRepository repository) { this.repository = repository; }
    public DriverSettlement save(DriverSettlement s) { return toDomain(repository.save(toEntity(s))); }
    public List<DriverSettlement> findByTripId(UUID id) { return repository.findByTripId(id).stream().map(this::toDomain).toList(); }
    private DriverSettlementJpaEntity toEntity(DriverSettlement s) { DriverSettlementJpaEntity e = new DriverSettlementJpaEntity(); e.setSettlementId(s.settlementId()); e.setTripId(s.tripId()); e.setDriverId(s.driverId()); e.setGrossAmount(s.grossAmount()); e.setCommission(s.commission()); e.setNetAmount(s.netAmount()); e.setStatus(s.status()); return e; }
    private DriverSettlement toDomain(DriverSettlementJpaEntity e) { return new DriverSettlement(e.getSettlementId(), e.getTripId(), e.getDriverId(), e.getGrossAmount(), e.getCommission(), e.getNetAmount(), e.getStatus()); }
}
