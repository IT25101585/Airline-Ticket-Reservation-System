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

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Servlet handling user self-registration for customers.
 * Validates inputs, checks duplicate emails, hashes passwords, and saves records.
 */
@WebServlet(name = "RegisterServlet", urlPatterns = {"/register"})
public class RegisterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // RFC 5322 compliant regex for email validation
    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    // E.164 / International Phone Regex
    private static final Pattern PHONE_REGEX = Pattern.compile("^\\+?[0-9\\s\\-]{7,20}$");

    private UserDAO userDAO;

    @Override
    public void init() throws ServletException {
        super.init();
        this.userDAO = new UserDAOImpl();
    }

    // Constructor for dependency injection (e.g. unit testing)
    public RegisterServlet(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public RegisterServlet() {
        // Default constructor
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Forward to registration JSP page
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String phoneNumber = request.getParameter("phoneNumber");

        // Input Sanitization and Trimming
        if (fullName != null) fullName = fullName.trim();
        if (email != null) email = email.trim().toLowerCase();
        if (phoneNumber != null) phoneNumber = phoneNumber.trim();

        // 1. Mandatory Field Validation
        if (isNullOrEmpty(fullName) || isNullOrEmpty(email) || isNullOrEmpty(password) || isNullOrEmpty(confirmPassword)) {
            sendValidationError(request, response, "All required fields must be filled out.", fullName, email, phoneNumber);
            return;
        }

        // 2. Full Name length validation
        if (fullName.length() < 3 || fullName.length() > 100) {
            sendValidationError(request, response, "Full Name must be between 3 and 100 characters.", fullName, email, phoneNumber);
            return;
        }

        // 3. Email Format Validation
        if (!EMAIL_REGEX.matcher(email).matches()) {
            sendValidationError(request, response, "Please provide a valid email address.", fullName, email, phoneNumber);
            return;
        }

        // 4. Password Confirmation Matching
        if (!password.equals(confirmPassword)) {
            sendValidationError(request, response, "Passwords do not match.", fullName, email, phoneNumber);
            return;
        }

        // 5. Password Complexity Validation (At least 8 characters, 1 digit, 1 letter)
        if (password.length() < 8) {
            sendValidationError(request, response, "Password must be at least 8 characters long.", fullName, email, phoneNumber);
            return;
        }

        // 6. Phone Number Validation (optional format check)
        if (phoneNumber != null && !phoneNumber.isEmpty() && !PHONE_REGEX.matcher(phoneNumber).matches()) {
            sendValidationError(request, response, "Invalid phone number format. Example: +94771234567", fullName, email, phoneNumber);
            return;
        }

        // 7. Check Duplicate Email
        if (userDAO.getUserByEmail(email) != null) {
            sendValidationError(request, response, "An account with this email address already exists. Please login instead.", fullName, email, phoneNumber);
            return;
        }

        // 8. Hash Password securely with BCrypt
        String passwordHash = PasswordUtil.hashPassword(password);

        // 9. Construct User Entity
        User newUser = new User(
                fullName,
                email,
                passwordHash,
                phoneNumber,
                UserRole.CUSTOMER,
                UserStatus.ACTIVE
        );

        // 10. Persist User in Database
        boolean isCreated = userDAO.registerUser(newUser);

        if (isCreated) {
            // Redirect to login page with success notification
            response.sendRedirect(request.getContextPath() + "/login?status=registered");
        } else {
            sendValidationError(request, response, "An unexpected error occurred during registration. Please try again later.", fullName, email, phoneNumber);
        }
    }

    private void sendValidationError(HttpServletRequest request, HttpServletResponse response,
                                     String errorMessage, String fullName, String email, String phoneNumber)
            throws ServletException, IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request
        request.setAttribute("errorMessage", errorMessage);
        request.setAttribute("fullName", fullName);
        request.setAttribute("email", email);
        request.setAttribute("phoneNumber", phoneNumber);
        request.getRequestDispatcher("/register.jsp").forward(request, response);
    }

    private boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
