package com.raez.finance.controller;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.AuthService;
import com.raez.finance.service.AuthService.FirstLoginRequiredException;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.ValidationUtils;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;

public class FinanceUserLoginController {

    private static final String VIEW_PATH     = "/com/raez/finance/view/";
    private static final String DEMO_EMAIL    = "finance@raez.org.uk";
    private static final String DEMO_PASSWORD = "User@123";

    private final AuthService authService = new AuthService();

    // ── Login view ─────────────────────────────────────────────
    @FXML private Pane      animatedBg;
    @FXML private VBox      loginCard;
    @FXML private VBox      loginView;
    @FXML private TextField    txtEmail;
    @FXML private PasswordField txtPassword;
    @FXML private VBox      loginErrorBox;
    @FXML private Label     lblLoginError;
    @FXML private Button    btnLogin;
    @FXML private Button    btnBackToRole;
    @FXML private Hyperlink linkForgotPassword;

    // ── Password change view ───────────────────────────────────
    @FXML private VBox      passwordChangeCard;
    @FXML private VBox      passwordChangeView;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;
    @FXML private VBox      pwdErrorBox;
    @FXML private Label     lblPwdError;
    @FXML private Button    btnSetPassword;

    // ══════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        showLoginView();
        hideErrors();
        buildAnimatedBackground();
        fadeInCard();
        txtPassword.setOnAction(this::handleLogin);
    }

    /**
     * Called by AdminLoginController when an admin user triggers a first-time login.
     * Switches directly to the set-password view and pre-fills the identifier.
     */
    public void prepareForFirstLogin(String identifier) {
        if (identifier != null) txtEmail.setText(identifier);
        showPasswordChangeView();
        hideErrors();
    }

    // ══════════════════════════════════════════════════════════════
    //  BACKGROUND + ANIMATION
    // ══════════════════════════════════════════════════════════════

    private void buildAnimatedBackground() {
        if (animatedBg == null) return;
        double[][] specs = {
            {75,  0.05, 60,  120, 140, 240,  9500},
            {115, 0.04, 720,  50, 600, 190, 12500},
            {55,  0.06, 310, 500, 390, 580,  7000},
            {95,  0.04, 870, 340, 740, 460, 10500},
        };
        for (double[] s : specs) {
            Circle c = new Circle(s[0]);
            c.setFill(Color.rgb(30, 41, 57, s[1]));
            c.setTranslateX(s[2]);
            c.setTranslateY(s[3]);
            animatedBg.getChildren().add(c);
            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(c.translateXProperty(), s[2]),
                    new KeyValue(c.translateYProperty(), s[3])),
                new KeyFrame(Duration.millis(s[6]),
                    new KeyValue(c.translateXProperty(), s[4]),
                    new KeyValue(c.translateYProperty(), s[5]))
            );
            tl.setAutoReverse(true);
            tl.setCycleCount(Timeline.INDEFINITE);
            tl.play();
        }
    }

    private void fadeInCard() {
        VBox card = loginView.isVisible() ? loginCard : passwordChangeCard;
        if (card == null || card.getOpacity() >= 1) return;
        FadeTransition ft = new FadeTransition(Duration.millis(320), card);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setDelay(Duration.millis(60));
        ft.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN HANDLERS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleBack(ActionEvent event) {
        navigateTo(VIEW_PATH + "RoleSelection.fxml", event);
    }

    @FXML
    private void handleAddDemoCredentials(ActionEvent event) {
        txtEmail.setText(DEMO_EMAIL);
        txtPassword.setText(DEMO_PASSWORD);
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email    = txtEmail.getText() == null    ? "" : txtEmail.getText().trim();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Please fill in both fields.");
            return;
        }
        hideErrors();
        btnLogin.setDisable(true);
        btnLogin.setText("Signing in…");

        try {
            FUser user = authService.login(email, password);
            if (user.getRole() != UserRole.FINANCE_USER) {
                showLoginError("This login is for finance users only.\nAdministrators should use the Admin login.");
                return;
            }
            SessionManager.startSession(user);
            navigateToMainLayout(event);
        } catch (FirstLoginRequiredException e) {
            // Switch to the set-password panel — the identifier is already in txtEmail
            showPasswordChangeView();
            hideErrors();
        } catch (IllegalArgumentException e) {
            showLoginError(e.getMessage());
        } catch (Exception e) {
            String msg = (e.getCause() != null && e.getCause().getMessage() != null)
                ? e.getCause().getMessage() : e.getMessage();
            showLoginError(msg != null ? msg : "Authentication failed. Please try again.");
        } finally {
            btnLogin.setDisable(false);
            btnLogin.setText("Login as Finance User");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SET PASSWORD HANDLERS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handlePasswordChange(ActionEvent event) {
        String newPwd  = txtNewPassword.getText()     == null ? "" : txtNewPassword.getText();
        String confirm = txtConfirmPassword.getText() == null ? "" : txtConfirmPassword.getText();

        if (newPwd.isEmpty() || confirm.isEmpty()) {
            showPwdError("Please fill in both password fields.");
            return;
        }

        String pwdValidation = ValidationUtils.validateNewPassword(newPwd);
        if (pwdValidation != null) {
            showPwdError(pwdValidation);
            return;
        }

        if (!newPwd.equals(confirm)) {
            showPwdError("Passwords do not match.");
            return;
        }

        hideErrors();
        if (btnSetPassword != null) {
            btnSetPassword.setDisable(true);
            btnSetPassword.setText("Saving…");
        }

        try {
            String identifier = txtEmail.getText() == null ? "" : txtEmail.getText().trim();
            if (identifier.isEmpty()) {
                showPwdError("Session identifier missing. Please go back and log in again.");
                return;
            }
            // ── This is the fixed call ──────────────────────────────────
            // AuthService.completeFirstLogin() now handles the case where
            // lastLogin was set during the initial login attempt.
            FUser user = authService.completeFirstLogin(identifier, newPwd);

            SessionManager.startSession(user);
            navigateToMainLayout(event);

        } catch (IllegalArgumentException | IllegalStateException e) {
            showPwdError(e.getMessage() != null ? e.getMessage() : "Password update failed.");
        } catch (Exception e) {
            String msg = (e.getCause() != null && e.getCause().getMessage() != null)
                ? e.getCause().getMessage() : e.getMessage();
            // ── Show a clear message, NEVER show "Invalid credentials" here ──
            showPwdError(msg != null ? msg
                : "Unable to save password. Please contact your administrator.");
        } finally {
            if (btnSetPassword != null) {
                btnSetPassword.setDisable(false);
                btnSetPassword.setText("Set Password & Continue");
            }
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Forgot Password");
        dialog.setHeaderText("Enter your @raez.org.uk email.\nA temporary password will be printed to the console (dev mode).");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField emailField = new TextField();
        emailField.setPromptText("your.name@raez.org.uk");
        emailField.setPrefWidth(280);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? "ok" : null);

        dialog.showAndWait().ifPresent(r -> {
            String e = emailField.getText() == null ? "" : emailField.getText().trim();
            if (e.isEmpty()) { alert("Email is required."); return; }
            if (!ValidationUtils.isRaezEmail(e)) { alert("Must be a @raez.org.uk address."); return; }
            try {
                authService.requestTemporaryPassword(e);
                alert("If this account exists, a temporary password has been sent.\n"
                    + "(Dev mode: check the application console for the temporary password.)");
            } catch (Exception ex) {
                alert(ex.getMessage() != null ? ex.getMessage() : "Request failed.");
            }
        });
    }

    @FXML
    private void handleReportIssue(ActionEvent event) {
        alert("Report technical issues to your system administrator at support@raez.org.uk");
    }

    // ══════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════

    private void navigateToMainLayout(ActionEvent event) {
        String path = VIEW_PATH + "MainLayout.fxml";
        try {
            URL url = getClass().getResource(path);
            if (url == null) throw new IllegalStateException("Not found: " + path);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL css = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("RAEZ Finance");
            stage.setMinWidth(900);
            stage.setMinHeight(650);
            stage.setMaximized(true);   // ← launch maximised
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + path, e);
        }
    }

    private void navigateTo(String resourcePath, ActionEvent event) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("Not found: " + resourcePath);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL css = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  VIEW STATE TOGGLING
    // ══════════════════════════════════════════════════════════════

    private void showLoginView() {
        setVisible(loginCard,          true);
        setVisible(passwordChangeCard, false);
    }

    private void showPasswordChangeView() {
        setVisible(loginCard,          false);
        setVisible(passwordChangeCard, true);
        // Fade in the password change card
        if (passwordChangeCard != null) {
            passwordChangeCard.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(300), passwordChangeCard);
            ft.setFromValue(0); ft.setToValue(1); ft.play();
        }
    }

    private void setVisible(VBox box, boolean v) {
        if (box == null) return;
        box.setVisible(v);
        box.setManaged(v);
    }

    // ══════════════════════════════════════════════════════════════
    //  ERROR HELPERS
    // ══════════════════════════════════════════════════════════════

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
        if (loginErrorBox != null) { loginErrorBox.setVisible(false); loginErrorBox.setManaged(false); }
        if (pwdErrorBox   != null) { pwdErrorBox.setVisible(false);   pwdErrorBox.setManaged(false); }
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
}