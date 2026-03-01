package com.raez.finance.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.net.URL;

public class RoleSelectionController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    @FXML private Button btnSuperAdmin;
    @FXML private Button btnFinanceAdmin;

    @FXML
    public void initialize() {}

    @FXML
    private void handleSuperAdminSelect(ActionEvent event) {
        loadView("AdminLogin.fxml", event);
    }

    @FXML
    private void handleFinanceAdminSelect(ActionEvent event) {
        loadView("FinanceUserLogin.fxml", event);
    }

    private void loadView(String fxmlName, ActionEvent event) {
        try {
            URL url = getClass().getResource(VIEW_PATH + fxmlName);
            if (url == null) throw new IllegalStateException("Resource not found: " + VIEW_PATH + fxmlName);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlName, e);
        }
    }
}