package com.fundatechperu.shareway.bookingpricing.booking.application.service;

import com.fundatechperu.shareway.bookingpricing.booking.application.model.Booking;
import com.fundatechperu.shareway.bookingpricing.booking.application.repository.BookingRepository;
import com.fundatechperu.shareway.bookingpricing.booking.domain.dto.BookingRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking createBooking(BookingRequestDto request) {
        Booking booking = new Booking(request.getRequestId(), request.getGroupId());
        return bookingRepository.save(booking);
    }
}
