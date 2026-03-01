package com.raez.finance.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
            }

            URL topBarUrl = getClass().getResource(VIEW_PATH + "TopBar.fxml");
            if (topBarUrl != null) {
                Parent topBarRoot = FXMLLoader.load(topBarUrl);
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
                Parent overviewRoot = FXMLLoader.load(overviewUrl);
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
}