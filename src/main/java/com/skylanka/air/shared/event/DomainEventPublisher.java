package com.skylanka.air.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subject in the Observer pattern. Services publish a {@link DomainEvent}; the
 * publisher notifies every registered {@link DomainEventListener}, in order, on
 * the calling thread (so observers take part in the caller's transaction).
 *
 * One observer failing (for example the mail server being down) is logged and
 * never stops the other observers or breaks the booking/payment that triggered it.
 * Spring creates exactly one instance of this class (singleton scope).
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final List<DomainEventListener> listeners = new CopyOnWriteArrayList<>();

    /** Empty publisher; observers are added with {@link #register}. Used by unit tests. */
    public DomainEventPublisher() {
    }

    /** Spring injects every DomainEventListener bean, ordered by @Order. */
    @Autowired
    public DomainEventPublisher(List<DomainEventListener> listeners) {
        this.listeners.addAll(listeners);
    }

    public void register(DomainEventListener listener) {
        listeners.add(listener);
    }

    public void publish(DomainEvent event) {
        for (DomainEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                log.warn("Observer {} failed for event {}: {}",
                        listener.getClass().getSimpleName(), event.type(), ex.getMessage());
            }
        }
    }
}
