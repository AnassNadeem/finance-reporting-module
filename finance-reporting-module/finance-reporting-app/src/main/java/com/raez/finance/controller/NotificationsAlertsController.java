package com.raez.finance.controller;

import com.raez.finance.dao.AlertDao;
import com.raez.finance.dao.AlertDaoInterface;
import com.raez.finance.dao.FinancialAnomalyDao;
import com.raez.finance.dao.FinancialAnomalyDaoInterface;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NotificationsAlertsController {

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbFilter;

    @FXML private Label lblCriticalCount;
    @FXML private Label lblWarningCount;
    @FXML private Label lblInfoCount;
    @FXML private Label lblResolvedCount;

    @FXML private VBox  vboxAlerts;
    @FXML private Label lblNoAlerts;
    @FXML private Label badgeCritical;
    @FXML private Button btnResolveAllAlerts;

    @FXML private VBox  vboxNotifications;
    @FXML private Label lblNoNotifications;
    @FXML private Label badgeUnread;
    @FXML private Button btnResolveAllNotifs;
    @FXML private Button btnMarkAllRead;

    // ── Services ──────────────────────────────────────────────────────────
    private final AlertDaoInterface            alertDao   = new AlertDao();
    private final FinancialAnomalyDaoInterface anomalyDao = new FinancialAnomalyDao();
    private final ExecutorService     executor   = Executors.newSingleThreadExecutor();

    // In-memory state (rows are re-rendered on filter change without reloading)
    private final ObservableList<AlertDao.AlertRow>            allAlerts      = FXCollections.observableArrayList();
    private final ObservableList<FinancialAnomalyDao.AnomalyRow> allAnomalies = FXCollections.observableArrayList();

    // Track resolved IDs locally (until DB write is confirmed)
    private final Set<Integer> resolvedAlertIds   = new HashSet<>();
    private final Set<Integer> resolvedAnomalyIds = new HashSet<>();
    private final Set<Integer> readAnomalyIds     = new HashSet<>();

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        cmbFilter.setValue("All");
        cmbFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        loadAll();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadAll() {
        Task<Void> task = new Task<>() {
            List<AlertDao.AlertRow>            alerts   = new ArrayList<>();
            List<FinancialAnomalyDao.AnomalyRow> anomalies = new ArrayList<>();

            @Override
            protected Void call() {
                // ── Alerts ───────────────────────────────────────────────
                try { alerts.addAll(alertDao.findAlerts(false)); } catch (Exception ignored) {}

                // ── Anomalies ────────────────────────────────────────────
                try { anomalies.addAll(anomalyDao.findAnomalies(false)); } catch (Exception ignored) {}

                return null;
            }

            @Override
            protected void succeeded() {
                allAlerts.setAll(alerts);
                allAnomalies.setAll(anomalies);
                applyFilter();
            }

            @Override
            protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FILTER + RENDER
    // ══════════════════════════════════════════════════════════════════════

    private void applyFilter() {
        String filter = cmbFilter.getValue() != null ? cmbFilter.getValue() : "All";

        // Filter alerts
        List<AlertDao.AlertRow> filteredAlerts = allAlerts.stream()
            .filter(a -> matchesFilter(a.getSeverity(), resolvedAlertIds.contains(a.getAlertID()), filter))
            .toList();

        // Filter anomalies
        List<FinancialAnomalyDao.AnomalyRow> filteredAnomalies = allAnomalies.stream()
            .filter(a -> {
                boolean resolved = resolvedAnomalyIds.contains(a.getAnomalyID()) || a.isResolved();
                boolean unread   = !readAnomalyIds.contains(a.getAnomalyID());
                if ("Unread".equals(filter)) return unread && !resolved;
                if ("Resolved".equals(filter)) return resolved;
                return matchesFilter(a.getSeverity(), resolved, filter);
            })
            .toList();

        renderSummaryBadges();
        renderAlerts(filteredAlerts);
        renderAnomalies(filteredAnomalies);
    }

    private boolean matchesFilter(String severity, boolean resolved, String filter) {
        return switch (filter) {
            case "Critical" -> !resolved && ("CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity));
            case "Warnings" -> !resolved && "WARNING".equalsIgnoreCase(severity);
            case "Info"     -> !resolved && "INFO".equalsIgnoreCase(severity);
            case "Resolved" -> resolved;
            case "Unread"   -> !resolved;
            default         -> true; // "All"
        };
    }

    // ── Summary badges ────────────────────────────────────────────────────

    private void renderSummaryBadges() {
        long critical = allAlerts.stream()
            .filter(a -> !resolvedAlertIds.contains(a.getAlertID()))
            .filter(a -> isCritical(a.getSeverity())).count();
        long warning = allAlerts.stream()
            .filter(a -> !resolvedAlertIds.contains(a.getAlertID()))
            .filter(a -> "WARNING".equalsIgnoreCase(a.getSeverity())).count();
        long info = allAnomalies.stream()
            .filter(a -> !resolvedAnomalyIds.contains(a.getAnomalyID()))
            .filter(a -> "INFO".equalsIgnoreCase(a.getSeverity())).count();
        long resolved = resolvedAlertIds.size() + resolvedAnomalyIds.size();
        long unread   = allAnomalies.stream()
            .filter(a -> !readAnomalyIds.contains(a.getAnomalyID()) && !resolvedAnomalyIds.contains(a.getAnomalyID())).count();

        setText(lblCriticalCount, String.valueOf(critical));
        setText(lblWarningCount,  String.valueOf(warning));
        setText(lblInfoCount,     String.valueOf(info));
        setText(lblResolvedCount, String.valueOf(resolved));
        setText(badgeCritical,    String.valueOf(critical));
        setText(badgeUnread,      unread + " unread");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER — ALERTS
    // ══════════════════════════════════════════════════════════════════════

    private void renderAlerts(List<AlertDao.AlertRow> rows) {
        vboxAlerts.getChildren().clear();
        boolean empty = rows.isEmpty();
        if (lblNoAlerts != null) { lblNoAlerts.setManaged(empty); lblNoAlerts.setVisible(empty); }

        for (AlertDao.AlertRow row : rows) {
            boolean resolved = resolvedAlertIds.contains(row.getAlertID()) || row.isResolved();
            vboxAlerts.getChildren().add(buildAlertRow(row, resolved));
        }
    }

    private HBox buildAlertRow(AlertDao.AlertRow row, boolean resolved) {
        boolean crit     = isCritical(row.getSeverity());
        String bg        = resolved ? "#F9FAFB" : (crit ? "#FEF2F2" : "#FEFCE8");
        String accent    = resolved ? "#9CA3AF" : (crit ? "#EF4444" : "#EAB308");
        String titleClr  = resolved ? "#9CA3AF" : (crit ? "#7F1D1D" : "#713F12");
        String msgClr    = resolved ? "#9CA3AF" : (crit ? "#B91C1C" : "#A16207");

        HBox card = new HBox(12);
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle("-fx-background-color: " + bg + ";" +
                      "-fx-border-color: " + accent + " transparent transparent transparent;" +
                      "-fx-border-width: 0 0 1 0;" +
                      (resolved ? "-fx-opacity: 0.7;" : ""));
        card.setPadding(new Insets(16, 20, 16, 20));

        // ── Checkbox ───────────────────────────────────────────────────
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(resolved);
        checkBox.setStyle("-fx-cursor: hand;");
        checkBox.setTooltip(new Tooltip(resolved ? "Mark as unresolved" : "Mark as resolved"));
        checkBox.setOnAction(e -> toggleAlertResolved(row, checkBox.isSelected(), card, titleClr, msgClr));

        // ── Accent dot ────────────────────────────────────────────────
        Circle dot = new Circle(5);
        dot.setFill(Color.web(resolved ? "#9CA3AF" : accent));
        HBox dotWrapper = new HBox(dot);
        dotWrapper.setAlignment(Pos.CENTER);
        dotWrapper.setPrefWidth(14);

        // ── Text block ────────────────────────────────────────────────
        VBox text = new VBox(4);
        HBox.setHgrow(text, Priority.ALWAYS);

        // Title row with severity badge
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label(row.getAlertType() != null ? row.getAlertType() : "Alert");
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: " + titleClr + ";");

        String badgeText  = resolved ? "Resolved" : (crit ? "Critical" : "Warning");
        String badgeBg    = resolved ? "#F3F4F6"  : (crit ? "#FEE2E2"  : "#FEF9C3");
        String badgeFg    = resolved ? "#6B7280"  : (crit ? "#991B1B"  : "#92400E");
        Label severityBadge = new Label(badgeText);
        severityBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                               "-fx-padding: 1 7 1 7; -fx-background-radius: 999;" +
                               "-fx-background-color: " + badgeBg + ";" +
                               "-fx-text-fill: " + badgeFg + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Timestamp
        Label timeLbl = new Label(formatDate(row.getCreatedAt()));
        timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

        titleRow.getChildren().addAll(titleLbl, severityBadge, spacer, timeLbl);

        // Message
        Label msgLbl = new Label(row.getMessage() != null ? row.getMessage() : "");
        msgLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + msgClr + ";");
        msgLbl.setWrapText(true);

        text.getChildren().addAll(titleRow, msgLbl);
        card.getChildren().addAll(checkBox, dotWrapper, text);

        // Hover
        card.setOnMouseEntered(e -> {
            if (!resolved) card.setStyle(card.getStyle() + "-fx-cursor: hand;");
        });

        // Fade-in animation
        fadeIn(card);
        return card;
    }

    private void toggleAlertResolved(AlertDao.AlertRow row, boolean markResolved, HBox card,
                                     String oldTitle, String oldMsg) {
        if (markResolved) resolvedAlertIds.add(row.getAlertID());
        else              resolvedAlertIds.remove(row.getAlertID());

        // Persist async
        executor.execute(() -> {
            try { alertDao.setResolved(row.getAlertID(), markResolved); } catch (Exception ignored) {}
        });

        applyFilter(); // re-render
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER — NOTIFICATIONS / ANOMALIES
    // ══════════════════════════════════════════════════════════════════════

    private void renderAnomalies(List<FinancialAnomalyDao.AnomalyRow> rows) {
        vboxNotifications.getChildren().clear();
        boolean empty = rows.isEmpty();
        if (lblNoNotifications != null) { lblNoNotifications.setManaged(empty); lblNoNotifications.setVisible(empty); }

        for (FinancialAnomalyDao.AnomalyRow row : rows) {
            boolean resolved = resolvedAnomalyIds.contains(row.getAnomalyID()) || row.isResolved();
            boolean unread   = !readAnomalyIds.contains(row.getAnomalyID()) && !resolved;
            vboxNotifications.getChildren().add(buildNotificationRow(row, resolved, unread));
        }
    }

    private HBox buildNotificationRow(FinancialAnomalyDao.AnomalyRow row, boolean resolved, boolean unread) {
        boolean crit   = isCritical(row.getSeverity());
        String bg      = resolved ? "white" : (unread ? (crit ? "#FEF2F2" : "#EFF6FF") : "white");
        String iconClr = resolved ? "#9CA3AF" : (crit ? "#DC2626" : "#2563EB");

        HBox card = new HBox(12);
        card.setAlignment(Pos.TOP_LEFT);
        card.setStyle("-fx-background-color: " + bg + ";" +
                      "-fx-border-color: transparent transparent #F3F4F6 transparent;" +
                      "-fx-border-width: 0 0 1 0;" +
                      "-fx-cursor: hand;" +
                      (resolved ? "-fx-opacity: 0.65;" : ""));
        card.setPadding(new Insets(16, 20, 16, 20));

        // Mark read on click
        card.setOnMouseClicked(e -> {
            readAnomalyIds.add(row.getAnomalyID());
            applyFilter();
        });

        // ── Checkbox ──────────────────────────────────────────────────
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(resolved);
        checkBox.setStyle("-fx-cursor: hand;");
        checkBox.setTooltip(new Tooltip(resolved ? "Mark as unresolved" : "Resolve"));
        checkBox.setOnAction(e -> {
            e.consume(); // prevent card click from firing
            if (checkBox.isSelected()) resolvedAnomalyIds.add(row.getAnomalyID());
            else                       resolvedAnomalyIds.remove(row.getAnomalyID());
            try { anomalyDao.setResolved(row.getAnomalyID(), checkBox.isSelected()); } catch (Exception ignored) {}
            applyFilter();
        });

        // ── Icon dot ──────────────────────────────────────────────────
        Circle dot = new Circle(6);
        dot.setFill(Color.web(iconClr + (resolved ? "66" : "FF")));
        HBox dotWrapper = new HBox(dot);
        dotWrapper.setAlignment(Pos.CENTER);
        dotWrapper.setPrefWidth(18);

        // ── Text block ────────────────────────────────────────────────
        VBox text = new VBox(4);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        String title = row.getAnomalyType() != null ? row.getAnomalyType() : "Anomaly";
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700;" +
                          "-fx-text-fill: " + (resolved ? "#9CA3AF" : "#111827") + ";");

        // Unread indicator
        if (unread) {
            Circle unreadDot = new Circle(4);
            unreadDot.setFill(Color.web("#2563EB"));
            titleRow.getChildren().addAll(titleLbl, unreadDot);
        } else {
            titleRow.getChildren().add(titleLbl);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label timeLbl = new Label(formatDate(row.getAlertDate()));
        timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");
        titleRow.getChildren().addAll(spacer, timeLbl);

        String desc = row.getDescription() != null ? row.getDescription()
                    : (row.getDetectionRule() != null ? row.getDetectionRule() : "");
        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (resolved ? "#9CA3AF" : "#4B5563") + ";");
        descLbl.setWrapText(true);

        text.getChildren().addAll(titleRow, descLbl);
        card.getChildren().addAll(checkBox, dotWrapper, text);

        fadeIn(card);
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FXML HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleFilterChange() {
        applyFilter();
    }

    @FXML
    private void handleMarkAllRead() {
        allAnomalies.forEach(a -> readAnomalyIds.add(a.getAnomalyID()));
        applyFilter();
    }

    @FXML
    private void handleResolveAllAlerts() {
        allAlerts.forEach(a -> resolvedAlertIds.add(a.getAlertID()));
        executor.execute(() -> {
            allAlerts.forEach(a -> { try { alertDao.setResolved(a.getAlertID(), true); } catch (Exception ignored) {} });
        });
        applyFilter();
    }

    @FXML
    private void handleResolveAllNotifs() {
        allAnomalies.forEach(a -> resolvedAnomalyIds.add(a.getAnomalyID()));
        executor.execute(() -> {
            allAnomalies.forEach(a -> { try { anomalyDao.setResolved(a.getAnomalyID(), true); } catch (Exception ignored) {} });
        });
        applyFilter();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private boolean isCritical(String severity) {
        return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            LocalDate d = LocalDate.parse(raw);
            long days = java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now());
            if (days == 0) return "Today";
            if (days == 1) return "Yesterday";
            if (days < 7)  return days + " days ago";
            return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (Exception e) {
            return raw;
        }
    }

    private void setText(Label lbl, String value) {
        if (lbl != null) lbl.setText(value);
    }

    private void fadeIn(HBox node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(250), node);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}