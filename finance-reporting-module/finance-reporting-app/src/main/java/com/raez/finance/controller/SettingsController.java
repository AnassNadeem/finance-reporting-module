package com.raez.finance.controller;

import com.raez.finance.dao.FUserDao;
import com.raez.finance.dao.PasswordResetTokenDao;
import com.raez.finance.dao.RolePermissionDao;
import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.GlobalSettingsService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.service.UserService;
import com.raez.finance.util.PasswordGenerator;
import com.raez.finance.util.ValidationUtils;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;

import org.mindrot.jbcrypt.BCrypt;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsController {

    private final FUserDao fUserDao = new FUserDao();
    private final PasswordResetTokenDao resetTokenDao = new PasswordResetTokenDao();
    private final RolePermissionDao rolePermissionDao = new RolePermissionDao();
    private final UserService userService = new UserService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final DateTimeFormatter DATE_ONLY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML private StackPane rootStackPane;

    // --- Tabs ---
    @FXML private Button btnTabAccount;
    @FXML private Button btnTabUsers;
    @FXML private Button btnTabFinancial;
    @FXML private VBox viewAccount;
    @FXML private VBox viewUsers;
    @FXML private VBox viewFinancial;

    // --- Company & Financial tab ---
    @FXML private TextField txtCompanyName;
    @FXML private TextField txtCompanyAddress;
    @FXML private TextField txtVatPercent;
    @FXML private ComboBox<String> cmbCurrency;
    @FXML private ComboBox<String> cmbFinancialYearMonth;

    // --- Account Tab Form ---
    @FXML private Label lblAccountEmail;
    @FXML private Label lblAccountName;
    @FXML private Label lblAccountRole;
    @FXML private Label lblAccountCreatedAt;
    @FXML private PasswordField txtCurrentPwd;
    @FXML private PasswordField txtNewPwd;
    @FXML private PasswordField txtConfirmPwd;

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

        if (!SessionManager.isAdmin()) {
            btnTabUsers.setVisible(false);
            btnTabUsers.setManaged(false);
            viewUsers.setVisible(false);
            viewUsers.setManaged(false);
            if (btnTabFinancial != null) {
                btnTabFinancial.setVisible(false);
                btnTabFinancial.setManaged(false);
            }
            if (viewFinancial != null) {
                viewFinancial.setVisible(false);
                viewFinancial.setManaged(false);
            }
        } else {
            refreshUsers();
        }

        if (cmbCurrency != null) {
            cmbCurrency.setItems(FXCollections.observableArrayList("£", "$", "€", "¥", "CHF", "₹"));
            cmbCurrency.setValue("£");
        }
        if (cmbFinancialYearMonth != null) {
            cmbFinancialYearMonth.setItems(FXCollections.observableArrayList(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"));
            cmbFinancialYearMonth.setValue("January");
        }
        switchTab("account");
        loadAccountDetails();
    }

    private void loadAccountDetails() {
        if (lblAccountEmail == null) return;
        try {
            FUser user = SessionManager.getCurrentUser();
            lblAccountEmail.setText(user.getEmail() != null ? user.getEmail() : "—");
            String first = user.getFirstName() != null ? user.getFirstName() : "";
            String last = user.getLastName() != null ? user.getLastName() : "";
            String full = (first + " " + last).trim();
            lblAccountName.setText(full.isEmpty() ? "—" : full);
            lblAccountRole.setText(user.getRole() != null ? user.getRole().name() : "—");
            String createdAt = fUserDao.getCreatedAt(user.getId());
            lblAccountCreatedAt.setText(createdAt != null ? formatCreatedAt(createdAt) : "—");
        } catch (Exception e) {
            lblAccountEmail.setText("—");
            lblAccountName.setText("—");
            lblAccountRole.setText("—");
            lblAccountCreatedAt.setText("—");
        }
    }

    private static String formatCreatedAt(String sqlTimestamp) {
        if (sqlTimestamp == null) return "—";
        try {
            java.time.LocalDateTime dt = java.time.LocalDateTime.parse(sqlTimestamp.replace(" ", "T"));
            return dt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        } catch (Exception e) {
            return sqlTimestamp;
        }
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
        colRole.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getRole() == UserRole.ADMIN ? "Admin" : "User"));
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().isActive() ? "active" : "inactive"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if ("active".equalsIgnoreCase(item)) {
                    setStyle("-fx-background-color: #D1FAE5; -fx-background-radius: 4; -fx-padding: 4 10; -fx-text-fill: #065F46; -fx-alignment: center;");
                } else {
                    setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 4; -fx-padding: 4 10; -fx-text-fill: #4B5563; -fx-alignment: center;");
                }
            }
        });
        colLastLogin.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLastLogin() == null ? "—" : c.getValue().getLastLogin().format(DATE_ONLY_FMT)));
        colActions.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(""));
        colActions.setCellFactory(createActionsCellFactory());
    }

    private Callback<TableColumn<FUser, String>, TableCell<FUser, String>> createActionsCellFactory() {
        return col -> new TableCell<>() {
            private final HBox box = new HBox(8);
            private final Button btnEdit = new Button();
            private final Button btnDelete = new Button();
            private final Button btnToken = new Button();

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnEdit.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btnEdit.setTooltip(new Tooltip("Edit / Update"));
                SVGPath editSvg = new SVGPath();
                editSvg.setContent("M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z");
                editSvg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                editSvg.setStroke(javafx.scene.paint.Color.valueOf("#4B5563"));
                editSvg.setStrokeWidth(2);
                btnEdit.setGraphic(editSvg);

                btnDelete.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btnDelete.setTooltip(new Tooltip("Delete"));
                SVGPath deleteSvg = new SVGPath();
                deleteSvg.setContent("M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16");
                deleteSvg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                deleteSvg.setStroke(javafx.scene.paint.Color.valueOf("#DC2626"));
                deleteSvg.setStrokeWidth(2);
                btnDelete.setGraphic(deleteSvg);

                btnToken.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btnToken.setTooltip(new Tooltip("Generate token"));
                SVGPath keySvg = new SVGPath();
                keySvg.setContent("M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z");
                keySvg.setFill(javafx.scene.paint.Color.TRANSPARENT);
                keySvg.setStroke(javafx.scene.paint.Color.valueOf("#4B5563"));
                keySvg.setStrokeWidth(2);
                btnToken.setGraphic(keySvg);

                box.getChildren().addAll(btnEdit, btnDelete, btnToken);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                FUser user = getTableRow().getItem();
                btnEdit.setOnAction(e -> showEditModal(user));
                btnDelete.setOnAction(e -> confirmAndDelete(user));
                btnToken.setOnAction(e -> showResetTokenForUser(user));
                setGraphic(box);
            }
        };
    }

    private void confirmAndDelete(FUser user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete user \"" + (user.getUsername() != null ? user.getUsername() : user.getEmail()) + "\"?");
        confirm.setContentText("Are you sure you want to delete this user? This action cannot be undone.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    fUserDao.deleteByUserId(user.getId());
                    refreshUsers();
                    showSuccessToast("User deleted.");
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "Could not delete user: " + ex.getMessage()).showAndWait();
                }
            }
        });
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
        if (rolePermissionDao.hasPermission(SessionManager.getRole(), "MANAGE_USERS")) {
            refreshUsers();
        }
    }

    @FXML
    private void handleTabFinancial(ActionEvent event) {
        switchTab("financial");
    }

    @FXML
    private void handleSaveFinancialSettings(ActionEvent event) {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        try {
            double vat = 20.0;
            if (txtVatPercent != null && txtVatPercent.getText() != null && !txtVatPercent.getText().isBlank()) {
                vat = Double.parseDouble(txtVatPercent.getText().trim());
                if (vat < 0 || vat > 100) {
                    showAlert("Invalid VAT", "VAT percentage must be between 0 and 100.");
                    return;
                }
            }
            gs.setDefaultVatPercent(vat);
            gs.setCompanyName(txtCompanyName != null ? txtCompanyName.getText() : "");
            gs.setCompanyAddress(txtCompanyAddress != null ? txtCompanyAddress.getText() : "");
            if (cmbCurrency != null && cmbCurrency.getValue() != null) gs.setDefaultCurrencySymbol(cmbCurrency.getValue());
            if (cmbFinancialYearMonth != null && cmbFinancialYearMonth.getValue() != null) {
                int month = cmbFinancialYearMonth.getItems().indexOf(cmbFinancialYearMonth.getValue()) + 1;
                gs.setFinancialYearStartMonth(month);
            }
            gs.save();
            showAlert("Saved", "Company & financial settings have been saved. Reports and dashboard will use these values.");
        } catch (NumberFormatException e) {
            showAlert("Invalid VAT", "Please enter a valid number for VAT percentage (e.g. 20).");
        }
    }

    private void showAlert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void switchTab(String tab) {
        String activeStyle = "-fx-background-color: transparent; -fx-border-color: #1E2939; -fx-border-width: 0 0 3 0; -fx-text-fill: #1E2939; -fx-cursor: hand; -fx-font-weight: bold;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #4B5563; -fx-cursor: hand;";

        btnTabAccount.setStyle(inactiveStyle);
        btnTabUsers.setStyle(inactiveStyle);
        if (btnTabFinancial != null) btnTabFinancial.setStyle(inactiveStyle);
        viewAccount.setVisible(false);
        viewAccount.setManaged(false);
        viewUsers.setVisible(false);
        viewUsers.setManaged(false);
        if (viewFinancial != null) {
            viewFinancial.setVisible(false);
            viewFinancial.setManaged(false);
        }

        if (tab.equals("account")) {
            btnTabAccount.setStyle(activeStyle);
            viewAccount.setVisible(true);
            viewAccount.setManaged(true);
            loadAccountDetails();
        } else if (tab.equals("users")) {
            btnTabUsers.setStyle(activeStyle);
            viewUsers.setVisible(true);
            viewUsers.setManaged(true);
        } else if (tab.equals("financial") && btnTabFinancial != null && viewFinancial != null) {
            btnTabFinancial.setStyle(activeStyle);
            viewFinancial.setVisible(true);
            viewFinancial.setManaged(true);
            loadFinancialSettings();
        }
    }

    private void loadFinancialSettings() {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        if (txtCompanyName != null) txtCompanyName.setText(gs.getCompanyName());
        if (txtCompanyAddress != null) txtCompanyAddress.setText(gs.getCompanyAddress());
        if (txtVatPercent != null) txtVatPercent.setText(String.valueOf((int) Math.round(gs.getDefaultVatPercent())));
        if (cmbCurrency != null) {
            String sym = gs.getDefaultCurrencySymbol();
            if (sym != null && cmbCurrency.getItems().contains(sym)) cmbCurrency.setValue(sym);
            else cmbCurrency.setValue("£");
        }
        if (cmbFinancialYearMonth != null) {
            int m = gs.getFinancialYearStartMonth();
            if (m >= 1 && m <= 12) cmbFinancialYearMonth.setValue(cmbFinancialYearMonth.getItems().get(m - 1));
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
        String pwdError = ValidationUtils.validateNewPassword(newPwd);
        if (pwdError != null) {
            new Alert(Alert.AlertType.WARNING, pwdError).showAndWait();
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

    /** Admin-only: generate reset token for a user (e.g. from User Management table). */
    void showResetTokenForUser(FUser user) {
        if (user == null) return;
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "User has no email.").showAndWait();
            return;
        }
        try {
            String token = resetTokenDao.createToken(user.getId());
            showResetTokenDialog(email.trim(), token);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not create reset token: " + e.getMessage()).showAndWait();
        }
    }

    private void showResetTokenDialog(String email, String token) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Password Reset Token");
        dialog.setHeaderText("Share this one-time token with the user");
        dialog.setContentText("The user must use this token on the login screen (Forgot password) to set a new password.\n\nToken expires in 24 hours.\n\nEmail: " + email + "\nToken: " + token);

        ButtonType copyButton = new ButtonType("Copy Token", ButtonBar.ButtonData.APPLY);
        dialog.getButtonTypes().setAll(copyButton, ButtonType.OK);

        java.util.Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == copyButton) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(token);
            clipboard.setContent(content);
            showSuccessToast("Token copied to clipboard.");
        }
    }

    // --- Modal Logic ---

    @FXML
    private void handleShowCreateModal(ActionEvent event) {
        storedEditUser = null;
        isEditMode = false;
        lblModalTitle.setText("Create New User");
        btnModalSave.setText("Create User");
        clearModal();
        txtModalPassword.setDisable(true);
        txtModalPassword.setPromptText("(auto-generated)");

        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    public void showEditModal(FUser user) {
        storedEditUser = user;
        isEditMode = true;
        lblModalTitle.setText("Edit User");
        btnModalSave.setText("Update User");
        txtModalUsername.setText(user.getUsername());
        txtModalFirstName.setText(user.getFirstName() != null ? user.getFirstName() : "");
        txtModalLastName.setText(user.getLastName() != null ? user.getLastName() : "");
        txtModalEmail.setText(user.getEmail());
        txtModalPhone.setText(user.getPhone() != null ? user.getPhone() : "");
        if (txtModalIdCard != null) txtModalIdCard.clear();
        if (txtModalAddress != null) txtModalAddress.clear();
        txtModalPassword.clear();
        txtModalPassword.setDisable(true);
        cmbModalRole.setValue(user.getRole() == UserRole.ADMIN ? "Admin" : "Finance User");
        chkModalActive.setSelected(user.isActive());
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
            saveEditUser();
            return;
        }
        String username = txtModalUsername.getText();
        String email = txtModalEmail.getText();
        String roleStr = cmbModalRole.getValue();
        String firstName = txtModalFirstName.getText();
        String lastName = txtModalLastName.getText();
        String phone = txtModalPhone.getText();
        boolean active = chkModalActive.isSelected();

        if (username == null || username.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Username is required.").showAndWait();
            return;
        }
        if (firstName == null || firstName.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "First Name is required.").showAndWait();
            return;
        }
        if (lastName == null || lastName.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Last Name is required.").showAndWait();
            return;
        }
        if (email == null || email.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Email is required.").showAndWait();
            return;
        }
        if (!ValidationUtils.isRaezEmail(email)) {
            new Alert(Alert.AlertType.WARNING, "Email must be a valid @raez.org.uk address.").showAndWait();
            return;
        }
        UserRole role = "Admin".equals(roleStr) ? UserRole.ADMIN : UserRole.FINANCE_USER;

        String tempPassword = PasswordGenerator.generate();
        try {
            userService.createUser(email.trim(), username.trim(), tempPassword, role,
                    firstName != null ? firstName.trim() : null,
                    lastName != null ? lastName.trim() : null,
                    phone != null && !phone.isBlank() ? phone.trim() : null,
                    active);
            refreshUsers();
            handleCloseModal(null);
            showSuccessToast("User created successfully.");
            showTempPasswordDialog(email.trim(), tempPassword);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not create user: " + e.getMessage()).showAndWait();
        }
    }

    private void showTempPasswordDialog(String email, String tempPassword) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Temporary Password");
        dialog.setHeaderText("Share this temporary password with the user");
        dialog.setContentText("The user must log in and set a new password on first login.\n\nEmail/Username: " + email + "\nTemporary password: " + tempPassword);

        ButtonType copyButton = new ButtonType("Copy to Clipboard", ButtonBar.ButtonData.APPLY);
        dialog.getButtonTypes().setAll(copyButton, ButtonType.OK);

        java.util.Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == copyButton) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(tempPassword);
            clipboard.setContent(content);
            showSuccessToast("Password copied to clipboard.");
        }
    }

    private void saveEditUser() {
        FUser current = getStoredEditUser();
        if (current == null) return;
        String username = txtModalUsername.getText();
        String email = txtModalEmail.getText();
        String firstName = txtModalFirstName.getText();
        String lastName = txtModalLastName.getText();
        String phone = txtModalPhone.getText();
        String roleStr = cmbModalRole.getValue();
        boolean active = chkModalActive.isSelected();

        if (username == null || username.isBlank() || email == null || email.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Username and email are required.").showAndWait();
            return;
        }
        if (!ValidationUtils.isRaezEmail(email)) {
            new Alert(Alert.AlertType.WARNING, "Email must be a valid @raez.org.uk address.").showAndWait();
            return;
        }
        UserRole role = "Admin".equals(roleStr) ? UserRole.ADMIN : UserRole.FINANCE_USER;
        try {
            fUserDao.updateUser(current.getId(), email.trim(), username.trim(),
                    firstName != null ? firstName.trim() : null,
                    lastName != null ? lastName.trim() : null,
                    phone != null && !phone.isBlank() ? phone.trim() : null,
                    role, active);
            storedEditUser = null;
            refreshUsers();
            handleCloseModal(null);
            showSuccessToast("User updated.");
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not update user: " + e.getMessage()).showAndWait();
        }
    }

    private FUser storedEditUser = null;

    private FUser getStoredEditUser() {
        return storedEditUser;
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
        if (txtModalPassword != null) {
            txtModalPassword.setDisable(false);
            txtModalPassword.setPromptText("");
        }
        txtModalFirstName.clear();
        txtModalLastName.clear();
        txtModalEmail.clear();
        txtModalPhone.clear();
        if (txtModalIdCard != null) txtModalIdCard.clear();
        if (txtModalAddress != null) txtModalAddress.clear();
        cmbModalRole.setValue("Finance User");
        chkModalActive.setSelected(true);
    }
}