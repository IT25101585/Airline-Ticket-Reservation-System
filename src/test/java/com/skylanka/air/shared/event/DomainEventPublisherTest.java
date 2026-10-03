package com.skylanka.air.shared.event;

import com.skylanka.air.booking.entity.Booking;
import com.skylanka.air.shared.entity.Notification;
import com.skylanka.air.shared.repository.NotificationRepository;
import com.skylanka.air.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DomainEventPublisherTest {

    private static DomainEvent event(DomainEvent.Type type) {
        return new DomainEvent(type, new Booking(), new User(), "Title", "Message");
    }

    @Test
    void notifiesEveryObserverInRegistrationOrder() {
        List<String> seen = new ArrayList<>();
        DomainEventPublisher publisher = new DomainEventPublisher();
        publisher.register(e -> seen.add("first:" + e.type()));
        publisher.register(e -> seen.add("second:" + e.type()));

        publisher.publish(event(DomainEvent.Type.BOOKING_CREATED));

        assertEquals(List.of("first:BOOKING_CREATED", "second:BOOKING_CREATED"), seen);
    }

    @Test
    void aFailingObserverDoesNotStopTheOthers() {
        List<String> seen = new ArrayList<>();
        DomainEventPublisher publisher = new DomainEventPublisher();
        publisher.register(e -> { throw new IllegalStateException("mail server down"); });
        publisher.register(e -> seen.add("still notified"));

        assertDoesNotThrow(() -> publisher.publish(event(DomainEvent.Type.PAYMENT_COMPLETED)));
        assertEquals(List.of("still notified"), seen);
    }

    @Test
    void inAppObserverStoresANotificationForTheRecipient() {
        NotificationRepository repository = mock(NotificationRepository.class);
        DomainEventPublisher publisher = new DomainEventPublisher();
        publisher.register(new InAppNotificationObserver(repository));

        publisher.publish(event(DomainEvent.Type.BOOKING_CREATED));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertEquals("Title", saved.getValue().getTitle());
        assertEquals("Message", saved.getValue().getMessage());
    }

    @Test
    void inAppObserverIgnoresEventsWithoutARecipient() {
        NotificationRepository repository = mock(NotificationRepository.class);
        InAppNotificationObserver observer = new InAppNotificationObserver(repository);

        observer.onEvent(new DomainEvent(DomainEvent.Type.BOOKING_CANCELLED, new Booking(), null, "Title", "Message"));

        verifyNoInteractions(repository);
    }
}
