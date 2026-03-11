package com.raez.finance.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;

public class MainLayoutController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

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
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MainLayout", e);
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
