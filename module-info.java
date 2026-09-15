module com.example.demoseat {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires com.microsoft.sqlserver.jdbc;

    opens com.example.demoseat to javafx.fxml;
    opens com.example.demoseat.controller to javafx.fxml;
    opens com.example.demoseat.model to javafx.base, javafx.fxml;

    exports com.example.demoseat;
    exports com.example.demoseat.controller;
    exports com.example.demoseat.model;
}