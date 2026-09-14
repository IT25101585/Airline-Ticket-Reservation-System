//defines the operations of booking management service
//does not contain implementation
//abstraction-->allows implementation to be changed w/o changing the controller

package com.example.booking.service;

import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.dto.ModifyBookingRequest;

import java.util.List;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request);
    BookingResponse getBookingByReference(String bookingReference);
    List<BookingResponse> getBookingHistory(Long customerId);
    BookingResponse modifyBooking(String bookingReference, ModifyBookingRequest request);
    BookingResponse cancelBooking(String bookingReference);
    BookingResponse confirmBookingAfterPayment(String bookingReference, String paymentId);
    BookingResponse markPaymentFailed(String bookingReference);
    void expireStaleBookings();
}