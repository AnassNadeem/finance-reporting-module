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

public class FinanceUserLoginController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private final AuthService authService = new AuthService();

    @FXML private VBox loginView;
    @FXML private TextField txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private VBox loginErrorBox;
    @FXML private Label lblLoginError;
    @FXML private Button btnLogin;
    @FXML private Button btnBackToRole;
    @FXML private Hyperlink linkForgotPassword;

    @FXML private VBox passwordChangeView;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private VBox pwdErrorBox;
    @FXML private Label lblPwdError;

    @FXML
    public void initialize() {
        showLoginView();
        hideErrors();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo(VIEW_PATH + "RoleSelection.fxml", event);
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        showLoginView();
        hideErrors();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = txtEmail.getText();
        String password = txtPassword.getText();

        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            showLoginError("Please fill in all fields");
            return;
        }
        hideErrors();

        try {
            FUser user = authService.login(email, password);
            if (user.getRole() != UserRole.FINANCE_USER) {
                showLoginError("You do not have access to this area with this account.");
                return;
            }
            com.raez.finance.service.SessionManager.startSession(user);
            navigateToMainLayout(event);
        } catch (FirstLoginRequiredException e) {
            showPasswordChangeView();
            hideErrors();
        } catch (IllegalArgumentException e) {
            showLoginError(e.getMessage());
        } catch (Exception e) {
            String msg = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
            showLoginError(msg != null ? msg : "Authentication failed. Please try again.");
        }
    }

    @FXML
    private void handlePasswordChange(ActionEvent event) {
        String newPwd = txtNewPassword.getText();
        String confirmPwd = txtConfirmPassword.getText();

        if (newPwd == null || newPwd.trim().isEmpty() || confirmPwd == null || confirmPwd.trim().isEmpty()) {
            showPwdError("Please fill in all fields");
            return;
        }

        if (newPwd.length() < 8) {
            showPwdError("Password must be at least 8 characters");
            return;
        }

        if (!newPwd.equals(confirmPwd)) {
            showPwdError("Passwords do not match");
            return;
        }

        // Cursor will add AuthService.updatePassword() logic here
        System.out.println("Password changed successfully. Logging in...");
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

    // --- Helper Methods for UI State Toggling ---

    private void showLoginView() {
        loginView.setVisible(true);
        loginView.setManaged(true);
        passwordChangeView.setVisible(false);
        passwordChangeView.setManaged(false);
    }

    private void showPasswordChangeView() {
        loginView.setVisible(false);
        loginView.setManaged(false);
        passwordChangeView.setVisible(true);
        passwordChangeView.setManaged(true);
    }

    private void showLoginError(String message) {
        lblLoginError.setText(message);
        loginErrorBox.setVisible(true);
        loginErrorBox.setManaged(true);
    }

    private void showPwdError(String message) {
        lblPwdError.setText(message);
        pwdErrorBox.setVisible(true);
        pwdErrorBox.setManaged(true);
    }

    private void hideErrors() {
        loginErrorBox.setVisible(false);
        loginErrorBox.setManaged(false);
        pwdErrorBox.setVisible(false);
        pwdErrorBox.setManaged(false);
    }
}