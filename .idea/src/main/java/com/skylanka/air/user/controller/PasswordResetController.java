package com.skylanka.air.user.controller;

import com.skylanka.air.shared.service.EmailNotificationService;
import com.skylanka.air.user.entity.PasswordResetToken;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.PasswordResetTokenRepository;
import com.skylanka.air.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
public class PasswordResetController {

    private static final int TOKEN_VALID_MINUTES = 30;

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final EmailNotificationService emailNotifications;

    public PasswordResetController(
            UserRepository u,
            PasswordResetTokenRepository t,
            PasswordEncoder e,
            EmailNotificationService email) {

        users = u;
        tokens = t;
        encoder = e;
        emailNotifications = email;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, HttpServletRequest request, Model m) {
        String normalized = email == null ? null : email.trim().toLowerCase();
        Optional<User> found = (normalized == null || normalized.isBlank())
                ? Optional.empty()
                : users.findByEmail(normalized);

        found.ifPresent(u -> {
            tokens.deleteAll(tokens.findByUserId(u.getId()));

            PasswordResetToken t = new PasswordResetToken();
            t.setUser(u);
            t.setToken(UUID.randomUUID().toString().replace("-", ""));
            t.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALID_MINUTES));
            tokens.save(t);

            String link = baseUrl(request) + "/reset-password?token=" + t.getToken();
            emailNotifications.passwordReset(u, link);
        });

        m.addAttribute(
                "success",
                "If an account exists for that email, we've sent a password reset link. " +
                        "It expires in " + TOKEN_VALID_MINUTES + " minutes."
        );
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model m) {
        var found = tokens.findByToken(token);
        if (found.isEmpty() || found.get().isExpired()) {
            m.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "reset-password";
        }
        m.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model m) {

        var found = tokens.findByToken(token);
        if (found.isEmpty() || found.get().isExpired()) {
            m.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "reset-password";
        }

        if (newPassword == null || newPassword.length() < 6) {
            m.addAttribute("token", token);
            m.addAttribute("error", "New password must be at least 6 characters.");
            return "reset-password";
        }

        if (!newPassword.equals(confirmPassword)) {
            m.addAttribute("token", token);
            m.addAttribute("error", "New password and confirmation do not match.");
            return "reset-password";
        }

        PasswordResetToken t = found.get();
        User u = t.getUser();
        u.setPassword(encoder.encode(newPassword));
        u.setGuest(false); // setting a password claims a guest-checkout account
        users.save(u);

        tokens.deleteAll(tokens.findByUserId(u.getId()));

        return "redirect:/login?reset=success";
    }

    private String baseUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean defaultPort = port == 80 || port == 443;
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
