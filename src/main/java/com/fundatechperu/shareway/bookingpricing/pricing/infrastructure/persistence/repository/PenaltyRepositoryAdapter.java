package com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.repository;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.model.Penalty;
import com.fundatechperu.shareway.bookingpricing.pricing.domain.repository.PenaltyRepository;
import com.fundatechperu.shareway.bookingpricing.pricing.infrastructure.persistence.entity.PenaltyJpaEntity;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public class PenaltyRepositoryAdapter implements PenaltyRepository {
    private final SpringDataPenaltyRepository repository;
    public PenaltyRepositoryAdapter(SpringDataPenaltyRepository repository) { this.repository = repository; }
    public Penalty save(Penalty p) { return toDomain(repository.save(toEntity(p))); }
    public List<Penalty> findByBookingId(UUID id) { return repository.findByBookingId(id).stream().map(this::toDomain).toList(); }
    private PenaltyJpaEntity toEntity(Penalty p) { PenaltyJpaEntity e = new PenaltyJpaEntity(); e.setPenaltyId(p.penaltyId()); e.setBookingId(p.bookingId()); e.setAmount(p.amount()); e.setReason(p.reason()); e.setCreatedAt(p.createdAt()); return e; }
    private Penalty toDomain(PenaltyJpaEntity e) { return new Penalty(e.getPenaltyId(), e.getBookingId(), e.getAmount(), e.getReason(), e.getCreatedAt()); }
}
