package com.skylanka.model;

/**
 * Enumeration representing user account activation status.
 * Supports soft-delete/deactivation.
 */
public enum UserStatus {
    ACTIVE("Active"),
    DEACTIVATED("Deactivated");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Safely parses a status string into a UserStatus enum.
     *
     * @param statusStr string representation of status
     * @return matching UserStatus or default to ACTIVE if null or invalid
     */
    public static UserStatus fromString(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return ACTIVE;
        }
        for (UserStatus status : UserStatus.values()) {
            if (status.name().equalsIgnoreCase(statusStr.trim())) {
                return status;
            }
        }
        return ACTIVE;
    }
}
