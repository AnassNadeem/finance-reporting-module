package com.raez.finance.controller;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * NotificationToastController
 *
 * Usage:
 *   toastController.setNotification("success", "Saved!", "Report exported to CSV.", onClose);
 *   toastController.setNotification("error",   "Export failed", "Could not write file.", onClose);
 *   toastController.setNotification("info",    "New order", "ORD-1848 received.", onClose);
 *   toastController.setNotification("warning", "Low stock", "AR7 Robots below threshold.", onClose);
 *
 * The toast auto-dismisses after 5 seconds. Clicking the × closes it early.
 */
public class NotificationToastController {

    @FXML private HBox      rootBox;
    @FXML private Rectangle accentBar;
    @FXML private StackPane iconWrapper;
    @FXML private SVGPath   iconPath;
    @FXML private Label     lblTitle;
    @FXML private Label     lblMessage;

    private Runnable onCloseCallback;

    // SVG icon paths per type
    private static final String ICON_SUCCESS = "M22 11.08V12a10 10 0 1 1-5.93-9.14 M22 4L12 14.01l-3-3";
    private static final String ICON_ERROR   = "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M15 9l-6 6 M9 9l6 6";
    private static final String ICON_WARNING = "M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01";
    private static final String ICON_INFO    = "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10z M12 16v-4 M12 8h.01";

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // Start offscreen to the right, invisible
        if (rootBox != null) {
            rootBox.setTranslateX(420);
            rootBox.setOpacity(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Configure the toast with a type, title, message and optional close callback.
     * Call this after loading the FXML — it triggers the slide-in animation automatically.
     *
     * @param type     "success" | "error" | "warning" | "info"
     * @param title    Short heading (e.g. "Saved!" or "Export failed")
     * @param message  Detail text
     * @param callback Called when the toast is dismissed (may be null)
     */
    public void setNotification(String type, String title, String message, Runnable callback) {
        this.onCloseCallback = callback;

        if (lblTitle   != null) lblTitle.setText(title != null ? title : "");
        if (lblMessage != null) lblMessage.setText(message != null ? message : "");

        applyType(type != null ? type.toLowerCase() : "info");
        playSlideIn();
    }

    /**
     * Convenience overload — derives title from type when not supplied.
     */
    public void setNotification(String type, String message, Runnable callback) {
        String title = switch (type != null ? type.toLowerCase() : "info") {
            case "success" -> "Success";
            case "error"   -> "Error";
            case "warning" -> "Warning";
            default        -> "Info";
        };
        setNotification(type, title, message, callback);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STYLING PER TYPE
    // ══════════════════════════════════════════════════════════════════════

    private void applyType(String type) {
        String accentHex, iconHex, iconBg, titleFg;

        switch (type) {
            case "success" -> {
                accentHex = "#16A34A"; iconHex = "#16A34A"; iconBg = "#DCFCE7"; titleFg = "#15803D";
                if (iconPath != null) iconPath.setContent(ICON_SUCCESS);
            }
            case "error" -> {
                accentHex = "#DC2626"; iconHex = "#DC2626"; iconBg = "#FEE2E2"; titleFg = "#991B1B";
                if (iconPath != null) iconPath.setContent(ICON_ERROR);
            }
            case "warning" -> {
                accentHex = "#F59E0B"; iconHex = "#D97706"; iconBg = "#FEF3C7"; titleFg = "#92400E";
                if (iconPath != null) iconPath.setContent(ICON_WARNING);
            }
            default -> { // info
                accentHex = "#2563EB"; iconHex = "#2563EB"; iconBg = "#DBEAFE"; titleFg = "#1E40AF";
                if (iconPath != null) iconPath.setContent(ICON_INFO);
            }
        }

        // Accent bar colour
        if (accentBar != null) accentBar.setFill(Color.web(accentHex));

        // Icon colour
        if (iconPath != null) {
            iconPath.setStroke(Color.web(iconHex));
            iconPath.setFill(Color.TRANSPARENT);
        }

        // Icon background circle
        if (iconWrapper != null) {
            iconWrapper.setStyle("-fx-background-color: " + iconBg + "; -fx-background-radius: 999;");
        }

        // Title colour
        if (lblTitle != null) {
            lblTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + titleFg + ";");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ANIMATION
    // ══════════════════════════════════════════════════════════════════════

    private void playSlideIn() {
        if (rootBox == null) return;

        TranslateTransition slide = new TranslateTransition(Duration.millis(320), rootBox);
        slide.setFromX(420); slide.setToX(0);

        FadeTransition fade = new FadeTransition(Duration.millis(320), rootBox);
        fade.setFromValue(0); fade.setToValue(1);

        ParallelTransition anim = new ParallelTransition(slide, fade);
        anim.play();

        // Auto-dismiss after 5 s
        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(e -> dismiss());
        pause.play();
    }

    private void dismiss() {
        if (rootBox == null) return;

        TranslateTransition slide = new TranslateTransition(Duration.millis(280), rootBox);
        slide.setToX(420);

        FadeTransition fade = new FadeTransition(Duration.millis(280), rootBox);
        fade.setToValue(0);

        ParallelTransition anim = new ParallelTransition(slide, fade);
        anim.setOnFinished(e -> {
            if (onCloseCallback != null) onCloseCallback.run();
        });
        anim.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FXML HANDLER
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleClose(ActionEvent event) {
        dismiss();
    }
}