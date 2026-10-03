package com.skylanka.air.shared.event;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.user.entity.User;

/**
 * Something that happened in the booking or payment flow that other parts of the
 * system may want to react to (Observer pattern - the "event" passed to observers).
 *
 * @param type      what happened
 * @param booking   the booking it relates to (may be null for events not tied to one)
 * @param recipient the customer who should be told about it (may be null, e.g. a flight with no account)
 * @param title     short headline shown in the customer's notification list
 * @param message   full notification text
 */
public record DomainEvent(Type type, Booking booking, User recipient, String title, String message) {

    public enum Type {
        BOOKING_CREATED,
        BOOKING_MODIFIED,
        BOOKING_CANCELLED,
        BOOKING_EXPIRED,
        FLIGHT_DELAYED,
        PAYMENT_COMPLETED,
        PAYMENT_DECLINED,
        PAYMENT_VERIFIED,
        PAYMENT_REFUNDED,
        PAYMENT_VOIDED
    }
}
