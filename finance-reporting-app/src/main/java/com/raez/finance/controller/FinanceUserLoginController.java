package com.raez.finance.controller;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.service.AuthService;
import com.raez.finance.service.AuthService.FirstLoginRequiredException;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.StageNavigator;
import com.raez.finance.util.ValidationUtils;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
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

public class FinanceUserLoginController {

    private static final String VIEW_PATH     = "/com/raez/finance/view/";
    private static final String DEMO_EMAIL    = "finance@raez.org.uk";
    private static final String DEMO_PASSWORD = "User123@";

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

    // ── Forgot password flow ───────────────────────────────────
    @FXML private VBox         forgotFlowCard;
    @FXML private VBox         forgotPaneSend;
    @FXML private VBox         forgotPaneToken;
    @FXML private TextField    txtForgotAccount;
    @FXML private TextField    txtForgotSendTo;
    @FXML private Button       btnForgotSend;
    @FXML private Label        lblForgotSendCountdown;
    @FXML private TextField    txtForgotToken;
    @FXML private PasswordField txtForgotNewPassword;
    @FXML private PasswordField txtForgotConfirmPassword;
    @FXML private Button       btnForgotApplyReset;

    private Timeline forgotResendCooldownTimeline;

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
        stopForgotCooldown();
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
        VBox card;
        if (loginCard != null && loginCard.isVisible()) card = loginCard;
        else if (forgotFlowCard != null && forgotFlowCard.isVisible()) card = forgotFlowCard;
        else card = passwordChangeCard;
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
            authService.completeFirstLogin(identifier, newPwd);
            MainLayoutController.queueStartupToast("success", "Welcome! You're signed in.");
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
        hideErrors();
        stopForgotCooldown();
        if (forgotPaneToken != null) {
            forgotPaneToken.setVisible(false);
            forgotPaneToken.setManaged(false);
        }
        if (txtForgotToken != null) txtForgotToken.clear();
        if (txtForgotNewPassword != null) txtForgotNewPassword.clear();
        if (txtForgotConfirmPassword != null) txtForgotConfirmPassword.clear();
        showForgotFlowView();
    }

    @FXML
    private void handleForgotBackToLogin(ActionEvent event) {
        stopForgotCooldown();
        showLoginView();
        fadeInCard();
    }

    @FXML
    private void handleForgotSendEmail(ActionEvent event) {
        String account = txtForgotAccount != null && txtForgotAccount.getText() != null
                ? txtForgotAccount.getText().trim() : "";
        String sendTo = txtForgotSendTo != null && txtForgotSendTo.getText() != null
                ? txtForgotSendTo.getText().trim() : "";
        if (account.isEmpty()) {
            alert("Enter your account email or username.");
            return;
        }
        if (!sendTo.isEmpty() && !ValidationUtils.isValidEmailFormat(sendTo)) {
            alert("Enter a valid email address for where to send the code, or leave it blank to use the email on file.");
            return;
        }
        try {
            authService.requestPasswordResetEmail(account, sendTo.isEmpty() ? null : sendTo);
            alert("If an account matches, a reset code was sent (check the inbox you specified). "
                    + "If SMTP is off, see the application console for the token.");
            if (forgotPaneToken != null) {
                forgotPaneToken.setVisible(true);
                forgotPaneToken.setManaged(true);
            }
            startForgotSendCooldown();
        } catch (Exception ex) {
            alert(ex.getMessage() != null ? ex.getMessage() : "Request failed.");
        }
    }

    @FXML
    private void handleForgotApplyReset(ActionEvent event) {
        String token = txtForgotToken != null && txtForgotToken.getText() != null
                ? txtForgotToken.getText().trim() : "";
        String newPwd = txtForgotNewPassword != null ? txtForgotNewPassword.getText() : "";
        String confirm = txtForgotConfirmPassword != null ? txtForgotConfirmPassword.getText() : "";
        if (token.isEmpty()) {
            alert("Enter the reset token from your email.");
            return;
        }
        String pwdErr = ValidationUtils.validateNewPassword(newPwd);
        if (pwdErr != null) {
            alert(pwdErr);
            return;
        }
        if (!newPwd.equals(confirm)) {
            alert("Passwords do not match.");
            return;
        }
        try {
            authService.resetPasswordWithToken(token, newPwd);
            alert("Password updated. You can sign in now.");
            stopForgotCooldown();
            showLoginView();
            fadeInCard();
        } catch (Exception ex) {
            alert(ex.getMessage() != null ? ex.getMessage() : "Reset failed.");
        }
    }

    private void startForgotSendCooldown() {
        if (btnForgotSend == null) return;
        if (forgotResendCooldownTimeline != null) {
            forgotResendCooldownTimeline.stop();
        }
        btnForgotSend.setDisable(true);
        if (lblForgotSendCountdown != null) {
            lblForgotSendCountdown.setVisible(true);
            lblForgotSendCountdown.setManaged(true);
            lblForgotSendCountdown.setText("Resend available in 60s");
        }
        final int[] sec = {60};
        forgotResendCooldownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            sec[0]--;
            if (lblForgotSendCountdown != null) {
                if (sec[0] <= 0) {
                    lblForgotSendCountdown.setVisible(false);
                    lblForgotSendCountdown.setManaged(false);
                } else {
                    lblForgotSendCountdown.setText("Resend available in " + sec[0] + "s");
                }
            }
            if (sec[0] <= 0) {
                btnForgotSend.setDisable(false);
                ((Timeline) e.getSource()).stop();
            }
        }));
        forgotResendCooldownTimeline.setCycleCount(60);
        forgotResendCooldownTimeline.play();
    }

    private void stopForgotCooldown() {
        if (forgotResendCooldownTimeline != null) {
            forgotResendCooldownTimeline.stop();
            forgotResendCooldownTimeline = null;
        }
        if (btnForgotSend != null) btnForgotSend.setDisable(false);
        if (lblForgotSendCountdown != null) {
            lblForgotSendCountdown.setVisible(false);
            lblForgotSendCountdown.setManaged(false);
        }
    }

    private void showForgotFlowView() {
        setVisible(loginCard, false);
        setVisible(passwordChangeCard, false);
        setVisible(forgotFlowCard, true);
        if (forgotFlowCard != null) {
            forgotFlowCard.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(280), forgotFlowCard);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
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
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            StageNavigator.navigate(stage, path);
            stage.setTitle("RAEZ Finance");
            stage.setMinWidth(900);
            stage.setMinHeight(650);
            StageNavigator.forceMaximizedLayout(stage);
        } catch (Exception e) {
            throw new RuntimeException("Navigation failed: " + path, e);
        }
    }

    private void navigateTo(String resourcePath, ActionEvent event) {
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            StageNavigator.navigate(stage, resourcePath);
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
        setVisible(forgotFlowCard,     false);
    }

    private void showPasswordChangeView() {
        setVisible(loginCard,          false);
        setVisible(forgotFlowCard,     false);
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