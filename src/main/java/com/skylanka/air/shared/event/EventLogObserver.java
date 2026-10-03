package com.skylanka.air.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Observer: writes one log line per event so the booking/payment flow can be traced. */
@Component
@Order(3)
public class EventLogObserver implements DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(EventLogObserver.class);

    @Override
    public void onEvent(DomainEvent event) {
        String reference = event.booking() == null ? "-" : event.booking().getReference();
        log.info("[event] {} booking={}", event.type(), reference);
    }
}
