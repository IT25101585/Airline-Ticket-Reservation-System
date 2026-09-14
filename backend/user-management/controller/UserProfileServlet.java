package com.skylanka.controller;

import com.skylanka.dao.UserDAO;
import com.skylanka.dao.UserDAOImpl;
import com.skylanka.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Servlet handling User Profile viewing, updating personal details,
 * and changing passwords.
 */
@WebServlet(name = "UserProfileServlet", urlPatterns = {"/profile"})
public class UserProfileServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        super.init();
        this.userDAO = new UserDAOImpl();
    }

    public UserProfileServlet(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public UserProfileServlet() {
        // Default constructor
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User currentUser = getAuthenticatedUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        // Fetch fresh copy from database to ensure up-to-date data
        User freshUser = userDAO.getUserById(currentUser.getUserId());
        if (freshUser == null) {
            response.sendRedirect(request.getContextPath() + "/logout");
            return;
        }

        request.setAttribute("user", freshUser);
        request.getRequestDispatcher("/profile.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User currentUser = getAuthenticatedUser(request);
        if (currentUser == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");

        if ("updateProfile".equalsIgnoreCase(action)) {
            handleProfileUpdate(request, response, currentUser);
        } else if ("changePassword".equalsIgnoreCase(action)) {
            handlePasswordChange(request, response, currentUser);
        } else if ("deactivateAccount".equalsIgnoreCase(action)) {
            handleDeactivateAccount(request, response, currentUser);
        } else {
            response.sendRedirect(request.getContextPath() + "/profile");
        }
    }

    private void handleDeactivateAccount(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {
        if (currentUser.getRole() == com.skylanka.model.UserRole.ADMIN) {
            request.setAttribute("profileError", "System Administrators cannot deactivate their accounts through the profile portal.");
            reloadProfile(request, response, currentUser.getUserId());
            return;
        }

        boolean deactivated = userDAO.deactivateUser(currentUser.getUserId());
        if (deactivated) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/login?status=deactivated");
        } else {
            request.setAttribute("profileError", "Unable to deactivate account. Please contact support.");
            reloadProfile(request, response, currentUser.getUserId());
        }
    }

    private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {

        String fullName = request.getParameter("fullName");
        String phoneNumber = request.getParameter("phoneNumber");

        if (fullName == null || fullName.trim().isEmpty()) {
            request.setAttribute("profileError", "Full name cannot be empty.");
            reloadProfile(request, response, currentUser.getUserId());
            return;
        }

        fullName = fullName.trim();
        if (phoneNumber != null) phoneNumber = phoneNumber.trim();

        // Update database
        currentUser.setFullName(fullName);
        currentUser.setPhoneNumber(phoneNumber);

        boolean updated = userDAO.updateUserProfile(currentUser);
        if (updated) {
            // Update session cache
            request.getSession().setAttribute(LoginServlet.SESSION_USER_KEY, currentUser);
            request.setAttribute("profileSuccess", "Profile updated successfully.");
        } else {
            request.setAttribute("profileError", "Failed to update profile. Please try again.");
        }

        reloadProfile(request, response, currentUser.getUserId());
    }

    private void handlePasswordChange(HttpServletRequest request, HttpServletResponse response, User currentUser)
            throws ServletException, IOException {

        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmNewPassword = request.getParameter("confirmNewPassword");

        if (isNullOrEmpty(currentPassword) || isNullOrEmpty(newPassword) || isNullOrEmpty(confirmNewPassword)) {
            request.setAttribute("pwdError", "All password fields are required.");
            reloadProfile(request, response, currentUser.getUserId());
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            request.setAttribute("pwdError", "New password and confirmation do not match.");
            reloadProfile(request, response, currentUser.getUserId());
            return;
        }

        if (newPassword.length() < 8) {
            request.setAttribute("pwdError", "New password must be at least 8 characters long.");
            reloadProfile(request, response, currentUser.getUserId());
            return;
        }

        boolean success = userDAO.changePassword(currentUser.getUserId(), currentPassword, newPassword);
        if (success) {
            request.setAttribute("pwdSuccess", "Password changed successfully.");
        } else {
            request.setAttribute("pwdError", "Incorrect current password. Please try again.");
        }

        reloadProfile(request, response, currentUser.getUserId());
    }

    private void reloadProfile(HttpServletRequest request, HttpServletResponse response, int userId)
            throws ServletException, IOException {
        User freshUser = userDAO.getUserById(userId);
        request.setAttribute("user", freshUser);
        request.getRequestDispatcher("/profile.jsp").forward(request, response);
    }

    private User getAuthenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return (User) session.getAttribute(LoginServlet.SESSION_USER_KEY);
        }
        return null;
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
