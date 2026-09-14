package com.skylanka.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe Database Connection Utility for MySQL JDBC.
 * Loads configuration properties from classpath (db.properties) with sane fallback defaults.
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());

    private static final String DEFAULT_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/skylanka_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";

    private static String driverClassName;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    static {
        loadProperties();
        registerDriver();
    }

    private DBConnection() {
        // Utility class: private constructor
    }

    private static void loadProperties() {
        Properties props = new Properties();
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input != null) {
                props.load(input);
                driverClassName = props.getProperty("db.driver", DEFAULT_DRIVER);
                jdbcUrl = props.getProperty("db.url", DEFAULT_URL);
                username = props.getProperty("db.username", DEFAULT_USER);
                password = props.getProperty("db.password", DEFAULT_PASSWORD);
                LOGGER.info("Successfully loaded database properties from db.properties");
            } else {
                LOGGER.warning("db.properties not found on classpath. Falling back to default connection settings.");
                driverClassName = DEFAULT_DRIVER;
                jdbcUrl = DEFAULT_URL;
                username = DEFAULT_USER;
                password = DEFAULT_PASSWORD;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading db.properties. Using default credentials.", e);
            driverClassName = DEFAULT_DRIVER;
            jdbcUrl = DEFAULT_URL;
            username = DEFAULT_USER;
            password = DEFAULT_PASSWORD;
        }
    }

    private static void registerDriver() {
        try {
            Class.forName(driverClassName);
            LOGGER.info("MySQL JDBC Driver registered: " + driverClassName);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Failed to load MySQL JDBC driver class: " + driverClassName, e);
            throw new RuntimeException("MySQL JDBC Driver not found in classpath", e);
        }
    }

    /**
     * Establishes and returns an active database connection.
     *
     * @return Connection to MySQL database
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Closes ResultSet safely without throwing checked exceptions.
     */
    public static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing ResultSet", e);
            }
        }
    }

    /**
     * Closes Statement/PreparedStatement safely without throwing checked exceptions.
     */
    public static void close(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Statement", e);
            }
        }
    }

    /**
     * Closes Connection safely without throwing checked exceptions.
     */
    public static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing Connection", e);
            }
        }
    }

    /**
     * Helper to close ResultSet, Statement, and Connection in one call.
     */
    public static void closeAll(Connection conn, Statement stmt, ResultSet rs) {
        close(rs);
        close(stmt);
        close(conn);
    }
}
