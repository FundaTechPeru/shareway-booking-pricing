package com.fundatechperu.shareway.booking.infrastructure.persistence.repository;

import com.fundatechperu.shareway.booking.application.port.ProcessedEventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * processed_events through a single INSERT ... ON CONFLICT DO NOTHING: two concurrent
 * deliveries of the same event serialize on the primary key, so exactly one of them inserts
 * the row (and processes the event) and the other one sees a re-delivery.
 */
@Repository
public class JdbcProcessedEventStore implements ProcessedEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(UUID eventId, String type) {
        return jdbcTemplate.update(
                "INSERT INTO processed_events (event_id, type) VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING",
                eventId, type) == 1;
    }
}
