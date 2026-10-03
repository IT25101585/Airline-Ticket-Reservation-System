package com.skylanka.air.booking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpiryScheduler {

    private final BookingService bookingService;
    private final com.skylanka.air.seat.service.SeatService seatService;

    public BookingExpiryScheduler(
            BookingService bookingService,
            com.skylanka.air.seat.service.SeatService seatService) {

        this.bookingService = bookingService;
        this.seatService = seatService;
    }

    /** Seats held on the seat map but never booked are released automatically when the timer lapses. */
    @Scheduled(fixedDelay = 30000)
    public void releaseLapsedSeatHolds() {
        seatService.releaseExpiredHolds();
    }

    @Scheduled(fixedDelay = 60000)
    public void expireBookings() {

        int expired =
                bookingService.expirePendingBookings();

        if (expired > 0) {
            System.out.println(
                    "Expired " +
                            expired +
                            " unpaid booking(s)."
            );
        }
    }
}