package com.skylanka.dao;

import com.skylanka.model.User;
import com.skylanka.model.UserRole;
import com.skylanka.model.UserStatus;
import com.skylanka.util.DBConnection;
import com.skylanka.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC Implementation of UserDAO.
 * Strictly uses PreparedStatement across all operations to prevent SQL injection.
 */
public class UserDAOImpl implements UserDAO {

    private static final Logger LOGGER = Logger.getLogger(UserDAOImpl.class.getName());

    // Prepared SQL Queries
    private static final String SQL_INSERT_USER =
            "INSERT INTO users (full_name, email, password_hash, phone_number, role, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_BY_ID =
            "SELECT user_id, full_name, email, password_hash, phone_number, role, status, created_at, updated_at " +
            "FROM users WHERE user_id = ?";

    private static final String SQL_SELECT_BY_EMAIL =
            "SELECT user_id, full_name, email, password_hash, phone_number, role, status, created_at, updated_at " +
            "FROM users WHERE email = ?";

    private static final String SQL_UPDATE_PROFILE =
            "UPDATE users SET full_name = ?, phone_number = ? WHERE user_id = ?";

    private static final String SQL_UPDATE_PASSWORD =
            "UPDATE users SET password_hash = ? WHERE user_id = ?";

    private static final String SQL_DEACTIVATE_USER =
            "UPDATE users SET status = 'DEACTIVATED' WHERE user_id = ?";

    private static final String SQL_ACTIVATE_USER =
            "UPDATE users SET status = 'ACTIVE' WHERE user_id = ?";

    private static final String SQL_DELETE_PERMANENT =
            "DELETE FROM users WHERE user_id = ?";

    @Override
    public boolean registerUser(User user) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail().trim().toLowerCase());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getPhoneNumber());
            ps.setString(5, user.getRole() != null ? user.getRole().name() : UserRole.CUSTOMER.name());
            ps.setString(6, user.getStatus() != null ? user.getStatus().name() : UserStatus.ACTIVE.name());

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    user.setUserId(rs.getInt(1));
                }
                LOGGER.info("User registered successfully with ID: " + user.getUserId() + " (" + user.getEmail() + ")");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error registering user: " + user.getEmail(), e);
        } finally {
            DBConnection.closeAll(conn, ps, rs);
        }
        return false;
    }

    @Override
    public User authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return null;
        }

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_SELECT_BY_EMAIL);
            ps.setString(1, email.trim().toLowerCase());

            rs = ps.executeQuery();
            if (rs.next()) {
                User user = mapRowToUser(rs);

                // Reject deactivated or suspended accounts
                if (user.getStatus() != UserStatus.ACTIVE) {
                    LOGGER.warning("Authentication failed: Account deactivated for email " + email);
                    return null;
                }

                // Verify plain password against stored BCrypt hash
                if (PasswordUtil.verifyPassword(rawPassword, user.getPasswordHash())) {
                    LOGGER.info("Authentication successful for user ID: " + user.getUserId() + " (" + user.getRole() + ")");
                    return user;
                } else {
                    LOGGER.warning("Authentication failed: Incorrect password for email " + email);
                }
            } else {
                LOGGER.warning("Authentication failed: User not found with email " + email);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during authentication for email: " + email, e);
        } finally {
            DBConnection.closeAll(conn, ps, rs);
        }
        return null;
    }

    @Override
    public User getUserById(int userId) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_SELECT_BY_ID);
            ps.setInt(1, userId);

            rs = ps.executeQuery();
            if (rs.next()) {
                return mapRowToUser(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving user by ID: " + userId, e);
        } finally {
            DBConnection.closeAll(conn, ps, rs);
        }
        return null;
    }

    @Override
    public User getUserByEmail(String email) {
        if (email == null) return null;

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_SELECT_BY_EMAIL);
            ps.setString(1, email.trim().toLowerCase());

            rs = ps.executeQuery();
            if (rs.next()) {
                return mapRowToUser(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving user by email: " + email, e);
        } finally {
            DBConnection.closeAll(conn, ps, rs);
        }
        return null;
    }

    @Override
    public List<User> getAllUsers(String roleFilter, String searchKeyword) {
        List<User> userList = new ArrayList<>();
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        // Construct dynamic parameterized SQL query
        StringBuilder sql = new StringBuilder("SELECT user_id, full_name, email, password_hash, phone_number, ")
                .append("role, status, created_at, updated_at FROM users WHERE 1=1 ");

        List<Object> parameters = new ArrayList<>();

        // Role filtering
        if (roleFilter != null && !roleFilter.trim().isEmpty() && !roleFilter.equalsIgnoreCase("ALL")) {
            sql.append("AND role = ? ");
            parameters.add(roleFilter.trim().toUpperCase());
        }

        // Search keyword across full name and email
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            sql.append("AND (LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR phone_number LIKE ?) ");
            String wildcard = "%" + searchKeyword.trim().toLowerCase() + "%";
            parameters.add(wildcard);
            parameters.add(wildcard);
            parameters.add(wildcard);
        }

        sql.append("ORDER BY created_at DESC");

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(sql.toString());

            // Bind dynamic parameters safely
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }

            rs = ps.executeQuery();
            while (rs.next()) {
                userList.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching users with filter [role=" + roleFilter + ", keyword=" + searchKeyword + "]", e);
        } finally {
            DBConnection.closeAll(conn, ps, rs);
        }

        return userList;
    }

    @Override
    public boolean updateUserProfile(User user) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_UPDATE_PROFILE);
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getPhoneNumber());
            ps.setInt(3, user.getUserId());

            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating profile for user ID: " + user.getUserId(), e);
        } finally {
            DBConnection.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public boolean changePassword(int userId, String oldPassword, String newPassword) {
        User user = getUserById(userId);
        if (user == null) {
            LOGGER.warning("Cannot change password: User not found for ID: " + userId);
            return false;
        }

        // Verify current password first
        if (!PasswordUtil.verifyPassword(oldPassword, user.getPasswordHash())) {
            LOGGER.warning("Password change rejected: Current password does not match for user ID: " + userId);
            return false;
        }

        // Hash new password using BCrypt
        String newHashedPassword = PasswordUtil.hashPassword(newPassword);

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_UPDATE_PASSWORD);
            ps.setString(1, newHashedPassword);
            ps.setInt(2, userId);

            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error updating password for user ID: " + userId, e);
        } finally {
            DBConnection.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public boolean deactivateUser(int userId) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_DEACTIVATE_USER);
            ps.setInt(1, userId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("User soft-deactivated successfully: ID " + userId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deactivating user ID: " + userId, e);
        } finally {
            DBConnection.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public boolean activateUser(int userId) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_ACTIVATE_USER);
            ps.setInt(1, userId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("User reactivated successfully: ID " + userId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error activating user ID: " + userId, e);
        } finally {
            DBConnection.closeAll(conn, ps, null);
        }
        return false;
    }

    @Override
    public boolean deleteUserPermanent(int userId) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = DBConnection.getConnection();
            ps = conn.prepareStatement(SQL_DELETE_PERMANENT);
            ps.setInt(1, userId);

            int affected = ps.executeUpdate();
            if (affected > 0) {
                LOGGER.info("User permanently deleted from database: ID " + userId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error permanently deleting user ID: " + userId, e);
        } finally {
            DBConnection.closeAll(conn, ps, null);
        }
        return false;
    }

    /**
     * Maps the current row of a ResultSet to a User domain model.
     */
    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPhoneNumber(rs.getString("phone_number"));
        user.setRole(UserRole.fromString(rs.getString("role")));
        user.setStatus(UserStatus.fromString(rs.getString("status")));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setUpdatedAt(rs.getTimestamp("updated_at"));
        return user;
    }
}
