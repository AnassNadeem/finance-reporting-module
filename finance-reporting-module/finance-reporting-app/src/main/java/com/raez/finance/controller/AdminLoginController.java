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
            // For first-time admin login, send the user to the unified first-login password change flow
            navigateToFinanceFirstLogin(email, event);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            String msg = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getMessage();
            showError(msg != null ? msg : "Authentication failed. Please try again.");
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Reset Password with Token");
        dialog.setHeaderText("Enter your email and the one-time token from your administrator.");
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        javafx.scene.control.TextField emailField = new javafx.scene.control.TextField();
        emailField.setPromptText("Email");
        emailField.setPrefWidth(280);
        javafx.scene.control.TextField tokenField = new javafx.scene.control.TextField();
        tokenField.setPromptText("Reset token");
        tokenField.setPrefWidth(280);
        javafx.scene.control.PasswordField newPwdField = new javafx.scene.control.PasswordField();
        newPwdField.setPromptText("New password (min 8 characters)");
        newPwdField.setPrefWidth(280);
        javafx.scene.control.PasswordField confirmField = new javafx.scene.control.PasswordField();
        confirmField.setPromptText("Confirm new password");
        confirmField.setPrefWidth(280);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new javafx.scene.control.Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        grid.add(new javafx.scene.control.Label("Token:"), 0, 1);
        grid.add(tokenField, 1, 1);
        grid.add(new javafx.scene.control.Label("New password:"), 0, 2);
        grid.add(newPwdField, 1, 2);
        grid.add(new javafx.scene.control.Label("Confirm:"), 0, 3);
        grid.add(confirmField, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> btn == javafx.scene.control.ButtonType.OK ? "ok" : null);
        dialog.showAndWait().ifPresent(result -> {
            String email = emailField.getText();
            String token = tokenField.getText();
            String newPwd = newPwdField.getText();
            String confirm = confirmField.getText();
            if (email == null || email.trim().isEmpty() || token == null || token.trim().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Email and token are required.").showAndWait();
                return;
            }
            if (newPwd == null || newPwd.length() < 8) {
                new Alert(Alert.AlertType.WARNING, "New password must be at least 8 characters.").showAndWait();
                return;
            }
            if (!newPwd.equals(confirm)) {
                new Alert(Alert.AlertType.WARNING, "Passwords do not match.").showAndWait();
                return;
            }
            try {
                authService.resetPasswordWithToken(email.trim(), token.trim(), newPwd);
                new Alert(Alert.AlertType.INFORMATION, "Password updated. You can now log in.").showAndWait();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage() != null ? e.getMessage() : "Reset failed.").showAndWait();
            }
        });
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

    /**
     * Navigates to the FinanceUserLogin screen and pre-loads the first-time password
     * change view for the given identifier (email/username). This reuses the same
     * first-login UX for both ADMIN and FINANCE_USER roles.
     */
    private void navigateToFinanceFirstLogin(String identifier, ActionEvent event) {
        String resourcePath = VIEW_PATH + "FinanceUserLogin.fxml";
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("Resource not found: " + resourcePath);
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();

            FinanceUserLoginController controller = loader.getController();
            if (controller != null) {
                controller.prepareForFirstLogin(identifier);
            }

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("RAEZ Finance – First-time Login");
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }
}
