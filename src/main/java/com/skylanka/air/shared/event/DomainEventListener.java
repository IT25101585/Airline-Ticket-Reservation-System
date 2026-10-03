package com.skylanka.air.shared.event;

/**
 * Observer in the Observer pattern. Every bean that implements this interface is
 * registered automatically with {@link DomainEventPublisher}; adding a new reaction
 * to an event (SMS, push, analytics...) means adding one class, with no change to
 * BookingService or PaymentService.
 */
public interface DomainEventListener {

    void onEvent(DomainEvent event);
}
