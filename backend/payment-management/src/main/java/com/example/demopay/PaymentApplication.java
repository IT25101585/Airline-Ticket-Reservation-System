package com.example.demopay;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class PaymentApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(PaymentApplication.class.getResource("payment-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 720, 560);
        stage.setTitle("SkyLanka Air Travels - Payment Management System");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}