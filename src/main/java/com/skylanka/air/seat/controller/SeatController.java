package com.skylanka.air.seat.controller;

import com.skylanka.air.seat.repository.SeatRepository;
import com.skylanka.air.seat.service.HoldRateLimiter;
import com.skylanka.air.seat.service.SeatService;
import com.skylanka.air.shared.entity.SeatStatus;
import com.skylanka.air.shared.security.AuthorizationSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class SeatController {
    private final SeatRepository seats;
    private final SeatService service;
    private final AuthorizationSupport authz;
    private final HoldRateLimiter holdLimiter;

    public SeatController(SeatRepository s, SeatService service, AuthorizationSupport authz,
                          HoldRateLimiter holdLimiter) {
        seats = s;
        this.service = service;
        this.authz = authz;
        this.holdLimiter = holdLimiter;
    }

    /** Holder identity: signed-in user, otherwise the anonymous (guest) browser session. */
    public static String holderFor(AuthorizationSupport authz, HttpSession session) {
        Long uid = authz.currentUserId();
        return uid != null ? "U" + uid : "S" + session.getId();
    }

    @PostMapping("/seats/{id}/hold")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> hold(@PathVariable Long id, HttpSession session,
                                                     HttpServletRequest request) {
        if (authz.currentUserId() != null && !authz.hasAnyRole("CUSTOMER")) {
            return ResponseEntity.status(403).build();
        }
        if (!holdLimiter.allow(request.getRemoteAddr())) {
            return ResponseEntity.status(429).body(Map.of("held", false, "limit", true,
                    "message", "Too many seat requests. Please wait a moment and try again."));
        }
        var r = service.hold(id, holderFor(authz, session));
        return ResponseEntity.ok(Map.of("held", r.held(), "limit", r.limitReached(), "message", r.message()));
    }

    @PostMapping("/seats/{id}/release")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> release(@PathVariable Long id, HttpSession session) {
        service.release(id, holderFor(authz, session));
        return ResponseEntity.ok(Map.of("released", true));
    }

    @GetMapping("/flights/{flightId}/seat-status")
    @ResponseBody
    public Map<Long, String> status(@PathVariable Long flightId, HttpSession session) {
        return service.statusMap(flightId, holderFor(authz, session));
    }

    @PreAuthorize("hasAnyRole('OPERATIONS','ADMIN')")
    @PostMapping("/operations/seat/{id}")
    public String update(@PathVariable Long id, @RequestParam SeatStatus status) {
        try {
            service.setStatus(id, status);
        } catch (IllegalStateException e) {
            return "redirect:/operations?error=booked-seat";
        }
        return "redirect:/operations";
    }
}
