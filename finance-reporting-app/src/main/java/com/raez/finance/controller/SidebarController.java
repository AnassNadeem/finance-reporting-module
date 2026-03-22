package com.raez.finance.controller;

import com.raez.finance.service.SessionManager;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SidebarController {

    private static final String VIEW_PATH    = "/com/raez/finance/view/";
    private static final String CHEVRON_DOWN = "M19 9l-7 7-7-7";
    private static final String CHEVRON_UP   = "M5 15l7-7 7 7";

    // ── Active / inactive styles ─────────────────────────────────────────
    private static final String ACTIVE_STYLE =
        "-fx-background-color: #1E2939; -fx-text-fill: white; " +
        "-fx-background-radius: 8; -fx-cursor: hand;";
    private static final String INACTIVE_STYLE =
        "-fx-background-color: transparent; -fx-text-fill: #374151; " +
        "-fx-background-radius: 8; -fx-cursor: hand;";
    private static final String HOVER_STYLE =
        "-fx-background-color: #F0F1F3; -fx-text-fill: #1E2939; " +
        "-fx-background-radius: 8; -fx-cursor: hand;";

    // ── FXML ────────────────────────────────────────────────────────────
    @FXML private Button   btnDashboard;
    @FXML private Button   btnReports;
    @FXML private Button   btnAnalytics;
    @FXML private VBox     analyticsChildBox;
    @FXML private Button   btnInsights;
    @FXML private Button   btnProfitability;
    @FXML private Button   btnRevenueVat;
    @FXML private Button   btnInventorySupplier;
    @FXML private Button   btnAiInsights;
    @FXML private Button   btnInvoices;
    @FXML private Button   btnAuditLog;
    @FXML private Button   btnSettings;
    @FXML private Button   btnLogout;
    @FXML private SVGPath  chevronIcon;

    // ── State ────────────────────────────────────────────────────────────
    private MainLayoutController mainLayoutController;
    private boolean analyticsExpanded = false;
    private Button  activeButton      = null;

    // ── Debug log helpers (preserved from original) ───────────────────────
    private static final String DEBUG_LOG_PATH   = "C:\\Users\\Projects\\Desktop\\Final GP\\debug-cd3c43.log";
    private static final String DEBUG_SESSION_ID = "cd3c43";
    private static final String DEBUG_RUN_ID     = "sidebar_topbar_pre_fix";

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private static void agentLog(String hypothesisId, String location, String message, String data) {
        try {
            long ts = System.currentTimeMillis();
            String json = "{\"sessionId\":\"" + esc(DEBUG_SESSION_ID) +
                    "\",\"runId\":\"" + esc(DEBUG_RUN_ID) +
                    "\",\"hypothesisId\":\"" + esc(hypothesisId) +
                    "\",\"location\":\"" + esc(location) +
                    "\",\"message\":\"" + esc(message) +
                    "\",\"data\":\"" + esc(data) +
                    "\",\"timestamp\":" + ts + "}";
            Files.writeString(
                    Path.of(DEBUG_LOG_PATH),
                    json + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            System.err.println("[agentLog:Sidebar] failed to write log: " + e.getMessage());
        }
    }

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setActiveButton(btnDashboard);
        applyRoleVisibility();
        wireHoverAnimations();
    }

    private void applyRoleVisibility() {
        if (!SessionManager.isAdmin()) {
            hideNav(btnAuditLog);
        }
    }

    private static void hideNav(Button b) {
        if (b == null) return;
        b.setVisible(false);
        b.setManaged(false);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HOVER ANIMATIONS
    //  Each non-active button gets a smooth bg-fade + icon scale on hover.
    //  Uses Timeline so the transition is interpolated, not instant.
    // ══════════════════════════════════════════════════════════════════════

    private void wireHoverAnimations() {
        Button[] all = {
            btnDashboard, btnReports, btnAnalytics,
            btnInsights, btnProfitability, btnRevenueVat,
            btnInventorySupplier, btnAiInsights,
            btnInvoices, btnAuditLog, btnSettings
        };
        for (Button b : all) {
            if (b != null) addHoverEffect(b);
        }
        if (btnLogout != null) addLogoutHover(btnLogout);
    }

    /**
     * Smooth hover for nav buttons.
     * On enter: fade bg to #F0F1F3, scale icon to 1.10.
     * On exit:  restore inactive or active style.
     */
    private void addHoverEffect(Button btn) {
        Node graphic = btn.getGraphic();

        btn.setOnMouseEntered(e -> {
            if (btn == activeButton) return;   // never override active style
            btn.setStyle(HOVER_STYLE);
            if (graphic != null) {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), graphic);
                st.setToX(1.10); st.setToY(1.10); st.play();
                setIconColor(btn, Color.web("#1E2939"));
            }
        });

        btn.setOnMouseExited(e -> {
            if (btn == activeButton) return;
            btn.setStyle(INACTIVE_STYLE);
            if (graphic != null) {
                ScaleTransition st = new ScaleTransition(Duration.millis(120), graphic);
                st.setToX(1.0); st.setToY(1.0); st.play();
                setIconColor(btn, Color.web("#374151"));
            }
        });
    }

    /** Logout gets a subtle red tint hover instead. */
    private void addLogoutHover(Button btn) {
        btn.setOnMouseEntered(e ->
            btn.setStyle("-fx-background-color: #FEF2F2; -fx-text-fill: #DC2626; " +
                         "-fx-background-radius: 8; -fx-cursor: hand;"));
        btn.setOnMouseExited(e ->
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #4B5563; -fx-cursor: hand;"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANALYTICS ACCORDION
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void handleToggleAnalytics(ActionEvent event) {
        analyticsExpanded = !analyticsExpanded;
        agentLog("H4", "SidebarController.handleToggleAnalytics",
                 "toggle analytics",
                 "analyticsExpanded=" + analyticsExpanded +
                 ";analyticsChildBoxNull=" + (analyticsChildBox == null));
        analyticsChildBox.setVisible(analyticsExpanded);
        analyticsChildBox.setManaged(analyticsExpanded);
        if (chevronIcon != null)
            chevronIcon.setContent(analyticsExpanded ? CHEVRON_UP : CHEVRON_DOWN);
    }

    private void expandAnalytics() {
        if (!analyticsExpanded && analyticsChildBox != null) {
            analyticsExpanded = true;
            analyticsChildBox.setVisible(true);
            analyticsChildBox.setManaged(true);
            if (chevronIcon != null) chevronIcon.setContent(CHEVRON_UP);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NAV HANDLERS
    //  Note: "Support & Alerts" (btnAlerts) removed per redesign spec.
    // ══════════════════════════════════════════════════════════════════════

    @FXML public void handleNavDashboard(ActionEvent e)      { load("Overview.fxml",            btnDashboard); }
    @FXML public void handleNavReports(ActionEvent e)        { load("DetailedReports.fxml",     btnReports);   }
    @FXML public void handleNavAuditLog(ActionEvent e)       { load("AuditLog.fxml",            btnAuditLog);  }
    @FXML public void handleNavSettings(ActionEvent e)       { load("Settings.fxml",            btnSettings);  }
    @FXML public void handleNavInvoices(ActionEvent e)       { load("Invoices.fxml",            btnInvoices);  }

    @FXML
    public void handleNavInsights(ActionEvent e) {
        expandAnalytics();
        load("CustomerInsights.fxml", btnInsights);
    }

    @FXML
    public void handleNavProfitability(ActionEvent e) {
        expandAnalytics();
        load("ProductProfitability.fxml", btnProfitability);
    }

    @FXML
    public void handleNavRevenueVat(ActionEvent e) {
        expandAnalytics();
        load("RevenueVatSummary.fxml", btnRevenueVat);
    }

    @FXML
    public void handleNavInventorySupplier(ActionEvent e) {
        expandAnalytics();
        load("InventorySupplier.fxml", btnInventorySupplier);
    }

    @FXML
    public void handleNavAiInsights(ActionEvent e) {
        expandAnalytics();
        load("AiInsights.fxml", btnAiInsights);
    }

    @FXML
    public void handleLogout(ActionEvent e) {
        if (mainLayoutController != null) mainLayoutController.handleLogout();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTENT LOADER
    // ══════════════════════════════════════════════════════════════════════

    private void load(String fxmlName, Button activeBtn) {
        agentLog("H1", "SidebarController.load", "enter load()",
                 "fxmlName=" + fxmlName +
                 ";mainLayoutControllerNull=" + (mainLayoutController == null) +
                 ";activeBtnText=" + (activeBtn == null ? "" : activeBtn.getText()));

        if (mainLayoutController == null) return;
        SessionManager.extendSession();

        try {
            URL url = resolveUrl(fxmlName);
            agentLog("H2", "SidebarController.resolveUrl", "url resolution",
                     "fxmlName=" + fxmlName + ";urlNull=" + (url == null));

            if (url == null) {
                System.err.println("[Sidebar] Resource not found: " + fxmlName);
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            Object ctrl = loader.getController();

            String wired = "none";
            if (ctrl instanceof OverviewController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "OverviewController";
            } else if (ctrl instanceof DetailedReportsController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "DetailedReportsController";
            } else if (ctrl instanceof CustomerInsightsController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "CustomerInsightsController";
            } else if (ctrl instanceof ProductProfitabilityController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "ProductProfitabilityController";
            } else if (ctrl instanceof RevenueVatSummaryController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "RevenueVatSummaryController";
            } else if (ctrl instanceof InventorySupplierController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "InventorySupplierController";
            } else if (ctrl instanceof NotificationsAlertsController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "NotificationsAlertsController";
            } else if (ctrl instanceof AuditLogController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "AuditLogController";
            } else if (ctrl instanceof SettingsController sc) {
                sc.setMainLayoutController(mainLayoutController); root.setUserData(sc); wired = "SettingsController";
            } else if (ctrl instanceof InvoicesController ic) {
                ic.setMainLayoutController(mainLayoutController); root.setUserData(ic); wired = "InvoicesController";
            } else if (ctrl instanceof AiInsightsController c) {
                c.setMainLayoutController(mainLayoutController); root.setUserData(c); wired = "AiInsightsController";
            }

            agentLog("H3", "SidebarController.load", "loaded controller",
                     "ctrlClass=" + (ctrl == null ? "" : ctrl.getClass().getSimpleName()) + ";wired=" + wired);

            root.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(200), root);
            ft.setFromValue(0); ft.setToValue(1); ft.play();

            mainLayoutController.setContent(root);
            setActiveButton(activeBtn);

        } catch (Exception ex) {
            System.err.println("[Sidebar] Failed to load " + fxmlName + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private URL resolveUrl(String fxmlName) {
        URL url = MainLayoutController.class.getResource(VIEW_PATH + fxmlName);
        if (url == null) url = getClass().getResource(VIEW_PATH + fxmlName);
        if (url == null) url = getClass().getClassLoader()
            .getResource("com/raez/finance/view/" + fxmlName);
        return url;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACTIVE BUTTON STYLING
    // ══════════════════════════════════════════════════════════════════════

    private void setActiveButton(Button active) {
        Button[] all = {
            btnDashboard, btnReports, btnAnalytics,
            btnInsights, btnProfitability, btnRevenueVat,
            btnInventorySupplier, btnAiInsights,
            btnInvoices, btnAuditLog, btnSettings
        };
        for (Button b : all) {
            if (b == null) continue;
            boolean isActive = (b == active);
            b.setStyle(isActive ? ACTIVE_STYLE : INACTIVE_STYLE);
            setIconColor(b, isActive ? Color.WHITE : Color.web("#374151"));
        }
        activeButton = active;
    }

    private void setIconColor(Button btn, Color color) {
        Node g = btn.getGraphic();
        if (g == null) return;
        applyColor(g, color);
    }

    private void applyColor(Node node, Color color) {
        if (node instanceof SVGPath s) {
            s.setStroke(color);
        } else if (node instanceof StackPane sp) {
            sp.getChildren().forEach(child -> applyColor(child, color));
        } else if (node instanceof HBox hb) {
            hb.getChildren().forEach(child -> applyColor(child, color));
        } else if (node instanceof VBox vb) {
            vb.getChildren().forEach(child -> applyColor(child, color));
        }
    }
}