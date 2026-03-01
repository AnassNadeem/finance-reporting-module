package com.raez.finance.controller;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color; // <-- Added this import
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class NotificationToastController {

    @FXML private HBox rootBox;
    @FXML private SVGPath iconPath;
    @FXML private Label lblMessage;

    private Runnable onCloseCallback;

    @FXML
    public void initialize() {
        // Prepare for slide-in animation by pushing it to the right initially
        rootBox.setTranslateX(400); 
        rootBox.setOpacity(0);
    }

    /**
     * Replaces the React component props: type, message, onClose
     */
    public void setNotification(String type, String message, Runnable onCloseCallback) {
        this.lblMessage.setText(message);
        this.onCloseCallback = onCloseCallback;

        // Apply styles and icons based on type using Color.web()
        switch (type.toLowerCase()) {
            case "success":
                rootBox.setStyle("-fx-background-color: #F0FDF4; -fx-border-color: #BBF7D0; -fx-border-radius: 8; -fx-background-radius: 8;");
                iconPath.setStroke(Color.web("#16A34A")); // Green
                iconPath.setContent("M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3"); 
                break;
            case "error":
                rootBox.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #FECACA; -fx-border-radius: 8; -fx-background-radius: 8;");
                iconPath.setStroke(Color.web("#DC2626")); // Red
                iconPath.setContent("M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 8v4 M12 16h.01"); 
                break;
            case "info":
            default:
                rootBox.setStyle("-fx-background-color: #EFF6FF; -fx-border-color: #BFDBFE; -fx-border-radius: 8; -fx-background-radius: 8;");
                iconPath.setStroke(Color.web("#2563EB")); // Blue
                iconPath.setContent("M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 16v-4 M12 8h.01"); 
                break;
        }

        playSlideInAnimation();
    }

    private void playSlideInAnimation() {
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), rootBox);
        slideIn.setToX(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), rootBox);
        fadeIn.setToValue(1.0);

        slideIn.play();
        fadeIn.play();

        PauseTransition delay = new PauseTransition(Duration.seconds(5));
        delay.setOnFinished(e -> closeNotification());
        delay.play();
    }

    @FXML
    private void handleClose(ActionEvent event) {
        closeNotification();
    }

    private void closeNotification() {
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), rootBox);
        slideOut.setToX(400);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), rootBox);
        fadeOut.setToValue(0);

        slideOut.setOnFinished(e -> {
            if (onCloseCallback != null) {
                onCloseCallback.run(); 
            }
        });

        slideOut.play();
        fadeOut.play();
    }
}