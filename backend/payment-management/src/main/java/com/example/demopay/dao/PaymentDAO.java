package com.example.demopay.dao;

import com.example.demopay.model.Payment;
import com.example.demopay.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    // CREATE: Process payment & Generate invoice
    public boolean processPaymentTransaction(Payment payment) {
        String insertPaymentSQL = "INSERT INTO payments (booking_id, user_id, amount, payment_method, card_number, transaction_id, payment_status, invoice_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        String updateBookingSQL = "UPDATE bookings SET status = 'Paid' WHERE booking_id = ?;";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement payStmt = conn.prepareStatement(insertPaymentSQL);
                 PreparedStatement bookStmt = conn.prepareStatement(updateBookingSQL)) {

                payStmt.setInt(1, payment.getBookingId());
                payStmt.setInt(2, payment.getUserId());
                payStmt.setBigDecimal(3, BigDecimal.valueOf(payment.getAmount()));
                payStmt.setString(4, payment.getPaymentMethod());
                payStmt.setString(5, payment.getCardNumber());
                payStmt.setString(6, payment.getTransactionId());
                payStmt.setString(7, payment.getPaymentStatus());
                payStmt.setString(8, payment.getInvoiceNumber());
                payStmt.executeUpdate();

                bookStmt.setInt(1, payment.getBookingId());
                bookStmt.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // READ: View transaction & payment history
    public List<Payment> getAllPayments() {
        List<Payment> list = new ArrayList<>();
        String sql = "SELECT * FROM payments ORDER BY payment_id DESC;";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Payment payment = new Payment(
                        rs.getInt("payment_id"),
                        rs.getInt("booking_id"),
                        rs.getInt("user_id"),
                        rs.getDouble("amount"),
                        rs.getString("payment_method"),
                        rs.getString("card_number"),
                        rs.getString("transaction_id"),
                        rs.getString("payment_status"),
                        rs.getString("invoice_number"),
                        rs.getTimestamp("created_at")
                );
                list.add(payment);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // UPDATE: Update status / Apply refund[cite: 2]
    public boolean updatePaymentStatus(int paymentId, String status) {
        String updatePaymentSQL = "UPDATE payments SET payment_status = ? WHERE payment_id = ?;";
        String updateBookingSQL = "UPDATE bookings SET status = ? WHERE booking_id = (SELECT booking_id FROM payments WHERE payment_id = ?);";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement payStmt = conn.prepareStatement(updatePaymentSQL);
                 PreparedStatement bookStmt = conn.prepareStatement(updateBookingSQL)) {

                payStmt.setString(1, status);
                payStmt.setInt(2, paymentId);
                payStmt.executeUpdate();

                String bookingStatus = status.equalsIgnoreCase("Refunded") ? "Cancelled" : status;
                bookStmt.setString(1, bookingStatus);
                bookStmt.setInt(2, paymentId);
                bookStmt.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // DELETE: Void a failed/duplicate transaction[cite: 2]
    public boolean voidTransaction(int paymentId) {
        String sql = "DELETE FROM payments WHERE payment_id = ?;";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, paymentId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}