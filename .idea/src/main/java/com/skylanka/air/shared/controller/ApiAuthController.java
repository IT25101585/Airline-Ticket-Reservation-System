package com.skylanka.air.shared.controller;

import com.skylanka.air.shared.security.JwtService;
import com.skylanka.air.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiAuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public ApiAuthController(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@RequestBody LoginRequest request) {
        var user = users.findByEmail(request.email() == null ? null : request.email().trim().toLowerCase())
                .filter(found -> found.isActive())
                .filter(found -> encoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        user.setLastLogin(java.time.LocalDateTime.now());
        users.save(user);
        return new TokenResponse(jwtService.issue(user), "Bearer", 3600);
    }

    @GetMapping("/me")
    public AccountResponse me(org.springframework.security.core.Authentication authentication) {
        return new AccountResponse(authentication.getName(), authentication.getAuthorities().iterator().next().getAuthority());
    }

    public record LoginRequest(String email, String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}
    public record AccountResponse(String userId, String authority) {}
}