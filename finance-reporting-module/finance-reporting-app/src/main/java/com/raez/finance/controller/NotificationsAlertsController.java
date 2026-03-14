package com.raez.finance.controller;

import com.raez.finance.dao.AlertDao;
import com.raez.finance.dao.FinancialAnomalyDao;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsAlertsController {

    private final AlertDao alertDao = new AlertDao();
    private final FinancialAnomalyDao anomalyDao = new FinancialAnomalyDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML
    private VBox vboxAlerts;

    @FXML
    private VBox vboxNotifications;

    @FXML
    public void initialize() {
        vboxAlerts.getChildren().clear();
        vboxNotifications.getChildren().clear();
        loadAlerts();
        loadAnomalies();
    }

    private void loadAlerts() {
        Task<List<AlertDao.AlertRow>> task = new Task<>() {
            @Override
            protected List<AlertDao.AlertRow> call() throws Exception {
                return alertDao.findAlerts(false);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                Platform.runLater(() -> renderAlerts(task.getValue()));
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> {
                    Label lbl = new Label("Unable to load alerts: " + ex.getMessage());
                    lbl.setWrapText(true);
                    vboxAlerts.getChildren().add(lbl);
                });
            }
        });
        executor.execute(task);
    }

    private void renderAlerts(List<AlertDao.AlertRow> rows) {
        vboxAlerts.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("No alerts.");
            empty.setStyle("-fx-text-fill: #6B7280;");
            vboxAlerts.getChildren().add(empty);
            return;
        }
        for (AlertDao.AlertRow row : rows) {
            String severity = row.getSeverity() != null ? row.getSeverity() : "INFO";
            boolean critical = "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
            String bg = critical ? "#FEF2F2" : "#FEFCE8";
            String border = critical ? "#EF4444" : "#EAB308";
            String titleColor = critical ? "#7F1D1D" : "#713F12";
            String msgColor = critical ? "#B91C1C" : "#A16207";

            HBox card = new HBox(12);
            card.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 0 4; -fx-background-radius: 0 8 8 0;");
            card.setPadding(new Insets(16));

            SVGPath icon = new SVGPath();
            icon.setContent("M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01");
            icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
            icon.setStroke(javafx.scene.paint.Color.web(border));
            icon.setStrokeWidth(2);

            VBox textBox = new VBox(4);
            textBox.setPadding(new Insets(0, 0, 0, 12));
            Label titleLbl = new Label((row.getAlertType() != null ? row.getAlertType() : "Alert") + (row.isResolved() ? " (Resolved)" : ""));
            titleLbl.setStyle("-fx-text-fill: " + titleColor + "; -fx-font-weight: bold;");
            titleLbl.setFont(Font.font(14));
            Label msgLbl = new Label(row.getMessage() != null ? row.getMessage() : "");
            msgLbl.setStyle("-fx-text-fill: " + msgColor + ";");
            msgLbl.setWrapText(true);
            Label timeLbl = new Label(row.getCreatedAt() != null ? row.getCreatedAt() : "");
            timeLbl.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12;");
            textBox.getChildren().addAll(titleLbl, msgLbl, timeLbl);
            HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);

            card.getChildren().addAll(icon, textBox);
            vboxAlerts.getChildren().add(card);
        }
    }

    private void loadAnomalies() {
        Task<List<FinancialAnomalyDao.AnomalyRow>> task = new Task<>() {
            @Override
            protected List<FinancialAnomalyDao.AnomalyRow> call() throws Exception {
                return anomalyDao.findAnomalies(false);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                Platform.runLater(() -> renderAnomalies(task.getValue()));
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> {
            if (ex != null) {
                Platform.runLater(() -> {
                    Label lbl = new Label("Unable to load anomalies: " + ex.getMessage());
                    lbl.setWrapText(true);
                    vboxNotifications.getChildren().add(lbl);
                });
            }
        });
        executor.execute(task);
    }

    private void renderAnomalies(List<FinancialAnomalyDao.AnomalyRow> rows) {
        vboxNotifications.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label("No financial anomalies recorded.");
            empty.setStyle("-fx-text-fill: #6B7280;");
            vboxNotifications.getChildren().add(empty);
            return;
        }
        for (FinancialAnomalyDao.AnomalyRow row : rows) {
            String severity = row.getSeverity() != null ? row.getSeverity() : "INFO";
            boolean critical = "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
            String bg = critical ? "#FEF2F2" : "#EFF6FF";
            String border = critical ? "#EF4444" : "#E5E7EB";

            HBox card = new HBox(16);
            card.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
            card.setPadding(new Insets(24));

            SVGPath icon = new SVGPath();
            icon.setContent("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z");
            icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
            icon.setStroke(javafx.scene.paint.Color.web("#2563EB"));
            icon.setStrokeWidth(2);

            VBox textBox = new VBox(4);
            textBox.setPadding(new Insets(0, 0, 0, 16));
            String title = (row.getAnomalyType() != null ? row.getAnomalyType() : "Anomaly") + (row.isResolved() ? " (Resolved)" : "");
            Label titleLbl = new Label(title);
            titleLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold;");
            titleLbl.setFont(Font.font(14));
            Label descLbl = new Label(row.getDescription() != null ? row.getDescription() : (row.getDetectionRule() != null ? row.getDetectionRule() : ""));
            descLbl.setStyle("-fx-text-fill: #4B5563;");
            descLbl.setWrapText(true);
            Label timeLbl = new Label(row.getAlertDate() != null ? row.getAlertDate() : "");
            timeLbl.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12;");
            textBox.getChildren().addAll(titleLbl, descLbl, timeLbl);
            HBox.setHgrow(textBox, javafx.scene.layout.Priority.ALWAYS);

            card.getChildren().addAll(icon, textBox);
            vboxNotifications.getChildren().add(card);
        }
    }
}
