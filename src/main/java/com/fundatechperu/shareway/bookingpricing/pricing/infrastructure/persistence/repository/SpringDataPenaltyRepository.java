package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.PenaltyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface SpringDataPenaltyRepository extends JpaRepository<PenaltyJpaEntity, UUID> {
    List<PenaltyJpaEntity> findByBookingId(UUID bookingId);
}
