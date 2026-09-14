package com.example.demopay.controller;

import com.example.demopay.dao.PaymentDAO;
import com.example.demopay.model.Payment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.UUID;

public class PaymentController {

    // Form inputs (Create)
    @FXML private TextField bookingIdInput;
    @FXML private TextField amountInput;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private PasswordField cvvField;
    @FXML private Button payButton;
    @FXML private Label statusLabel;

    // Table view (Read, Update, Delete)
    @FXML private TableView<Payment> paymentTable;
    @FXML private TableColumn<Payment, Integer> colId;
    @FXML private TableColumn<Payment, Integer> colBookingId;
    @FXML private TableColumn<Payment, Double> colAmount;
    @FXML private TableColumn<Payment, String> colMethod;
    @FXML private TableColumn<Payment, String> colCard;
    @FXML private TableColumn<Payment, String> colTxnId;
    @FXML private TableColumn<Payment, String> colInvoice;
    @FXML private TableColumn<Payment, String> colStatus;

    private final PaymentDAO paymentDAO = new PaymentDAO();

    @FXML
    public void initialize() {
        paymentMethodBox.setItems(FXCollections.observableArrayList("Visa", "MasterCard"));
        paymentMethodBox.setValue("Visa");

        // Table setup
        colId.setCellValueFactory(new PropertyValueFactory<>("paymentId"));
        colBookingId.setCellValueFactory(new PropertyValueFactory<>("bookingId"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colMethod.setCellValueFactory(new PropertyValueFactory<>("paymentMethod"));
        colCard.setCellValueFactory(new PropertyValueFactory<>("cardNumber"));
        colTxnId.setCellValueFactory(new PropertyValueFactory<>("transactionId"));
        colInvoice.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));

        loadPaymentHistory();
    }

    // CREATE: Process payment / generate invoice[cite: 2]
    @FXML
    protected void onPayButtonClick() {
        String cardNumber = cardNumberField.getText().trim();
        String expiry = expiryField.getText().trim();
        String cvv = cvvField.getText().trim();

        if (cardNumber.length() < 16 || expiry.isEmpty() || cvv.isEmpty()) {
            setStatus("Please complete all card details correctly.", true);
            return;
        }

        try {
            int bookingId = Integer.parseInt(bookingIdInput.getText().trim());
            double amount = Double.parseDouble(amountInput.getText().trim());
            int userId = 201;
            String method = paymentMethodBox.getValue();
            String maskedCard = "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);

            String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String invoiceNumber = "INV-" + System.currentTimeMillis() / 1000;

            Payment payment = new Payment(bookingId, userId, amount, method, maskedCard, transactionId, "Paid", invoiceNumber);

            if (paymentDAO.processPaymentTransaction(payment)) {
                setStatus("Payment Successful! Invoice Issued: " + invoiceNumber, false);
                clearFields();
                loadPaymentHistory();
            } else {
                setStatus("Payment failed. Please check DB connection or Booking ID.", true);
            }
        } catch (NumberFormatException e) {
            setStatus("Invalid numerical input for Booking ID or Amount.", true);
        }
    }

    // READ: View transaction & payment history[cite: 2]
    @FXML
    protected void loadPaymentHistory() {
        ObservableList<Payment> payments = FXCollections.observableArrayList(paymentDAO.getAllPayments());
        paymentTable.setItems(payments);
    }

    // UPDATE: Update status; apply refund[cite: 2]
    @FXML
    protected void onRefundClick() {
        Payment selected = paymentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Please select a transaction to refund.", true);
            return;
        }

        if ("Refunded".equalsIgnoreCase(selected.getPaymentStatus())) {
            setStatus("Selected transaction is already refunded.", true);
            return;
        }

        if (paymentDAO.updatePaymentStatus(selected.getPaymentId(), "Refunded")) {
            setStatus("Refund processed successfully for Txn: " + selected.getTransactionId(), false);
            loadPaymentHistory();
        } else {
            setStatus("Failed to process refund.", true);
        }
    }

    // DELETE: Void a failed/duplicate transaction[cite: 2]
    @FXML
    protected void onVoidClick() {
        Payment selected = paymentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Please select a transaction to void.", true);
            return;
        }

        if (paymentDAO.voidTransaction(selected.getPaymentId())) {
            setStatus("Transaction voided and deleted successfully.", false);
            loadPaymentHistory();
        } else {
            setStatus("Failed to void transaction.", true);
        }
    }

    private void clearFields() {
        cardNumberField.clear();
        expiryField.clear();
        cvvField.clear();
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setStyle(isError ? "-fx-text-fill: red;" : "-fx-text-fill: green;");
        statusLabel.setText(message);
    }
}