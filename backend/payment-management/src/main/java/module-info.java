module com.example.demopay {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.microsoft.sqlserver.jdbc;
    requires org.kordamp.bootstrapfx.core;

    // Open packages to javafx.fxml for reflection loading
    opens com.example.demopay to javafx.fxml;
    opens com.example.demopay.controller to javafx.fxml;
    opens com.example.demopay.model to javafx.base, javafx.fxml;
    opens com.example.demopay.dao to javafx.fxml;
    opens com.example.demopay.util to javafx.fxml;

    // Export packages for project accessibility
    exports com.example.demopay;
    exports com.example.demopay.controller;
    exports com.example.demopay.model;
    exports com.example.demopay.dao;
    exports com.example.demopay.util;
}