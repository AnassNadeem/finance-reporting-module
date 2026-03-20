package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.CustomerDaoInterface;
import com.raez.finance.model.TopBuyerRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CustomerInsightsController {

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbCustomerFilter;

    @FXML private Label lblTotalCustomers;
    @FXML private Label lblCustomersSub;
    @FXML private Label lblAvgSpending;
    @FXML private Label lblAvgFrequency;
    @FXML private Label lblCompanyCustomers;
    @FXML private Label lblCompanySub;
    @FXML private Label lblBuyerCount;
    @FXML private Label lblTotalSpentSummary;

    @FXML private BarChart<String, Number> chartFrequency;

    @FXML private TableView<TopBuyerRow>           tblTopBuyers;
    @FXML private TableColumn<TopBuyerRow, Number> colRank;
    @FXML private TableColumn<TopBuyerRow, String> colName;
    @FXML private TableColumn<TopBuyerRow, String> colType;
    @FXML private TableColumn<TopBuyerRow, String> colCountry;
    @FXML private TableColumn<TopBuyerRow, Number> colSpent;
    @FXML private TableColumn<TopBuyerRow, Number> colOrders;
    @FXML private TableColumn<TopBuyerRow, Number> colAOV;
    @FXML private TableColumn<TopBuyerRow, String> colLastPurchase;

    @FXML private VBox  vboxRefundAlerts;
    @FXML private Label lblRefundCount;
    @FXML private Label lblNoRefunds;

    @FXML private VBox  vboxProductIssues;
    @FXML private Label lblIssueCount;
    @FXML private Label lblNoIssues;

    @FXML private MenuButton exportMenuButton;

    // ── Services ─────────────────────────────────────────────────────────
    private final CustomerDaoInterface customerDao = new CustomerDao();
    private final ObservableList<TopBuyerRow> topBuyerItems = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "insights-worker");
        t.setDaemon(true);
        return t;
    });
    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController c) { this.mainLayoutController = c; }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        cmbCustomerFilter.setItems(FXCollections.observableArrayList(
            "All Customers", "Companies", "Normal Users"));
        cmbCustomerFilter.setValue("All Customers");

        bindColumns();
        tblTopBuyers.setItems(topBuyerItems);
        tblTopBuyers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblTopBuyers);

        cmbCustomerFilter.valueProperty().addListener((obs, o, n) -> loadData());
        loadData();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindColumns() {
        // Rank — medal emoji for top 3
        colRank.setCellValueFactory(new PropertyValueFactory<>("rank"));
        colRank.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                if (empty || v == null) return;
                int r = v.intValue();
                setText(r == 1 ? "🥇" : r == 2 ? "🥈" : r == 3 ? "🥉" : String.valueOf(r));
                setStyle("-fx-alignment: CENTER; -fx-font-size: 13px;");
            }
        });

        // Name — bold
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                if (empty || v == null) return;
                setText(v);
                setStyle("-fx-font-weight: 600; -fx-text-fill: #111827;");
            }
        });

        // Type — badge pill
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty); setGraphic(null); setText(null);
                if (empty || v == null) return;
                Label badge = new Label(v);
                boolean co = "Company".equalsIgnoreCase(v.trim());
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                    "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                    (co ? "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;"
                        : "-fx-background-color: #F3F4F6; -fx-text-fill: #4B5563;"));
                HBox w = new HBox(badge); w.setAlignment(Pos.CENTER_LEFT); setGraphic(w);
            }
        });

        colCountry.setCellValueFactory(new PropertyValueFactory<>("country"));

        colSpent.setCellValueFactory(new PropertyValueFactory<>("totalSpent"));
        colSpent.setCellFactory(CurrencyUtil.currencyCellFactory());

        // ── FIXED: was "orderCount" — correct property name is "totalOrders" ──
        colOrders.setCellValueFactory(new PropertyValueFactory<>("totalOrders"));

        colAOV.setCellValueFactory(new PropertyValueFactory<>("avgOrderValue"));
        colAOV.setCellFactory(CurrencyUtil.currencyCellFactory());

        colLastPurchase.setCellValueFactory(new PropertyValueFactory<>("lastPurchase"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadData() {
        String filter = cmbCustomerFilter.getValue();

        Task<Void> task = new Task<>() {
            // result fields populated in call(), consumed in succeeded()
            int    total, companies;
            double totalRevenue, avgSpending, avgFrequency;
            List<CustomerDao.MonthlyCount> monthly;
            List<TopBuyerRow>              topBuyers;
            List<String>                   refundAlerts;
            List<String>                   issueAlerts;

            @Override
            protected Void call() throws Exception {
                total        = customerDao.getTotalCustomerCount();
                companies    = customerDao.getCompanyCustomerCount();
                totalRevenue = customerDao.getTotalRevenue();
                avgSpending  = total > 0 ? totalRevenue / total : 0;
                monthly      = customerDao.findMonthlyOrderCounts();

                int totalOrders = monthly.stream().mapToInt(m -> m.count).sum();
                avgFrequency = (total > 0 && totalOrders > 0)
                    ? (double) totalOrders / 12.0 / total : 0;

                // Fetch all top buyers then filter client-side
                topBuyers = customerDao.findTopBuyers(100);
                String typeArg = switch (filter != null ? filter : "All Customers") {
                    case "Companies"    -> "Company";
                    case "Normal Users" -> "Individual";
                    default             -> null;
                };
                if (typeArg != null) {
                    final String ft = typeArg;
                    topBuyers = topBuyers.stream()
                        .filter(r -> ft.equalsIgnoreCase(r.getType())).toList();
                }

                refundAlerts = customerDao.findRefundAlerts();
                issueAlerts  = customerDao.findProductIssueAlerts();
                return null;
            }

            @Override
            protected void succeeded() {
                // KPI labels with fade-in
                fadeLabel(lblTotalCustomers,  String.format("%,d", total));
                fadeLabel(lblAvgSpending,      CurrencyUtil.formatCurrency(avgSpending));
                fadeLabel(lblAvgFrequency,     String.format("%.2f / mo", avgFrequency));
                fadeLabel(lblCompanyCustomers, String.format("%,d", companies));

                if (lblCustomersSub != null)
                    lblCustomersSub.setText(companies + " companies, " +
                        (total - companies) + " individuals");
                if (lblCompanySub != null)
                    lblCompanySub.setText(String.format("%.0f%% of total",
                        total > 0 ? (double) companies / total * 100 : 0));

                // Top buyers table
                topBuyerItems.setAll(topBuyers);
                if (lblBuyerCount != null)
                    lblBuyerCount.setText(topBuyers.size() + " customers");
                if (lblTotalSpentSummary != null) {
                    double sum = topBuyers.stream().mapToDouble(TopBuyerRow::getTotalSpent).sum();
                    lblTotalSpentSummary.setText("Combined total: " + CurrencyUtil.formatCurrency(sum));
                }
                setTableHeight(tblTopBuyers, topBuyers.size());

                // Bar chart — Companies vs Individuals side by side
                buildBarChart(monthly);

                // Alert cards
                populateAlertList(vboxRefundAlerts, lblNoRefunds, lblRefundCount,
                    refundAlerts, "#991B1B", "#FEF2F2");
                populateAlertList(vboxProductIssues, lblNoIssues, lblIssueCount,
                    issueAlerts, "#92400E", "#FFFBEB");
            }

            @Override
            protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    // ── Bar chart (monthly order volume) ─────────────────────────────────

    private void buildBarChart(List<CustomerDao.MonthlyCount> monthly) {
        if (chartFrequency == null) return;
        chartFrequency.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Orders");
        for (CustomerDao.MonthlyCount m : monthly)
            series.getData().add(new XYChart.Data<>(m.month, m.count));
        chartFrequency.getData().add(series);

        // Style bars after layout
        javafx.application.Platform.runLater(() ->
            chartFrequency.lookupAll(".default-color0.chart-bar")
                .forEach(n -> n.setStyle("-fx-bar-fill: #1E2939;")));
    }

    // ── Alert card builder ────────────────────────────────────────────────

    private void populateAlertList(VBox container, Label emptyLabel, Label countLabel,
                                    List<String> alerts, String textColour, String hoverBg) {
        if (container == null) return;
        container.getChildren().clear();

        if (alerts == null || alerts.isEmpty()) {
            if (emptyLabel != null) { emptyLabel.setManaged(true);  emptyLabel.setVisible(true);  }
            if (countLabel != null) countLabel.setText("0");
            return;
        }
        if (emptyLabel != null) { emptyLabel.setManaged(false); emptyLabel.setVisible(false); }
        if (countLabel != null) countLabel.setText(String.valueOf(alerts.size()));

        for (String alert : alerts) {
            // Separator between rows
            if (!container.getChildren().isEmpty()) {
                Separator sep = new Separator();
                sep.setStyle("-fx-opacity: 0.4;");
                container.getChildren().add(sep);
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.TOP_LEFT);
            row.setStyle("-fx-padding: 12 20 12 20;");

            Label dot = new Label("•");
            dot.setStyle("-fx-font-size: 16px; -fx-text-fill: " + textColour + ";");

            Label text = new Label(alert);
            text.setWrapText(true);
            text.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;");
            HBox.setHgrow(text, Priority.ALWAYS);

            row.getChildren().addAll(dot, text);
            row.setOnMouseEntered(e -> row.setStyle(
                "-fx-padding: 12 20 12 20; -fx-background-color: " + hoverBg + ";"));
            row.setOnMouseExited(e  -> row.setStyle("-fx-padding: 12 20 12 20;"));
            container.getChildren().add(row);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT  — builds List<String[]> from topBuyerItems
    //            (ExportService does not accept TableView directly)
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleExportCSV(ActionEvent e) { doExport("csv"); }
    @FXML private void handleExportPDF(ActionEvent e) { doExport("pdf"); }

    private void doExport(String format) {
        if (!SessionManager.isAdmin()) return;
        Window window = tblTopBuyers != null && tblTopBuyers.getScene() != null
            ? tblTopBuyers.getScene().getWindow() : null;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Customer Insights");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "csv".equals(format) ? "CSV Files" : "PDF Files",
            "csv".equals(format) ? "*.csv"     : "*.pdf"));
        fc.setInitialFileName("customer_insights." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;

        try {
            List<String[]> data = buildExportData();
            if ("csv".equals(format)) ExportService.exportRowsToCSV(data, file);
            else                       ExportService.exportRowsToPDF("Customer Insights — Top Buyers", data, file);
            toast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown"));
        }
    }

    private List<String[]> buildExportData() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Rank","Customer","Type","Country",
            "Total Spent","Total Orders","Avg Order Value","Last Purchase"});
        for (TopBuyerRow r : topBuyerItems) {
            rows.add(new String[]{
                String.valueOf(r.getRank()),
                r.getName(),
                r.getType(),
                r.getCountry(),
                CurrencyUtil.formatCurrency(r.getTotalSpent()),
                String.valueOf(r.getTotalOrders()),
                CurrencyUtil.formatCurrency(r.getAvgOrderValue()),
                r.getLastPurchase()
            });
        }
        return rows;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void fadeLabel(Label lbl, String value) {
        if (lbl == null) return;
        lbl.setText(value);
        lbl.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(400), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void setTableHeight(TableView<?> t, int rows) {
        if (t == null) return;
        double h = 38 + Math.max(rows, 5) * 44.0;
        t.setPrefHeight(h); t.setMinHeight(h);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void applyRowFactory(TableView table) {
        table.setRowFactory(tv -> {
            TableRow row = new TableRow() {
                @Override protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setStyle(empty || item == null ? "-fx-background-color: transparent;"
                        : getIndex() % 2 == 0 ? "-fx-background-color: white;"
                                               : "-fx-background-color: #F9FAFB;");
                }
            };
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;"); });
            row.setOnMouseExited(e  -> { if (!row.isEmpty()) row.setStyle(row.getIndex() % 2 == 0
                ? "-fx-background-color: white;" : "-fx-background-color: #F9FAFB;"); });
            return row;
        });
    }

    private void toast(String type, String msg) {
        if (mainLayoutController != null) mainLayoutController.showToast(type, msg);
        else new Alert("success".equals(type) ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, msg).showAndWait();
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException ex) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}