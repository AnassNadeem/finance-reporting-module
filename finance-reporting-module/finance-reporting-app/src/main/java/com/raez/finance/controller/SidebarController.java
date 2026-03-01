package com.raez.finance.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;

import java.net.URL;

public class SidebarController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    private MainLayoutController mainLayoutController;

    @FXML private Button btnDashboard;
    @FXML private Button btnReports;
    @FXML private Button btnInsights;
    @FXML private Button btnProfitability;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void initialize() {
        // User Management tab visibility is controlled in SettingsController by SessionManager.getRole().
    }

    @FXML
    private void handleNavDashboard(ActionEvent event) {
        loadContent("Overview.fxml");
        setActiveButton(btnDashboard);
    }

    @FXML
    private void handleNavReports(ActionEvent event) {
        loadContent("DetailedReports.fxml");
        setActiveButton(btnReports);
    }

    @FXML
    private void handleNavInsights(ActionEvent event) {
        loadContent("CustomerInsights.fxml");
        setActiveButton(btnInsights);
    }

    @FXML
    private void handleNavProfitability(ActionEvent event) {
        loadContent("ProductProfitability.fxml");
        setActiveButton(btnProfitability);
    }

    @FXML
    private void handleNavSettings(ActionEvent event) {
        loadContent("Settings.fxml");
        setActiveButton(btnSettings);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        if (mainLayoutController != null) {
            mainLayoutController.handleLogout();
        }
    }

    private void loadContent(String fxmlName) {
        if (mainLayoutController == null) return;
        try {
            URL url = MainLayoutController.class.getResource(VIEW_PATH + fxmlName);
            if (url == null) {
                url = getClass().getResource(VIEW_PATH + fxmlName);
            }
            if (url == null) {
                throw new IllegalStateException("Resource not found: " + VIEW_PATH + fxmlName);
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            mainLayoutController.setContent(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlName, e);
        }
    }

    private void setActiveButton(Button active) {
        String activeStyle = "-fx-background-color: #1E2939; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #374151; -fx-background-radius: 8; -fx-cursor: hand;";
        if (btnDashboard != null) btnDashboard.setStyle(btnDashboard == active ? activeStyle : inactiveStyle);
        if (btnReports != null) btnReports.setStyle(btnReports == active ? activeStyle : inactiveStyle);
        if (btnInsights != null) btnInsights.setStyle(btnInsights == active ? activeStyle : inactiveStyle);
        if (btnProfitability != null) btnProfitability.setStyle(btnProfitability == active ? activeStyle : inactiveStyle);
        if (btnSettings != null) btnSettings.setStyle(btnSettings == active ? activeStyle : inactiveStyle);
    }
}