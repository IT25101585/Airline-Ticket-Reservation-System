package com.skylanka.ticketmanagement.security;

import com.skylanka.ticketmanagement.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated principal extracted from the JWT token.
 * Held in the Spring Security context for the duration of the request.
 */
public class UserPrincipal implements UserDetails {

    private final UUID     id;
    private final String   email;
    private final UserRole role;

    public UserPrincipal(UUID id, String email, UserRole role) {
        this.id    = id;
        this.email = email;
        this.role  = role;
    }

    public UUID     getId()   { return id;    }
    public UserRole getRole() { return role;  }

    // ===== UserDetails =====

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String  getPassword()                    { return null;  }
    @Override public String  getUsername()                    { return email; }
    @Override public boolean isAccountNonExpired()            { return true;  }
    @Override public boolean isAccountNonLocked()             { return true;  }
    @Override public boolean isCredentialsNonExpired()        { return true;  }
    @Override public boolean isEnabled()                      { return true;  }
}
