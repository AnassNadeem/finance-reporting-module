package com.raez.finance.controller;

import com.raez.finance.model.UserRole;
import com.raez.finance.service.UserService;
import com.raez.finance.util.ValidationUtils;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class CreateUserController {

    @FXML private TextField     emailField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<UserRole> roleCombo;
    @FXML private TextField     firstNameField;
    @FXML private TextField     lastNameField;
    @FXML private TextField     phoneField;
    @FXML private CheckBox      activeCheck;
    @FXML private Label         statusLabel;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        roleCombo.getItems().setAll(UserRole.ADMIN, UserRole.FINANCE_USER);
        roleCombo.getSelectionModel().select(UserRole.FINANCE_USER);
        if (statusLabel != null) statusLabel.setText("");
    }

    @FXML
    private void handleCreate() {
        if (statusLabel != null) statusLabel.setText("");

        String email    = get(emailField);
        String username = get(usernameField);
        String password = passwordField != null ? passwordField.getText() : "";
        String first    = get(firstNameField);
        String last     = get(lastNameField);
        String phone    = get(phoneField);
        UserRole role   = roleCombo.getValue();
        boolean active  = activeCheck == null || activeCheck.isSelected();

        // Validate
        if (email.isEmpty())    { error("Email is required."); return; }
        if (!ValidationUtils.isRaezEmail(email)) { error("Email must end with @raez.org.uk."); return; }
        if (username.isEmpty()) { error("Username is required."); return; }
        if (password.isEmpty()) { error("Password is required."); return; }

        String pwdErr = ValidationUtils.validateNewPassword(password);
        if (pwdErr != null) { error(pwdErr); return; }

        if (role == null) { error("Role is required."); return; }

        try {
            userService.createUser(email, username, password, role,
                first.isEmpty() ? null : first,
                last.isEmpty()  ? null : last,
                phone.isEmpty() ? null : phone,
                null, // staffID (field not present on CreateUser.fxml)
                null, // addressLine1 (field not present on CreateUser.fxml)
                null, // addressLine2 (field not present on CreateUser.fxml)
                null, // addressLine3 (field not present on CreateUser.fxml)
                active);
            success("User created successfully.");
        } catch (Exception e) {
            error(e.getMessage() != null ? e.getMessage() : "Failed to create user.");
        }
    }

    @FXML
    private void handleClose(javafx.event.ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void error(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
            statusLabel.setStyle("-fx-text-fill: #DC2626; -fx-font-size: 12px;");
        }
    }

    private void success(String msg) {
        if (statusLabel != null) {
            statusLabel.setText(msg);
            statusLabel.setStyle("-fx-text-fill: #16A34A; -fx-font-size: 12px;");
        }
        // Clear fields so admin can create another user
        if (emailField    != null) emailField.clear();
        if (usernameField != null) usernameField.clear();
        if (passwordField != null) passwordField.clear();
        if (firstNameField != null) firstNameField.clear();
        if (lastNameField  != null) lastNameField.clear();
        if (phoneField     != null) phoneField.clear();
        if (roleCombo != null) roleCombo.getSelectionModel().select(UserRole.FINANCE_USER);
        if (activeCheck != null) activeCheck.setSelected(true);
    }

    private String get(TextField f) {
        return f == null || f.getText() == null ? "" : f.getText().trim();
    }
}