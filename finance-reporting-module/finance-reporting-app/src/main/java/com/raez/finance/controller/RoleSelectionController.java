package com.raez.finance.controller;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RoleSelectionController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    @FXML private StackPane rootPane;
    @FXML private Pane      animatedBg;
    @FXML private VBox      mainCard;
    @FXML private VBox      adminCard;
    @FXML private VBox      userCard;

    // State: which card is currently selected (null = none)
    private String selectedRole = null;

    private final List<Timeline> bgTimelines = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        buildAnimatedBackground();
        fadeInCard();
    }

    // ══════════════════════════════════════════════════════════════
    //  ANIMATED BACKGROUND — floating translucent circles
    // ══════════════════════════════════════════════════════════════

    private void buildAnimatedBackground() {
        Random rnd = new Random(42);
        double[][] specs = {
            // radius, opacity, startX, startY, endX, endY, durationMs
            {80,  0.06, 50,   80,   120,  200,  8000},
            {120, 0.04, 600,  50,   500,  180,  11000},
            {60,  0.07, 200,  500,  280,  600,  7000},
            {100, 0.05, 800,  300,  700,  450,  9000},
            {50,  0.08, 400,  200,  350,  100,  6500},
            {140, 0.03, 150,  650,  250,  700,  13000},
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
            bgTimelines.add(tl);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  CARD FADE-IN ON LOAD
    // ══════════════════════════════════════════════════════════════

    private void fadeInCard() {
        FadeTransition ft = new FadeTransition(Duration.millis(350), mainCard);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setDelay(Duration.millis(80));
        ft.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  CARD CLICK HANDLERS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleAdminCardClick(javafx.scene.input.MouseEvent event) {
        selectCard("ADMIN");
        // Short delay so user sees the selection, then navigate
        Timeline delay = new Timeline(new KeyFrame(Duration.millis(180), e -> {
            // Use a dummy ActionEvent-like approach by getting the stage from the node
            Stage stage = (Stage) adminCard.getScene().getWindow();
            loadView("AdminLogin.fxml", stage);
        }));
        delay.play();
    }

    @FXML
    private void handleUserCardClick(javafx.scene.input.MouseEvent event) {
        selectCard("USER");
        Timeline delay = new Timeline(new KeyFrame(Duration.millis(180), e -> {
            Stage stage = (Stage) userCard.getScene().getWindow();
            loadView("FinanceUserLogin.fxml", stage);
        }));
        delay.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  HOVER HANDLERS — subtle brightness effect
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleAdminCardHover(javafx.scene.input.MouseEvent event) {
        if (!"ADMIN".equals(selectedRole)) {
            adminCard.setStyle(
                "-fx-background-color: #2D3E54; -fx-background-radius: 12; -fx-cursor: hand;");
        }
    }

    @FXML
    private void handleAdminCardExit(javafx.scene.input.MouseEvent event) {
        if (!"ADMIN".equals(selectedRole)) {
            adminCard.setStyle(
                "-fx-background-color: #1E2939; -fx-background-radius: 12; -fx-cursor: hand;");
        }
    }

    @FXML
    private void handleUserCardHover(javafx.scene.input.MouseEvent event) {
        if (!"USER".equals(selectedRole)) {
            userCard.setStyle(
                "-fx-background-color: #E5E7EB; -fx-background-radius: 12; -fx-cursor: hand;");
        }
    }

    @FXML
    private void handleUserCardExit(javafx.scene.input.MouseEvent event) {
        if (!"USER".equals(selectedRole)) {
            userCard.setStyle(
                "-fx-background-color: #F3F4F6; -fx-background-radius: 12; -fx-cursor: hand;");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SELECTION VISUAL STATE
    // ══════════════════════════════════════════════════════════════

    private void selectCard(String role) {
        selectedRole = role;

        // Reset both cards to default
        adminCard.setStyle(
            "-fx-background-color: #1E2939; -fx-background-radius: 12; -fx-cursor: hand;");
        userCard.setStyle(
            "-fx-background-color: #F3F4F6; -fx-background-radius: 12; -fx-cursor: hand;");

        // Highlight selected card with a bright border
        if ("ADMIN".equals(role)) {
            adminCard.setStyle(
                "-fx-background-color: #1E2939;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #10B981;" +
                "-fx-border-width: 2;" +
                "-fx-border-radius: 12;" +
                "-fx-cursor: hand;");
            animateCardSelect(adminCard);
        } else {
            userCard.setStyle(
                "-fx-background-color: #F3F4F6;" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: #1E2939;" +
                "-fx-border-width: 2;" +
                "-fx-border-radius: 12;" +
                "-fx-cursor: hand;");
            animateCardSelect(userCard);
        }
    }

    /** Quick scale 0.97 → 1.03 → 1.0 on the selected card. */
    private void animateCardSelect(VBox card) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
        st.setFromX(1.0); st.setFromY(1.0);
        st.setToX(1.04);  st.setToY(1.04);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    // ══════════════════════════════════════════════════════════════
    //  NAVIGATION
    // ══════════════════════════════════════════════════════════════

    private void loadView(String fxmlName, Stage stage) {
        String resourcePath = VIEW_PATH + fxmlName;
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) throw new IllegalStateException("Resource not found: " + resourcePath);
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + fxmlName, e);
        }
    }
}