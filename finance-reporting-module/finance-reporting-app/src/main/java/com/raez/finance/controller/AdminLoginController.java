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

public class AdminLoginController {

    private static final String VIEW_PATH     = "/com/raez/finance/view/";
    private static final String DEMO_EMAIL    = "admin@raez.org.uk";
    private static final String DEMO_PASSWORD = "Admin@123";

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
        if (loginCard == null) return;
        FadeTransition ft = new FadeTransition(Duration.millis(320), loginCard);
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
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter your email and the one-time token from your administrator.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField     emailField = new TextField();     emailField.setPromptText("Email");
        TextField     tokenField = new TextField();     tokenField.setPromptText("Reset token");
        PasswordField newPwd     = new PasswordField(); newPwd.setPromptText("New password (min 8 chars)");
        PasswordField confirm    = new PasswordField(); confirm.setPromptText("Confirm new password");

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.add(new Label("Email:"),        0, 0); grid.add(emailField, 1, 0);
        grid.add(new Label("Token:"),        0, 1); grid.add(tokenField, 1, 1);
        grid.add(new Label("New password:"), 0, 2); grid.add(newPwd,     1, 2);
        grid.add(new Label("Confirm:"),      0, 3); grid.add(confirm,    1, 3);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? "ok" : null);

        dialog.showAndWait().ifPresent(result -> {
            String e = emailField.getText() == null ? "" : emailField.getText().trim();
            String t = tokenField.getText()  == null ? "" : tokenField.getText().trim();
            String p = newPwd.getText()      == null ? "" : newPwd.getText();
            String c = confirm.getText()     == null ? "" : confirm.getText();

            if (e.isEmpty() || t.isEmpty()) { alert("Email and token are required."); return; }
            if (p.length() < 8)             { alert("Password must be at least 8 characters."); return; }
            if (!p.equals(c))               { alert("Passwords do not match."); return; }
            try {
                authService.resetPasswordWithToken(e, t, p);
                alert("Password updated. You can now log in.");
            } catch (Exception ex) {
                alert(ex.getMessage() != null ? ex.getMessage() : "Reset failed.");
            }
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════

    private void navigateToMainLayout(ActionEvent event) {
        String path = VIEW_PATH + "MainLayout.fxml";
        try {
            URL url = getClass().getResource(path);
            if (url == null) throw new IllegalStateException("FXML not found: " + path);
            Parent root = FXMLLoader.load(url);
            Scene  scene = new Scene(root);
            URL    css   = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("RAEZ Finance");
            stage.setMinWidth(900);
            stage.setMinHeight(650);
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            System.err.println("=== NAVIGATION TO MAIN LAYOUT FAILED ===");
            e.printStackTrace();
            System.err.println("========================================");
            throw new RuntimeException("Navigation failed: " + path, e);
        }
    }

    private void navigateTo(String resourcePath, ActionEvent event) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("FXML not found: " + resourcePath);
            Parent root = FXMLLoader.load(url);
            Scene  scene = new Scene(root);
            URL    css   = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            System.err.println("=== NAVIGATION FAILED: " + resourcePath + " ===");
            e.printStackTrace();
            throw new RuntimeException("Navigation failed: " + resourcePath, e);
        }
    }

    private void navigateToFirstLogin(String identifier, ActionEvent event) {
        String path = VIEW_PATH + "FinanceUserLogin.fxml";
        try {
            URL url = getClass().getResource(path);
            if (url == null) throw new IllegalStateException("FXML not found: " + path);
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            FinanceUserLoginController ctrl = loader.getController();
            if (ctrl != null) ctrl.prepareForFirstLogin(identifier);
            Scene scene = new Scene(root);
            URL   css   = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("RAEZ Finance – Set Password");
            stage.show();
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