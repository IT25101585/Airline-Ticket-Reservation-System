package com.skylanka.controller;

import com.skylanka.dao.UserDAO;
import com.skylanka.dao.UserDAOImpl;
import com.skylanka.model.User;
import com.skylanka.model.UserRole;
import com.skylanka.model.UserStatus;
import com.skylanka.util.PasswordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Controller for Administrative User Management operations.
 * Allows System Administrators to perform full CRUD on staff and customer accounts,
 * including soft-delete (deactivation), reactivation, role modification, and permanent deletion.
 */
@WebServlet(name = "AdminUserServlet", urlPatterns = {"/admin/users"})
public class AdminUserServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        super.init();
        this.userDAO = new UserDAOImpl();
    }

    public AdminUserServlet(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public AdminUserServlet() {
        // Default constructor
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrator role required.");
            return;
        }

        String action = request.getParameter("action");

        if ("view".equalsIgnoreCase(action)) {
            handleViewUser(request, response);
        } else {
            handleListUsers(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Administrator role required.");
            return;
        }

        request.setCharacterEncoding("UTF-8");
        String action = request.getParameter("action");

        if ("create".equalsIgnoreCase(action)) {
            handleCreateUser(request, response);
        } else if ("update".equalsIgnoreCase(action)) {
            handleUpdateUser(request, response);
        } else if ("deactivate".equalsIgnoreCase(action)) {
            handleDeactivateUser(request, response);
        } else if ("activate".equalsIgnoreCase(action)) {
            handleActivateUser(request, response);
        } else if ("delete".equalsIgnoreCase(action)) {
            handlePermanentDelete(request, response);
        } else {
            response.sendRedirect(request.getContextPath() + "/admin/users");
        }
    }

    private void handleListUsers(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String roleFilter = request.getParameter("role");
        String searchKeyword = request.getParameter("q");

        List<User> userList = userDAO.getAllUsers(roleFilter, searchKeyword);

        request.setAttribute("users", userList);
        request.setAttribute("selectedRole", roleFilter != null ? roleFilter : "ALL");
        request.setAttribute("searchKeyword", searchKeyword != null ? searchKeyword : "");
        request.setAttribute("roles", UserRole.values());

        request.getRequestDispatcher("/admin-users.jsp").forward(request, response);
    }

    private void handleViewUser(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String idParam = request.getParameter("id");
        if (idParam == null || idParam.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        try {
            int userId = Integer.parseInt(idParam);
            User user = userDAO.getUserById(userId);
            if (user == null) {
                request.getSession().setAttribute("adminError", "User not found with ID: " + userId);
                response.sendRedirect(request.getContextPath() + "/admin/users");
                return;
            }
            request.setAttribute("selectedUser", user);
            request.setAttribute("roles", UserRole.values());
            request.setAttribute("statuses", UserStatus.values());
            request.getRequestDispatcher("/views/admin/user_detail.jsp").forward(request, response);
        } catch (NumberFormatException e) {
            response.sendRedirect(request.getContextPath() + "/admin/users");
        }
    }

    private void handleCreateUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String phoneNumber = request.getParameter("phoneNumber");
        String roleStr = request.getParameter("role");

        if (fullName == null || fullName.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            request.getSession().setAttribute("adminError", "Full Name, Email, and Password are required.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        email = email.trim().toLowerCase();

        // Check duplicate email
        if (userDAO.getUserByEmail(email) != null) {
            request.getSession().setAttribute("adminError", "A user with email '" + email + "' already exists.");
            response.sendRedirect(request.getContextPath() + "/admin/users");
            return;
        }

        UserRole role = UserRole.fromString(roleStr);
        String passwordHash = PasswordUtil.hashPassword(password);

        User newUser = new User(
                fullName.trim(),
                email,
                passwordHash,
                phoneNumber != null ? phoneNumber.trim() : null,
                role,
                UserStatus.ACTIVE
        );

        boolean created = userDAO.registerUser(newUser);
        if (created) {
            request.getSession().setAttribute("adminSuccess", "New user '" + email + "' created successfully with role " + role.name() + ".");
        } else {
            request.getSession().setAttribute("adminError", "Failed to create user. Please try again.");
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handleUpdateUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String idParam = request.getParameter("userId");
        String fullName = request.getParameter("fullName");
        String phoneNumber = request.getParameter("phoneNumber");
        String roleStr = request.getParameter("role");
        String statusStr = request.getParameter("status");

        try {
            int userId = Integer.parseInt(idParam);
            User user = userDAO.getUserById(userId);
            if (user == null) {
                request.getSession().setAttribute("adminError", "User not found.");
                response.sendRedirect(request.getContextPath() + "/admin/users");
                return;
            }

            user.setFullName(fullName != null ? fullName.trim() : user.getFullName());
            user.setPhoneNumber(phoneNumber != null ? phoneNumber.trim() : user.getPhoneNumber());
            user.setRole(UserRole.fromString(roleStr));
            user.setStatus(UserStatus.fromString(statusStr));

            // We update profile details and status/role
            boolean updated = userDAO.updateUserProfile(user);
            if (user.getStatus() == UserStatus.DEACTIVATED) {
                userDAO.deactivateUser(userId);
            } else {
                userDAO.activateUser(userId);
            }

            if (updated) {
                request.getSession().setAttribute("adminSuccess", "User details updated successfully.");
            } else {
                request.getSession().setAttribute("adminError", "Failed to update user.");
            }
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("adminError", "Invalid user ID.");
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handleDeactivateUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String idParam = request.getParameter("userId");
        try {
            int userId = Integer.parseInt(idParam);

            // Prevent admin from deactivating their own account
            HttpSession session = request.getSession(false);
            if (session != null) {
                User currentAdmin = (User) session.getAttribute(LoginServlet.SESSION_USER_KEY);
                if (currentAdmin != null && currentAdmin.getUserId() == userId) {
                    request.getSession().setAttribute("adminError", "Safety violation: You cannot deactivate your own administrative account.");
                    response.sendRedirect(request.getContextPath() + "/admin/users");
                    return;
                }
            }

            boolean deactivated = userDAO.deactivateUser(userId);
            if (deactivated) {
                request.getSession().setAttribute("adminSuccess", "User (ID: " + userId + ") has been soft-deactivated.");
            } else {
                request.getSession().setAttribute("adminError", "Failed to deactivate user.");
            }
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("adminError", "Invalid user ID.");
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handleActivateUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String idParam = request.getParameter("userId");
        try {
            int userId = Integer.parseInt(idParam);
            boolean activated = userDAO.activateUser(userId);
            if (activated) {
                request.getSession().setAttribute("adminSuccess", "User (ID: " + userId + ") reactivated to ACTIVE status.");
            } else {
                request.getSession().setAttribute("adminError", "Failed to activate user.");
            }
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("adminError", "Invalid user ID.");
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private void handlePermanentDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String idParam = request.getParameter("userId");
        try {
            int userId = Integer.parseInt(idParam);

            // Prevent admin from deleting their own account
            HttpSession session = request.getSession(false);
            if (session != null) {
                User currentAdmin = (User) session.getAttribute(LoginServlet.SESSION_USER_KEY);
                if (currentAdmin != null && currentAdmin.getUserId() == userId) {
                    request.getSession().setAttribute("adminError", "Safety violation: You cannot permanently delete your own administrative account.");
                    response.sendRedirect(request.getContextPath() + "/admin/users");
                    return;
                }
            }

            boolean deleted = userDAO.deleteUserPermanent(userId);
            if (deleted) {
                request.getSession().setAttribute("adminSuccess", "User record (ID: " + userId + ") was permanently deleted.");
            } else {
                request.getSession().setAttribute("adminError", "Failed to delete user record.");
            }
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("adminError", "Invalid user ID.");
        }

        response.sendRedirect(request.getContextPath() + "/admin/users");
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            User user = (User) session.getAttribute(LoginServlet.SESSION_USER_KEY);
            return user != null && user.getRole() == UserRole.ADMIN;
        }
        return false;
    }
}
