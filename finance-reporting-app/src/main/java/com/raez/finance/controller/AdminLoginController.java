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

public class AdminLoginController {

    private static final String VIEW_PATH     = "/com/raez/finance/view/";
    private static final String DEMO_EMAIL    = "admin@raez.org.uk";
    private static final String DEMO_PASSWORD = "Admin123!";

    private final AuthService authService = new AuthService();

    @FXML private Pane          animatedBg;
    @FXML private VBox          loginCard;
    @FXML private Button        btnBack;
    @FXML private Button        btnLogin;
    @FXML private VBox          errorBox;
    @FXML private Label         lblError;
    @FXML private Hyperlink     linkForgotPassword;
    @FXML private TextField     txtEmail;
    @FXML private PasswordField txtPassword;

    @FXML private VBox          forgotFlowCard;
    @FXML private VBox          forgotPaneSend;
    @FXML private VBox          forgotPaneToken;
    @FXML private TextField     txtForgotAccount;
    @FXML private TextField     txtForgotSendTo;
    @FXML private Button        btnForgotSend;
    @FXML private Label         lblForgotSendCountdown;
    @FXML private TextField     txtForgotToken;
    @FXML private PasswordField txtForgotNewPassword;
    @FXML private PasswordField txtForgotConfirmPassword;
    @FXML private Button        btnForgotApplyReset;

    private Timeline forgotResendCooldownTimeline;

    // ══════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        hideError();
        buildAnimatedBackground();
        fadeInCard();
        txtPassword.setOnAction(this::handleLogin);
    }

    private void buildAnimatedBackground() {
        if (animatedBg == null) return;
        double[][] specs = {
            {70,  0.05,  80, 100, 160, 220,  9000},
            {110, 0.04, 700,  60, 580, 200, 12000},
            {55,  0.06, 300, 480, 380, 560,  7500},
            {90,  0.04, 850, 320, 720, 440, 10000},
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
        VBox card = loginCard;
        if (forgotFlowCard != null && forgotFlowCard.isVisible()) card = forgotFlowCard;
        if (card == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(320), card);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setDelay(Duration.millis(60));
        ft.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLERS
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
        String email    = txtEmail.getText()    == null ? "" : txtEmail.getText().trim();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill in both fields.");
            return;
        }
        if (!ValidationUtils.isRaezEmail(email)) {
            showError("Email must end with @raez.org.uk");
            return;
        }
        hideError();
        btnLogin.setDisable(true);
        btnLogin.setText("Signing in…");

        try {
            FUser user = authService.login(email, password);
            if (user.getRole() != UserRole.ADMIN) {
                showError("This login is for administrators only.\nUse Finance User Login instead.");
                return;
            }
            SessionManager.startSession(user);
            navigateToMainLayout(event);

        } catch (FirstLoginRequiredException e) {
            navigateToFirstLogin(email, event);

        } catch (IllegalArgumentException e) {
            showError(e.getMessage());

        } catch (Exception e) {
            // ── Always print the FULL stack trace to the terminal
            //    so you can see the real root cause line number.
            System.err.println("=== LOGIN ERROR (full stack trace) ===");
            e.printStackTrace();
            System.err.println("======================================");

            // ── Extract the real error message, not the FXML file path
            showError(extractUserMessage(e));

        } finally {
            btnLogin.setDisable(false);
            btnLogin.setText("Login as Administrator");
        }
    }

    /**
     * Walks the exception chain to find the deepest non-null message that
     * does NOT look like a file path (which is what JavaFX LoadException
     * puts in its message when an inner controller crashes).
     *
     * Returns a human-readable fallback if nothing useful is found.
     */
    private String extractUserMessage(Throwable t) {
        // Walk the full cause chain, collect all messages
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null && !msg.isBlank()
                    && !msg.contains("/")       // skip file paths
                    && !msg.contains("\\")
                    && !msg.contains("%20")
                    && !msg.contains(".fxml")
                    && !msg.contains(".class")) {
                return msg;
            }
            current = current.getCause();
        }
        // Nothing useful found — give a diagnostic hint
        return "Failed to load the main dashboard.\n"
             + "Check the terminal for the full error (look for '=== LOGIN ERROR ===').";
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        hideError();
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
        showMainLoginView();
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
            alert("Enter a valid delivery email or leave blank.");
            return;
        }
        try {
            authService.requestPasswordResetEmail(account, sendTo.isEmpty() ? null : sendTo);
            alert("If an account matches, a reset code was sent. Check the console if SMTP is not configured.");
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
            alert("Enter the reset token.");
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
            alert("Password updated. You can now log in.");
            stopForgotCooldown();
            showMainLoginView();
            fadeInCard();
        } catch (Exception ex) {
            alert(ex.getMessage() != null ? ex.getMessage() : "Reset failed.");
        }
    }

    private void startForgotSendCooldown() {
        if (btnForgotSend == null) return;
        if (forgotResendCooldownTimeline != null) forgotResendCooldownTimeline.stop();
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
        if (loginCard != null) {
            loginCard.setVisible(false);
            loginCard.setManaged(false);
        }
        if (forgotFlowCard != null) {
            forgotFlowCard.setVisible(true);
            forgotFlowCard.setManaged(true);
            forgotFlowCard.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(280), forgotFlowCard);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }

    private void showMainLoginView() {
        if (forgotFlowCard != null) {
            forgotFlowCard.setVisible(false);
            forgotFlowCard.setManaged(false);
        }
        if (loginCard != null) {
            loginCard.setVisible(true);
            loginCard.setManaged(true);
        }
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
            System.err.println("=== NAVIGATION TO MAIN LAYOUT FAILED ===");
            e.printStackTrace();
            System.err.println("========================================");
            throw new RuntimeException("Navigation failed: " + path, e);
        }
    }

    private void navigateTo(String resourcePath, ActionEvent event) {
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            StageNavigator.navigate(stage, resourcePath);
        } catch (Exception e) {
            System.err.println("=== NAVIGATION FAILED: " + resourcePath + " ===");
            e.printStackTrace();
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }

    private void navigateToFirstLogin(String identifier, ActionEvent event) {
        String path = VIEW_PATH + "FinanceUserLogin.fxml";
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            StageNavigator.navigate(stage, path, ctrl -> {
                if (ctrl instanceof FinanceUserLoginController f)
                    f.prepareForFirstLogin(identifier);
            });
            stage.setTitle("RAEZ Finance – Set Password");
        } catch (Exception e) {
            System.err.println("=== NAVIGATION TO FIRST LOGIN FAILED ===");
            e.printStackTrace();
            throw new RuntimeException("Navigation failed: " + path, e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════

    private void showError(String message) {
        if (lblError  != null) lblError.setText(message);
        if (errorBox  != null) { errorBox.setVisible(true); errorBox.setManaged(true); }
    }

    private void hideError() {
        if (errorBox != null) { errorBox.setVisible(false); errorBox.setManaged(false); }
    }

    private void alert(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
}