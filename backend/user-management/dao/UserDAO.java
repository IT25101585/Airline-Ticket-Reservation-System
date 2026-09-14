package com.skylanka.dao;

import com.skylanka.model.User;

import java.util.List;

/**
 * Data Access Object (DAO) interface for User Management operations.
 * Defines data access contracts for authentication, registration, CRUD, and RBAC queries.
 */
public interface UserDAO {

    /**
     * Registers a new user in the database.
     *
     * @param user User entity containing registration details and hashed password
     * @return true if user was successfully registered; false otherwise
     */
    boolean registerUser(User user);

    /**
     * Authenticates a user by verifying email, verifying plain password against stored BCrypt hash,
     * and confirming account is in ACTIVE status.
     *
     * @param email       user email address
     * @param rawPassword candidate plain-text password
     * @return User entity if authenticated; null if credentials invalid or account deactivated
     */
    User authenticate(String email, String rawPassword);

    /**
     * Retrieves user record by unique primary key user_id.
     *
     * @param userId unique user identifier
     * @return User entity or null if not found
     */
    User getUserById(int userId);

    /**
     * Retrieves user record by unique email address.
     *
     * @param email user email
     * @return User entity or null if not found
     */
    User getUserByEmail(String email);

    /**
     * Retrieves all users with optional filtering by role and keyword search (full_name or email).
     *
     * @param roleFilter    optional role filter string (e.g. "CUSTOMER", "STAFF_OPERATIONS", or null/empty)
     * @param searchKeyword optional search query matching full_name or email (or null/empty)
     * @return list of matching User entities
     */
    List<User> getAllUsers(String roleFilter, String searchKeyword);

    /**
     * Updates profile details (full_name, phone_number) for a user.
     *
     * @param user User entity with updated fields
     * @return true if update succeeded; false otherwise
     */
    boolean updateUserProfile(User user);

    /**
     * Verifies old password and updates user's password with new BCrypt hash.
     *
     * @param userId      unique user ID
     * @param oldPassword current plain password for verification
     * @param newPassword new plain password to be hashed and saved
     * @return true if password changed successfully; false if old password invalid or error occurred
     */
    boolean changePassword(int userId, String oldPassword, String newPassword);

    /**
     * Soft-deactivates a user by setting status to DEACTIVATED.
     * Prevents login while preserving audit logs and ticket booking history.
     *
     * @param userId unique user ID
     * @return true if deactivated; false otherwise
     */
    boolean deactivateUser(int userId);

    /**
     * Activates a deactivated user account back to ACTIVE status.
     *
     * @param userId unique user ID
     * @return true if activated; false otherwise
     */
    boolean activateUser(int userId);

    /**
     * Permanently deletes a user record from the database.
     *
     * @param userId unique user ID
     * @return true if deleted; false otherwise
     */
    boolean deleteUserPermanent(int userId);
}
