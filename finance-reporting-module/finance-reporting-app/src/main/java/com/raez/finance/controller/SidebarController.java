package com.raez.finance.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.net.URL;

public class SidebarController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    private MainLayoutController mainLayoutController;

    @FXML private Button btnDashboard;
    @FXML private Button btnReports;
    @FXML private Button btnInsights;
    @FXML private Button btnProfitability;
    @FXML private Button btnSettings;
    @FXML private Button btnAuditLog;
    @FXML private Button btnLogout;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void initialize() {
        setActiveButton(btnDashboard);
        // Ensure Reports button opens Reports even if FXML onAction fails to bind
        if (btnReports != null) {
            btnReports.setOnAction(e -> {
                loadContent("DetailedReports.fxml");
                setActiveButton(btnReports);
            });
            btnReports.setFocusTraversable(true);
        }
    }

    @FXML
    public void handleNavDashboard(ActionEvent event) {
        loadContent("Overview.fxml");
        setActiveButton(btnDashboard);
    }

    @FXML
    public void handleNavReports(ActionEvent event) {
        loadContent("DetailedReports.fxml");
        setActiveButton(btnReports);
    }

    @FXML
    public void handleNavInsights(ActionEvent event) {
        loadContent("CustomerInsights.fxml");
        setActiveButton(btnInsights);
    }

    @FXML
    public void handleNavProfitability(ActionEvent event) {
        loadContent("ProductProfitability.fxml");
        setActiveButton(btnProfitability);
    }

    @FXML
    public void handleNavSettings(ActionEvent event) {
        loadContent("Settings.fxml");
        setActiveButton(btnSettings);
    }

    @FXML
    public void handleNavAuditLog(ActionEvent event) {
        loadContent("AuditLog.fxml");
        setActiveButton(btnAuditLog);
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        if (mainLayoutController != null) {
            mainLayoutController.handleLogout();
        }
    }

    private void loadContent(String fxmlName) {
        if (mainLayoutController == null) return;
        try {
            // Use same class loader as MainLayoutController so the view is always found
            URL url = MainLayoutController.class.getResource(VIEW_PATH + fxmlName);
            if (url == null) {
                url = getClass().getResource(VIEW_PATH + fxmlName);
            }
            if (url == null) {
                url = getClass().getClassLoader().getResource("com/raez/finance/view/" + fxmlName);
            }
            if (url == null) {
                throw new IllegalStateException("Resource not found: " + VIEW_PATH + fxmlName);
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof OverviewController) {
                ((OverviewController) ctrl).setMainLayoutController(mainLayoutController);
                root.setUserData(ctrl);
            } else if (ctrl instanceof DetailedReportsController) {
                ((DetailedReportsController) ctrl).setMainLayoutController(mainLayoutController);
                root.setUserData(ctrl);
            } else if (ctrl instanceof CustomerInsightsController) {
                ((CustomerInsightsController) ctrl).setMainLayoutController(mainLayoutController);
            } else if (ctrl instanceof ProductProfitabilityController) {
                ((ProductProfitabilityController) ctrl).setMainLayoutController(mainLayoutController);
            }
            mainLayoutController.setContent(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlName, e);
        }
    }

    private void setActiveButton(Button active) {
        String activeStyle = "-fx-background-color: #1E2939; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #374151; -fx-background-radius: 8; -fx-cursor: hand;";
        Button[] navButtons = { btnDashboard, btnReports, btnInsights, btnProfitability, btnSettings, btnAuditLog };
        for (Button btn : navButtons) {
            if (btn == null) continue;
            btn.setStyle(btn == active ? activeStyle : inactiveStyle);
            setNavButtonIconFill(btn, btn == active);
        }
    }

    private void setNavButtonIconFill(Button btn, boolean active) {
        Node graphic = btn.getGraphic();
        if (graphic instanceof SVGPath) {
            ((SVGPath) graphic).setFill(active ? Color.WHITE : Color.web("#374151"));
        } else if (graphic instanceof StackPane && !((StackPane) graphic).getChildren().isEmpty()) {
            Node icon = ((StackPane) graphic).getChildren().get(0);
            if (icon instanceof SVGPath) {
                ((SVGPath) icon).setStroke(active ? Color.WHITE : Color.web("#374151"));
            }
        }
    }
}