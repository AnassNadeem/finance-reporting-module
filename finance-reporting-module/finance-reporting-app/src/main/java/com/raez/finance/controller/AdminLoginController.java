package com.raez.finance.controller;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.AuthService;
import com.raez.finance.service.AuthService.FirstLoginRequiredException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;

public class AdminLoginController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private final AuthService authService = new AuthService();

    @FXML private Button btnBack;
    @FXML private Button btnLogin;
    @FXML private VBox errorBox;
    @FXML private Label lblError;
    @FXML private Hyperlink linkForgotPassword;
    @FXML private Hyperlink linkReportIssue;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;

    @FXML
    public void initialize() {
        hideError();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo(VIEW_PATH + "RoleSelection.fxml", event);
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = txtEmail.getText();
        String password = txtPassword.getText();

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            showError("Please fill in all fields");
            return;
        }
        hideError();

        try {
            FUser user = authService.login(email, password);
            if (user.getRole() != UserRole.ADMIN) {
                showError("You do not have access to this area with this account.");
                return;
            }
            com.raez.finance.service.SessionManager.startSession(user);
            navigateToMainLayout(event);
        } catch (FirstLoginRequiredException e) {
            showError("First-time login detected. Please change your password (use Finance User login for password change).");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            String msg = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
            showError(msg != null ? msg : "Authentication failed. Please try again.");
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        new Alert(Alert.AlertType.INFORMATION, "Password reset will be implemented in a later phase. Contact an administrator.").showAndWait();
    }

    @FXML
    private void handleReportIssue(ActionEvent event) {
        new Alert(Alert.AlertType.INFORMATION, "Report technical issues to your system administrator.").showAndWait();
    }

    private void navigateToMainLayout(ActionEvent event) {
        String resourcePath = "/com/raez/finance/view/MainLayout.fxml";
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("Resource not found: " + resourcePath);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("RAEZ Finance – Main");
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }

    private void navigateTo(String resourcePath, ActionEvent event) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("Resource not found: " + resourcePath);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }

    // --- Helper Methods for UI State ---

    private void showError(String message) {
        lblError.setText(message);
        errorBox.setVisible(true);
        errorBox.setManaged(true); // Tells JavaFX to take up space for the box
    }

    private void hideError() {
        errorBox.setVisible(false);
        errorBox.setManaged(false); // Tells JavaFX to collapse the space
    }
}