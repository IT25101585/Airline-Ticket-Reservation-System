package com.skylanka.air.shared.service;

import com.skylanka.air.shared.entity.AuditLog;
import com.skylanka.air.shared.repository.AuditLogRepository;
import com.skylanka.air.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {
    /** Audit action written when unusual administrative activity is detected. */
    public static final String FLAG_ACTION = "FLAGGED_ACTIVITY";
    /** A staff member performing this many audited business actions inside the window is flagged. */
    static final int BURST_THRESHOLD = 15;
    static final int BURST_WINDOW_MINUTES = 5;

    private final AuditLogRepository repo;
    private final UserRepository users;

    public AuditService(AuditLogRepository r, UserRepository users) {
        repo = r;
        this.users = users;
    }

    public void log(HttpSession s, String action, String target, String details) {
        AuditLog a = new AuditLog();
        Object id = s.getAttribute("userId");
        if (id instanceof Long) a.setUserId((Long) id);
        a.setAction(action);
        a.setTarget(target);
        a.setDetails(details);
        repo.save(a);

        if (a.getUserId() != null) {
            detectUnusualActivity(a);
        }
    }

    /**
     * Flags (as a separate, highlighted audit entry) two kinds of unusual administrative activity:
     * a role being elevated to ADMIN, and a staff account performing an abnormal burst of actions.
     */
    private void detectUnusualActivity(AuditLog a) {
        if ("CHANGE_USER_ROLE".equals(a.getAction())
                && a.getDetails() != null && a.getDetails().endsWith(" to ADMIN")) {
            flag(a, "Administrator privilege granted to user " + a.getTarget());
        }

        boolean staffActor = users != null
                && users.findById(a.getUserId()).map(u -> u.isStaff()).orElse(false);
        if (!staffActor) return;

        long recent = repo.countBusinessActionsSince(
                a.getUserId(), LocalDateTime.now().minusMinutes(BURST_WINDOW_MINUTES));
        if (recent == BURST_THRESHOLD) {
            flag(a, recent + " audited actions within " + BURST_WINDOW_MINUTES + " minutes");
        }
    }

    private void flag(AuditLog cause, String reason) {
        AuditLog f = new AuditLog();
        f.setUserId(cause.getUserId());
        f.setAction(FLAG_ACTION);
        f.setTarget(cause.getAction());
        f.setDetails(reason);
        repo.save(f);
    }
}
