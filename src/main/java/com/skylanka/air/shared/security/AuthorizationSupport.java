package com.skylanka.air.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthorizationSupport {

    public Long currentUserId() {
        Authentication auth = authentication();
        if (auth == null) return null;
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isAuthenticated() {
        return currentUserId() != null;
    }

    public boolean hasAnyRole(String... roles) {
        Authentication auth = authentication();
        if (auth == null) return false;
        List<String> wanted = Arrays.stream(roles).map(r -> "ROLE_" + r).toList();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (wanted.contains(authority.getAuthority())) return true;
        }
        return false;
    }

    public boolean isOwner(Long ownerId) {
        Long uid = currentUserId();
        return uid != null && ownerId != null && uid.equals(ownerId);
    }

    public boolean isOwnerOrHasAnyRole(Long ownerId, String... roles) {
        return isOwner(ownerId) || hasAnyRole(roles);
    }

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        if (!auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return auth;
    }
}
