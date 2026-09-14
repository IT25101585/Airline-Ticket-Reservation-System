package com.skylanka.model;

/**
 * Enumeration representing user roles within the SkyLanka Air Travels system.
 * Used for Role-Based Access Control (RBAC).
 */
public enum UserRole {
    CUSTOMER("Customer"),
    ADMIN("System Administrator"),
    STAFF_OPERATIONS("Flight Operations Staff"),
    FINANCE_MANAGER("Finance Manager"),
    CUSTOMER_SUPPORT("Customer Support Staff");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Safely parses a role string into a UserRole enum.
     *
     * @param roleStr string representation of role
     * @return matching UserRole or default to CUSTOMER if null or invalid
     */
    public static UserRole fromString(String roleStr) {
        if (roleStr == null || roleStr.trim().isEmpty()) {
            return CUSTOMER;
        }
        for (UserRole role : UserRole.values()) {
            if (role.name().equalsIgnoreCase(roleStr.trim())) {
                return role;
            }
        }
        return CUSTOMER;
    }

    /**
     * Checks if this role is considered internal staff or administrative.
     *
     * @return true if role is Admin or any Staff role
     */
    public boolean isStaffOrAdmin() {
        return this == ADMIN || this == STAFF_OPERATIONS || this == FINANCE_MANAGER || this == CUSTOMER_SUPPORT;
    }
}
