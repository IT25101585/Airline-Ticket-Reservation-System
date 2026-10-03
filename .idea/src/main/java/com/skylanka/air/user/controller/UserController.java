package com.skylanka.air.user.controller;

import com.skylanka.air.shared.entity.Role;
import com.skylanka.air.shared.entity.ComplaintStatus;
import com.skylanka.air.shared.entity.PaymentStatus;
import com.skylanka.air.shared.repository.AuditLogRepository;
import com.skylanka.air.shared.repository.ComplaintRepository;
import com.skylanka.air.booking.repository.BookingRepository;
import com.skylanka.air.payment.repository.PaymentRepository;
import com.skylanka.air.flight.repository.FlightRepository;
import com.skylanka.air.shared.security.AuthorizationSupport;
import com.skylanka.air.shared.service.AuditService;
import com.skylanka.air.user.entity.User;
import com.skylanka.air.user.repository.UserRepository;
import com.skylanka.air.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
public class UserController {

    /** Rows shown in the dashboard previews (audit log, manage users). */
    private static final int ADMIN_PREVIEW_SIZE = 5;
    /** Audit entries per page on the full audit log page. */
    private static final int AUDIT_PAGE_SIZE = 50;

    private final UserRepository users;
    private final UserService service;
    private final AuditLogRepository audits;
    private final PasswordEncoder encoder;
    private final AuditService auditService;
    private final AuthorizationSupport authz;
    private final ComplaintRepository complaints;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final FlightRepository flights;
    private final com.skylanka.air.booking.service.BookingService bookingService;
    private final com.skylanka.air.shared.service.SettingsService settings;
    private final com.skylanka.air.shared.repository.NotificationRepository notifications;
    private final com.skylanka.air.user.repository.PasswordResetTokenRepository resetTokens;

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final java.util.regex.Pattern CONTACT_PATTERN =
            java.util.regex.Pattern.compile("\\+?[0-9]{7,15}");

    public UserController(UserRepository u, UserService s, AuditLogRepository a, PasswordEncoder e,
                           AuditService audit, AuthorizationSupport authorizationSupport,
                           ComplaintRepository c, BookingRepository b, PaymentRepository p, FlightRepository f,
                           com.skylanka.air.booking.service.BookingService bookingService,
                           com.skylanka.air.shared.service.SettingsService settings,
                           com.skylanka.air.shared.repository.NotificationRepository notifications,
                           com.skylanka.air.user.repository.PasswordResetTokenRepository resetTokens) {

        users = u;
        service = s;
        audits = a;
        encoder = e;
        auditService = audit;
        authz = authorizationSupport;
        complaints = c;
        bookings = b;
        payments = p;
        flights = f;
        this.bookingService = bookingService;
        this.settings = settings;
        this.notifications = notifications;
        this.resetTokens = resetTokens;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model m) {

        m.addAttribute("user", new User());

        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute User user, BindingResult br, Model m, HttpSession s,
                           HttpServletRequest request) {
        user.setEmail(user.getEmail() == null ? null : user.getEmail().trim().toLowerCase());

        var existing = users.findByEmail(user.getEmail());
        if (existing.isPresent()) {
            // A guest account is claimed via "Forgot password" so ownership of the email is proven first.
            br.rejectValue("email", "duplicate", existing.get().isGuest()
                    ? "This email was used for a guest booking. Use \"Forgot password\" to set a password and claim it."
                    : "Email is already registered.");
        }

        if (br.hasErrors()) return "register";

        User saved = service.register(user);

        login(saved, s, request);

        return "redirect:/dashboard";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, Model m, HttpSession s,
                        HttpServletRequest request) {
        var found = users.findByEmail(email == null ? null : email.trim().toLowerCase());

        if (found.isEmpty() || !found.get().isActive() || !encoder.matches(password, found.get().getPassword())) {
            m.addAttribute("error", "Invalid email or password.");
            return "login";
        }

        User u = found.get();
        u.setLastLogin(LocalDateTime.now());
        users.save(u);
        login(u, s, request);
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession s) {
        s.invalidate();
        return "redirect:/";
    }

    @GetMapping("/profile")
    public String profile(Model m) {
        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        m.addAttribute("user", users.findById(id).orElseThrow());
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String contactNumber,
            @RequestParam(required = false) String passportNumber,
            @RequestParam(defaultValue = "false") boolean marketingOptIn,
            Model m) {

        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        User u = users.findById(id).orElseThrow();
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();

        if (name == null || name.isBlank()
                || contactNumber == null || contactNumber.isBlank()
                || normalizedEmail == null || normalizedEmail.isBlank()) {

            m.addAttribute("user", u);
            m.addAttribute("error", "All fields are required.");
            return "profile";
        }

        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            m.addAttribute("user", u);
            m.addAttribute("error", "Enter a valid email address.");
            return "profile";
        }

        if (!CONTACT_PATTERN.matcher(contactNumber.trim()).matches()) {
            m.addAttribute("user", u);
            m.addAttribute("error", "Enter a valid contact number (7-15 digits, optional leading +).");
            return "profile";
        }

        var existing = users.findByEmail(normalizedEmail);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            m.addAttribute("user", u);
            m.addAttribute("error", "That email is already in use by another account.");
            return "profile";
        }

        u.setName(name.trim());
        u.setEmail(normalizedEmail);
        if (passportNumber != null && !passportNumber.isBlank()
                && !passportNumber.trim().matches("[A-Za-z0-9]{6,9}")) {
            m.addAttribute("user", u);
            m.addAttribute("error", "Enter a valid passport number (6-9 letters/digits) or leave it blank.");
            return "profile";
        }

        u.setContactNumber(contactNumber.trim());
        if (u.getRole() == Role.CUSTOMER) {
            u.setPassportNumber(passportNumber);
            u.setMarketingOptIn(marketingOptIn);
        }
        users.save(u);

        m.addAttribute("user", u);
        m.addAttribute("success", "Your profile has been updated.");
        return "profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model m) {

        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        User u = users.findById(id).orElseThrow();
        m.addAttribute("user", u);

        if (!encoder.matches(currentPassword == null ? "" : currentPassword, u.getPassword())) {
            m.addAttribute("passwordError", "Current password is incorrect.");
            return "profile";
        }

        if (newPassword == null || newPassword.length() < 6) {
            m.addAttribute("passwordError", "New password must be at least 6 characters.");
            return "profile";
        }

        if (!newPassword.equals(confirmPassword)) {
            m.addAttribute("passwordError", "New password and confirmation do not match.");
            return "profile";
        }

        u.setPassword(encoder.encode(newPassword));
        users.save(u);

        m.addAttribute("passwordSuccess", "Your password has been changed.");
        return "profile";
    }

    @PostMapping("/profile/delete")
    public String deleteAccount(@RequestParam String password, Model m, HttpSession s) {
        Long id = authz.currentUserId();
        if (id == null) return "redirect:/login";

        User u = users.findById(id).orElseThrow();
        m.addAttribute("user", u);

        if (!encoder.matches(password == null ? "" : password, u.getPassword())) {
            m.addAttribute("deleteError", "Password is incorrect - your account was not deleted.");
            return "profile";
        }
        if (u.getRole() == Role.ADMIN && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            m.addAttribute("deleteError", "The last active administrator account cannot be deleted.");
            return "profile";
        }

        var mine = bookings.findByCustomerIdOrderByCreatedAtDesc(id);
        boolean hasLiveTrip = mine.stream().anyMatch(b ->
                b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.CONFIRMED
                        && !b.getFlight().getDepartureDate().isBefore(java.time.LocalDate.now()));
        if (hasLiveTrip) {
            m.addAttribute("deleteError",
                    "You have confirmed upcoming bookings. Cancel them first, then delete your account.");
            return "profile";
        }
        // Unpaid holds are released so the seats go back on sale.
        mine.stream()
                .filter(b -> b.getStatus() == com.skylanka.air.shared.entity.BookingStatus.PENDING)
                .forEach(bookingService::cancel);

        resetTokens.deleteAll(resetTokens.findByUserId(id));
        notifications.deleteAll(notifications.findByUserIdOrderByCreatedAtDesc(id));
        auditService.log(s, "DELETE_ACCOUNT", String.valueOf(id), "Account deleted by owner");
        service.anonymise(u);

        s.invalidate();
        return "redirect:/?deleted";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/settings")
    public String saveSettings(@RequestParam int bookingHoldMinutes,
                               @RequestParam int seatHoldMinutes,
                               @RequestParam java.math.BigDecimal taxPercent,
                               @RequestParam java.math.BigDecimal serviceFeePercent,
                               HttpSession s) {
        try {
            settings.update(bookingHoldMinutes, seatHoldMinutes, taxPercent, serviceFeePercent);
        } catch (IllegalArgumentException e) {
            return "redirect:/admin?settingsError=" + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
        auditService.log(s, "UPDATE_SETTINGS", "system",
                "booking hold " + bookingHoldMinutes + "m, seat hold " + seatHoldMinutes + "m, tax " + taxPercent
                        + "%, service fee " + serviceFeePercent + "%");
        return "redirect:/admin?settingsSaved";
    }

    /**
     * Establishes the signed-in session. The session id is rotated first so an id that was known
     * before authentication (session fixation) can never be used to act as the signed-in user.
     * Session attributes (none of them privileged yet at this point) are preserved by the container.
     */
    private void login(User u, HttpSession s, HttpServletRequest request) {
        request.changeSessionId();
        s.setAttribute("userId", u.getId());
        s.setAttribute("name", u.getName());
        s.setAttribute("email", u.getEmail());
        s.setAttribute("role", u.getRole().name());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public String admin(Model m) {
        var allUsers = users.findAll(org.springframework.data.domain.Sort.by("id"));

        // Dashboard preview only: the first few users and the newest audit entries.
        // The complete lists live on /admin/users and /admin/audit.
        m.addAttribute("users", allUsers.stream().limit(ADMIN_PREVIEW_SIZE).toList());
        m.addAttribute("totalUsers", allUsers.size());

        m.addAttribute("logs", audits.findTop5ByOrderByCreatedAtDesc());
        m.addAttribute("totalLogs", audits.count());
        m.addAttribute("userNamesById", userNamesById(allUsers));

        java.util.Map<String, Long> roleCounts = new java.util.LinkedHashMap<>();
        for (Role r : Role.values()) {
            roleCounts.put(r.name(), 0L);
        }
        for (User u : allUsers) {
            roleCounts.merge(u.getRole().name(), 1L, Long::sum);
        }
        m.addAttribute("roleCounts", roleCounts);

        var recentSignups = allUsers.stream()
                .sorted(java.util.Comparator.comparing(User::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(5)
                .toList();
        m.addAttribute("recentSignups", recentSignups);

        m.addAttribute("bookingHoldMinutes", settings.getInt(
                com.skylanka.air.shared.service.SettingsService.BOOKING_HOLD_MINUTES, 15));
        m.addAttribute("seatHoldMinutes", settings.getInt(
                com.skylanka.air.shared.service.SettingsService.SEAT_HOLD_MINUTES, 10));
        m.addAttribute("taxPercent", settings.getDecimal(
                com.skylanka.air.shared.service.SettingsService.TAX_PERCENT, new java.math.BigDecimal("10")));
        m.addAttribute("serviceFeePercent", settings.getDecimal(
                com.skylanka.air.shared.service.SettingsService.SERVICE_FEE_PERCENT, new java.math.BigDecimal("5")));
        m.addAttribute("flaggedCount", audits.countByAction(AuditService.FLAG_ACTION));

        var startOfToday = java.time.LocalDate.now().atStartOfDay();

        m.addAttribute("bookingsToday", bookings.countByCreatedAtAfter(startOfToday));
        m.addAttribute("flightsToday", flights.countByDepartureDate(java.time.LocalDate.now()));

        long openComplaints = complaints.countByStatus(ComplaintStatus.OPEN)
                + complaints.countByStatus(ComplaintStatus.IN_PROGRESS);
        m.addAttribute("openComplaints", openComplaints);

        m.addAttribute("revenueToday",
                payments.sumByStatusesSince(
                        java.util.List.of(PaymentStatus.PAID, PaymentStatus.VERIFIED),
                        startOfToday));

        return "admin";
    }

    /** Full user list (the dashboard only previews the first few). */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users")
    public String adminUsers(Model m) {
        m.addAttribute("users", users.findAll(org.springframework.data.domain.Sort.by("id")));
        return "admin-users";
    }

    /** Full audit log, newest first, {@value #AUDIT_PAGE_SIZE} entries per page. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/audit")
    public String adminAudit(@RequestParam(defaultValue = "0") int page, Model m) {
        var result = audits.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), AUDIT_PAGE_SIZE));

        if (result.getTotalPages() > 0 && page >= result.getTotalPages()) {
            return "redirect:/admin/audit?page=" + (result.getTotalPages() - 1);
        }

        m.addAttribute("logs", result.getContent());
        m.addAttribute("page", result.getNumber());
        m.addAttribute("totalPages", Math.max(result.getTotalPages(), 1));
        m.addAttribute("totalLogs", result.getTotalElements());
        m.addAttribute("pageSize", AUDIT_PAGE_SIZE);
        m.addAttribute("flaggedCount", audits.countByAction(AuditService.FLAG_ACTION));
        m.addAttribute("userNamesById", userNamesById(users.findAll()));
        return "admin-audit";
    }

    private static java.util.Map<Long, String> userNamesById(java.util.List<User> allUsers) {
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (User u : allUsers) {
            names.put(u.getId(), u.getName());
        }
        return names;
    }

    /** Back to the page the admin acted from: the full user list or the dashboard. */
    private static String adminReturn(String returnTo) {
        return "users".equals(returnTo) ? "redirect:/admin/users" : "redirect:/admin";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/user/{id}/toggle")
    public String toggle(@PathVariable Long id, @RequestParam(required = false) String returnTo, HttpSession s) {
        User u = users.findById(id).orElseThrow();
        if (u.getId().equals(s.getAttribute("userId")) && u.isActive()) {
            return adminReturn(returnTo) + "?error=self";
        }
        u.setActive(!u.isActive());
        users.save(u);
        auditService.log(s, "TOGGLE_USER", String.valueOf(u.getId()), "User active = " + u.isActive());

        return adminReturn(returnTo);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/user/{id}/role")
    public String role(@PathVariable Long id, @RequestParam Role role,
                       @RequestParam(required = false) String returnTo, HttpSession s) {
        User u = users.findById(id).orElseThrow();
        Role oldRole = u.getRole();
        u.setRole(role);
        users.save(u);
        u.assignStaffProfile();
        users.save(u);
        auditService.log(s, "CHANGE_USER_ROLE", String.valueOf(u.getId()), "Role changed from " + oldRole + " to " + role);

        return adminReturn(returnTo);
    }
}