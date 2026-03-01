package com.raez.finance.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class NotificationsAlertsController {

    @FXML
    private VBox vboxAlerts;

    @FXML
    private VBox vboxNotifications;

    @FXML
    public void initialize() {
        System.out.println("Notifications & Alerts Initialized");
        // Cursor will add logic here to fetch Alerts and Notifications 
        // from the database and inject them into the VBox containers.
        // The mock elements currently in the FXML will be cleared out 
        // using vboxAlerts.getChildren().clear() before rendering live data.
    }
}