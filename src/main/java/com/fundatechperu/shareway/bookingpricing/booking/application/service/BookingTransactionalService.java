package com.fundatechperu.shareway.bookingpricing.booking.application.service;

import com.fundatechperu.shareway.bookingpricing.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.bookingpricing.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.*;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.TripGroup;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class BookingTransactionalService {
    private final BookingRepository bookingRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripRequestRepository tripRequestRepository;
    private final Counter retryCounter;
    public BookingTransactionalService(BookingRepository bookingRepository, TripGroupRepository tripGroupRepository,
                                        TripRequestRepository tripRequestRepository, MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository; this.tripGroupRepository = tripGroupRepository;
        this.tripRequestRepository = tripRequestRepository;
        this.retryCounter = meterRegistry.counter("booking.reservation.retries");
    }
    @Transactional
    public BookingResponse createOnce(CreateBookingRequest request) {
        UUID requestId = request.getRequestId();
        if (!tripRequestRepository.existsById(requestId)) throw new TripRequestNotFoundException(requestId);
        if (bookingRepository.existsByRequestId(requestId))
            throw new BookingAlreadyExistsException("A booking already exists for requestId " + requestId);
        TripGroup group = tripGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new TripGroupNotFoundException(request.getGroupId()));
        group.reserveSeat();
        tripGroupRepository.save(group);
        try {
            return BookingResponse.from(bookingRepository.save(Booking.create(requestId, request.getGroupId())));
        } catch (DataIntegrityViolationException exception) {
            throw new BookingAlreadyExistsException("A booking already exists for requestId " + requestId);
        }
    }
    public void incrementRetry() { retryCounter.increment(); }
}
