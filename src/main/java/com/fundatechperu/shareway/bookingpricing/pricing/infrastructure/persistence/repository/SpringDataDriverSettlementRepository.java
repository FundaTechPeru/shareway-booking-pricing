package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.DriverSettlementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface SpringDataDriverSettlementRepository extends JpaRepository<DriverSettlementJpaEntity, UUID> {
    List<DriverSettlementJpaEntity> findByTripId(UUID tripId);
}
