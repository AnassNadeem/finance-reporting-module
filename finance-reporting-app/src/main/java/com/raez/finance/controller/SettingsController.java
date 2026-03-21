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
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;
import javafx.util.Duration;
import org.mindrot.jbcrypt.BCrypt;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SettingsController {

    // ── Services ─────────────────────────────────────────────────────────
    private final FUserDao             fUserDao       = new FUserDao();
    private final PasswordResetTokenDao resetTokenDao = new PasswordResetTokenDao();
    private final RolePermissionDao    rolePermDao    = new RolePermissionDao();
    private final UserService          userService    = new UserService();
    private final ExecutorService      executor       = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "settings-worker");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ── Injected from parent ─────────────────────────────────────────────
    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    // ── FXML — root ──────────────────────────────────────────────────────
    @FXML private StackPane rootStackPane;

    // ── FXML — tabs ──────────────────────────────────────────────────────
    @FXML private Button btnTabAccount;
    @FXML private Button btnTabUsers;
    @FXML private Button btnTabFinancial;
    @FXML private VBox   viewAccount;
    @FXML private VBox   viewUsers;
    @FXML private VBox   viewFinancial;

    // ── FXML — Account tab ───────────────────────────────────────────────
    @FXML private Label         lblAccountEmail;
    @FXML private Label         lblAccountName;
    @FXML private Label         lblAccountRole;
    @FXML private Label         lblAccountCreatedAt;
    @FXML private PasswordField txtCurrentPwd;
    @FXML private PasswordField txtNewPwd;
    @FXML private PasswordField txtConfirmPwd;

    // ── FXML — User Management tab ───────────────────────────────────────
    @FXML private TableView<FUser>             tblUsers;
    @FXML private TableColumn<FUser, String>   colUsername;
    @FXML private TableColumn<FUser, String>   colName;
    @FXML private TableColumn<FUser, String>   colEmail;
    @FXML private TableColumn<FUser, String>   colRole;
    @FXML private TableColumn<FUser, String>   colStatus;
    @FXML private TableColumn<FUser, String>   colLastLogin;
    @FXML private TableColumn<FUser, String>   colActions;

    // ── FXML — User modal ────────────────────────────────────────────────
    @FXML private StackPane modalOverlay;
    @FXML private VBox      modalCard;
    @FXML private Label          lblModalTitle;
    @FXML private TextField      txtModalUsername;
    @FXML private PasswordField  txtModalPassword;
    @FXML private ComboBox<String> cmbModalRole;
    @FXML private TextField      txtModalFirstName;
    @FXML private TextField      txtModalLastName;
    @FXML private TextField      txtModalEmail;
    @FXML private TextField      txtModalPhone;
    @FXML private TextField      txtModalStaffId;
    @FXML private TextField      txtModalAddress1;
    @FXML private TextField      txtModalAddress2;
    @FXML private TextField      txtModalAddress3;
    @FXML private Label          lblErrFirstName;
    @FXML private Label          lblErrLastName;
    @FXML private Label          lblErrUsername;
    @FXML private Label          lblErrStaffId;
    @FXML private Label          lblErrEmail;
    @FXML private Label          lblErrPhone;
    @FXML private Label          lblErrAddress1;
    @FXML private CheckBox       chkModalActive;
    @FXML private Button         btnModalSave;

    // ── FXML — Company & Financials tab ──────────────────────────────────
    @FXML private TextField        txtCompanyName;
    @FXML private TextField        txtCompanyAddress;
    @FXML private TextField        txtVatPercent;
    @FXML private ComboBox<String> cmbCurrency;
    @FXML private ComboBox<String> cmbFinancialYearMonth;
    @FXML private CheckBox         chkSmtpEnabled;
    @FXML private TextField        txtSmtpHost;
    @FXML private TextField        txtSmtpPort;
    @FXML private TextField        txtSmtpFrom;
    @FXML private TextField        txtSmtpUser;
    @FXML private PasswordField    pwdSmtpPassword;
    @FXML private CheckBox         chkSmtpTls;

    // ── Internal state ───────────────────────────────────────────────────
    private boolean isEditMode    = false;
    private FUser   storedEditUser = null;

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // Role ComboBox seed
        if (cmbModalRole != null) {
            cmbModalRole.setItems(FXCollections.observableArrayList("Admin", "Finance User"));
            cmbModalRole.setValue("Finance User");
        }

        // Currency + financial year
        if (cmbCurrency != null)
            cmbCurrency.setItems(FXCollections.observableArrayList("£", "$", "€", "¥", "CHF", "₹"));
        if (cmbFinancialYearMonth != null)
            cmbFinancialYearMonth.setItems(FXCollections.observableArrayList(
                "January","February","March","April","May","June",
                "July","August","September","October","November","December"));

        bindUserColumns();
        if (tblUsers != null) {
            tblUsers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            tblUsers.setRowFactory(tv -> {
                TableRow<FUser> row = new TableRow<>();
                row.itemProperty().addListener((obs, oldU, u) -> {
                    if (u == null || row.isEmpty()) {
                        row.setStyle("");
                        return;
                    }
                    int i = row.getIndex();
                    row.setStyle(i % 2 == 0
                        ? "-fx-background-color: #FFFFFF;"
                        : "-fx-background-color: #F9FAFB;");
                });
                row.setOnMouseEntered(e -> {
                    if (!row.isEmpty()) row.setStyle("-fx-background-color: #EFF6FF;");
                });
                row.setOnMouseExited(e -> {
                    if (row.isEmpty()) return;
                    int i = row.getIndex();
                    row.setStyle(i % 2 == 0
                        ? "-fx-background-color: #FFFFFF;"
                        : "-fx-background-color: #F9FAFB;");
                });
                return row;
            });
        }
        wireModalLiveValidation();

        boolean isAdmin = SessionManager.isAdmin();

        // Hide admin-only tabs for non-admins
        if (!isAdmin) {
            hide(btnTabUsers);
            hide(viewUsers);
            hide(btnTabFinancial);
            hide(viewFinancial);
        } else {
            refreshUsers();
        }

        switchTab("account");
        loadAccountDetails();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACCOUNT TAB
    // ══════════════════════════════════════════════════════════════════════

    private void loadAccountDetails() {
        if (lblAccountEmail == null) return;
        try {
            FUser user = SessionManager.getCurrentUser();
            lblAccountEmail.setText(nvl(user.getEmail()));
            String first = user.getFirstName() != null ? user.getFirstName() : "";
            String last  = user.getLastName() != null ? user.getLastName() : "";
            String full = (first + " " + last).trim();
            lblAccountName.setText(full.isEmpty() ? "-" : full);
            lblAccountRole.setText(user.getRole() != null ? user.getRole().name() : "-");
            String ca = fUserDao.getCreatedAt(user.getId());
            lblAccountCreatedAt.setText(formatTs(ca));
        } catch (Exception e) {
            lblAccountEmail.setText("-");
            if (lblAccountName    != null) lblAccountName.setText("-");
            if (lblAccountRole    != null) lblAccountRole.setText("-");
            if (lblAccountCreatedAt != null) lblAccountCreatedAt.setText("-");
        }
    }

    @FXML
    private void handleUpdatePassword(ActionEvent event) {
        String current = txtCurrentPwd.getText();
        String newPwd  = txtNewPwd.getText();
        String confirm = txtConfirmPwd.getText();

        if (current == null || current.isBlank()) {
            toast("warning", "Enter your current password.");
            return;
        }
        String err = ValidationUtils.validateNewPassword(newPwd);
        if (err != null) { toast("warning", err); return; }
        if (!newPwd.equals(confirm)) { toast("warning", "Passwords do not match."); return; }

        FUser user;
        try { user = SessionManager.getCurrentUser(); }
        catch (Exception e) { toast("error", "Session expired. Please log in again."); return; }

        if (!BCrypt.checkpw(current, user.getPasswordHash())) {
            toast("error", "Current password is incorrect.");
            return;
        }
        try {
            fUserDao.updatePasswordByUserId(user.getId(),
                BCrypt.hashpw(newPwd, BCrypt.gensalt(12)));
            txtCurrentPwd.clear(); txtNewPwd.clear(); txtConfirmPwd.clear();
            toast("success", "Password updated successfully.");
        } catch (Exception e) {
            toast("error", "Failed to update password: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  USER MANAGEMENT TAB
    // ══════════════════════════════════════════════════════════════════════

    private void bindUserColumns() {
        colUsername.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getUsername()));

        colName.setCellValueFactory(c -> {
            String f = c.getValue().getFirstName() != null ? c.getValue().getFirstName() : "";
            String l = c.getValue().getLastName()  != null ? c.getValue().getLastName()  : "";
            String full = (f + " " + l).trim();
            return new javafx.beans.property.SimpleStringProperty(full.isEmpty() ? "-" : full);
        });

        colEmail.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getEmail()));

        colRole.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getRole() == UserRole.ADMIN ? "Admin" : "User"));

        // Status as oval badge
        colStatus.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().isActive() ? "Active" : "Inactive"));
        colStatus.setCellFactory(col -> new TableCell<FUser, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                boolean active = "Active".equalsIgnoreCase(item);
                badge.setStyle(
                    "-fx-font-size: 10px; -fx-font-weight: 700;" +
                    "-fx-padding: 2 10 2 10; -fx-background-radius: 999;" +
                    (active
                        ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                        : "-fx-background-color: #F3F4F6; -fx-text-fill: #4B5563;"));
                HBox w = new HBox(badge);
                w.setAlignment(Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });

        colLastLogin.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLastLogin() == null ? "-"
                    : c.getValue().getLastLogin()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))));

        colActions.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(""));
        colActions.setCellFactory(buildActionsCellFactory());
    }

    private Callback<TableColumn<FUser, String>, TableCell<FUser, String>> buildActionsCellFactory() {
        return col -> new TableCell<FUser, String>() {
            private final Button btnEdit   = iconBtn(
                "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5" +
                "m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z",
                "#4B5563", "Edit user");
            private final Button btnDelete = iconBtn(
                "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7" +
                "m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16",
                "#DC2626", "Delete user");
            private final Button btnToken  = iconBtn(
                "M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1" +
                "v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z",
                "#4B5563", "Generate reset token");

            private final HBox box = new HBox(6, btnEdit, btnDelete, btnToken);
            { box.setAlignment(Pos.CENTER_LEFT); }

            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null); return;
                }
                FUser user = getTableRow().getItem();
                btnEdit.setOnAction(e   -> showEditModal(user));
                btnDelete.setOnAction(e -> confirmAndDelete(user));
                btnToken.setOnAction(e  -> showResetTokenForUser(user));
                setGraphic(box);
            }
        };
    }

    private Button iconBtn(String svgContent, String strokeHex, String tooltip) {
        Button btn = new Button();
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;" +
                     "-fx-border-color: transparent; -fx-padding: 4;");
        btn.setTooltip(new Tooltip(tooltip));
        SVGPath svg = new SVGPath();
        svg.setContent(svgContent);
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(Color.web(strokeHex));
        svg.setStrokeWidth(1.8);
        btn.setGraphic(svg);
        btn.setOnMouseEntered(e ->
            btn.setStyle("-fx-background-color: #F3F4F6; -fx-cursor: hand;" +
                         "-fx-border-color: transparent; -fx-padding: 4; -fx-background-radius: 6;"));
        btn.setOnMouseExited(e ->
            btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;" +
                         "-fx-border-color: transparent; -fx-padding: 4;"));
        return btn;
    }

    private void refreshUsers() {
        if (tblUsers == null) return;
        Task<List<FUser>> task = new Task<List<FUser>>() {
            @Override protected List<FUser> call() throws Exception {
                return fUserDao.findAll();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null)
                tblUsers.setItems(FXCollections.observableList(task.getValue()));
        });
        task.setOnFailed(e -> toast("error", "Failed to load users."));
        executor.execute(task);
    }

    private void confirmAndDelete(FUser user) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Delete User");
        dlg.setHeaderText("Delete \"" + nvl(user.getUsername()) + "\"?");
        dlg.setContentText("This action cannot be undone.");
        dlg.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    fUserDao.deleteByUserId(user.getId());
                    refreshUsers();
                    toast("success", "User deleted.");
                } catch (Exception ex) {
                    toast("error", "Could not delete user: " + ex.getMessage());
                }
            }
        });
    }

    // ── Modal ────────────────────────────────────────────────────────────

    private void wireModalLiveValidation() {
        Runnable r = this::revalidateModalForm;
        if (txtModalFirstName != null) txtModalFirstName.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalLastName  != null) txtModalLastName.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalUsername  != null) txtModalUsername.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalStaffId   != null) txtModalStaffId.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalEmail     != null) txtModalEmail.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalPhone     != null) txtModalPhone.textProperty().addListener((o, a, b) -> r.run());
        if (txtModalAddress1  != null) txtModalAddress1.textProperty().addListener((o, a, b) -> r.run());
        if (cmbModalRole      != null) cmbModalRole.valueProperty().addListener((o, a, b) -> r.run());
    }

    private void revalidateModalForm() {
        if (btnModalSave == null) return;
        if (isEditMode) {
            btnModalSave.setDisable(!isEditFormStructurallyValid());
            return;
        }
        btnModalSave.setDisable(!isCreateFormStructurallyValid());
    }

    private boolean isCreateFormStructurallyValid() {
        if (!txt(txtModalFirstName).isEmpty()
            && !txt(txtModalLastName).isEmpty()
            && !txt(txtModalUsername).isEmpty()
            && !txt(txtModalStaffId).isEmpty()
            && !txt(txtModalAddress1).isEmpty()) {
            String email = txt(txtModalEmail);
            String phone = txt(txtModalPhone);
            return ValidationUtils.isRaezEmail(email)
                && !phone.isEmpty()
                && phone.replaceAll("\\D", "").length() >= 8;
        }
        return false;
    }

    private boolean isEditFormStructurallyValid() {
        if (txt(txtModalUsername).isEmpty()) return false;
        String email = txt(txtModalEmail);
        if (!ValidationUtils.isRaezEmail(email)) return false;
        String phone = txt(txtModalPhone);
        if (phone.isEmpty() || phone.replaceAll("\\D", "").length() < 8) return false;
        if (txt(txtModalStaffId).isEmpty() || txt(txtModalAddress1).isEmpty()) return false;
        return true;
    }

    private boolean validateAndShowErrorsCreate() {
        clearFieldErrors();
        boolean ok = true;
        if (txt(txtModalFirstName).isEmpty()) {
            fieldError(txtModalFirstName, lblErrFirstName, "Required"); ok = false;
        }
        if (txt(txtModalLastName).isEmpty()) {
            fieldError(txtModalLastName, lblErrLastName, "Required"); ok = false;
        }
        if (txt(txtModalUsername).isEmpty()) {
            fieldError(txtModalUsername, lblErrUsername, "Required"); ok = false;
        }
        if (txt(txtModalStaffId).isEmpty()) {
            fieldError(txtModalStaffId, lblErrStaffId, "Required"); ok = false;
        }
        String email = txt(txtModalEmail);
        if (email.isEmpty()) {
            fieldError(txtModalEmail, lblErrEmail, "Required"); ok = false;
        } else if (!ValidationUtils.isRaezEmail(email)) {
            fieldError(txtModalEmail, lblErrEmail, "Must end with @raez.org.uk"); ok = false;
        }
        String phone = txt(txtModalPhone);
        if (phone.isEmpty() || phone.replaceAll("\\D", "").length() < 8) {
            fieldError(txtModalPhone, lblErrPhone, "Enter a valid phone (8+ digits)"); ok = false;
        }
        if (txt(txtModalAddress1).isEmpty()) {
            fieldError(txtModalAddress1, lblErrAddress1, "Required"); ok = false;
        }
        return ok;
    }

    private boolean validateAndShowErrorsEdit() {
        clearFieldErrors();
        boolean ok = true;
        if (txt(txtModalUsername).isEmpty()) {
            fieldError(txtModalUsername, lblErrUsername, "Required"); ok = false;
        }
        String email = txt(txtModalEmail);
        if (email.isEmpty()) {
            fieldError(txtModalEmail, lblErrEmail, "Required"); ok = false;
        } else if (!ValidationUtils.isRaezEmail(email)) {
            fieldError(txtModalEmail, lblErrEmail, "Must end with @raez.org.uk"); ok = false;
        }
        String phone = txt(txtModalPhone);
        if (phone.isEmpty() || phone.replaceAll("\\D", "").length() < 8) {
            fieldError(txtModalPhone, lblErrPhone, "Enter a valid phone (8+ digits)"); ok = false;
        }
        if (txt(txtModalStaffId).isEmpty()) {
            fieldError(txtModalStaffId, lblErrStaffId, "Required"); ok = false;
        }
        if (txt(txtModalAddress1).isEmpty()) {
            fieldError(txtModalAddress1, lblErrAddress1, "Required"); ok = false;
        }
        return ok;
    }

    private void fieldError(TextField field, Label errLbl, String msg) {
        if (field != null) field.getStyleClass().add("form-input-invalid");
        if (errLbl != null) {
            errLbl.setText(msg);
            errLbl.setVisible(true);
            errLbl.setManaged(true);
        }
    }

    private void clearFieldErrors() {
        for (TextField f : new TextField[]{
            txtModalFirstName, txtModalLastName, txtModalUsername, txtModalStaffId,
            txtModalEmail, txtModalPhone, txtModalAddress1}) {
            if (f != null) f.getStyleClass().remove("form-input-invalid");
        }
        for (Label l : new Label[]{
            lblErrFirstName, lblErrLastName, lblErrUsername, lblErrStaffId,
            lblErrEmail, lblErrPhone, lblErrAddress1}) {
            if (l != null) {
                l.setText("");
                l.setVisible(false);
                l.setManaged(false);
            }
        }
    }

    @FXML
    private void handleShowCreateModal(ActionEvent event) {
        storedEditUser = null;
        isEditMode     = false;
        if (lblModalTitle  != null) lblModalTitle.setText("Create New User");
        if (btnModalSave   != null) btnModalSave.setText("Create User");
        clearModal();
        if (txtModalPassword != null) {
            txtModalPassword.setDisable(true);
            txtModalPassword.setPromptText("(auto-generated)");
        }
        revalidateModalForm();
        showModal();
    }

    public void showEditModal(FUser user) {
        storedEditUser = user;
        isEditMode     = true;
        clearFieldErrors();
        if (lblModalTitle  != null) lblModalTitle.setText("Edit User");
        if (btnModalSave   != null) btnModalSave.setText("Update User");
        if (txtModalUsername  != null) txtModalUsername.setText(nvl(user.getUsername()));
        if (txtModalFirstName != null) txtModalFirstName.setText(nvl(user.getFirstName()));
        if (txtModalLastName  != null) txtModalLastName.setText(nvl(user.getLastName()));
        if (txtModalEmail     != null) txtModalEmail.setText(nvl(user.getEmail()));
        if (txtModalPhone     != null) txtModalPhone.setText(nvl(user.getPhone()));
        if (txtModalStaffId   != null) txtModalStaffId.setText(nvl(user.getStaffID()));
        if (txtModalAddress1  != null) txtModalAddress1.setText(nvl(user.getAddressLine1()));
        if (txtModalAddress2  != null) txtModalAddress2.setText(nvl(user.getAddressLine2()));
        if (txtModalAddress3  != null) txtModalAddress3.setText(nvl(user.getAddressLine3()));
        if (txtModalPassword  != null) {
            txtModalPassword.clear();
            txtModalPassword.setDisable(true);
            txtModalPassword.setPromptText("(password not changed here)");
        }
        if (cmbModalRole   != null) cmbModalRole.setValue(user.getRole() == UserRole.ADMIN ? "Admin" : "Finance User");
        if (chkModalActive != null) chkModalActive.setSelected(user.isActive());
        revalidateModalForm();
        showModal();
    }

    @FXML private void handleCloseModal(ActionEvent event) { hideModal(); }

    @FXML
    private void handleSaveUser(ActionEvent event) {
        if (isEditMode) {
            if (!validateAndShowErrorsEdit()) return;
            saveEditUser();
            return;
        }
        if (!validateAndShowErrorsCreate()) return;

        String username  = txt(txtModalUsername);
        String email     = txt(txtModalEmail);
        String firstName = txt(txtModalFirstName);
        String lastName  = txt(txtModalLastName);
        String phone     = txt(txtModalPhone);
        String staffId   = txt(txtModalStaffId);
        String a1        = txt(txtModalAddress1);
        String a2        = txt(txtModalAddress2);
        String a3        = txt(txtModalAddress3);
        String roleStr   = cmbModalRole != null ? cmbModalRole.getValue() : "Finance User";
        boolean active   = chkModalActive == null || chkModalActive.isSelected();

        UserRole role = "Admin".equals(roleStr) ? UserRole.ADMIN : UserRole.FINANCE_USER;
        String tempPwd = PasswordGenerator.generate();
        try {
            userService.createUser(email, username, tempPwd, role,
                firstName, lastName, phone, staffId, a1, a2, a3, active);
            refreshUsers();
            hideModal();
            toast("success", "User created successfully.");
            showTempPasswordDialog(email, tempPwd);
        } catch (Exception ex) {
            toast("error", "Could not create user: " + ex.getMessage());
        }
    }

    private void saveEditUser() {
        FUser current = storedEditUser;
        if (current == null) return;
        String username  = txt(txtModalUsername);
        String email     = txt(txtModalEmail);
        String firstName = txt(txtModalFirstName);
        String lastName  = txt(txtModalLastName);
        String phone     = txt(txtModalPhone);
        String staffId   = txt(txtModalStaffId);
        String a1        = txt(txtModalAddress1);
        String a2        = txt(txtModalAddress2);
        String a3        = txt(txtModalAddress3);
        String roleStr   = cmbModalRole != null ? cmbModalRole.getValue() : "Finance User";
        boolean active   = chkModalActive == null || chkModalActive.isSelected();

        UserRole role = "Admin".equals(roleStr) ? UserRole.ADMIN : UserRole.FINANCE_USER;
        try {
            fUserDao.updateUser(current.getId(), email, username,
                firstName.isEmpty() ? null : firstName,
                lastName.isEmpty()  ? null : lastName,
                phone.isEmpty()     ? null : phone,
                staffId.isEmpty()   ? null : staffId,
                a1.isEmpty()        ? null : a1,
                a2.isEmpty()        ? null : a2,
                a3.isEmpty()        ? null : a3,
                role, active);
            storedEditUser = null;
            refreshUsers();
            hideModal();
            toast("success", "User updated.");
        } catch (Exception ex) {
            toast("error", "Could not update user: " + ex.getMessage());
        }
    }

    void showResetTokenForUser(FUser user) {
        if (user == null) return;
        String email = user.getEmail();
        if (email == null || email.isBlank()) { toast("warning", "User has no email."); return; }
        try {
            String token = resetTokenDao.createToken(user.getId());
            showResetTokenDialog(email.trim(), token);
        } catch (Exception ex) {
            toast("error", "Could not create reset token: " + ex.getMessage());
        }
    }

    private void showResetTokenDialog(String email, String token) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Password Reset Token");
        dlg.setHeaderText("Share this one-time token with the user");
        dlg.setContentText(
            "The user opens Forgot password on the login screen, sends email if needed, then enters this token and a new password.\n" +
            "Token expires in 24 hours.\n\n" +
            "Account email on file: " + email + "\nToken: " + token);
        ButtonType copy = new ButtonType("Copy Token", ButtonBar.ButtonData.APPLY);
        dlg.getButtonTypes().setAll(copy, ButtonType.OK);
        dlg.showAndWait().ifPresent(btn -> {
            if (btn == copy) {
                Clipboard cb = Clipboard.getSystemClipboard();
                ClipboardContent cc = new ClipboardContent();
                cc.putString(token);
                cb.setContent(cc);
                toast("success", "Token copied to clipboard.");
            }
        });
    }

    private void showTempPasswordDialog(String email, String tempPassword) {
        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
        dlg.setTitle("Temporary Password");
        dlg.setHeaderText("Share this temporary password with the new user");
        dlg.setContentText(
            "The user must log in and set a new password on first login.\n\n" +
            "Email: " + email + "\nTemporary password: " + tempPassword);
        ButtonType copy = new ButtonType("Copy to Clipboard", ButtonBar.ButtonData.APPLY);
        dlg.getButtonTypes().setAll(copy, ButtonType.OK);
        dlg.showAndWait().ifPresent(btn -> {
            if (btn == copy) {
                Clipboard cb = Clipboard.getSystemClipboard();
                ClipboardContent cc = new ClipboardContent();
                cc.putString(tempPassword);
                cb.setContent(cc);
                toast("success", "Password copied to clipboard.");
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COMPANY & FINANCIALS TAB
    // ══════════════════════════════════════════════════════════════════════

    private void loadFinancialSettings() {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        if (txtCompanyName    != null) txtCompanyName.setText(gs.getCompanyName());
        if (txtCompanyAddress != null) txtCompanyAddress.setText(gs.getCompanyAddress());
        if (txtVatPercent     != null) txtVatPercent.setText(
            String.valueOf((int) Math.round(gs.getDefaultVatPercent())));
        if (cmbCurrency != null) {
            String sym = gs.getDefaultCurrencySymbol();
            cmbCurrency.setValue(cmbCurrency.getItems().contains(sym) ? sym : "£");
        }
        if (cmbFinancialYearMonth != null) {
            int m = gs.getFinancialYearStartMonth();
            if (m >= 1 && m <= 12)
                cmbFinancialYearMonth.setValue(cmbFinancialYearMonth.getItems().get(m - 1));
        }
        if (chkSmtpEnabled != null) chkSmtpEnabled.setSelected(gs.isSmtpEnabled());
        if (txtSmtpHost != null) txtSmtpHost.setText(gs.getSmtpHost());
        if (txtSmtpPort != null) txtSmtpPort.setText(String.valueOf(gs.getSmtpPort()));
        if (txtSmtpFrom != null) txtSmtpFrom.setText(gs.getSmtpFrom());
        if (txtSmtpUser != null) txtSmtpUser.setText(gs.getSmtpUser());
        if (pwdSmtpPassword != null) pwdSmtpPassword.setText(gs.getSmtpPassword());
        if (chkSmtpTls != null) chkSmtpTls.setSelected(gs.isSmtpUseTls());
    }

    @FXML
    private void handleSaveFinancialSettings(ActionEvent event) {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        try {
            double vat = 20.0;
            if (txtVatPercent != null && !txtVatPercent.getText().isBlank()) {
                vat = Double.parseDouble(txtVatPercent.getText().trim());
                if (vat < 0 || vat > 100) { toast("warning", "VAT must be 0–100."); return; }
            }
            gs.setDefaultVatPercent(vat);
            gs.setCompanyName(    txtCompanyName    != null ? txtCompanyName.getText()    : "");
            gs.setCompanyAddress( txtCompanyAddress != null ? txtCompanyAddress.getText() : "");
            if (cmbCurrency           != null && cmbCurrency.getValue()           != null)
                gs.setDefaultCurrencySymbol(cmbCurrency.getValue());
            if (cmbFinancialYearMonth != null && cmbFinancialYearMonth.getValue() != null) {
                int m = cmbFinancialYearMonth.getItems().indexOf(cmbFinancialYearMonth.getValue()) + 1;
                gs.setFinancialYearStartMonth(m);
            }
            if (chkSmtpEnabled != null) gs.setSmtpEnabled(chkSmtpEnabled.isSelected());
            if (txtSmtpHost != null) gs.setSmtpHost(txtSmtpHost.getText());
            if (txtSmtpPort != null && !txtSmtpPort.getText().isBlank()) {
                try {
                    gs.setSmtpPort(Integer.parseInt(txtSmtpPort.getText().trim()));
                } catch (NumberFormatException e) {
                    toast("warning", "SMTP port must be a number.");
                    return;
                }
            }
            if (txtSmtpFrom != null) gs.setSmtpFrom(txtSmtpFrom.getText());
            if (txtSmtpUser != null) gs.setSmtpUser(txtSmtpUser.getText());
            if (pwdSmtpPassword != null) gs.setSmtpPassword(pwdSmtpPassword.getText());
            if (chkSmtpTls != null) gs.setSmtpUseTls(chkSmtpTls.isSelected());
            gs.save();
            toast("success", "Settings saved. Reports will reflect the new values.");
        } catch (NumberFormatException e) {
            toast("warning", "Enter a valid number for VAT (e.g. 20).");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TAB SWITCHING
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleTabAccount(ActionEvent e)  { switchTab("account");   }
    @FXML private void handleTabUsers(ActionEvent e)    { switchTab("users");     }
    @FXML private void handleTabFinancial(ActionEvent e){ switchTab("financial"); }

    private void switchTab(String tab) {
        String active   = "-fx-background-color: transparent; -fx-border-color: #1E2939;" +
                          "-fx-border-width: 0 0 3 0; -fx-text-fill: #1E2939;" +
                          "-fx-cursor: hand; -fx-font-weight: bold;";
        String inactive = "-fx-background-color: transparent; -fx-border-color: transparent;" +
                          "-fx-text-fill: #4B5563; -fx-cursor: hand;";

        setStyle(btnTabAccount,  inactive); setStyle(btnTabUsers,     inactive);
        setStyle(btnTabFinancial,inactive);
        hide(viewAccount); hide(viewUsers); hide(viewFinancial);

        switch (tab) {
            case "account":
                setStyle(btnTabAccount,  active);
                show(viewAccount);
                loadAccountDetails();
                break;
            case "users":
                setStyle(btnTabUsers,    active);
                show(viewUsers);
                refreshUsers();
                break;
            case "financial":
                setStyle(btnTabFinancial,active);
                show(viewFinancial);
                loadFinancialSettings();
                break;
            default:
                // no-op
                break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOAST
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Shows a toast. Prefers mainLayoutController.showToast() so it appears
     * in the main content area (top-right). Falls back to local rootStackPane.
     */
    private void toast(String type, String message) {
        if (mainLayoutController != null) {
            mainLayoutController.showToast(type, message);
            return;
        }
        // Fallback: show inside settings' own StackPane
        if (rootStackPane == null) {
            new Alert(type.equals("error") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION,
                message).showAndWait();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/raez/finance/view/NotificationToast.fxml"));
            Node toastNode = loader.load();
            NotificationToastController c = loader.getController();
            if (c != null) {
                c.setNotification(type, message, () -> {
                    if (rootStackPane.getChildren().contains(toastNode))
                        rootStackPane.getChildren().remove(toastNode);
                });
            }
            rootStackPane.getChildren().add(toastNode);
            StackPane.setAlignment(toastNode, Pos.TOP_RIGHT);
            StackPane.setMargin(toastNode, new Insets(24, 24, 0, 0));
        } catch (Exception ex) {
            new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
        }
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private void clearModal() {
        clearFieldErrors();
        if (txtModalUsername  != null) txtModalUsername.clear();
        if (txtModalPassword  != null) { txtModalPassword.clear(); txtModalPassword.setDisable(false); }
        if (txtModalFirstName != null) txtModalFirstName.clear();
        if (txtModalLastName  != null) txtModalLastName.clear();
        if (txtModalEmail     != null) txtModalEmail.clear();
        if (txtModalPhone     != null) txtModalPhone.clear();
        if (txtModalStaffId   != null) txtModalStaffId.clear();
        if (txtModalAddress1  != null) txtModalAddress1.clear();
        if (txtModalAddress2  != null) txtModalAddress2.clear();
        if (txtModalAddress3  != null) txtModalAddress3.clear();
        if (cmbModalRole      != null) cmbModalRole.setValue("Finance User");
        if (chkModalActive    != null) chkModalActive.setSelected(true);
    }

    private void showModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(true);
            modalOverlay.setManaged(true);
        }
        if (modalCard != null) {
            modalCard.setOpacity(0);
            modalCard.setScaleX(0.96);
            modalCard.setScaleY(0.96);
            FadeTransition ft = new FadeTransition(Duration.millis(200), modalCard);
            ft.setFromValue(0);
            ft.setToValue(1);
            ScaleTransition st = new ScaleTransition(Duration.millis(200), modalCard);
            st.setFromX(0.96);
            st.setToX(1);
            st.setFromY(0.96);
            st.setToY(1);
            new ParallelTransition(ft, st).play();
        }
    }

    private void hideModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
            modalOverlay.setManaged(false);
        }
    }

    private void show(Node n) {
        if (n == null) return;
        n.setVisible(true);
        ((javafx.scene.layout.Region) n).setManaged(true);
    }

    private void hide(Node n) {
        if (n == null) return;
        n.setVisible(false);
        ((javafx.scene.layout.Region) n).setManaged(false);
    }

    private void setStyle(Button btn, String style) {
        if (btn != null) btn.setStyle(style);
    }

    private String txt(TextField f) {
        return f == null || f.getText() == null ? "" : f.getText().trim();
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private String formatTs(String ts) {
        if (ts == null) return "-";
        try { return java.time.LocalDateTime.parse(ts.replace(" ", "T")).format(DATE_FMT); }
        catch (Exception e) { return ts; }
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}