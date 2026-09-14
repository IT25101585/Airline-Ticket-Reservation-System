package com.example.demopay.model;

import java.sql.Timestamp;

public class Payment {
    private int paymentId;
    private int bookingId;
    private int userId;
    private double amount;
    private String paymentMethod;
    private String cardNumber;
    private String transactionId;
    private String paymentStatus;
    private String invoiceNumber;
    private Timestamp createdAt;

    public Payment() {}

    public Payment(int bookingId, int userId, double amount, String paymentMethod, String cardNumber, String transactionId, String paymentStatus, String invoiceNumber) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.cardNumber = cardNumber;
        this.transactionId = transactionId;
        this.paymentStatus = paymentStatus;
        this.invoiceNumber = invoiceNumber;
    }

    public Payment(int paymentId, int bookingId, int userId, double amount, String paymentMethod, String cardNumber, String transactionId, String paymentStatus, String invoiceNumber, Timestamp createdAt) {
        this(bookingId, userId, amount, paymentMethod, cardNumber, transactionId, paymentStatus, invoiceNumber);
        this.paymentId = paymentId;
        this.createdAt = createdAt;
    }

    // Getters
    public int getPaymentId() { return paymentId; }
    public int getBookingId() { return bookingId; }
    public int getUserId() { return userId; }
    public double getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getCardNumber() { return cardNumber; }
    public String getTransactionId() { return transactionId; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public Timestamp getCreatedAt() { return createdAt; }

    // Setters
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }
    public void setUserId(int userId) { this.userId = userId; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}