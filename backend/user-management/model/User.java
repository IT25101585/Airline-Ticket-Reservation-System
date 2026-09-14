package com.skylanka.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * User entity representing an account in SkyLanka Air Travels system.
 * Encapsulates credentials, contact info, RBAC role, and account state.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userId;
    private String fullName;
    private String email;
    private String passwordHash;
    private String phoneNumber;
    private UserRole role;
    private UserStatus status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * Default No-arg Constructor
     */
    public User() {
        this.role = UserRole.CUSTOMER;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Constructor for self-registration of customers
     *
     * @param fullName     User full name
     * @param email        Unique email address
     * @param passwordHash BCrypt hashed password
     * @param phoneNumber  Contact phone number
     */
    public User(String fullName, String email, String passwordHash, String phoneNumber) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.role = UserRole.CUSTOMER;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Constructor for administrative creation of users (Staff/Admin/Customer)
     *
     * @param fullName     User full name
     * @param email        Unique email address
     * @param passwordHash BCrypt hashed password
     * @param phoneNumber  Contact phone number
     * @param role         User RBAC role
     * @param status       Active or Deactivated status
     */
    public User(String fullName, String email, String passwordHash, String phoneNumber, UserRole role, UserStatus status) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.role = role != null ? role : UserRole.CUSTOMER;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    /**
     * Full Constructor for mapping from database records
     */
    public User(int userId, String fullName, String email, String passwordHash, String phoneNumber,
                UserRole role, UserStatus status, Timestamp createdAt, Timestamp updatedAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Checks if the user account is active and permitted to authenticate.
     *
     * @return true if account status is ACTIVE
     */
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * Safe string representation concealing password hash for log security.
     */
    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", role=" + role +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
