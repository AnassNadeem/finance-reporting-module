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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    private final AuthService authService = new AuthService();

    private UserRole expectedRole;

    public void configureForRole(UserRole role) {
        this.expectedRole = role;
        if (role == UserRole.ADMIN) {
            titleLabel.setText("Admin Login");
            subtitleLabel.setText("Restricted Access - Admin Only");
            loginButton.setText("Login as Admin");
        } else {
            titleLabel.setText("Finance User Login");
            subtitleLabel.setText("Finance & Reporting Access");
            loginButton.setText("Login as Finance User");
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = emailField.getText();
        String password = passwordField.getText();

        try {
            FUser user = authService.login(email, password);

            if (expectedRole != null && user.getRole() != expectedRole) {
                errorLabel.setText("You do not have access to this area with this account.");
                return;
            }

            try {
                navigateToDashboard(event);
            } catch (Exception navEx) {
                String msg = navEx.getMessage() != null ? navEx.getMessage() : navEx.getClass().getSimpleName();
                errorLabel.setText("Login succeeded but could not open dashboard: " + msg);
                System.err.println("=== Dashboard load error ===");
                navEx.printStackTrace(System.err);
                if (navEx.getCause() != null) {
                    System.err.println("Cause: " + navEx.getCause().getMessage());
                    navEx.getCause().printStackTrace(System.err);
                }
            }
        } catch (FirstLoginRequiredException e) {
            errorLabel.setText("First-time login detected. Please change your password.");
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Authentication failed. Please try again.";
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                msg = e.getCause().getMessage();
            }
            errorLabel.setText(msg);
        }
    }

    @FXML
    private void handleBackToRoleSelection(ActionEvent event) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/raez/finance/view/RoleSelection.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm()
        );

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
    }

    @FXML
    private void handleForgotPassword() {
        new Alert(Alert.AlertType.INFORMATION,
                "Password reset will be implemented in a later phase. For now, please contact an administrator.")
                .showAndWait();
    }

    private void navigateToDashboard(ActionEvent event) throws Exception {
        java.net.URL fxmlUrl = getClass().getResource("/com/raez/finance/view/Dashboard.fxml");
        if (fxmlUrl == null) {
            throw new IllegalStateException("Dashboard.fxml not found on classpath (resource path: /com/raez/finance/view/Dashboard.fxml)");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        Scene scene = new Scene(root);
        java.net.URL cssUrl = getClass().getResource("/css/app.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
    }
}
