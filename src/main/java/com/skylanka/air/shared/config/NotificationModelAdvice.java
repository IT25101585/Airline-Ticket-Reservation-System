package com.skylanka.air.shared.config;

import com.skylanka.air.shared.repository.NotificationRepository;
import com.skylanka.air.shared.security.AuthorizationSupport;
import com.skylanka.air.shared.security.NavPermissions;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the unread notification count available on every server-rendered page,
 * so the bell icon in the shared nav can show a badge no matter which page loaded it.
 * The badge itself is then kept fresh via polling against /api/notifications/unread-count.
 */
@ControllerAdvice
public class NotificationModelAdvice {

    private final NotificationRepository notifications;
    private final AuthorizationSupport authz;
    private final NavPermissions navPermissions;

    public NotificationModelAdvice(NotificationRepository notifications, AuthorizationSupport authz,
                                   NavPermissions navPermissions) {
        this.notifications = notifications;
        this.authz = authz;
        this.navPermissions = navPermissions;
    }

    /**
     * Exposed to templates as ${isStaff}. Thymeleaf 3.1 forbids bean references (@bean) in many
     * attribute expressions (e.g. th:data-*), so shared pages read this instead.
     */
    @ModelAttribute("isStaff")
    public boolean isStaff() {
        return navPermissions.isStaff();
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount() {
        Long id = authz.currentUserId();
        if (id == null) return 0;

        return notifications.findByUserIdOrderByCreatedAtDesc(id)
                .stream()
                .filter(n -> !n.isRead())
                .count();
    }
}
