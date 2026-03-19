package com.raez.finance.controller;

import com.raez.finance.service.DashboardService;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.service.GlobalSettingsService;
import com.raez.finance.service.MockDataProvider;
import com.raez.finance.util.CurrencyUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Revenue Analysis & VAT Summary
 *
 * New vs original:
 *  - Added VAT breakdown table (tableVatBreakdown) with per-category rows
 *  - Added lblMargin, lblTotalOrders, lblAvgOrder KPI labels
 *  - Added lblFooterGross, lblFooterVat, lblFooterNet summary footer
 *  - Added ExportService wiring (CSV + PDF) with FileChooser
 *  - Applied CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS to table
 *  - Row-hover factory applied to table
 *  - KPI labels animate in with FadeTransition on each data refresh
 *  - Runs all IO on background executor; all UI updates on FX thread
 */
public class RevenueVatSummaryController {

    // ── FXML injections ────────────────────────────────────────────────────
    @FXML private ScrollPane pageScrollPane;

    @FXML private ComboBox<String> cmbDateRange;
    @FXML private VBox             boxStartDate;
    @FXML private VBox             boxEndDate;
    @FXML private DatePicker       dpStartDate;
    @FXML private DatePicker       dpEndDate;

    // KPI row 1
    @FXML private Label lblGrossRevenue;
    @FXML private Label lblNetRevenue;
    @FXML private Label lblVatCollected;
    @FXML private Label lblVatRate;

    // KPI row 2
    @FXML private Label lblMargin;
    @FXML private Label lblTotalOrders;
    @FXML private Label lblAvgOrder;

    // Table
    @FXML private TableView<VatRow>         tableVatBreakdown;
    @FXML private TableColumn<VatRow, String>  colVatCategory;
    @FXML private TableColumn<VatRow, Number>  colVatOrders;
    @FXML private TableColumn<VatRow, Number>  colVatGross;
    @FXML private TableColumn<VatRow, Number>  colVatAmount;
    @FXML private TableColumn<VatRow, Number>  colVatNet;
    @FXML private TableColumn<VatRow, Number>  colVatMargin;

    // Footer totals
    @FXML private Label lblFooterGross;
    @FXML private Label lblFooterVat;
    @FXML private Label lblFooterNet;

    // Export
    @FXML private MenuButton exportMenuButton;
    @FXML private MenuItem   exportCsvItem;
    @FXML private MenuItem   exportPdfItem;

    // ── Services / state ──────────────────────────────────────────────────
    private MainLayoutController mainLayoutController;
    private final DashboardService dashboardService = new DashboardService();
    private final ExecutorService  executor         = Executors.newSingleThreadExecutor();
    private final ObservableList<VatRow> vatItems   = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════
    //  MODEL CLASS
    // ══════════════════════════════════════════════════════════════════════

    public static class VatRow {
        private final String category;
        private final int    orders;
        private final double gross;
        private final double vat;
        private final double net;
        private final double margin;   // %

        public VatRow(String category, int orders, double gross, double vat, double cogs) {
            this.category = category;
            this.orders   = orders;
            this.gross    = gross;
            this.vat      = vat;
            this.net      = gross - vat;
            this.margin   = net > 0 && cogs >= 0 ? ((net - cogs) / net) * 100 : 0;
        }

        public String getCategory() { return category; }
        public int    getOrders()   { return orders; }
        public double getGross()    { return gross; }
        public double getVat()      { return vat; }
        public double getNet()      { return net; }
        public double getMargin()   { return margin; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    public void setMainLayoutController(MainLayoutController c) { this.mainLayoutController = c; }

    @FXML
    public void initialize() {
        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        // VAT rate label
        if (lblVatRate != null) {
            int rate = (int) Math.round(GlobalSettingsService.getInstance().getDefaultVatPercent());
            lblVatRate.setText("@ " + rate + "% VAT rate");
        }

        // Date range combo
        if (cmbDateRange != null) {
            cmbDateRange.getItems().setAll(
                "Last 7 days", "Last 30 days", "Last 90 days", "Last year", "Custom Range");
            cmbDateRange.setValue("Last 30 days");

            cmbDateRange.valueProperty().addListener((o, a, n) -> {
                boolean custom = "Custom Range".equals(n);
                setVisible(boxStartDate, custom);
                setVisible(boxEndDate,   custom);
                loadData();
            });
        }
        if (dpStartDate != null) dpStartDate.valueProperty().addListener((o, a, n) -> loadData());
        if (dpEndDate   != null) dpEndDate.valueProperty().addListener((o, a, n) -> loadData());

        // Table
        bindTableColumns();
        tableVatBreakdown.setItems(vatItems);
        tableVatBreakdown.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tableVatBreakdown);

        loadData();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindTableColumns() {
        colVatCategory.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getCategory()));
        colVatOrders.setCellValueFactory(d   -> new ReadOnlyObjectWrapper<Number>(d.getValue().getOrders()));
        colVatGross.setCellValueFactory(d    -> new ReadOnlyObjectWrapper<Number>(d.getValue().getGross()));
        colVatAmount.setCellValueFactory(d   -> new ReadOnlyObjectWrapper<Number>(d.getValue().getVat()));
        colVatNet.setCellValueFactory(d      -> new ReadOnlyObjectWrapper<Number>(d.getValue().getNet()));
        colVatMargin.setCellValueFactory(d   -> new ReadOnlyObjectWrapper<Number>(d.getValue().getMargin()));

        // Currency formatting
        for (TableColumn<VatRow, Number> col : List.of(colVatGross, colVatAmount, colVatNet)) {
            col.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(Number v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : CurrencyUtil.formatCurrency(v.doubleValue()));
                }
            });
        }

        // Margin % with colour
        colVatMargin.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(null); setText(null);
                if (empty || v == null) return;
                double pct = v.doubleValue();
                Label badge = new Label(String.format("%.1f%%", pct));
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                               "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                               (pct >= 40 ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                              : pct >= 20 ? "-fx-background-color: #FEF9C3; -fx-text-fill: #92400E;"
                              :             "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;"));
                HBox w = new HBox(badge);
                w.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadData() {
        LocalDate[] r = resolveRange();
        final LocalDate from = r[0];
        final LocalDate to   = r[1];

        Task<Void> task = new Task<>() {
            double gross, vat, net, cogs;
            int    totalOrders;
            List<VatRow> rows = new ArrayList<>();

            @Override protected Void call() {
                try {
                    gross       = dashboardService.getTotalSales(from, to, null);
                    vat         = dashboardService.getTotalVatCollected(from, to, null);
                    cogs        = dashboardService.getTotalCogs(from, to, null);
                    totalOrders = dashboardService.getTotalOrders(from, to, null);
                } catch (Exception e) {
                    MockDataProvider mock = MockDataProvider.getInstance();
                    gross       = mock.getTotalSales(from, to, null);
                    vat         = mock.getTotalVatCollected(from, to, null);
                    cogs        = mock.getCogs(from, to, null);
                    totalOrders = mock.getTotalOrders(from, to, null);
                }
                net = gross - vat;

                // Per-category breakdown from mock
                MockDataProvider mock = MockDataProvider.getInstance();
                for (String cat : List.of("Drones", "Robots", "Services", "Accessories")) {
                    double g = mock.getTotalSales(from, to, cat);
                    double v = mock.getTotalVatCollected(from, to, cat);
                    double cg = mock.getCogs(from, to, cat);
                    int    o = mock.getTotalOrders(from, to, cat);
                    if (g > 0) rows.add(new VatRow(cat, o, g, v, cg));
                }
                return null;
            }

            @Override protected void succeeded() {
                double margin     = net > 0 ? ((net - cogs) / net) * 100 : 0;
                double avgOrder   = totalOrders > 0 ? gross / totalOrders : 0;

                animateLabel(lblGrossRevenue, CurrencyUtil.formatCurrency(gross));
                animateLabel(lblNetRevenue,   CurrencyUtil.formatCurrency(net));
                animateLabel(lblVatCollected, CurrencyUtil.formatCurrency(vat));
                animateLabel(lblTotalOrders,  String.valueOf(totalOrders));
                animateLabel(lblAvgOrder,     CurrencyUtil.formatCurrency(avgOrder));

                if (lblMargin != null) {
                    lblMargin.setText(String.format("%.1f%%", margin));
                    lblMargin.setStyle("-fx-font-size: 24px; -fx-font-weight: 700; " +
                        (margin >= 30 ? "-fx-text-fill: #16A34A;" : "-fx-text-fill: #DC2626;"));
                }

                // TODO: replace with live DAO data once schema is finalised
                if (rows.isEmpty()) {
                    double vatRate = GlobalSettingsService.getInstance().getDefaultVatPercent() / 100.0;
                    rows.add(new VatRow("Drones",      18, 124500, 124500 * vatRate, 124500 * 0.55));
                    rows.add(new VatRow("Robots",      12,  89200,  89200 * vatRate,  89200 * 0.50));
                    rows.add(new VatRow("Services",     8,  34800,  34800 * vatRate,  34800 * 0.30));
                    rows.add(new VatRow("Accessories", 22,  18600,  18600 * vatRate,  18600 * 0.45));
                }
                vatItems.setAll(rows);

                // Footer totals
                double fGross = rows.stream().mapToDouble(VatRow::getGross).sum();
                double fVat   = rows.stream().mapToDouble(VatRow::getVat).sum();
                double fNet   = rows.stream().mapToDouble(VatRow::getNet).sum();
                if (lblFooterGross != null) lblFooterGross.setText(CurrencyUtil.formatCurrency(fGross));
                if (lblFooterVat   != null) lblFooterVat.setText(CurrencyUtil.formatCurrency(fVat));
                if (lblFooterNet   != null) lblFooterNet.setText(CurrencyUtil.formatCurrency(fNet));

                // Table height
                if (tableVatBreakdown != null) {
                    double h = 38 + Math.max(rows.size(), 3) * 44.0;
                    tableVatBreakdown.setPrefHeight(h);
                    tableVatBreakdown.setMinHeight(h);
                }
            }

            @Override protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    private LocalDate[] resolveRange() {
        LocalDate to   = LocalDate.now();
        LocalDate from;
        String val = cmbDateRange != null ? cmbDateRange.getValue() : null;
        if (val == null) val = "Last 30 days";
        from = switch (val) {
            case "Custom Range" -> {
                LocalDate s = dpStartDate != null ? dpStartDate.getValue() : null;
                LocalDate e = dpEndDate   != null ? dpEndDate.getValue()   : null;
                if (e != null) to = e;
                yield s != null ? s : to.minusDays(30);
            }
            case "Last 7 days"  -> to.minusDays(7);
            case "Last 90 days" -> to.minusDays(90);
            case "Last year"    -> to.minusYears(1);
            default             -> to.minusDays(30);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void animateLabel(Label lbl, String value) {
        if (lbl == null) return;
        lbl.setOpacity(0);
        lbl.setText(value);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(500), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void setVisible(VBox box, boolean v) {
        if (box == null) return;
        box.setVisible(v); box.setManaged(v);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void applyRowFactory(TableView table) {
        table.setRowFactory(tv -> {
            TableRow row = new TableRow() {
                @Override protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) setStyle("-fx-background-color: transparent;");
                    else setStyle(getIndex() % 2 == 0 ? "-fx-background-color: white;"
                                                       : "-fx-background-color: #F9FAFB;");
                }
            };
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;"); });
            row.setOnMouseExited(e  -> { if (!row.isEmpty()) row.setStyle(row.getIndex() % 2 == 0
                                          ? "-fx-background-color: white;" : "-fx-background-color: #F9FAFB;"); });
            return row;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleExportCsv() { doExport("csv"); }
    @FXML private void handleExportPdf() { doExport("pdf"); }

    private void doExport(String format) {
        javafx.stage.Window window = tableVatBreakdown != null && tableVatBreakdown.getScene() != null
            ? tableVatBreakdown.getScene().getWindow() : null;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Revenue & VAT Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "csv".equals(format) ? "CSV Files" : "PDF Files",
            "csv".equals(format) ? "*.csv"     : "*.pdf"));
        fc.setInitialFileName("revenue_vat_report." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;

        try {
            if ("csv".equals(format)) ExportService.exportToCSV(tableVatBreakdown, file);
            else ExportService.exportToPDF(tableVatBreakdown, "Revenue Analysis & VAT Summary", file);
            showToast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception e) {
            showToast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown"));
        }
    }

    private void showToast(String type, String msg) {
        if (mainLayoutController != null) mainLayoutController.showToast(type, msg);
        else new Alert("success".equals(type) ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, msg).showAndWait();
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}