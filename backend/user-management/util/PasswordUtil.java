package com.skylanka.util;

import org.mindrot.jbcrypt.BCrypt;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class providing cryptographic operations for passwords.
 * Implements BCrypt hashing algorithm with automatic salting and configurable workload.
 */
public final class PasswordUtil {

    private static final Logger LOGGER = Logger.getLogger(PasswordUtil.class.getName());

    // Workload log rounds: 12 offers strong defense against GPU cracking while maintaining sub-second verification
    private static final int BCRYPT_LOG_ROUNDS = 12;

    private PasswordUtil() {
        // Utility class: prevent instantiation
    }

    /**
     * Hashes a raw plain-text password using the BCrypt algorithm.
     *
     * @param plainPassword Raw password entered by user
     * @return 60-character BCrypt hash string containing algorithm, cost, salt, and hash
     * @throws IllegalArgumentException if password is null or empty
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty for hashing");
        }
        String salt = BCrypt.gensalt(BCRYPT_LOG_ROUNDS);
        return BCrypt.hashpw(plainPassword, salt);
    }

    /**
     * Verifies whether a candidate plain-text password matches an existing BCrypt hash.
     *
     * @param plainPassword  Raw password provided during login/authentication
     * @param hashedPassword Stored BCrypt hash from database
     * @return true if password matches hash; false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.isEmpty() || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Invalid hash format encountered during password verification", e);
            return false;
        }
    }
}
