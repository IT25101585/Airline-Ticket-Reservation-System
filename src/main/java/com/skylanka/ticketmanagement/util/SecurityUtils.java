package com.skylanka.ticketmanagement.util;

import com.skylanka.ticketmanagement.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility methods to safely extract the authenticated user from the
 * Spring Security context.
 *
 * IMPORTANT: Always use these helpers — never trust userId from the
 * request body for determining the current user (IDOR prevention).
 */
public class SecurityUtils {

    private SecurityUtils() {}

    /** Returns the authenticated UserPrincipal, or null if unauthenticated. */
    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    /** Returns the current authenticated user's UUID. */
    public static UUID getCurrentUserId() {
        UserPrincipal p = getCurrentUser();
        return (p != null) ? p.getId() : null;
    }
}
