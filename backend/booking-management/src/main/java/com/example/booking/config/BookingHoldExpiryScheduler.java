//supports temporary seat hold

package com.example.booking.config;

import com.example.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingHoldExpiryScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    public void releaseExpiredSeatHolds() {
        bookingService.expireStaleBookings();
    }
}