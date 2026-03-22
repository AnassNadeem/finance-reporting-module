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
import javafx.geometry.Side;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CustomerInsightsController {

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbChartDateRange;
    @FXML private VBox              boxChartStartDate;
    @FXML private VBox              boxChartEndDate;
    @FXML private DatePicker        dpChartStart;
    @FXML private DatePicker        dpChartEnd;

    @FXML private ComboBox<String> cmbBuyerDateRange;
    @FXML private VBox              boxBuyerStartDate;
    @FXML private VBox              boxBuyerEndDate;
    @FXML private DatePicker        dpBuyerStart;
    @FXML private DatePicker        dpBuyerEnd;
    @FXML private ComboBox<String> cmbBuyerCustomerFilter;
    @FXML private ComboBox<String> cmbBuyerRowsPerPage;
    @FXML private Label             lblBuyerPageInfo;
    @FXML private Button            btnBuyerPrevPage;
    @FXML private Button            btnBuyerNextPage;

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

    private int buyerCurrentPage = 1;
    private int totalBuyerRows = 0;

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

        ObservableList<String> dateChoices = FXCollections.observableArrayList(
            "Last 7 Days", "Last 30 Days", "Last 12 Months", "Year to Date", "Custom");
        if (cmbChartDateRange != null) {
            cmbChartDateRange.setItems(dateChoices);
            cmbChartDateRange.setValue("Last 12 Months");
        }
        if (cmbBuyerDateRange != null) {
            cmbBuyerDateRange.setItems(FXCollections.observableArrayList(dateChoices));
            cmbBuyerDateRange.setValue("Last 12 Months");
        }

        cmbBuyerCustomerFilter.setItems(FXCollections.observableArrayList(
            "All Customers", "Companies", "Normal Users"));
        cmbBuyerCustomerFilter.setValue("All Customers");

        if (cmbBuyerRowsPerPage != null) {
            cmbBuyerRowsPerPage.setValue("10");
        }

        bindColumns();
        tblTopBuyers.setItems(topBuyerItems);
        tblTopBuyers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblTopBuyers);
        setBuyerTableHeight();

        wireChartDateListeners();
        wireBuyerDateListeners();

        cmbBuyerCustomerFilter.valueProperty().addListener((obs, o, n) -> {
            buyerCurrentPage = 1;
            loadTopBuyersTable();
        });

        configureInsightsChart();
        loadKpiAndChart();
        loadInsightAlertsFromDatabase();
        loadTopBuyersTable();
    }

    /** X-axis layout: angled labels and spacing so months do not overlap (grouped bars). */
    private void configureInsightsChart() {
        if (chartFrequency == null) return;
        chartFrequency.setAnimated(false);
        chartFrequency.setLegendSide(Side.BOTTOM);
        if (chartFrequency.getXAxis() instanceof CategoryAxis cx) {
            cx.setTickLabelRotation(-52);
            cx.setTickLabelGap(4);
            cx.setGapStartAndEnd(true);
            cx.setAnimated(false);
        }
        if (chartFrequency.getYAxis() instanceof NumberAxis ny) {
            ny.setAnimated(false);
            ny.setForceZeroInRange(true);
            ny.setMinorTickVisible(false);
            ny.setAutoRanging(true);
        }
    }

    private void wireChartDateListeners() {
        if (cmbChartDateRange != null) {
            cmbChartDateRange.valueProperty().addListener((obs, o, n) -> {
                updateChartCustomVisibility();
                loadKpiAndChart();
            });
        }
        Runnable chartReload = this::loadKpiAndChart;
        if (dpChartStart != null) dpChartStart.valueProperty().addListener((o, a, b) -> chartReload.run());
        if (dpChartEnd != null) dpChartEnd.valueProperty().addListener((o, a, b) -> chartReload.run());
        updateChartCustomVisibility();
    }

    private void wireBuyerDateListeners() {
        if (cmbBuyerDateRange != null) {
            cmbBuyerDateRange.valueProperty().addListener((obs, o, n) -> {
                updateBuyerCustomVisibility();
                buyerCurrentPage = 1;
                loadTopBuyersTable();
            });
        }
        Runnable buyerReload = () -> {
            buyerCurrentPage = 1;
            loadTopBuyersTable();
        };
        if (dpBuyerStart != null) dpBuyerStart.valueProperty().addListener((o, a, b) -> buyerReload.run());
        if (dpBuyerEnd != null) dpBuyerEnd.valueProperty().addListener((o, a, b) -> buyerReload.run());
        updateBuyerCustomVisibility();
    }

    private void updateChartCustomVisibility() {
        boolean custom = "Custom".equals(cmbChartDateRange != null ? cmbChartDateRange.getValue() : null);
        setVisible(boxChartStartDate, custom);
        setVisible(boxChartEndDate, custom);
    }

    private void updateBuyerCustomVisibility() {
        boolean custom = "Custom".equals(cmbBuyerDateRange != null ? cmbBuyerDateRange.getValue() : null);
        setVisible(boxBuyerStartDate, custom);
        setVisible(boxBuyerEndDate, custom);
    }

    private static void setVisible(VBox box, boolean v) {
        if (box != null) {
            box.setVisible(v);
            box.setManaged(v);
        }
    }

    @FXML private void handleBuyerRowsPerPageChange(ActionEvent e) {
        buyerCurrentPage = 1;
        loadTopBuyersTable();
    }

    @FXML private void handleBuyerPrevPage(ActionEvent e) {
        if (buyerCurrentPage > 1) {
            buyerCurrentPage--;
            loadTopBuyersTable();
        }
    }

    @FXML private void handleBuyerNextPage(ActionEvent e) {
        int ps = getBuyerPageSize();
        int pages = Math.max(1, (int) Math.ceil((double) totalBuyerRows / ps));
        if (buyerCurrentPage < pages) {
            buyerCurrentPage++;
            loadTopBuyersTable();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindColumns() {
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

        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colName.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                if (empty || v == null) return;
                setText(v);
                setStyle("-fx-font-weight: 600; -fx-text-fill: #111827;");
            }
        });

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

        colOrders.setCellValueFactory(new PropertyValueFactory<>("totalOrders"));

        colAOV.setCellValueFactory(new PropertyValueFactory<>("avgOrderValue"));
        colAOV.setCellFactory(CurrencyUtil.currencyCellFactory());

        colLastPurchase.setCellValueFactory(new PropertyValueFactory<>("lastPurchase"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    /** KPI cards + monthly bar chart (alerts load separately via {@link #loadInsightAlertsFromDatabase()}). */
    private void loadKpiAndChart() {
        final LocalDate[] chartRange = resolveChartDateRange();

        Task<Void> task = new Task<>() {
            int    total, companies;
            double totalRevenue, avgSpending, avgFrequency;
            List<CustomerDao.MonthlySplit> monthlySplit;

            @Override
            protected Void call() throws Exception {
                total        = customerDao.getTotalCustomerCount();
                companies    = customerDao.getCompanyCustomerCount();
                totalRevenue = customerDao.getTotalRevenue();
                avgSpending  = total > 0 ? totalRevenue / total : 0;
                monthlySplit = customerDao.findMonthlyOrderCountsByCustomerType(chartRange[0], chartRange[1]);

                int totalOrders = monthlySplit.stream()
                    .mapToInt(m -> m.companyCount + m.individualCount).sum();
                int monthBuckets = Math.max(1, monthlySplit.size());
                avgFrequency = (total > 0 && totalOrders > 0)
                    ? (double) totalOrders / monthBuckets / total : 0;
                return null;
            }

            @Override
            protected void succeeded() {
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

                buildBarChart(monthlySplit);
            }

            @Override
            protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    /**
     * Dedicated DB refresh for refund/churn and product-issue cards (always queries live tables).
     * Runs on its own task so alert UI stays tied to current {@code Refund} / order data.
     */
    private void loadInsightAlertsFromDatabase() {
        Task<Void> task = new Task<>() {
            List<String> refundAlerts;
            List<String> issueAlerts;

            @Override
            protected Void call() throws Exception {
                refundAlerts = customerDao.findRefundAlerts();
                issueAlerts  = customerDao.findProductIssueAlerts();
                return null;
            }

            @Override
            protected void succeeded() {
                populateAlertList(vboxRefundAlerts, lblNoRefunds, lblRefundCount,
                    refundAlerts, "#991B1B", "#FEF2F2");
                populateAlertList(vboxProductIssues, lblNoIssues, lblIssueCount,
                    issueAlerts, "#92400E", "#FFFBEB");
            }

            @Override
            protected void failed() {
                if (getException() != null) getException().printStackTrace();
                populateAlertList(vboxRefundAlerts, lblNoRefunds, lblRefundCount,
                    List.of(), "#991B1B", "#FEF2F2");
                populateAlertList(vboxProductIssues, lblNoIssues, lblIssueCount,
                    List.of(), "#92400E", "#FFFBEB");
            }
        };
        executor.execute(task);
    }

    private void loadTopBuyersTable() {
        final LocalDate[] buyerRange = resolveBuyerDateRange();
        final String typeFilter = cmbBuyerCustomerFilter != null ? cmbBuyerCustomerFilter.getValue() : "All Customers";
        final int ps = getBuyerPageSize();
        final int offset = (buyerCurrentPage - 1) * ps;

        Task<Void> task = new Task<>() {
            List<TopBuyerRow> rows;
            int               count;
            double            combinedSpent;

            @Override
            protected Void call() throws Exception {
                count = customerDao.countTopBuyersInRange(buyerRange[0], buyerRange[1], typeFilter);
                combinedSpent = customerDao.sumOrderTotalInBuyerFilterRange(buyerRange[0], buyerRange[1], typeFilter);
                rows = customerDao.findTopBuyersInRange(buyerRange[0], buyerRange[1], typeFilter, ps, offset);
                return null;
            }

            @Override
            protected void succeeded() {
                totalBuyerRows = count;
                int pages = Math.max(1, (int) Math.ceil((double) count / (double) ps));
                if (buyerCurrentPage > pages) {
                    buyerCurrentPage = pages;
                    loadTopBuyersTable();
                    return;
                }
                topBuyerItems.setAll(rows != null ? rows : List.of());
                if (lblBuyerCount != null)
                    lblBuyerCount.setText(count + " customers");
                if (lblTotalSpentSummary != null) {
                    lblTotalSpentSummary.setText("Combined total: " + CurrencyUtil.formatCurrency(combinedSpent));
                }
                updateBuyerPaginationUi();
                setBuyerTableHeight();
            }

            @Override
            protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    private void updateBuyerPaginationUi() {
        int ps = getBuyerPageSize();
        int pages = Math.max(1, (int) Math.ceil((double) totalBuyerRows / ps));
        if (lblBuyerPageInfo != null)
            lblBuyerPageInfo.setText("Page " + buyerCurrentPage + " of " + pages);
        if (btnBuyerPrevPage != null) btnBuyerPrevPage.setDisable(buyerCurrentPage <= 1);
        if (btnBuyerNextPage != null) btnBuyerNextPage.setDisable(buyerCurrentPage >= pages);
    }

    private int getBuyerPageSize() {
        String v = cmbBuyerRowsPerPage != null ? cmbBuyerRowsPerPage.getValue() : "10";
        try { return Integer.parseInt(v != null ? v.trim() : "10"); }
        catch (NumberFormatException e) { return 10; }
    }

    private void setBuyerTableHeight() {
        if (tblTopBuyers == null) return;
        double h = 38 + (double) getBuyerPageSize() * 44.0;
        tblTopBuyers.setPrefHeight(h);
        tblTopBuyers.setMinHeight(h);
    }

    // ── Bar chart (monthly order volume — companies vs individuals) ───────

    private void buildBarChart(List<CustomerDao.MonthlySplit> monthly) {
        if (chartFrequency == null) return;
        chartFrequency.getData().clear();

        XYChart.Series<String, Number> companies = new XYChart.Series<>();
        companies.setName("Companies");
        XYChart.Series<String, Number> individuals = new XYChart.Series<>();
        individuals.setName("Individuals");

        for (CustomerDao.MonthlySplit m : monthly) {
            String label = formatChartMonthLabel(m.month);
            companies.getData().add(new XYChart.Data<>(label, m.companyCount));
            individuals.getData().add(new XYChart.Data<>(label, m.individualCount));
        }
        chartFrequency.getData().add(companies);
        chartFrequency.getData().add(individuals);

        javafx.application.Platform.runLater(() -> {
            configureInsightsChart();
            chartFrequency.lookupAll(".default-color0.chart-bar")
                .forEach(n -> n.setStyle("-fx-bar-fill: #1E40AF;"));
            chartFrequency.lookupAll(".default-color1.chart-bar")
                .forEach(n -> n.setStyle("-fx-bar-fill: #64748B;"));
            chartFrequency.requestLayout();
        });
    }

    /** Compact month label for category axis (e.g. {@code 2024-03} → {@code Mar 2024}). */
    private static String formatChartMonthLabel(String yyyyMm) {
        if (yyyyMm == null || yyyyMm.isBlank()) return "";
        String key = yyyyMm.trim();
        if (key.length() == 7 && key.charAt(4) == '-') {
            try {
                return LocalDate.parse(key + "-01")
                    .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
            } catch (Exception ignored) {
                return key;
            }
        }
        return key;
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
    //  EXPORT
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
            if ("csv".equals(format)) {
                ExportService.exportRowsToCSV(buildExportData(), file);
            } else {
                ExportService.exportMergedReport("Customer Insights - Top Buyers",
                    buildMergedCustomerInsightsExportData(), file);
            }
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

    /** Rich PDF: KPIs, monthly order bar charts, ranked buyers, refund and issue alerts. */
    private List<String[]> buildMergedCustomerInsightsExportData() throws Exception {
        LocalDate[] chartRange = resolveChartDateRange();
        LocalDate[] buyerRange = resolveBuyerDateRange();
        String buyerTypeFilter = cmbBuyerCustomerFilter != null ? cmbBuyerCustomerFilter.getValue() : "All Customers";

        int total = customerDao.getTotalCustomerCount();
        int companies = customerDao.getCompanyCustomerCount();
        double totalRevenue = customerDao.getTotalRevenue();
        double avgSpending = total > 0 ? totalRevenue / total : 0;
        List<CustomerDao.MonthlySplit> monthlySplit =
            customerDao.findMonthlyOrderCountsByCustomerType(chartRange[0], chartRange[1]);
        int totalOrders = monthlySplit.stream().mapToInt(m -> m.companyCount + m.individualCount).sum();
        int monthBuckets = Math.max(1, monthlySplit.size());
        double avgFrequency = (total > 0 && totalOrders > 0)
            ? (double) totalOrders / monthBuckets / total : 0;

        List<TopBuyerRow> topBuyers = customerDao.findTopBuyersInRange(
            buyerRange[0], buyerRange[1], buyerTypeFilter, 10_000, 0);

        List<String> refundAlerts = customerDao.findRefundAlerts();
        List<String> issueAlerts = customerDao.findProductIssueAlerts();

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__",
            "Customer Insights",
            "Top buyers, trends, and alerts",
            date});
        rows.add(new String[]{"__SECTION__", "Key metrics", "Portfolio overview"});
        rows.add(new String[]{"__KPI__",
            "Total Customers", String.format("%,d", total),
            "Companies", String.format("%,d", companies),
            "Avg Spend / Cust", CurrencyUtil.formatCurrency(avgSpending),
            "Avg frequency", String.format("%.2f / mo", avgFrequency)});

        List<String> barCo = new ArrayList<>();
        barCo.add("__BARCHART__");
        barCo.add("Orders by month — Companies");
        for (CustomerDao.MonthlySplit m : monthlySplit) {
            barCo.add(m.month);
            barCo.add(String.valueOf(m.companyCount));
        }
        if (barCo.size() > 2) rows.add(barCo.toArray(new String[0]));

        List<String> barInd = new ArrayList<>();
        barInd.add("__BARCHART__");
        barInd.add("Orders by month — Individuals");
        for (CustomerDao.MonthlySplit m : monthlySplit) {
            barInd.add(m.month);
            barInd.add(String.valueOf(m.individualCount));
        }
        if (barInd.size() > 2) rows.add(barInd.toArray(new String[0]));

        rows.add(new String[]{"__SECTION__", "Top buyers", "Ranked by total spent (table filters)"});
        rows.add(new String[]{"__TABLEHEADER__",
            "Rank", "Customer", "Type", "Country", "Total Spent", "Orders", "AOV", "Last purchase"});
        for (TopBuyerRow r : topBuyers) {
            rows.add(new String[]{
                String.valueOf(r.getRank()),
                r.getName(),
                r.getType(),
                r.getCountry(),
                CurrencyUtil.formatCurrency(r.getTotalSpent()),
                String.valueOf(r.getTotalOrders()),
                CurrencyUtil.formatCurrency(r.getAvgOrderValue()),
                r.getLastPurchase() != null ? r.getLastPurchase() : ""
            });
        }

        rows.add(new String[]{"__SECTION__", "Refund alerts", "Customers with refunds"});
        rows.add(new String[]{"__TABLEHEADER__", "Detail"});
        if (refundAlerts.isEmpty()) {
            rows.add(new String[]{"No refund alerts"});
        } else {
            for (String a : refundAlerts) {
                rows.add(new String[]{a != null ? a : ""});
            }
        }

        rows.add(new String[]{"__SECTION__", "Product / order issues", "Follow-up items"});
        rows.add(new String[]{"__TABLEHEADER__", "Detail"});
        if (issueAlerts.isEmpty()) {
            rows.add(new String[]{"No issues"});
        } else {
            for (String a : issueAlerts) {
                rows.add(new String[]{a != null ? a : ""});
            }
        }

        return rows;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private LocalDate[] resolveChartDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = cmbChartDateRange != null ? cmbChartDateRange.getValue() : "Last 12 Months";
        if (val == null) val = "Last 12 Months";
        from = switch (val) {
            case "Custom" -> {
                LocalDate s = dpChartStart != null ? dpChartStart.getValue() : null;
                LocalDate e = dpChartEnd != null ? dpChartEnd.getValue() : null;
                if (e != null) to = e;
                yield s != null ? s : to.minusMonths(12);
            }
            case "Last 7 Days" -> to.minusDays(7);
            case "Last 30 Days" -> to.minusDays(30);
            case "Year to Date" -> to.withDayOfYear(1);
            default -> to.minusMonths(12);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    private LocalDate[] resolveBuyerDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = cmbBuyerDateRange != null ? cmbBuyerDateRange.getValue() : "Last 12 Months";
        if (val == null) val = "Last 12 Months";
        from = switch (val) {
            case "Custom" -> {
                LocalDate s = dpBuyerStart != null ? dpBuyerStart.getValue() : null;
                LocalDate e = dpBuyerEnd != null ? dpBuyerEnd.getValue() : null;
                if (e != null) to = e;
                yield s != null ? s : to.minusMonths(12);
            }
            case "Last 7 Days" -> to.minusDays(7);
            case "Last 30 Days" -> to.minusDays(30);
            case "Year to Date" -> to.withDayOfYear(1);
            default -> to.minusMonths(12);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    private void fadeLabel(Label lbl, String value) {
        if (lbl == null) return;
        lbl.setText(value);
        lbl.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(400), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
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
