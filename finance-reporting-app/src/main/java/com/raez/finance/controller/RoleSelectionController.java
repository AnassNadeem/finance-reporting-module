package com.raez.finance.controller;

import com.raez.finance.util.StageNavigator;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class RoleSelectionController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    // ── FXML injections ──────────────────────────────────────────────────
    @FXML private StackPane rootPane;
    @FXML private Pane      animatedBg;
    @FXML private Pane      spotlightPane;   // cursor spotlight overlay
    @FXML private HBox      mainPanel;
    @FXML private HBox      adminRow;
    @FXML private HBox      userRow;
    @FXML private Label     lblMetric1;
    @FXML private Label     lblMetric2;
    @FXML private Label     lblMetric3;

    // ── State ────────────────────────────────────────────────────────────
    private String selectedRole = null;
    private final List<Timeline> timelines = new ArrayList<>();

    // ── Hover base styles ─────────────────────────────────────────────────
    private static final String ADMIN_BASE =
        "-fx-background-color: rgba(16,185,129,0.08);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: rgba(16,185,129,0.20) rgba(16,185,129,0.20) rgba(16,185,129,0.20) #10B981;" +
        "-fx-border-radius: 14; -fx-border-width: 1 1 1 3; -fx-cursor: hand;";

    private static final String ADMIN_HOVER =
        "-fx-background-color: rgba(16,185,129,0.15);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: rgba(16,185,129,0.55) rgba(16,185,129,0.55) rgba(16,185,129,0.55) #10B981;" +
        "-fx-border-radius: 14; -fx-border-width: 1 1 1 3; -fx-cursor: hand;" +
        "-fx-effect: dropshadow(gaussian, rgba(16,185,129,0.25), 20, 0, 0, 0);";

    private static final String ADMIN_SELECTED =
        "-fx-background-color: rgba(16,185,129,0.20);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: #10B981;" +
        "-fx-border-radius: 14; -fx-border-width: 2; -fx-cursor: hand;" +
        "-fx-effect: dropshadow(gaussian, rgba(16,185,129,0.45), 24, 0, 0, 0);";

    private static final String USER_BASE =
        "-fx-background-color: rgba(37,99,235,0.07);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: rgba(37,99,235,0.20) rgba(37,99,235,0.20) rgba(37,99,235,0.20) #2563EB;" +
        "-fx-border-radius: 14; -fx-border-width: 1 1 1 3; -fx-cursor: hand;";

    private static final String USER_HOVER =
        "-fx-background-color: rgba(37,99,235,0.14);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: rgba(37,99,235,0.55) rgba(37,99,235,0.55) rgba(37,99,235,0.55) #2563EB;" +
        "-fx-border-radius: 14; -fx-border-width: 1 1 1 3; -fx-cursor: hand;" +
        "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.25), 20, 0, 0, 0);";

    private static final String USER_SELECTED =
        "-fx-background-color: rgba(37,99,235,0.18);" +
        "-fx-background-radius: 14;" +
        "-fx-border-color: #2563EB;" +
        "-fx-border-radius: 14; -fx-border-width: 2; -fx-cursor: hand;" +
        "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.45), 24, 0, 0, 0);";

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        buildAnimatedBackground();
        wireCursorSpotlight();
        staggeredEntranceAnimation();
        animateMetricTickers();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANIMATED BACKGROUND BLOBS
    // ══════════════════════════════════════════════════════════════════════

    private void buildAnimatedBackground() {
        // [radius, r, g, b, opacity, x0, y0, x1, y1, ms]
        double[][] specs = {
            {220, 16, 185, 129, 0.07,  -60,  80,   80, 220, 18000},  // emerald XL
            {180, 37,  99, 235, 0.07, 1100,  60,  920, 200, 15000},  // blue XL
            {140, 16, 185, 129, 0.05,  300, 620,  200, 480, 12000},  // emerald mid
            {100, 139, 92, 246, 0.06,  800, 500,  920, 380,  9000},  // violet
            { 80, 37,  99, 235, 0.08,  120, 400,  200, 560,  8000},  // blue small
            {300,   0,   0,   0, 0.15,  520, 320,  440, 220, 20000}, // depth blob
            { 60, 16, 185, 129, 0.10,  900, 620,  820, 720,  7000},  // accent dot
            {160, 37,  99, 235, 0.04,  500, 700,  600, 580, 14000},  // blue mid
        };

        for (double[] s : specs) {
            Circle c = new Circle(s[0]);
            c.setFill(Color.rgb((int) s[1], (int) s[2], (int) s[3], s[4]));
            c.setTranslateX(s[5]);
            c.setTranslateY(s[6]);
            animatedBg.getChildren().add(c);

            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(c.translateXProperty(), s[5]),
                    new KeyValue(c.translateYProperty(), s[6])),
                new KeyFrame(Duration.millis(s[9]),
                    new KeyValue(c.translateXProperty(), s[7]),
                    new KeyValue(c.translateYProperty(), s[8]))
            );
            tl.setAutoReverse(true);
            tl.setCycleCount(Timeline.INDEFINITE);
            tl.play();
            timelines.add(tl);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CURSOR SPOTLIGHT  — radial glow tracks the mouse
    //  This is the signature "beyond imagination" element.
    //  A subtle radial gradient centered on the mouse position makes the
    //  screen feel alive and responsive before any clicks happen.
    // ══════════════════════════════════════════════════════════════════════

    private void wireCursorSpotlight() {
        rootPane.setOnMouseMoved(e -> {
            double x = e.getX();
            double y = e.getY();
            // Create a radial gradient centered on cursor position
            // JavaFX accepts percent-based or pixel-based center coords
            spotlightPane.setStyle(
                "-fx-background-color: radial-gradient(focus-angle 0deg, " +
                "focus-distance 0%, center " + (x / rootPane.getWidth() * 100) + "% " +
                (y / rootPane.getHeight() * 100) + "%, radius 30%, " +
                "rgba(16,185,129,0.08) 0%, transparent 100%);"
            );
        });

        rootPane.setOnMouseExited(e ->
            spotlightPane.setStyle("-fx-background-color: transparent;")
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STAGGERED ENTRANCE ANIMATION
    //  Panel rises from translateY=20 → -12 with fade, then cards cascade in
    // ══════════════════════════════════════════════════════════════════════

    private void staggeredEntranceAnimation() {
        // Panel fade + rise
        FadeTransition panelFade = new FadeTransition(Duration.millis(600), mainPanel);
        panelFade.setFromValue(0); panelFade.setToValue(1);
        panelFade.setDelay(Duration.millis(100));

        TranslateTransition panelRise = new TranslateTransition(Duration.millis(600), mainPanel);
        panelRise.setFromY(20); panelRise.setToY(-12);
        panelRise.setDelay(Duration.millis(100));

        ParallelTransition entrance = new ParallelTransition(panelFade, panelRise);

        // Admin row cascades in after panel is visible
        adminRow.setOpacity(0);
        adminRow.setTranslateX(-12);
        FadeTransition adminFade = new FadeTransition(Duration.millis(360), adminRow);
        adminFade.setFromValue(0); adminFade.setToValue(1);
        TranslateTransition adminSlide = new TranslateTransition(Duration.millis(360), adminRow);
        adminSlide.setFromX(-12); adminSlide.setToX(0);
        PauseTransition adminDelay = new PauseTransition(Duration.millis(380));

        // User row cascades in slightly after admin
        userRow.setOpacity(0);
        userRow.setTranslateX(-12);
        FadeTransition userFade = new FadeTransition(Duration.millis(360), userRow);
        userFade.setFromValue(0); userFade.setToValue(1);
        TranslateTransition userSlide = new TranslateTransition(Duration.millis(360), userRow);
        userSlide.setFromX(-12); userSlide.setToX(0);

        SequentialTransition full = new SequentialTransition(
            entrance,
            adminDelay,
            new ParallelTransition(
                new ParallelTransition(adminFade, adminSlide),
                new SequentialTransition(
                    new PauseTransition(Duration.millis(80)),
                    new ParallelTransition(userFade, userSlide)
                )
            )
        );
        full.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  METRIC TICKERS  — animates platform stats in the brand panel
    // ══════════════════════════════════════════════════════════════════════

    private void animateMetricTickers() {
        if (lblMetric1 == null) return;

        // Tick through values with a fade-swap every 3 seconds
        String[][] metric1Vals = {
            {"76 Orders processed today"},
            {"£128,400 revenue this month"},
            {"14 invoices awaiting review"}
        };
        String[][] metric2Vals = {
            {"30 active customers"},
            {"5 users online now"},
            {"3 alerts unresolved"}
        };
        String[][] metric3Vals = {
            {"12 products in catalogue"},
            {"4 low-stock SKUs flagged"},
            {"AI predictions: 94% accuracy"}
        };

        startTicker(lblMetric1, metric1Vals,     0);
        startTicker(lblMetric2, metric2Vals,  1000);
        startTicker(lblMetric3, metric3Vals,  2000);
    }

    private void startTicker(Label label, String[][] values, long initialDelayMs) {
        final int[] idx = {0};
        Timeline ticker = new Timeline(new KeyFrame(Duration.millis(3000), e -> {
            FadeTransition out = new FadeTransition(Duration.millis(200), label);
            out.setFromValue(1); out.setToValue(0);
            out.setOnFinished(ev -> {
                idx[0] = (idx[0] + 1) % values.length;
                label.setText(values[idx[0]][0]);
                FadeTransition in = new FadeTransition(Duration.millis(200), label);
                in.setFromValue(0); in.setToValue(1);
                in.play();
            });
            out.play();
        }));
        ticker.setCycleCount(Timeline.INDEFINITE);

        // Initial label text
        label.setText(values[0][0]);
        label.setOpacity(0);
        PauseTransition init = new PauseTransition(Duration.millis(600 + initialDelayMs));
        init.setOnFinished(e -> {
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), label);
            fadeIn.setFromValue(0); fadeIn.setToValue(1);
            fadeIn.setOnFinished(fe -> ticker.play());
            fadeIn.play();
        });
        init.play();
        timelines.add(ticker);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROW HOVER ANIMATIONS
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleAdminRowHover(javafx.scene.input.MouseEvent e) {
        if ("ADMIN".equals(selectedRole)) return;
        adminRow.setStyle(ADMIN_HOVER);
        TranslateTransition t = new TranslateTransition(Duration.millis(180), adminRow);
        t.setToX(6); t.play();
    }

    @FXML private void handleAdminRowExit(javafx.scene.input.MouseEvent e) {
        if ("ADMIN".equals(selectedRole)) return;
        adminRow.setStyle(ADMIN_BASE);
        TranslateTransition t = new TranslateTransition(Duration.millis(180), adminRow);
        t.setToX(0); t.play();
    }

    @FXML private void handleUserRowHover(javafx.scene.input.MouseEvent e) {
        if ("USER".equals(selectedRole)) return;
        userRow.setStyle(USER_HOVER);
        TranslateTransition t = new TranslateTransition(Duration.millis(180), userRow);
        t.setToX(6); t.play();
    }

    @FXML private void handleUserRowExit(javafx.scene.input.MouseEvent e) {
        if ("USER".equals(selectedRole)) return;
        userRow.setStyle(USER_BASE);
        TranslateTransition t = new TranslateTransition(Duration.millis(180), userRow);
        t.setToX(0); t.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CLICK HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleAdminCardClick(javafx.scene.input.MouseEvent e) {
        if ("ADMIN".equals(selectedRole)) return;
        selectRow("ADMIN");
        Timeline delay = new Timeline(new KeyFrame(Duration.millis(220), ev ->
            StageNavigator.navigate(
                (Stage) adminRow.getScene().getWindow(),
                VIEW_PATH + "AdminLogin.fxml"
            )
        ));
        delay.play();
    }

    @FXML
    private void handleUserCardClick(javafx.scene.input.MouseEvent e) {
        if ("USER".equals(selectedRole)) return;
        selectRow("USER");
        Timeline delay = new Timeline(new KeyFrame(Duration.millis(220), ev ->
            StageNavigator.navigate(
                (Stage) userRow.getScene().getWindow(),
                VIEW_PATH + "FinanceUserLogin.fxml"
            )
        ));
        delay.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SELECTION VISUAL STATE
    // ══════════════════════════════════════════════════════════════════════

    private void selectRow(String role) {
        selectedRole = role;

        // Reset both to base
        adminRow.setStyle(ADMIN_BASE);
        userRow.setStyle(USER_BASE);
        adminRow.setTranslateX(0);
        userRow.setTranslateX(0);

        HBox active = "ADMIN".equals(role) ? adminRow : userRow;
        active.setStyle("ADMIN".equals(role) ? ADMIN_SELECTED : USER_SELECTED);

        // Translate the selected card right
        TranslateTransition tr = new TranslateTransition(Duration.millis(150), active);
        tr.setToX(10); tr.play();

        // Scale pulse: 1.0 → 1.02 → 1.0
        ScaleTransition st = new ScaleTransition(Duration.millis(180), active);
        st.setFromX(1.0); st.setFromY(1.0);
        st.setToX(1.02);  st.setToY(1.02);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CLEANUP
    // ══════════════════════════════════════════════════════════════════════

    public void stopAnimations() {
        timelines.forEach(Timeline::stop);
        timelines.clear();
    }
}