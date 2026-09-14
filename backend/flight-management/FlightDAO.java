package com.airline.dao;

import com.airline.model.Flight;
import com.airline.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) for Flight entities.
 * Handles all JDBC operations (CRUD, Search, Dashboard Stats) using PreparedStatements.
 */
public class FlightDAO {

    /**
     * Maps a current row of a ResultSet to a Flight domain object.
     */
    private Flight mapResultSetToFlight(ResultSet rs) throws SQLException {
        Flight flight = new Flight();
        flight.setFlightId(rs.getInt("flight_id"));
        flight.setFlightNumber(rs.getString("flight_number"));
        flight.setAirlineName(rs.getString("airline_name"));
        flight.setDepartureAirport(rs.getString("departure_airport"));
        flight.setArrivalAirport(rs.getString("arrival_airport"));
        flight.setDepartureCity(rs.getString("departure_city"));
        flight.setArrivalCity(rs.getString("arrival_city"));
        flight.setDepartureDate(rs.getString("departure_date"));
        flight.setDepartureTime(rs.getString("departure_time"));
        flight.setArrivalDate(rs.getString("arrival_date"));
        flight.setArrivalTime(rs.getString("arrival_time"));
        flight.setAircraftType(rs.getString("aircraft_type"));
        flight.setTotalSeats(rs.getInt("total_seats"));
        flight.setAvailableSeats(rs.getInt("available_seats"));
        flight.setTicketPrice(rs.getBigDecimal("ticket_price"));
        flight.setStatus(rs.getString("status"));
        
        try {
            flight.setCreatedAt(rs.getString("created_at"));
            flight.setUpdatedAt(rs.getString("updated_at"));
        } catch (SQLException ignored) {
            // In case columns are omitted in projection queries
        }
        return flight;
    }

    /**
     * CREATE: Adds a new flight to the database.
     * @param flight The flight entity to persist.
     * @return true if insertion succeeded, false otherwise.
     */
    public boolean addFlight(Flight flight) {
        String sql = "INSERT INTO flights (" +
                "flight_number, airline_name, departure_airport, arrival_airport, " +
                "departure_city, arrival_city, departure_date, departure_time, " +
                "arrival_date, arrival_time, aircraft_type, total_seats, " +
                "available_seats, ticket_price, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet generatedKeys = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            stmt.setString(1, flight.getFlightNumber());
            stmt.setString(2, flight.getAirlineName());
            stmt.setString(3, flight.getDepartureAirport());
            stmt.setString(4, flight.getArrivalAirport());
            stmt.setString(5, flight.getDepartureCity());
            stmt.setString(6, flight.getArrivalCity());
            stmt.setString(7, flight.getDepartureDate());
            stmt.setString(8, flight.getDepartureTime());
            stmt.setString(9, flight.getArrivalDate());
            stmt.setString(10, flight.getArrivalTime());
            stmt.setString(11, flight.getAircraftType());
            stmt.setInt(12, flight.getTotalSeats());
            stmt.setInt(13, flight.getAvailableSeats());
            stmt.setBigDecimal(14, flight.getTicketPrice());
            stmt.setString(15, flight.getStatus());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    flight.setFlightId(generatedKeys.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("[FlightDAO] addFlight error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            DBConnection.close(conn, stmt, generatedKeys);
        }
    }

    /**
     * READ: Retrieves all flights, ordered by departure date and time ascending.
     * @return List of Flight objects.
     */
    public List<Flight> getAllFlights() {
        List<Flight> list = new ArrayList<>();
        String sql = "SELECT * FROM flights ORDER BY departure_date ASC, departure_time ASC";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                list.add(mapResultSetToFlight(rs));
            }
        } catch (SQLException e) {
            System.err.println("[FlightDAO] getAllFlights error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBConnection.close(conn, stmt, rs);
        }
        return list;
    }

    /**
     * READ: Retrieves an individual flight by its Primary Key (flight_id).
     * @param flightId Flight ID.
     * @return Flight object or null if not found.
     */
    public Flight getFlightById(int flightId) {
        String sql = "SELECT * FROM flights WHERE flight_id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, flightId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToFlight(rs);
            }
        } catch (SQLException e) {
            System.err.println("[FlightDAO] getFlightById error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBConnection.close(conn, stmt, rs);
        }
        return null;
    }

    /**
     * Checks if a flight number already exists for another flight.
     * Useful for unique constraint validation.
     * @param flightNumber Flight number to check.
     * @param excludeFlightId If greater than 0, excludes this flight_id (for update operations).
     * @return true if flight number exists, false otherwise.
     */
    public boolean isFlightNumberExists(String flightNumber, int excludeFlightId) {
        String sql = "SELECT 1 FROM flights WHERE flight_number = ? AND flight_id != ?";
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, flightNumber);
            stmt.setInt(2, excludeFlightId);
            rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("[FlightDAO] isFlightNumberExists error: " + e.getMessage());
            return false;
        } finally {
            DBConnection.close(conn, stmt, rs);
        }
    }

    /**
     * UPDATE: Updates existing flight record.
     * @param flight Flight entity with updated fields.
     * @return true if update succeeded, false otherwise.
     */
    public boolean updateFlight(Flight flight) {
        String sql = "UPDATE flights SET " +
                "flight_number = ?, airline_name = ?, departure_airport = ?, arrival_airport = ?, " +
                "departure_city = ?, arrival_city = ?, departure_date = ?, departure_time = ?, " +
                "arrival_date = ?, arrival_time = ?, aircraft_type = ?, total_seats = ?, " +
                "available_seats = ?, ticket_price = ?, status = ? " +
                "WHERE flight_id = ?";

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);

            stmt.setString(1, flight.getFlightNumber());
            stmt.setString(2, flight.getAirlineName());
            stmt.setString(3, flight.getDepartureAirport());
            stmt.setString(4, flight.getArrivalAirport());
            stmt.setString(5, flight.getDepartureCity());
            stmt.setString(6, flight.getArrivalCity());
            stmt.setString(7, flight.getDepartureDate());
            stmt.setString(8, flight.getDepartureTime());
            stmt.setString(9, flight.getArrivalDate());
            stmt.setString(10, flight.getArrivalTime());
            stmt.setString(11, flight.getAircraftType());
            stmt.setInt(12, flight.getTotalSeats());
            stmt.setInt(13, flight.getAvailableSeats());
            stmt.setBigDecimal(14, flight.getTicketPrice());
            stmt.setString(15, flight.getStatus());
            stmt.setInt(16, flight.getFlightId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[FlightDAO] updateFlight error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            DBConnection.close(conn, stmt);
        }
    }

    /**
     * DELETE: Deletes or cancels a flight by ID.
     * @param flightId The primary key of the flight.
     * @return true if deletion succeeded, false otherwise.
     */
    public boolean deleteFlight(int flightId) {
        String sql = "DELETE FROM flights WHERE flight_id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, flightId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[FlightDAO] deleteFlight error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            DBConnection.close(conn, stmt);
        }
    }

    /**
     * CANCEL: Sets flight status to 'Cancelled' (Soft-cancel alternative).
     * @param flightId Flight ID.
     * @return true if updated successfully.
     */
    public boolean cancelFlight(int flightId) {
        String sql = "UPDATE flights SET status = 'Cancelled' WHERE flight_id = ?";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, flightId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[FlightDAO] cancelFlight error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            DBConnection.close(conn, stmt);
        }
    }

    /**
     * SEARCH: Filters flights by departure city, arrival city, and/or travel date.
     * Allows empty parameters for flexible searching.
     * @param departureCity Optional departure city filter
     * @param arrivalCity Optional arrival city filter
     * @param travelDate Optional travel date filter (YYYY-MM-DD)
     * @return Matching List of Flights.
     */
    public List<Flight> searchFlights(String departureCity, String arrivalCity, String travelDate) {
        List<Flight> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM flights WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (departureCity != null && !departureCity.trim().isEmpty()) {
            sql.append("AND (LOWER(departure_city) LIKE ? OR LOWER(departure_airport) LIKE ?) ");
            String depParam = "%" + departureCity.trim().toLowerCase() + "%";
            params.add(depParam);
            params.add(depParam);
        }

        if (arrivalCity != null && !arrivalCity.trim().isEmpty()) {
            sql.append("AND (LOWER(arrival_city) LIKE ? OR LOWER(arrival_airport) LIKE ?) ");
            String arrParam = "%" + arrivalCity.trim().toLowerCase() + "%";
            params.add(arrParam);
            params.add(arrParam);
        }

        if (travelDate != null && !travelDate.trim().isEmpty()) {
            sql.append("AND departure_date = ? ");
            params.add(travelDate.trim());
        }

        sql.append("ORDER BY departure_date ASC, departure_time ASC");

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql.toString());

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToFlight(rs));
            }
        } catch (SQLException e) {
            System.err.println("[FlightDAO] searchFlights error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBConnection.close(conn, stmt, rs);
        }
        return list;
    }

    /**
     * STATISTICS: Computes key metrics for the Staff Dashboard.
     * @return Map containing: totalFlights, scheduledFlights, cancelledFlights, availableSeats
     */
    public Map<String, Object> getFlightStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFlights", 0);
        stats.put("scheduledFlights", 0);
        stats.put("cancelledFlights", 0);
        stats.put("availableSeats", 0);

        String sql = "SELECT " +
                "COUNT(*) AS total_flights, " +
                "SUM(CASE WHEN status = 'Scheduled' THEN 1 ELSE 0 END) AS scheduled_flights, " +
                "SUM(CASE WHEN status = 'Cancelled' THEN 1 ELSE 0 END) AS cancelled_flights, " +
                "COALESCE(SUM(available_seats), 0) AS total_available_seats " +
                "FROM flights";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            if (rs.next()) {
                stats.put("totalFlights", rs.getInt("total_flights"));
                stats.put("scheduledFlights", rs.getInt("scheduled_flights"));
                stats.put("cancelledFlights", rs.getInt("cancelled_flights"));
                stats.put("availableSeats", rs.getInt("total_available_seats"));
            }
        } catch (SQLException e) {
            System.err.println("[FlightDAO] getFlightStatistics error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DBConnection.close(conn, stmt, rs);
        }
        return stats;
    }
}
