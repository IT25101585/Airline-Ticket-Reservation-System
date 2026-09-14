package com.skylanka.filter;

import com.skylanka.controller.LoginServlet;
import com.skylanka.model.User;
import com.skylanka.model.UserRole;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Authentication and Role-Based Access Control (RBAC) Filter.
 * Enforces session verification and route authorization across the SkyLanka system.
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = {"/*"})
public class AuthFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(AuthFilter.class.getName());

    // Publicly accessible paths that bypass authentication
    private static final Set<String> PUBLIC_PATHS = new HashSet<>(Arrays.asList(
            "/login",
            "/login.jsp",
            "/login.html",
            "/register",
            "/register.jsp",
            "/register.html",
            "/logout",
            "/index.jsp",
            "/index.html",
            "/"
    ));

    // Static asset path prefixes
    private static final String[] STATIC_RESOURCE_PREFIXES = {
            "/css/", "/js/", "/images/", "/fonts/", "/assets/", "/static/", "/favicon.ico"
    };

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        LOGGER.info("AuthFilter initialized successfully for SkyLanka Air Travels.");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Apply defensive HTTP security headers
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-XSS-Protection", "1; mode=block");

        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI();
        String relativePath = requestURI.substring(contextPath.length());

        // 1. Allow static resources (CSS, JS, images, icons)
        if (isStaticResource(relativePath)) {
            chain.doFilter(req, res);
            return;
        }

        // 2. Allow explicitly public endpoints (login, register, home)
        if (isPublicPath(relativePath)) {
            chain.doFilter(req, res);
            return;
        }

        // 3. Inspect Session Authentication
        HttpSession session = request.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute(LoginServlet.SESSION_USER_KEY) : null;

        if (currentUser == null) {
            // Unauthenticated user attempting to access a secured page
            LOGGER.warning("Unauthorized access attempt to " + relativePath + " - Redirecting to login.");

            // Check for AJAX/JSON request
            String requestedWith = request.getHeader("X-Requested-With");
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session expired or authentication required.");
                return;
            }

            // Redirect to login preserving original intended destination
            String destinationParam = URLEncoder.encode(relativePath, StandardCharsets.UTF_8);
            response.sendRedirect(contextPath + "/login?redirect=" + destinationParam);
            return;
        }

        // 4. Double check if authenticated user is still in ACTIVE state
        if (!currentUser.isActive()) {
            LOGGER.warning("Deactivated user ID " + currentUser.getUserId() + " tried to access " + relativePath);
            session.invalidate();
            response.sendRedirect(contextPath + "/login?status=deactivated");
            return;
        }

        // 5. Role-Based Access Control (RBAC) Enforcement

        // Admin Routes: Strictly reserved for ADMIN role
        if (relativePath.startsWith("/admin") || relativePath.startsWith("/admin-users")) {
            if (currentUser.getRole() != UserRole.ADMIN) {
                LOGGER.warning("RBAC Violation: User " + currentUser.getEmail() + " (Role: " +
                        currentUser.getRole() + ") attempted to access Admin path " + relativePath);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Access Denied: You do not have permission to view administrative resources.");
                return;
            }
        }

        // Staff Routes: Reserved for internal airline staff and administrators
        if (relativePath.startsWith("/staff")) {
            if (!currentUser.getRole().isStaffOrAdmin()) {
                LOGGER.warning("RBAC Violation: Customer " + currentUser.getEmail() +
                        " attempted to access Staff path " + relativePath);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Access Denied: Staff clearance is required for flight operations.");
                return;
            }
        }

        // Customer & Profile Routes: Accessible to any authenticated user
        // (All authorized requests continue down the chain)
        chain.doFilter(req, res);
    }

    @Override
    public void destroy() {
        LOGGER.info("AuthFilter destroyed.");
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // Handle root path or empty string
        return path.isEmpty() || "/".equals(path);
    }

    private boolean isStaticResource(String path) {
        for (String prefix : STATIC_RESOURCE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
