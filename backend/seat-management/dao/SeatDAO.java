package com.example.demoseat.dao;
import com.example.demoseat.model.Seat;
import com.example.demoseat.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "SqlResolve", "UnusedReturnValue"})
public class SeatDAO {

    public boolean addSeat(Seat seat) throws SQLException {
        String sql = "INSERT INTO Seats (seat_id, flight_id, cabin_class, price, is_booked) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seat.getSeatId());
            stmt.setString(2, seat.getFlightId());
            stmt.setString(3, seat.getCabinClass());
            stmt.setDouble(4, seat.getPrice());
            stmt.setBoolean(5, seat.isBooked());
            return stmt.executeUpdate() > 0;
        }
    }

    public List<Seat> getAllSeats() throws SQLException {
        List<Seat> seats = new ArrayList<>();
        String sql = "SELECT seat_id, flight_id, cabin_class, price, is_booked FROM Seats ORDER BY flight_id, seat_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                seats.add(new Seat(
                        rs.getString("seat_id"),
                        rs.getString("flight_id"),
                        rs.getString("cabin_class"),
                        rs.getDouble("price"),
                        rs.getBoolean("is_booked")
                ));
            }
        }
        return seats;
    }

    public List<Seat> getSeatsByFlight(String flightId) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        String sql = "SELECT seat_id, flight_id, cabin_class, price, is_booked FROM Seats WHERE flight_id = ? ORDER BY seat_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, flightId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    seats.add(new Seat(
                            rs.getString("seat_id"),
                            rs.getString("flight_id"),
                            rs.getString("cabin_class"),
                            rs.getDouble("price"),
                            rs.getBoolean("is_booked")
                    ));
                }
            }
        }
        return seats;
    }

    public Seat getSeat(String seatId, String flightId) throws SQLException {
        String sql = "SELECT seat_id, flight_id, cabin_class, price, is_booked FROM Seats WHERE seat_id = ? AND flight_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seatId);
            stmt.setString(2, flightId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Seat(
                            rs.getString("seat_id"),
                            rs.getString("flight_id"),
                            rs.getString("cabin_class"),
                            rs.getDouble("price"),
                            rs.getBoolean("is_booked")
                    );
                }
            }
        }
        return null;
    }

    public boolean updateSeat(Seat seat) throws SQLException {
        String sql = "UPDATE Seats SET cabin_class = ?, price = ?, is_booked = ? WHERE seat_id = ? AND flight_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seat.getCabinClass());
            stmt.setDouble(2, seat.getPrice());
            stmt.setBoolean(3, seat.isBooked());
            stmt.setString(4, seat.getSeatId());
            stmt.setString(5, seat.getFlightId());
            return stmt.executeUpdate() > 0;
        }
    }

    public boolean deleteSeat(String seatId, String flightId) throws SQLException {
        String sql = "DELETE FROM Seats WHERE seat_id = ? AND flight_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, seatId);
            stmt.setString(2, flightId);
            return stmt.executeUpdate() > 0;
        }
    }
}