package com.raez.finance.controller;

import com.raez.finance.dao.FUserDao;
import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.SessionManager;
import com.raez.finance.service.UserService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.mindrot.jbcrypt.BCrypt;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsController {

    private final FUserDao fUserDao = new FUserDao();
    private final UserService userService = new UserService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private StackPane rootStackPane;

    // --- Tabs ---
    @FXML private Button btnTabAccount;
    @FXML private Button btnTabUsers;
    @FXML private VBox viewAccount;
    @FXML private VBox viewUsers;

    // --- Account Tab Form ---
    @FXML private PasswordField txtCurrentPwd;
    @FXML private PasswordField txtNewPwd;
    @FXML private PasswordField txtConfirmPwd;
    @FXML private TextField txtForgotEmail;

    // --- User Management Tab ---
    @FXML private TableView<FUser> tblUsers;
    @FXML private TableColumn<FUser, String> colUsername;
    @FXML private TableColumn<FUser, String> colName;
    @FXML private TableColumn<FUser, String> colEmail;
    @FXML private TableColumn<FUser, String> colRole;
    @FXML private TableColumn<FUser, String> colStatus;
    @FXML private TableColumn<FUser, String> colLastLogin;
    @FXML private TableColumn<FUser, String> colActions;

    // --- Modal Form ---
    @FXML private StackPane modalOverlay;
    @FXML private Label lblModalTitle;
    @FXML private TextField txtModalUsername;
    @FXML private PasswordField txtModalPassword;
    @FXML private ComboBox<String> cmbModalRole;
    @FXML private TextField txtModalFirstName;
    @FXML private TextField txtModalLastName;
    @FXML private TextField txtModalEmail;
    @FXML private TextField txtModalPhone;
    @FXML private TextField txtModalIdCard;
    @FXML private TextField txtModalAddress;
    @FXML private CheckBox chkModalActive;
    @FXML private Button btnModalSave;

    private boolean isEditMode = false;

    @FXML
    public void initialize() {
        cmbModalRole.setItems(FXCollections.observableArrayList("Admin", "Finance User"));
        cmbModalRole.setValue("Finance User");

        bindUserColumns();

        if (SessionManager.getRole() == UserRole.FINANCE_USER) {
            btnTabUsers.setVisible(false);
            btnTabUsers.setManaged(false);
            viewUsers.setVisible(false);
            viewUsers.setManaged(false);
        } else {
            refreshUsers();
        }

        switchTab("account");
    }

    private void bindUserColumns() {
        colUsername.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getUsername()));
        colName.setCellValueFactory(c -> {
            String first = c.getValue().getFirstName() != null ? c.getValue().getFirstName() : "";
            String last = c.getValue().getLastName() != null ? c.getValue().getLastName() : "";
            String full = (first + " " + last).trim();
            return new javafx.beans.property.SimpleStringProperty(full.isEmpty() ? "—" : full);
        });
        colEmail.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEmail()));
        colRole.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getRole().name()));
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().isActive() ? "Active" : "Inactive"));
        colLastLogin.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLastLogin() == null ? "—" : c.getValue().getLastLogin().format(DATE_FMT)));
        colActions.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("Edit"));
    }

    private void refreshUsers() {
        Task<List<FUser>> task = new Task<>() {
            @Override
            protected List<FUser> call() throws Exception {
                return fUserDao.findAll();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                tblUsers.setItems(FXCollections.observableList(task.getValue()));
            }
        });
        task.exceptionProperty().addListener((o, prev, ex) -> {
            if (ex != null) {
                new Alert(Alert.AlertType.ERROR, "Failed to load users: " + ex.getMessage()).showAndWait();
            }
        });
        executor.execute(task);
    }

    // --- Tab Logic ---

    @FXML
    private void handleTabAccount(ActionEvent event) { switchTab("account"); }

    @FXML
    private void handleTabUsers(ActionEvent event) {
        switchTab("users");
        if (SessionManager.getRole() == UserRole.ADMIN) {
            refreshUsers();
        }
    }

    private void switchTab(String tab) {
        String activeStyle = "-fx-background-color: transparent; -fx-border-color: #1E2939; -fx-border-width: 0 0 2 0; -fx-text-fill: #1E2939; -fx-cursor: hand; -fx-font-weight: bold;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #4B5563; -fx-cursor: hand;";
        
        btnTabAccount.setStyle(inactiveStyle);
        btnTabUsers.setStyle(inactiveStyle);
        viewAccount.setVisible(false);
        viewUsers.setVisible(false);

        if (tab.equals("account")) {
            btnTabAccount.setStyle(activeStyle);
            viewAccount.setVisible(true);
        } else {
            btnTabUsers.setStyle(activeStyle);
            viewUsers.setVisible(true);
        }
    }

    // --- Form Actions ---

    @FXML
    private void handleUpdatePassword(ActionEvent event) {
        String current = txtCurrentPwd.getText();
        String newPwd = txtNewPwd.getText();
        String confirm = txtConfirmPwd.getText();

        if (current == null || current.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Enter your current password.").showAndWait();
            return;
        }
        if (newPwd == null || newPwd.length() < 8) {
            new Alert(Alert.AlertType.WARNING, "New password must be at least 8 characters.").showAndWait();
            return;
        }
        if (!newPwd.equals(confirm)) {
            new Alert(Alert.AlertType.WARNING, "New password and confirmation do not match.").showAndWait();
            return;
        }

        FUser user;
        try {
            user = SessionManager.getCurrentUser();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Session expired. Please log in again.").showAndWait();
            return;
        }

        if (!BCrypt.checkpw(current, user.getPasswordHash())) {
            new Alert(Alert.AlertType.ERROR, "Current password is incorrect.").showAndWait();
            return;
        }

        try {
            String hash = BCrypt.hashpw(newPwd, BCrypt.gensalt(12));
            fUserDao.updatePasswordByUserId(user.getId(), hash);
            txtCurrentPwd.clear();
            txtNewPwd.clear();
            txtConfirmPwd.clear();
            showSuccessToast("Password updated successfully.");
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Failed to update password: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handleResetPassword(ActionEvent event) {
        System.out.println("Sending reset email to: " + txtForgotEmail.getText());
    }

    // --- Modal Logic ---

    @FXML
    private void handleShowCreateModal(ActionEvent event) {
        isEditMode = false;
        lblModalTitle.setText("Create New User");
        btnModalSave.setText("Create User");
        clearModal();
        
        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    public void showEditModal(/* Pass User Object here from TableView */) {
        isEditMode = true;
        lblModalTitle.setText("Edit User");
        btnModalSave.setText("Update User");
        // Populate fields with user data...
        
        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    @FXML
    private void handleCloseModal(ActionEvent event) {
        modalOverlay.setVisible(false);
        modalOverlay.setManaged(false);
    }

    @FXML
    private void handleSaveUser(ActionEvent event) {
        if (isEditMode) {
            new Alert(Alert.AlertType.INFORMATION, "Edit user will be implemented in a later phase.").showAndWait();
            return;
        }
        String username = txtModalUsername.getText();
        String email = txtModalEmail.getText();
        String password = txtModalPassword.getText();
        String roleStr = cmbModalRole.getValue();
        String firstName = txtModalFirstName.getText();
        String lastName = txtModalLastName.getText();
        String phone = txtModalPhone.getText();
        boolean active = chkModalActive.isSelected();

        if (username == null || username.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Username is required.").showAndWait();
            return;
        }
        if (email == null || email.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Email is required.").showAndWait();
            return;
        }
        if (password == null || password.length() < 8) {
            new Alert(Alert.AlertType.WARNING, "Initial password must be at least 8 characters.").showAndWait();
            return;
        }
        UserRole role = "Admin".equals(roleStr) ? UserRole.ADMIN : UserRole.FINANCE_USER;

        try {
            userService.createUser(email.trim(), username.trim(), password, role,
                    firstName != null ? firstName.trim() : null,
                    lastName != null ? lastName.trim() : null,
                    phone != null && !phone.isBlank() ? phone.trim() : null,
                    active);
            refreshUsers();
            handleCloseModal(null);
            showSuccessToast("User created successfully.");
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not create user: " + e.getMessage()).showAndWait();
        }
    }

    private void showSuccessToast(String message) {
        if (rootStackPane == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/raez/finance/view/NotificationToast.fxml"));
            Node toastNode = loader.load();
            NotificationToastController c = loader.getController();
            if (c != null) {
                c.setNotification("success", message, () -> {
                    if (rootStackPane.getChildren().contains(toastNode)) {
                        rootStackPane.getChildren().remove(toastNode);
                    }
                });
            }
            rootStackPane.getChildren().add(toastNode);
            StackPane.setAlignment(toastNode, Pos.TOP_RIGHT);
            StackPane.setMargin(toastNode, new javafx.geometry.Insets(24, 24, 0, 24));
        } catch (Exception e) {
            new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
        }
    }

    private void clearModal() {
        txtModalUsername.clear();
        txtModalPassword.clear();
        txtModalFirstName.clear();
        txtModalLastName.clear();
        txtModalEmail.clear();
        txtModalPhone.clear();
        txtModalIdCard.clear();
        txtModalAddress.clear();
        cmbModalRole.setValue("Finance User");
        chkModalActive.setSelected(true);
    }
}