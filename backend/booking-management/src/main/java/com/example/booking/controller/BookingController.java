//API layer-->Receives HTTP req and passes them to service

package com.example.booking.controller;

import com.example.booking.dto.BookingResponse;
import com.example.booking.dto.CreateBookingRequest;
import com.example.booking.dto.ModifyBookingRequest;
import com.example.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    //Accept POST req
    @PostMapping
    //Valid-->perform validation
    //RequestBody-->takes JSON and converts it to object
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {

        //passes object to service
        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //lookup
    @GetMapping("/{bookingReference}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String bookingReference) {
        return ResponseEntity.ok(bookingService.getBookingByReference(bookingReference));
    }

    //booking history
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<BookingResponse>> getBookingHistory(@PathVariable Long customerId) {
        return ResponseEntity.ok(bookingService.getBookingHistory(customerId));
    }

    //modification
    @PutMapping("/{bookingReference}")
    public ResponseEntity<BookingResponse> modifyBooking(
            @PathVariable String bookingReference,
            @RequestBody ModifyBookingRequest request) {
        return ResponseEntity.ok(bookingService.modifyBooking(bookingReference, request));
    }

    @DeleteMapping("/{bookingReference}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable String bookingReference) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingReference));
    }

    @PostMapping("/{bookingReference}/confirm-payment")
    public ResponseEntity<BookingResponse> confirmPayment(
            @PathVariable String bookingReference,
            @RequestParam String paymentId) {
        return ResponseEntity.ok(bookingService.confirmBookingAfterPayment(bookingReference, paymentId));
    }

    @PostMapping("/{bookingReference}/payment-failed")
    public ResponseEntity<BookingResponse> paymentFailed(@PathVariable String bookingReference) {
        return ResponseEntity.ok(bookingService.markPaymentFailed(bookingReference));
    }
}