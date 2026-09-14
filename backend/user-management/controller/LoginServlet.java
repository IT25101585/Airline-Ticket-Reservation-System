package com.skylanka.controller;

import com.skylanka.dao.UserDAO;
import com.skylanka.dao.UserDAOImpl;
import com.skylanka.model.User;
import com.skylanka.model.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Servlet handling user authentication, session creation, and role-based redirection.
 * Supports distinct portal access points (Passenger, Staff, and Administrator),
 * validating user roles against portal clearance.
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String SESSION_USER_KEY = "currentUser";
    public static final String SESSION_ROLE_KEY = "userRole";

    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        super.init();
        this.userDAO = new UserDAOImpl();
    }

    public LoginServlet(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public LoginServlet() {
        // Default constructor
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // If already logged in, redirect directly to user's landing page
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null && existingSession.getAttribute(SESSION_USER_KEY) != null) {
            User loggedUser = (User) existingSession.getAttribute(SESSION_USER_KEY);
            redirectToLandingPage(request, response, loggedUser);
            return;
        }

        // Dedicated portal tab selection (?portal=customer|staff|admin)
        String portalParam = request.getParameter("portal");
        if (portalParam != null && !portalParam.trim().isEmpty()) {
            request.setAttribute("activePortal", portalParam.trim().toLowerCase());
        } else {
            request.setAttribute("activePortal", "customer");
        }

        // Handle flash message notifications (e.g. from registration, logout)
        String status = request.getParameter("status");
        if ("registered".equals(status)) {
            request.setAttribute("infoMessage", "Registration successful! You may now sign in to your passenger account.");
        } else if ("logged_out".equals(status)) {
            request.setAttribute("infoMessage", "You have been logged out securely.");
        } else if ("session_expired".equals(status)) {
            request.setAttribute("warningMessage", "Your session has expired. Please sign in again.");
        } else if ("deactivated".equals(status)) {
            request.setAttribute("warningMessage", "Your account has been deactivated. Please contact customer support.");
        }

        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String portalType = request.getParameter("portalType");

        if (portalType == null || portalType.trim().isEmpty()) {
            portalType = "CUSTOMER";
        }
        portalType = portalType.trim().toUpperCase();
        request.setAttribute("activePortal", portalType.toLowerCase());

        // Basic presence validation
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("errorMessage", "Please provide both email and password.");
            request.setAttribute("email", email != null ? email.trim() : "");
            request.getRequestDispatcher("/login.jsp").forward(request, response);
            return;
        }

        email = email.trim().toLowerCase();

        // Authenticate against database with BCrypt verification
        User user = userDAO.authenticate(email, password);

        if (user != null) {
            // 1. Check if user account is in ACTIVE status
            if (!user.isActive()) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                request.setAttribute("errorMessage", "Your account has been deactivated. Please contact SkyLanka support.");
                request.setAttribute("email", email);
                request.getRequestDispatcher("/login.jsp").forward(request, response);
                return;
            }

            // 2. Enforce Portal-Specific Role Restrictions
            if ("ADMIN".equals(portalType)) {
                if (user.getRole() != UserRole.ADMIN) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    request.setAttribute("errorMessage",
                            "Access Denied: The Administrator Console is strictly reserved for System Administrators. " +
                            "Your account role (" + user.getRole().getDisplayName() + ") does not have administrative clearance.");
                    request.setAttribute("email", email);
                    request.getRequestDispatcher("/login.jsp").forward(request, response);
                    return;
                }
            } else if ("STAFF".equals(portalType)) {
                if (!user.getRole().isStaffOrAdmin()) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    request.setAttribute("errorMessage",
                            "Access Denied: Staff clearance is required for the Airline Operations Portal. " +
                            "Passenger accounts must sign in through the Passenger / Customer tab.");
                    request.setAttribute("email", email);
                    request.getRequestDispatcher("/login.jsp").forward(request, response);
                    return;
                }
            }

            // 3. Session Fixation Prevention: Invalidate existing session and create a fresh HTTP session
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }

            HttpSession newSession = request.getSession(true);
            newSession.setMaxInactiveInterval(30 * 60); // 30 minutes session timeout

            // Store user identity and role in session
            newSession.setAttribute(SESSION_USER_KEY, user);
            newSession.setAttribute(SESSION_ROLE_KEY, user.getRole().name());

            // Check if user was redirected from a protected page
            String redirectUrl = request.getParameter("redirect");
            if (redirectUrl != null && !redirectUrl.trim().isEmpty() && redirectUrl.startsWith("/")) {
                response.sendRedirect(request.getContextPath() + redirectUrl);
                return;
            }

            // Redirect according to authenticated role
            redirectToLandingPage(request, response, user);

        } else {
            // Failed authentication (Incorrect password, unknown user, or deactivated account)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
            request.setAttribute("errorMessage", "Invalid email or password. Please verify your credentials and try again.");
            request.setAttribute("email", email);
            request.getRequestDispatcher("/login.jsp").forward(request, response);
        }
    }

    /**
     * Dispatches user to appropriate dashboard based on RBAC role.
     */
    private void redirectToLandingPage(HttpServletRequest request, HttpServletResponse response, User user)
            throws IOException {
        String contextPath = request.getContextPath();

        switch (user.getRole()) {
            case ADMIN:
                response.sendRedirect(contextPath + "/admin/users");
                break;
            case STAFF_OPERATIONS:
            case FINANCE_MANAGER:
            case CUSTOMER_SUPPORT:
                response.sendRedirect(contextPath + "/staff/dashboard");
                break;
            case CUSTOMER:
            default:
                response.sendRedirect(contextPath + "/profile");
                break;
        }
    }
}
