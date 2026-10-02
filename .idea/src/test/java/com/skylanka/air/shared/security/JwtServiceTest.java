package com.skylanka.air.shared.security;

import com.skylanka.air.shared.entity.Role;
import com.skylanka.air.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test
    void issuesAndValidatesRequiredClaims() throws Exception {
        User user = new User();
        var id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 42L);
        user.setEmail("demo@skylankaair.com");
        user.setRole(Role.CUSTOMER);

        JwtService service = new JwtService("a-development-secret-that-is-long-enough", 60);
        var claims = service.validate(service.issue(user));

        assertEquals("42", claims.get("sub"));
        assertEquals("CUSTOMER", claims.get("role"));
        assertEquals("demo@skylankaair.com", claims.get("email"));
    }

    @Test
    void rejectsTamperedTokens() throws Exception {
        User user = new User();
        var id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, 42L);
        user.setRole(Role.ADMIN);
        JwtService service = new JwtService("a-development-secret-that-is-long-enough", 60);
        String token = service.issue(user);

        assertThrows(IllegalArgumentException.class,
                () -> service.validate(token.substring(0, token.length() - 1) + "x"));
    }
}