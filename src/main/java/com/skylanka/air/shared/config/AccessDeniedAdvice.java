package com.skylanka.air.shared.config;

import com.skylanka.air.shared.security.AuthorizationSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

@ControllerAdvice
public class AccessDeniedAdvice {

    private final AuthorizationSupport authz;

    public AccessDeniedAdvice(AuthorizationSupport authz) {
        this.authz = authz;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handle(
            AccessDeniedException e,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        response.sendRedirect(authz.isAuthenticated() ? "/dashboard" : "/login");
    }
}
