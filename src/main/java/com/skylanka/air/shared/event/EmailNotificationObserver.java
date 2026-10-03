package com.skylanka.air.shared.event;

import com.skylanka.air.shared.service.EmailNotificationService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Observer: sends the email for the events that have one. Whether mail is really sent
 * is still controlled by {@code notifications.email.enabled} inside EmailNotificationService.
 */
@Component
@Order(2)
public class EmailNotificationObserver implements DomainEventListener {

    private final EmailNotificationService email;

    public EmailNotificationObserver(EmailNotificationService email) {
        this.email = email;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event.booking() == null) {
            return;
        }
        switch (event.type()) {
            case BOOKING_CREATED -> email.bookingCreated(event.booking());
            case PAYMENT_COMPLETED -> email.paymentCompleted(event.booking());
            default -> { /* no email for the other events */ }
        }
    }
}
