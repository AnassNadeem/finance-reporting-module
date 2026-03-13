package com.raez.finance.controller;

import com.raez.finance.service.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainLayoutController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private static final int SESSION_CHECK_PERIOD_SECONDS = 60;
    private static final int SESSION_WARNING_THRESHOLD_SECONDS = 120;

    private final ScheduledExecutorService sessionChecker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-checker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean sessionWarningShown = new AtomicBoolean(false);

    @FXML private VBox sidebarContainer;
    @FXML private VBox topBarContainer;
    @FXML private StackPane contentArea;
    @FXML private VBox footerContainer;

    @FXML
    public void initialize() {
        try {
            URL sidebarUrl = getClass().getResource(VIEW_PATH + "Sidebar.fxml");
            if (sidebarUrl != null) {
                FXMLLoader sidebarLoader = new FXMLLoader(sidebarUrl);
                Node sidebarRoot = sidebarLoader.load();
                SidebarController sidebarController = sidebarLoader.getController();
                if (sidebarController != null) {
                    sidebarController.setMainLayoutController(this);
                }
                sidebarContainer.getChildren().clear();
                sidebarContainer.getChildren().add(sidebarRoot);
                VBox.setVgrow(sidebarRoot, Priority.ALWAYS);
            }

            URL topBarUrl = getClass().getResource(VIEW_PATH + "TopBar.fxml");
            if (topBarUrl != null) {
                FXMLLoader topBarLoader = new FXMLLoader(topBarUrl);
                Parent topBarRoot = topBarLoader.load();
                TopBarController topBarController = topBarLoader.getController();
                if (topBarController != null) {
                    topBarController.setMainLayoutController(this);
                }
                topBarContainer.getChildren().clear();
                topBarContainer.getChildren().add(topBarRoot);
            }

            URL footerUrl = getClass().getResource(VIEW_PATH + "Footer.fxml");
            if (footerUrl != null) {
                Parent footerRoot = FXMLLoader.load(footerUrl);
                footerContainer.getChildren().clear();
                footerContainer.getChildren().add(footerRoot);
            }

            URL overviewUrl = getClass().getResource(VIEW_PATH + "Overview.fxml");
            if (overviewUrl != null) {
                FXMLLoader overviewLoader = new FXMLLoader(overviewUrl);
                Parent overviewRoot = overviewLoader.load();
                OverviewController overviewController = overviewLoader.getController();
                if (overviewController != null) {
                    overviewController.setMainLayoutController(this);
                    overviewRoot.setUserData(overviewController);
                }
                contentArea.getChildren().clear();
                contentArea.getChildren().add(overviewRoot);
            }

            startSessionTimeoutChecker();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MainLayout", e);
        }
    }

    private void startSessionTimeoutChecker() {
        sessionChecker.scheduleAtFixedRate(() -> {
            Platform.runLater(this::checkSessionExpiry);
        }, SESSION_CHECK_PERIOD_SECONDS, SESSION_CHECK_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void checkSessionExpiry() {
        if (contentArea == null || contentArea.getScene() == null) return;
        long remaining = SessionManager.getRemainingSeconds();
        if (remaining <= 0) {
            handleLogout();
            return;
        }
        if (remaining > SESSION_WARNING_THRESHOLD_SECONDS) {
            sessionWarningShown.set(false);
            return;
        }
        if (sessionWarningShown.getAndSet(true)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Session Expiring");
        alert.setHeaderText("Your session will expire in about 2 minutes.");
        alert.setContentText("Click 'Stay logged in' to continue or 'Log out' to sign out now.");
        ButtonType stayButton = new ButtonType("Stay logged in", ButtonType.OK.getButtonData());
        ButtonType logoutButton = new ButtonType("Log out", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(stayButton, logoutButton);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == stayButton) {
            SessionManager.extendSession();
            sessionWarningShown.set(false);
        } else {
            handleLogout();
        }
    }

    /** Swaps the center content area to the given node. */
    public void setContent(Node node) {
        if (contentArea != null) {
            if (!contentArea.getChildren().isEmpty()) {
                Node current = contentArea.getChildren().get(0);
                Object userData = current.getUserData();
                if (userData instanceof GlobalSearchResultsController) {
                    ((GlobalSearchResultsController) userData).shutdown();
                } else if (userData instanceof DetailedReportsController) {
                    ((DetailedReportsController) userData).shutdown();
                } else if (userData instanceof OverviewController) {
                    ((OverviewController) userData).shutdown();
                }
            }
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);
        }
    }

    /** Logs out and navigates back to RoleSelection. */
    public void handleLogout() {
        com.raez.finance.service.SessionManager.logout();
        try {
            URL url = getClass().getResource(VIEW_PATH + "RoleSelection.fxml");
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Failed to navigate to RoleSelection", e);
        }
    }

    /**
     * Navigates to Detailed Reports, switches to the given tab, and triggers export (CSV or PDF).
     * @param reportType "orders", "products", or "customers"
     * @param format "csv" or "pdf"
     */
    public void navigateToReportsAndExport(String reportType, String format) {
        try {
            URL url = getClass().getResource(VIEW_PATH + "DetailedReports.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            DetailedReportsController controller = loader.getController();
            if (controller != null) {
                controller.setMainLayoutController(this);
                root.setUserData(controller);
                final String fmt = format;
                controller.setAfterLoadCallback(() -> {
                    if ("pdf".equalsIgnoreCase(fmt)) {
                        controller.performExportPDF();
                    } else {
                        controller.performExportCSV();
                    }
                });
            }
            setContent(root);
            if (controller != null) {
                controller.switchToTab(reportType != null ? reportType : "orders");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to navigate and export", e);
        }
    }

    /**
     * Shows a toast notification on top of the content area (success, error, or info).
     */
    public void showToast(String type, String message) {
        if (contentArea == null) return;
        try {
            URL toastUrl = getClass().getResource(VIEW_PATH + "NotificationToast.fxml");
            if (toastUrl == null) return;
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(toastUrl);
            Node toastNode = loader.load();
            NotificationToastController c = loader.getController();
            if (c != null) {
                c.setNotification(type, message, () -> {
                    if (contentArea.getChildren().contains(toastNode)) {
                        contentArea.getChildren().remove(toastNode);
                    }
                });
            }
            contentArea.getChildren().add(toastNode);
            StackPane.setAlignment(toastNode, Pos.TOP_RIGHT);
            StackPane.setMargin(toastNode, new Insets(24, 24, 0, 24));
        } catch (Exception ignored) {
        }
    }

    /**
     * Shows the unified global search results view for the given query.
     */
    public void showGlobalSearch(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        try {
            URL url = getClass().getResource(VIEW_PATH + "GlobalSearchResults.fxml");
            if (url == null) {
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            GlobalSearchResultsController controller = loader.getController();
            if (controller != null) {
                controller.setMainLayoutController(this);
                controller.setQuery(query);
                root.setUserData(controller);
            }
            setContent(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to show global search results", e);
        }
    }
}
