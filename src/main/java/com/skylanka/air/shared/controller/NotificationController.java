package com.skylanka.air.shared.controller;

import com.skylanka.air.shared.repository.NotificationRepository;
import com.skylanka.air.shared.security.AuthorizationSupport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class NotificationController {

    private final NotificationRepository notifications;
    private final AuthorizationSupport authz;

    public NotificationController(NotificationRepository notifications, AuthorizationSupport authz) {
        this.notifications = notifications;
        this.authz = authz;
    }

    @GetMapping("/notifications")
    public String list(Model m) {
        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        m.addAttribute("notifications", notifications.findByUserIdOrderByCreatedAtDesc(id));
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable Long id) {
        Long userId = authz.currentUserId();
        if (userId == null) return "redirect:/login";

        var n = notifications.findById(id).orElse(null);
        if (n != null && n.getUser().getId().equals(userId) && !n.isRead()) {
            n.setRead(true);
            notifications.save(n);
        }

        return "redirect:/notifications";
    }

    @GetMapping(value = "/api/notifications/unread-count", produces = "application/json")
    @ResponseBody
    public Map<String, Long> unreadCount() {
        Long id = authz.currentUserId();
        if (id == null) return Map.of("count", 0L);

        long count = notifications.findByUserIdOrderByCreatedAtDesc(id)
                .stream()
                .filter(n -> !n.isRead())
                .count();

        return Map.of("count", count);
    }
}
