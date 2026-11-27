package com.fundatechperu.shareway.booking.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /** Messages neither processed nor failed, oldest first. */
    @Query("""
            select message
            from OutboxMessage message
            where message.processedAt is null
              and message.failedAt is null
            order by message.sequenceNumber asc
            """)
    List<OutboxMessage> findPending(Pageable pageable);

    @Query("select message from OutboxMessage message order by message.sequenceNumber asc")
    List<OutboxMessage> findAllInOrder();
}
