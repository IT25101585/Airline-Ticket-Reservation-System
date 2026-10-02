package com.skylanka.air.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String bearer = request.getHeader("Authorization");
            if (bearer != null && bearer.startsWith("Bearer ")) {
                authenticateJwt(bearer.substring(7), request);
            } else {
                authenticateSession(request);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticateJwt(String token, HttpServletRequest request) {
        try {
            Map<String, String> claims = jwtService.validate(token);
            setAuthentication(
                    claims.get("sub"),
                    claims.get("role"),
                    request);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void authenticateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return;
        Object userId = session.getAttribute("userId");
        Object role = session.getAttribute("role");
        if (userId != null && role != null) {
            setAuthentication(String.valueOf(userId), String.valueOf(role), request);
        }
    }

    private void setAuthentication(String principal, String role, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}