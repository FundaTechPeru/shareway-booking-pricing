package com.fundatechperu.shareway.bookingpricing.booking.application.service;

import com.fundatechperu.shareway.bookingpricing.booking.application.dto.request.CreateBookingRequest;
import com.fundatechperu.shareway.bookingpricing.booking.application.dto.response.BookingResponse;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.BookingAlreadyExistsException;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.InvalidBookingRequestException;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.TripGroupNotFoundException;
import com.fundatechperu.shareway.bookingpricing.booking.domain.exception.TripRequestNotFoundException;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.domain.model.TripGroup;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.BookingRepository;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.TripGroupRepository;
import com.fundatechperu.shareway.bookingpricing.booking.domain.repository.TripRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final TripGroupRepository tripGroupRepository;
    private final TripRequestRepository tripRequestRepository;

    public BookingService(BookingRepository bookingRepository, TripGroupRepository tripGroupRepository,
                          TripRequestRepository tripRequestRepository) {
        this.bookingRepository = bookingRepository;
        this.tripGroupRepository = tripGroupRepository;
        this.tripRequestRepository = tripRequestRepository;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        if (request == null || request.getRequestId() == null || request.getGroupId() == null) {
            throw new InvalidBookingRequestException("requestId and groupId are required");
        }
        UUID requestId = request.getRequestId();
        if (!tripRequestRepository.existsById(requestId)) {
            throw new TripRequestNotFoundException(requestId);
        }
        if (bookingRepository.existsByRequestId(requestId)) {
            throw new BookingAlreadyExistsException("A booking already exists for requestId " + requestId);
        }
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

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }

    public record CancellationResponse(BookingResponse booking, boolean penaltyApplicable) {}

    @Transactional
    public CancellationResponse cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        TripGroup group = tripGroupRepository.findById(booking.getGroupId())
                .orElseThrow(() -> new TripGroupNotFoundException(booking.getGroupId()));
        booking.cancel();
        group.releaseSeat();
        tripGroupRepository.save(group);
        return new CancellationResponse(BookingResponse.from(bookingRepository.save(booking)), false);
    }
}
