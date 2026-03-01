package com.raez.finance.controller;

import com.raez.finance.model.UserRole;
import com.raez.finance.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CreateUserController {

    @FXML
    private TextField emailField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<UserRole> roleCombo;

    @FXML
    private TextField firstNameField;

    @FXML
    private TextField lastNameField;

    @FXML
    private TextField phoneField;

    @FXML
    private CheckBox activeCheck;

    @FXML
    private Label statusLabel;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        roleCombo.getItems().setAll(UserRole.ADMIN, UserRole.FINANCE_USER);
        roleCombo.getSelectionModel().select(UserRole.FINANCE_USER);
    }

    @FXML
    private void handleCreate() {
        statusLabel.setText("");
        try {
            userService.createUser(
                    emailField.getText(),
                    usernameField.getText(),
                    passwordField.getText(),
                    roleCombo.getValue(),
                    firstNameField.getText(),
                    lastNameField.getText(),
                    phoneField.getText(),
                    activeCheck.isSelected()
            );
            statusLabel.setText("User created successfully.");
        } catch (Exception e) {
            statusLabel.setText(e.getMessage());
        }
    }

    @FXML
    private void handleClose(javafx.event.ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
