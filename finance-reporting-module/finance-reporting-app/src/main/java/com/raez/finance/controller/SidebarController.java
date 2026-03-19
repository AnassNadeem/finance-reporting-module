package com.raez.finance.controller;

import javafx.animation.FadeTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.net.URL;

public class SidebarController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private static final String CHEVRON_DOWN = "M19 9l-7 7-7-7";
    private static final String CHEVRON_UP = "M5 15l7-7 7 7";

    private MainLayoutController mainLayoutController;
    private boolean analyticsExpanded = false;

    @FXML private Button btnDashboard;
    @FXML private Button btnReports;
    @FXML private Button btnAnalytics;
    @FXML private VBox analyticsChildBox;
    @FXML private Button btnInsights;
    @FXML private Button btnProfitability;
    @FXML private Button btnRevenueVat;
    @FXML private Button btnInventorySupplier;
    @FXML private Button btnInvoices;
    @FXML private Button btnAlerts;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private SVGPath chevronIcon;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    @FXML
    public void initialize() {
        setActiveButton(btnDashboard);
        if (btnReports != null) {
            btnReports.setOnAction(e -> {
                loadContent("DetailedReports.fxml");
                setActiveButton(btnReports);
            });
            btnReports.setFocusTraversable(true);
        }
    }

    public void applyRoleVisibility() {
        // No additional sidebar-level hiding needed currently;
        // individual page controllers handle export button and settings tab visibility.
        // Reserved for future sidebar restrictions.
    }

    @FXML
    public void handleToggleAnalytics(ActionEvent event) {
        analyticsExpanded = !analyticsExpanded;
        analyticsChildBox.setVisible(analyticsExpanded);
        analyticsChildBox.setManaged(analyticsExpanded);
        if (chevronIcon != null) {
            chevronIcon.setContent(analyticsExpanded ? CHEVRON_UP : CHEVRON_DOWN);
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
        expandAnalyticsAndSetActive(btnInsights);
        loadContent("CustomerInsights.fxml");
    }

    @FXML
    public void handleNavProfitability(ActionEvent event) {
        expandAnalyticsAndSetActive(btnProfitability);
        loadContent("ProductProfitability.fxml");
    }

    @FXML
    public void handleNavRevenueVat(ActionEvent event) {
        expandAnalyticsAndSetActive(btnRevenueVat);
        loadContent("RevenueVatSummary.fxml");
    }

    @FXML
    public void handleNavInventorySupplier(ActionEvent event) {
        expandAnalyticsAndSetActive(btnInventorySupplier);
        loadContent("InventorySupplier.fxml");
    }

    @FXML
    public void handleNavInvoices(ActionEvent event) {
        loadContent("Invoices.fxml");
        setActiveButton(btnInvoices);
    }

    private void expandAnalyticsAndSetActive(Button child) {
        if (!analyticsExpanded && analyticsChildBox != null) {
            analyticsExpanded = true;
            analyticsChildBox.setVisible(true);
            analyticsChildBox.setManaged(true);
            if (chevronIcon != null) chevronIcon.setContent(CHEVRON_UP);
        }
        setActiveButton(child);
    }

    @FXML
    public void handleNavAlerts(ActionEvent event) {
        loadContent("NotificationsAlerts.fxml");
        setActiveButton(btnAlerts);
    }

    @FXML
    public void handleNavSettings(ActionEvent event) {
        loadContent("Settings.fxml");
        setActiveButton(btnSettings);
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
            URL url = MainLayoutController.class.getResource(VIEW_PATH + fxmlName);
            if (url == null) url = getClass().getResource(VIEW_PATH + fxmlName);
            if (url == null) url = getClass().getClassLoader().getResource("com/raez/finance/view/" + fxmlName);
            if (url == null) throw new IllegalStateException("Resource not found: " + VIEW_PATH + fxmlName);
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
            } else if (ctrl instanceof RevenueVatSummaryController) {
                ((RevenueVatSummaryController) ctrl).setMainLayoutController(mainLayoutController);
            } else if (ctrl instanceof InventorySupplierController) {
                ((InventorySupplierController) ctrl).setMainLayoutController(mainLayoutController);
            }

            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(Duration.millis(200), root);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
            mainLayoutController.setContent(root);
        } catch (Exception e) {
            System.err.println("Failed to load " + fxmlName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setActiveButton(Button active) {
        String activeStyle = "-fx-background-color: #1E2939; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #374151; -fx-background-radius: 8; -fx-cursor: hand;";
        Button[] navButtons = { btnDashboard, btnReports, btnAnalytics, btnInsights, btnProfitability,
                btnRevenueVat, btnInventorySupplier, btnInvoices, btnAlerts, btnSettings };
        for (Button btn : navButtons) {
            if (btn == null) continue;
            btn.setStyle(btn == active ? activeStyle : inactiveStyle);
            setNavButtonIconFill(btn, btn == active);
        }
    }

    private void setNavButtonIconFill(Button btn, boolean active) {
        Node graphic = btn.getGraphic();
        Color color = active ? Color.WHITE : Color.web("#374151");
        if (graphic instanceof SVGPath) {
            ((SVGPath) graphic).setStroke(color);
        } else if (graphic instanceof StackPane) {
            StackPane sp = (StackPane) graphic;
            if (!sp.getChildren().isEmpty()) {
                Node first = sp.getChildren().get(0);
                if (first instanceof SVGPath) {
                    ((SVGPath) first).setStroke(color);
                }
            }
        } else if (graphic instanceof HBox) {
            HBox hbox = (HBox) graphic;
            for (Node n : hbox.getChildren()) {
                if (n instanceof SVGPath) {
                    ((SVGPath) n).setStroke(color);
                }
            }
        }
    }
}
