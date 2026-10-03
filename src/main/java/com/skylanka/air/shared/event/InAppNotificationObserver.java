package com.skylanka.air.shared.event;

import com.skylanka.air.shared.entity.Notification;
import com.skylanka.air.shared.repository.NotificationRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Observer: stores an in-app notification for the customer (the bell icon / notifications page). */
@Component
@Order(1)
public class InAppNotificationObserver implements DomainEventListener {

    private final NotificationRepository notifications;

    public InAppNotificationObserver(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event.recipient() == null || event.title() == null) {
            return;
        }
        Notification n = new Notification();
        n.setUser(event.recipient());
        n.setTitle(event.title());
        n.setMessage(event.message());
        notifications.save(n);
    }
}
