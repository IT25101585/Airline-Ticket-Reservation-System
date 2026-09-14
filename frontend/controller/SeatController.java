package com.example.demoseat.controller;

import com.example.demoseat.dao.SeatDAO;
import com.example.demoseat.model.Seat;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SeatController {

    @FXML private TextField txtFlightId;
    @FXML private TextField txtSeatId;
    @FXML private TextField txtPrice;
    @FXML private ComboBox<String> cmbClass;
    @FXML private CheckBox chkBooked;

    @FXML private VBox flightPanesContainer;

    private final SeatDAO seatDAO = new SeatDAO();

    @FXML
    public void initialize() {
        cmbClass.setItems(FXCollections.observableArrayList("Economy", "Business"));
        cmbClass.getSelectionModel().selectFirst();


        txtFlightId.textProperty().addListener((obs, oldVal, newVal) -> loadTableData());

        loadTableData();
    }

    @FXML
    private void loadTableData() {
        try {
            flightPanesContainer.getChildren().clear();
            List<Seat> allSeats;
            String filterFlightId = txtFlightId.getText().trim();

            if (filterFlightId.isEmpty()) {
                allSeats = seatDAO.getAllSeats();
            } else {
                allSeats = seatDAO.getSeatsByFlight(filterFlightId);
            }


            Map<String, List<Seat>> groupedSeats = allSeats.stream()
                    .collect(Collectors.groupingBy(Seat::getFlightId));

            if (groupedSeats.isEmpty() && !filterFlightId.isEmpty()) {
                Label emptyLabel = new Label("No seats found for flight: " + filterFlightId);
                emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280; -fx-padding: 20;");
                flightPanesContainer.getChildren().add(emptyLabel);
                return;
            }


            for (Map.Entry<String, List<Seat>> entry : groupedSeats.entrySet()) {
                String flightId = entry.getKey();
                List<Seat> seatsForFlight = entry.getValue();

                VBox flightCard = createFlightCard(flightId, seatsForFlight);
                flightPanesContainer.getChildren().add(flightCard);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to load seat data: " + e.getMessage());
        }
    }

    private VBox createFlightCard(String flightId, List<Seat> seats) {
        VBox card = new VBox(10);
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-padding: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 2);");


        Label header = new Label("✈  Flight: " + flightId);
        header.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #00205b; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 8 0;");


        TableView<Seat> table = new TableView<>();
        table.setPrefHeight(180);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle("-fx-background-radius: 4; -fx-border-color: #e5e7eb;");

        TableColumn<Seat, String> colSeatId = new TableColumn<>("Seat ID");
        colSeatId.setCellValueFactory(new PropertyValueFactory<>("seatId"));

        TableColumn<Seat, String> colClass = new TableColumn<>("Cabin Class");
        colClass.setCellValueFactory(new PropertyValueFactory<>("cabinClass"));

        TableColumn<Seat, Double> colPrice = new TableColumn<>("Price ($)");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<Seat, Boolean> colBooked = new TableColumn<>("Booked");
        colBooked.setCellValueFactory(new PropertyValueFactory<>("booked"));

        table.getColumns().addAll(colSeatId, colClass, colPrice, colBooked);

        ObservableList<Seat> seatList = FXCollections.observableArrayList(seats);
        table.setItems(seatList);


        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                txtSeatId.setText(newVal.getSeatId());
                txtFlightId.setText(newVal.getFlightId());
                cmbClass.setValue(newVal.getCabinClass());
                txtPrice.setText(String.valueOf(newVal.getPrice()));
                chkBooked.setSelected(newVal.isBooked());
            }
        });

        card.getChildren().addAll(header, table);
        return card;
    }

    @FXML
    private void handleSave() {
        try {
            String seatId = txtSeatId.getText().trim();
            String flightId = txtFlightId.getText().trim();
            String cabinClass = cmbClass.getValue();
            double price = Double.parseDouble(txtPrice.getText().trim());
            boolean isBooked = chkBooked.isSelected();

            if (seatId.isEmpty() || flightId.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation Error", "Please fill in all required fields.");
                return;
            }

            Seat seat = new Seat(seatId, flightId, cabinClass, price, isBooked);

            if (seatDAO.getSeat(seatId, flightId) == null) {
                seatDAO.addSeat(seat);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Seat added successfully!");
            } else {
                seatDAO.updateSeat(seat);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Seat updated successfully!");
            }

            loadTableData();
            handleClear();
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Price must be a valid numeric value.");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Operation failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        try {
            String seatId = txtSeatId.getText().trim();
            String flightId = txtFlightId.getText().trim();

            if (seatId.isEmpty() || flightId.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Selection Error", "Please select a seat to delete.");
                return;
            }

            if (seatDAO.deleteSeat(seatId, flightId)) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Seat deleted successfully!");
                loadTableData();
                handleClear();
            } else {
                showAlert(Alert.AlertType.WARNING, "Not Found", "Seat record could not be found.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Delete operation failed.");
        }
    }

    @FXML
    private void handleClear() {
        txtFlightId.clear();
        txtSeatId.clear();
        txtPrice.clear();
        chkBooked.setSelected(false);
        loadTableData();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}