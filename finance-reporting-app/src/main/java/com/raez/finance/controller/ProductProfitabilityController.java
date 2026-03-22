package com.raez.finance.controller;

import com.raez.finance.dao.ProductDao;
import com.raez.finance.dao.ProductDaoInterface;
import com.raez.finance.model.ProductReportRow;
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
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProductProfitabilityController {

    // ── Margin threshold ──────────────────────────────────────────────────
    private static final double LOW_MARGIN_THRESHOLD = 35.0;

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbCategoryFilter;
    @FXML private ComboBox<String> cmbDateRange;
    @FXML private VBox              boxProfitStartDate;
    @FXML private VBox              boxProfitEndDate;
    @FXML private DatePicker        dpProfitStart;
    @FXML private DatePicker        dpProfitEnd;

    @FXML private Label lblTotalRevenue;
    @FXML private Label lblRevenueSub;
    @FXML private Label lblTotalProfit;
    @FXML private Label lblProfitSub;
    @FXML private Label lblAvgMargin;
    @FXML private Label lblLowMarginCount;
    @FXML private Label lblLowMarginSub;
    @FXML private Label lblProductCount;
    @FXML private Label lblTableSummary;
    @FXML private ComboBox<String> cmbProductRowsPerPage;
    @FXML private Label            lblProductPageInfo;
    @FXML private Button           btnProductPrevPage;
    @FXML private Button           btnProductNextPage;

    @FXML private VBox                      boxChartProfitability;
    @FXML private BarChart<Number, String> chartProfitability;
    @FXML private HBox chartLegend;

    @FXML private TableView<ProductReportRow>           tblProducts;
    @FXML private TableColumn<ProductReportRow, String> colName;
    @FXML private TableColumn<ProductReportRow, String> colCategory;
    @FXML private TableColumn<ProductReportRow, Number> colRevenue;
    @FXML private TableColumn<ProductReportRow, Number> colCost;
    @FXML private TableColumn<ProductReportRow, Number> colProfit;
    @FXML private TableColumn<ProductReportRow, Number> colMargin;
    @FXML private TableColumn<ProductReportRow, Number> colUnits;
    @FXML private TableColumn<ProductReportRow, String> colTrend;

    @FXML private VBox  vboxHighPerformers;
    @FXML private Label lblHighCount;
    @FXML private Label lblNoHighPerformers;
    @FXML private VBox  vboxNeedsAttention;
    @FXML private Label lblLowCount;
    @FXML private Label lblNoNeedsAttention;

    @FXML private MenuButton exportMenuButton;

    // ── Services ──────────────────────────────────────────────────────────
    private final ProductDaoInterface productDao    = new ProductDao();
    private final ExecutorService     executor      = Executors.newSingleThreadExecutor();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();
    private final List<ProductReportRow> allProductsFiltered = new ArrayList<>();
    private int productCurrentPage = 1;
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

        bindColumns();
        tblProducts.setItems(productItems);
        tblProducts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblProducts);

        chartProfitability.getStyleClass().add("profitability-chart");
        buildChartLegend();
        configureProfitabilityChart();

        if (cmbProductRowsPerPage != null) {
            cmbProductRowsPerPage.setValue("10");
        }

        loadCategoryOptions();
        if (cmbDateRange != null) {
            cmbDateRange.setItems(FXCollections.observableArrayList(
                "Last 7 Days", "Last 30 Days", "Last 1 Year", "Year to Date", "All Time", "Custom"));
            cmbDateRange.setValue("All Time");
            cmbDateRange.valueProperty().addListener((obs, o, n) -> {
                boolean custom = "Custom".equals(n);
                if (boxProfitStartDate != null) {
                    boxProfitStartDate.setVisible(custom);
                    boxProfitStartDate.setManaged(custom);
                }
                if (boxProfitEndDate != null) {
                    boxProfitEndDate.setVisible(custom);
                    boxProfitEndDate.setManaged(custom);
                }
                loadData();
            });
        }
        if (dpProfitStart != null) dpProfitStart.valueProperty().addListener((o, a, b) -> loadData());
        if (dpProfitEnd != null) dpProfitEnd.valueProperty().addListener((o, a, b) -> loadData());
        cmbCategoryFilter.valueProperty().addListener((obs, o, n) -> loadData());
    }

    @FXML private void handleProductRowsPerPageChange(ActionEvent e) {
        productCurrentPage = 1;
        applyProductPageSlice();
    }

    @FXML private void handleProductPrevPage(ActionEvent e) {
        if (productCurrentPage > 1) {
            productCurrentPage--;
            applyProductPageSlice();
        }
    }

    @FXML private void handleProductNextPage(ActionEvent e) {
        int ps = getProductPageSize();
        int pages = Math.max(1, (int) Math.ceil((double) allProductsFiltered.size() / ps));
        if (productCurrentPage < pages) {
            productCurrentPage++;
            applyProductPageSlice();
        }
    }

    /** Horizontal bar chart: categories on Y-axis, values on X-axis. */
    private void configureProfitabilityChart() {
        if (chartProfitability == null) return;
        chartProfitability.setAnimated(false);
        if (chartProfitability.getYAxis() instanceof CategoryAxis cy) {
            cy.setGapStartAndEnd(true);
            cy.setTickLabelGap(6);
            cy.setAnimated(false);
        }
        if (chartProfitability.getXAxis() instanceof NumberAxis nx) {
            nx.setAnimated(false);
            nx.setForceZeroInRange(true);
            nx.setMinorTickVisible(false);
            nx.setAutoRanging(true);
        }
    }

    private LocalDate[] resolveProfitDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = cmbDateRange != null ? cmbDateRange.getValue() : "All Time";
        if (val == null) val = "All Time";
        from = switch (val) {
            case "Custom" -> {
                LocalDate s = dpProfitStart != null ? dpProfitStart.getValue() : null;
                LocalDate e = dpProfitEnd != null ? dpProfitEnd.getValue() : null;
                if (e != null) to = e;
                yield s != null ? s : to.minusYears(1);
            }
            case "Last 7 Days" -> to.minusDays(7);
            case "Last 30 Days" -> to.minusDays(30);
            case "Last 1 Year" -> to.minusYears(1);
            case "Year to Date" -> to.withDayOfYear(1);
            case "All Time" -> null;
            default -> null;
        };
        if (from != null && from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindColumns() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        colRevenue.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        colRevenue.setCellFactory(CurrencyUtil.currencyCellFactory());

        colCost.setCellValueFactory(new PropertyValueFactory<>("cost"));
        colCost.setCellFactory(CurrencyUtil.currencyCellFactory());

        colProfit.setCellValueFactory(new PropertyValueFactory<>("profit"));
        colProfit.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                getStyleClass().removeAll("table-profit-positive", "table-profit-negative");
                if (empty || v == null) return;
                setText(CurrencyUtil.formatCurrency(v.doubleValue()));
                getStyleClass().add(v.doubleValue() >= 0 ? "table-profit-positive" : "table-profit-negative");
            }
        });

        colMargin.setCellValueFactory(new PropertyValueFactory<>("marginPercent"));
        colMargin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(null); setText(null);
                if (empty || v == null) return;
                double pct = v.doubleValue();
                Label badge = new Label(String.format("%.1f%%", pct));
                if (pct >= LOW_MARGIN_THRESHOLD)
                    badge.getStyleClass().add("status-badge-paid");
                else if (pct >= 20)
                    badge.getStyleClass().add("status-badge-warning");
                else
                    badge.getStyleClass().add("status-badge-danger");
                HBox w = new HBox(badge);
                w.setAlignment(Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });

        colUnits.setCellValueFactory(new PropertyValueFactory<>("unitsSold"));

        colTrend.setCellValueFactory(new PropertyValueFactory<>("trend"));
        colTrend.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(null); setText(null);
                if (empty || v == null) return;
                String arrow;
                String colour;
                if (v.equalsIgnoreCase("up") || v.contains("▲") || v.contains("+")) {
                    arrow = "▲ Up"; colour = "#16A34A";
                } else if (v.equalsIgnoreCase("down") || v.contains("▼") || v.contains("-")) {
                    arrow = "▼ Down"; colour = "#DC2626";
                } else {
                    arrow = "– Flat"; colour = "#6B7280";
                }
                Label lbl = new Label(arrow);
                lbl.getStyleClass().add("table-trend-label");
                lbl.setTextFill(javafx.scene.paint.Paint.valueOf(colour));
                setGraphic(lbl);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadCategoryOptions() {
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception {
                return productDao.findCategoryNames();
            }
        };
        task.setOnSucceeded(e -> {
            ObservableList<String> options = FXCollections.observableArrayList("All Categories");
            if (task.getValue() != null) options.addAll(task.getValue());
            cmbCategoryFilter.setItems(options);
            cmbCategoryFilter.setValue("All Categories");
        });
        executor.execute(task);
    }

    private void loadData() {
        productCurrentPage = 1;
        String categoryFilter = cmbCategoryFilter != null ? cmbCategoryFilter.getValue() : null;
        final LocalDate[] range = resolveProfitDateRange();

        Task<Void> task = new Task<>() {
            List<ProductReportRow>                   products;
            List<ProductDao.CategoryRevenueProfit>   catData;
            double totalRevenue, totalProfit, avgMargin;
            long   lowCount;

            @Override protected Void call() throws Exception {
                products     = productDao.findReportRows(range[0], range[1], categoryFilter, null);
                List<ProductDao.CategoryRevenueProfit> raw =
                    productDao.findCategoryRevenueProfit(range[0], range[1]);
                if (categoryFilter != null && !"All Categories".equalsIgnoreCase(categoryFilter.trim())) {
                    String cf = categoryFilter.trim();
                    catData = raw.stream()
                        .filter(c -> cf.equalsIgnoreCase(c.category))
                        .collect(Collectors.toList());
                } else {
                    catData = raw;
                }
                totalRevenue = products.stream().mapToDouble(ProductReportRow::getRevenue).sum();
                totalProfit  = products.stream().mapToDouble(ProductReportRow::getProfit).sum();
                avgMargin    = products.isEmpty() ? 0
                    : products.stream().mapToDouble(ProductReportRow::getMarginPercent).average().orElse(0);
                lowCount = products.stream()
                    .filter(p -> p.getMarginPercent() < LOW_MARGIN_THRESHOLD).count();
                return null;
            }

            @Override protected void succeeded() {
                // KPI labels
                animateLabel(lblTotalRevenue,    CurrencyUtil.formatCurrency(totalRevenue));
                animateLabel(lblTotalProfit,     CurrencyUtil.formatCurrency(totalProfit));
                animateLabel(lblAvgMargin,       String.format("%.1f%%", avgMargin));
                animateLabel(lblLowMarginCount,  String.valueOf(lowCount));

                if (lblRevenueSub  != null) lblRevenueSub.setText(products.size() + " products");
                if (lblProfitSub   != null) lblProfitSub.setText(String.format("%.1f%% margin overall", avgMargin));
                if (lblLowMarginSub != null)
                    lblLowMarginSub.setText("Below " + (int) LOW_MARGIN_THRESHOLD + "% threshold");

                // Table — full filtered list cached; visible rows paginated
                allProductsFiltered.clear();
                allProductsFiltered.addAll(products);
                if (lblProductCount != null) lblProductCount.setText(products.size() + " products");
                if (lblTableSummary != null)
                    lblTableSummary.setText(String.format(
                        "Total: %s revenue · %s profit",
                        CurrencyUtil.formatCurrency(totalRevenue),
                        CurrencyUtil.formatCurrency(totalProfit)));
                applyProductPageSlice();

                // Horizontal bar chart — one row per category on Y-axis (no overlapping X labels)
                buildCategoryProfitabilityChart(catData);

                // Performers
                List<ProductReportRow> high = products.stream()
                    .filter(p -> p.getMarginPercent() >= LOW_MARGIN_THRESHOLD).toList();
                List<ProductReportRow> low  = products.stream()
                    .filter(p -> p.getMarginPercent() < LOW_MARGIN_THRESHOLD && p.getRevenue() > 0).toList();

                buildPerformerList(vboxHighPerformers, lblNoHighPerformers, lblHighCount,
                    high, "#166534", "#ECFDF5");
                buildPerformerList(vboxNeedsAttention, lblNoNeedsAttention, lblLowCount,
                    low,  "#92400E", "#FFF7ED");
            }

            @Override protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    /**
     * Horizontal bars: category on Y-axis (readable for long names), value on X-axis.
     * Categories are ordered with highest revenue toward the top of the chart.
     */
    private void buildCategoryProfitabilityChart(List<ProductDao.CategoryRevenueProfit> catData) {
        if (chartProfitability == null) return;
        chartProfitability.getData().clear();
        if (catData == null || catData.isEmpty()) {
            chartProfitability.setMinHeight(200);
            chartProfitability.setPrefHeight(200);
            return;
        }

        List<ProductDao.CategoryRevenueProfit> ordered = new ArrayList<>(catData);
        // Query returns revenue DESC; axis lists first category at bottom — reverse so largest is at top
        Collections.reverse(ordered);

        XYChart.Series<Number, String> revSeries = new XYChart.Series<>();
        revSeries.setName("Revenue");
        XYChart.Series<Number, String> profSeries = new XYChart.Series<>();
        profSeries.setName("Profit");
        for (ProductDao.CategoryRevenueProfit c : ordered) {
            String catLabel = categoryAxisLabel(c.category);
            revSeries.getData().add(new XYChart.Data<>(c.revenue, catLabel));
            profSeries.getData().add(new XYChart.Data<>(c.profit, catLabel));
        }
        chartProfitability.getData().add(revSeries);
        chartProfitability.getData().add(profSeries);

        double rowH = 46.0;
        double chartH = Math.max(220, 80 + ordered.size() * rowH);
        chartProfitability.setMinHeight(chartH);
        chartProfitability.setPrefHeight(chartH);

        javafx.application.Platform.runLater(() -> {
            configureProfitabilityChart();
            chartProfitability.lookupAll(".default-color0.chart-bar")
                .forEach(n -> n.setStyle("-fx-bar-fill: #1E2939;"));
            chartProfitability.lookupAll(".default-color1.chart-bar")
                .forEach(n -> n.setStyle("-fx-bar-fill: #10B981;"));
            chartProfitability.requestLayout();
        });
    }

    private static String categoryAxisLabel(String raw) {
        if (raw == null || raw.isBlank()) return "Uncategorized";
        return raw.trim();
    }

    private void applyProductPageSlice() {
        int ps = getProductPageSize();
        int total = allProductsFiltered.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / Math.max(1, ps)));
        if (productCurrentPage > pages) productCurrentPage = pages;
        if (productCurrentPage < 1) productCurrentPage = 1;
        int from = (productCurrentPage - 1) * ps;
        if (from >= total || total == 0) {
            productItems.setAll(List.of());
        } else {
            int to = Math.min(from + ps, total);
            productItems.setAll(allProductsFiltered.subList(from, to));
        }
        updateProductPaginationUi();
        setProductTableHeight();
    }

    private void updateProductPaginationUi() {
        int ps = getProductPageSize();
        int total = allProductsFiltered.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / Math.max(1, ps)));
        if (lblProductPageInfo != null)
            lblProductPageInfo.setText("Page " + productCurrentPage + " of " + pages);
        if (btnProductPrevPage != null) btnProductPrevPage.setDisable(productCurrentPage <= 1);
        if (btnProductNextPage != null)
            btnProductNextPage.setDisable(productCurrentPage >= pages || total == 0);
    }

    private int getProductPageSize() {
        String v = cmbProductRowsPerPage != null ? cmbProductRowsPerPage.getValue() : "10";
        try { return Integer.parseInt(v != null ? v.trim() : "10"); }
        catch (NumberFormatException e) { return 10; }
    }

    private void setProductTableHeight() {
        if (tblProducts == null) return;
        int rows = Math.max(productItems.size(), 1);
        double h = 38 + rows * 44.0;
        tblProducts.setPrefHeight(h);
        tblProducts.setMinHeight(h);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT  — fixed: uses exportRowsToCSV / exportRowsToPDF with data list
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleExportCSV(ActionEvent e) { doExport("csv"); }
    @FXML private void handleExportPDF(ActionEvent e) { doExport("pdf"); }

    private void doExport(String format) {
        if (!SessionManager.isAdmin()) return;
        Window window = tblProducts.getScene() != null ? tblProducts.getScene().getWindow() : null;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Product Profitability");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "csv".equals(format) ? "CSV Files" : "PDF Files",
            "csv".equals(format) ? "*.csv"     : "*.pdf"));
        fc.setInitialFileName("product_profitability." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            if ("csv".equals(format)) {
                ExportService.exportRowsToCSV(buildExportData(), file);
            } else {
                ExportService.exportMergedReport("Product Profitability Report",
                    buildMergedProductProfitabilityExportData(), file);
            }
            toast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " +
                (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
        }
    }

    /**
     * Builds a List<String[]> from the current table items.
     * ExportService.exportRowsToCSV / exportRowsToPDF require this format —
     * never pass a TableView directly (exportToCSV(TableView) does not exist).
     */
    private List<String[]> buildExportData() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{
            "Product", "Category", "Revenue", "Cost", "Profit", "Margin %", "Units Sold", "Trend"
        });
        for (ProductReportRow p : allProductsFiltered) {
            rows.add(new String[]{
                p.getName(),
                p.getCategory(),
                CurrencyUtil.formatCurrency(p.getRevenue()),
                CurrencyUtil.formatCurrency(p.getCost()),
                CurrencyUtil.formatCurrency(p.getProfit()),
                String.format("%.1f%%", p.getMarginPercent()),
                String.valueOf(p.getUnitsSold()),
                p.getTrend() != null ? p.getTrend() : "—"
            });
        }
        return rows;
    }

    /** Rich PDF aligned with on-screen KPIs, category bars, performer lists, and product table. */
    private List<String[]> buildMergedProductProfitabilityExportData() throws Exception {
        String categoryFilter = cmbCategoryFilter != null ? cmbCategoryFilter.getValue() : null;
        LocalDate[] range = resolveProfitDateRange();
        List<ProductReportRow> products =
            productDao.findReportRows(range[0], range[1], categoryFilter, null);
        List<ProductDao.CategoryRevenueProfit> raw =
            productDao.findCategoryRevenueProfit(range[0], range[1]);
        List<ProductDao.CategoryRevenueProfit> catData;
        if (categoryFilter != null && !"All Categories".equalsIgnoreCase(categoryFilter.trim())) {
            String cf = categoryFilter.trim();
            catData = raw.stream()
                .filter(c -> cf.equalsIgnoreCase(c.category))
                .collect(Collectors.toList());
        } else {
            catData = raw;
        }

        double totalRevenue = products.stream().mapToDouble(ProductReportRow::getRevenue).sum();
        double totalProfit = products.stream().mapToDouble(ProductReportRow::getProfit).sum();
        double avgMargin = products.isEmpty() ? 0
            : products.stream().mapToDouble(ProductReportRow::getMarginPercent).average().orElse(0);
        long lowCount = products.stream()
            .filter(p -> p.getMarginPercent() < LOW_MARGIN_THRESHOLD).count();

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__",
            "Product Profitability Report",
            "Margins, categories, and rankings",
            date});
        rows.add(new String[]{"__SECTION__", "Key metrics", "Current filters"});
        rows.add(new String[]{"__KPI__",
            "Total Revenue", CurrencyUtil.formatCurrency(totalRevenue),
            "Total Profit", CurrencyUtil.formatCurrency(totalProfit),
            "Avg Margin", String.format("%.1f%%", avgMargin),
            "Low margin count", String.valueOf(lowCount)});
        rows.add(new String[]{"__KPI__",
            "Products", String.valueOf(products.size()),
            "Category", categoryFilter != null ? categoryFilter : "All",
            "Period", formatProfitRangeLabel(range)});

        if (!catData.isEmpty()) {
            rows.add(new String[]{"__SECTION__", "Revenue by category", "Bar chart"});
            List<String> revBar = new ArrayList<>();
            revBar.add("__BARCHART__");
            revBar.add("Revenue by category");
            for (ProductDao.CategoryRevenueProfit c : catData) {
                revBar.add(c.category);
                revBar.add(String.valueOf(c.revenue));
            }
            rows.add(revBar.toArray(new String[0]));

            rows.add(new String[]{"__SECTION__", "Profit by category", "Bar chart"});
            List<String> profBar = new ArrayList<>();
            profBar.add("__BARCHART__");
            profBar.add("Profit by category");
            for (ProductDao.CategoryRevenueProfit c : catData) {
                profBar.add(c.category);
                profBar.add(String.valueOf(c.profit));
            }
            rows.add(profBar.toArray(new String[0]));
        }

        List<ProductReportRow> high = products.stream()
            .filter(p -> p.getMarginPercent() >= LOW_MARGIN_THRESHOLD).toList();
        List<ProductReportRow> low = products.stream()
            .filter(p -> p.getMarginPercent() < LOW_MARGIN_THRESHOLD && p.getRevenue() > 0).toList();

        rows.add(new String[]{"__SECTION__",
            "Strong margin (>= " + (int) LOW_MARGIN_THRESHOLD + "%)",
            "Top performers"});
        rows.add(new String[]{"__TABLEHEADER__",
            "Product", "Margin %", "Revenue", "Profit"});
        if (high.isEmpty()) {
            rows.add(new String[]{"None", "", "", ""});
        } else {
            for (ProductReportRow p : high) {
                rows.add(new String[]{
                    p.getName(),
                    String.format("%.1f%%", p.getMarginPercent()),
                    CurrencyUtil.formatCurrency(p.getRevenue()),
                    CurrencyUtil.formatCurrency(p.getProfit())
                });
            }
        }

        rows.add(new String[]{"__SECTION__",
            "Needs attention (below " + (int) LOW_MARGIN_THRESHOLD + "%)",
            "Review these SKUs"});
        rows.add(new String[]{"__TABLEHEADER__",
            "Product", "Margin %", "Revenue", "Profit"});
        if (low.isEmpty()) {
            rows.add(new String[]{"None", "", "", ""});
        } else {
            for (ProductReportRow p : low) {
                rows.add(new String[]{
                    p.getName(),
                    String.format("%.1f%%", p.getMarginPercent()),
                    CurrencyUtil.formatCurrency(p.getRevenue()),
                    CurrencyUtil.formatCurrency(p.getProfit())
                });
            }
        }

        rows.add(new String[]{"__SECTION__", "All products", "Full listing"});
        rows.add(new String[]{"__TABLEHEADER__",
            "Product", "Category", "Revenue", "Cost", "Profit", "Margin %", "Units", "Trend"});
        for (ProductReportRow p : products) {
            rows.add(new String[]{
                p.getName(),
                p.getCategory(),
                CurrencyUtil.formatCurrency(p.getRevenue()),
                CurrencyUtil.formatCurrency(p.getCost()),
                CurrencyUtil.formatCurrency(p.getProfit()),
                String.format("%.1f%%", p.getMarginPercent()),
                String.valueOf(p.getUnitsSold()),
                asciiTrendForExport(p)
            });
        }
        return rows;
    }

    private static String formatProfitRangeLabel(LocalDate[] range) {
        if (range[0] == null) return "All time";
        return range[0].format(DateTimeFormatter.ISO_LOCAL_DATE)
            + " to " + range[1].format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static String asciiTrendForExport(ProductReportRow p) {
        String t = p.getTrend();
        if (t == null) return "-";
        String u = t.toLowerCase();
        if (u.contains("up") || t.contains("+")) return "Up";
        if (u.contains("down") || t.contains("-")) return "Down";
        return "Flat";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void buildPerformerList(VBox container, Label emptyLabel, Label countLabel,
                                    List<ProductReportRow> rows,
                                    String textColor, String hoverBg) {
        container.getChildren().clear();
        if (rows.isEmpty()) {
            if (emptyLabel != null) { emptyLabel.setManaged(true);  emptyLabel.setVisible(true);  }
            if (countLabel != null) countLabel.setText("0");
            return;
        }
        if (emptyLabel != null) { emptyLabel.setManaged(false); emptyLabel.setVisible(false); }
        if (countLabel != null) countLabel.setText(String.valueOf(rows.size()));

        for (ProductReportRow p : rows) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 10 20 10 20; -fx-cursor: hand;");

            Label name = new Label(p.getName());
            name.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: " + textColor + ";");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);

            Label margin = new Label(String.format("%.1f%%", p.getMarginPercent()));
            margin.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: " + textColor + ";");

            Label rev = new Label(CurrencyUtil.formatCurrency(p.getRevenue()));
            rev.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF;");

            row.getChildren().addAll(name, rev, margin);
            row.setOnMouseEntered(e -> row.setStyle(
                "-fx-padding: 10 20 10 20; -fx-cursor: hand; -fx-background-color: " + hoverBg + ";"));
            row.setOnMouseExited(e  -> row.setStyle("-fx-padding: 10 20 10 20; -fx-cursor: hand;"));

            if (!container.getChildren().isEmpty()) {
                Separator sep = new Separator();
                sep.setStyle("-fx-padding: 0 20 0 20; -fx-background-color: transparent;");
                container.getChildren().add(sep);
            }
            container.getChildren().add(row);
        }
    }

    private void buildChartLegend() {
        if (chartLegend == null) return;
        chartLegend.getChildren().clear();
        String[][] entries = {{"#1E2939", "Revenue"}, {"#10B981", "Profit"}};
        for (String[] e : entries) {
            Rectangle dot = new Rectangle(10, 10);
            dot.setFill(Color.web(e[0]));
            dot.setArcWidth(3); dot.setArcHeight(3);
            Label lbl = new Label(e[1]);
            lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
            HBox item = new HBox(6, dot, lbl);
            item.setAlignment(Pos.CENTER);
            chartLegend.getChildren().add(item);
        }
    }

    private void animateLabel(Label lbl, String value) {
        if (lbl == null) return;
        lbl.setOpacity(0);
        lbl.setText(value);
        FadeTransition ft = new FadeTransition(Duration.millis(400), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;");
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) row.setStyle(row.getIndex() % 2 == 0
                    ? "-fx-background-color: white;" : "-fx-background-color: #F9FAFB;");
            });
            return row;
        });
    }

    private void toast(String type, String msg) {
        if (mainLayoutController != null) mainLayoutController.showToast(type, msg);
        else new Alert(
            "success".equals(type) ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
            msg).showAndWait();
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException ex) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}