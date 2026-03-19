package com.raez.finance.controller;

import com.raez.finance.dao.AlertDao;
import com.raez.finance.dao.FinancialAnomalyDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.MockDataProvider;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OverviewController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private static final String[] PIE_COLORS = {"#1E2939", "#10B981", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4"};

    private final DashboardService dashboardService = new DashboardService();
    private final ProductDao productDao = new ProductDao();
    private final AlertDao alertDao = new AlertDao();
    private final FinancialAnomalyDao financialAnomalyDao = new FinancialAnomalyDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dashboard-worker");
        t.setDaemon(true);
        return t;
    });

    private MainLayoutController mainLayoutController;
    private double lastRefundsForExport;

    public void setMainLayoutController(MainLayoutController mlc) { this.mainLayoutController = mlc; }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    @FXML private ComboBox<String> cmbDateRange;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private VBox boxStartDate;
    @FXML private VBox boxEndDate;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;
    @FXML private MenuButton btnExport;

    @FXML private Label lblTotalSales;
    @FXML private Label lblSalesGrowth;
    @FXML private Label lblTotalProfit;
    @FXML private Label lblOutstanding;
    @FXML private Label lblVatCollected;
    @FXML private Label lblRefunds;
    @FXML private Label lblCustomers;
    @FXML private Label lblOrders;
    @FXML private Label lblAOV;
    @FXML private Label lblPopular;

    @FXML private LineChart<String, Number> chartSales;
    @FXML private PieChart chartRevenue;
    @FXML private VBox vboxTopProducts;
    @FXML private VBox vboxAlerts;

    @FXML
    public void initialize() {
        if (btnExport != null && !SessionManager.isAdmin()) {
            btnExport.setVisible(false);
            btnExport.setManaged(false);
        }

        cmbDateRange.setItems(FXCollections.observableArrayList(
                "Last 7 days", "Last 30 days", "Last 90 days", "Last year", "Custom Range"));
        cmbDateRange.setValue("Last 30 days");

        loadCategoryOptions();
        cmbCategory.setValue("All Categories");

        if (chartSales != null) {
            chartSales.getStyleClass().add("dashboard-line-chart");
            chartSales.setAnimated(true);
            chartSales.setLegendVisible(false);
            if (chartSales.getXAxis() != null) chartSales.getXAxis().setTickLabelRotation(-45);
        }
        if (chartRevenue != null) {
            chartRevenue.getStyleClass().add("dashboard-pie-chart");
            chartRevenue.setAnimated(true);
            chartRevenue.setLegendVisible(false);
            chartRevenue.setLabelsVisible(false);
        }

        cmbDateRange.valueProperty().addListener((obs, o, n) -> {
            boolean isCustom = "Custom Range".equals(n);
            boxStartDate.setVisible(isCustom);
            boxStartDate.setManaged(isCustom);
            boxEndDate.setVisible(isCustom);
            boxEndDate.setManaged(isCustom);
            loadDashboardData();
        });
        cmbCategory.valueProperty().addListener((obs, o, n) -> loadDashboardData());
        dpStartDate.valueProperty().addListener((obs, o, n) -> loadDashboardData());
        dpEndDate.valueProperty().addListener((obs, o, n) -> loadDashboardData());

        loadDashboardData();
    }

    private void loadCategoryOptions() {
        Task<List<String>> task = new Task<>() {
            @Override protected List<String> call() throws Exception { return productDao.findCategoryNames(); }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                List<String> items = new ArrayList<>();
                items.add("All Categories");
                items.addAll(task.getValue());
                cmbCategory.setItems(FXCollections.observableList(items));
                cmbCategory.setValue("All Categories");
            }
        });
        executor.execute(task);
    }

    private void loadDashboardData() {
        String category = cmbCategory.getValue() != null ? cmbCategory.getValue() : "All Categories";
        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                LocalDate[] range = resolveDateRange();
                LocalDate from = range[0], to = range[1];

                double totalSales, totalProfit, outstanding, refundsVal, vatCollected, prevMonthSales;
                int customers, orders;
                double aov;
                String popular;
                List<DashboardService.DataPoint<String, Number>> timeSeries;
                List<DashboardService.DataPoint<String, Number>> categoryRevenue;
                List<DashboardService.TopProductRow> topProducts;
                List<String> lowStock, overdue;

                try {
                    totalSales = dashboardService.getTotalSales(from, to, category);
                    totalProfit = dashboardService.getTotalProfit(from, to, category);
                    outstanding = dashboardService.getOutstandingPayments(from, to, category);
                    vatCollected = dashboardService.getTotalVatCollected(from, to, category);
                    refundsVal = dashboardService.getRefunds(from, to, category);
                    customers = dashboardService.getTotalCustomers();
                    orders = dashboardService.getTotalOrders(from, to, category);
                    aov = dashboardService.getAverageOrderValue(from, to, category);
                    popular = dashboardService.getMostPopularProductName(from, to, category);
                    timeSeries = dashboardService.getSalesTimeSeries(from, to, category);
                    categoryRevenue = dashboardService.getCategoryRevenue(from, to, category);
                    topProducts = dashboardService.getTopProductsByQuantity(from, to, category, 3);
                    lowStock = dashboardService.getLowStockAlerts(100);
                    overdue = dashboardService.getOverduePaymentAlerts();

                    LocalDate prevFrom = from.minusMonths(1);
                    LocalDate prevTo = to.minusMonths(1);
                    prevMonthSales = dashboardService.getTotalSales(prevFrom, prevTo, category);
                } catch (Exception e) {
                    MockDataProvider mock = MockDataProvider.getInstance();
                    totalSales = mock.getTotalSales(from, to, category);
                    totalProfit = mock.getNetIncome(from, to, category);
                    outstanding = mock.getOutstandingPayments(from, to, category);
                    vatCollected = mock.getTotalVatCollected(from, to, category);
                    refundsVal = mock.getRefunds(from, to, category);
                    customers = mock.getCustomers().size();
                    orders = mock.getOrders(from, to, category).size();
                    aov = mock.getAverageOrderValue(from, to, category);
                    popular = mock.getProducts().isEmpty() ? "—" : mock.getProducts().getFirst().name;
                    timeSeries = new ArrayList<>();
                    for (Map.Entry<String, Number> entry : mock.getSalesTimeSeries(from, to, category)) {
                        timeSeries.add(new DashboardService.DataPoint<>(entry.getKey(), entry.getValue()));
                    }
                    categoryRevenue = new ArrayList<>();
                    if (totalSales > 0) categoryRevenue.add(new DashboardService.DataPoint<>("Sales", totalSales));
                    topProducts = new ArrayList<>();
                    lowStock = new ArrayList<>();
                    overdue = new ArrayList<>();
                    prevMonthSales = 0;
                }

                final double fTotalSales = totalSales, fTotalProfit = totalProfit, fOutstanding = outstanding;
                final double fVatCollected = vatCollected, fRefunds = refundsVal, fAov = aov, fPrev = prevMonthSales;
                final int fCustomers = customers, fOrders = orders;
                final String fPopular = popular;
                final var fTimeSeries = timeSeries;
                final var fCategoryRevenue = categoryRevenue;
                final var fTopProducts = topProducts;
                final var fLowStock = lowStock;
                final var fOverdue = overdue;

                List<AlertDao.AlertRow> dbAlerts;
                List<FinancialAnomalyDao.AnomalyRow> anomalies;
                try { dbAlerts = alertDao.findAlerts(true); } catch (Exception ignored) { dbAlerts = new ArrayList<>(); }
                try { anomalies = financialAnomalyDao.findAnomalies(true); } catch (Exception ignored) { anomalies = new ArrayList<>(); }
                final var fAlerts = dbAlerts;
                final var fAnomalies = anomalies;

                Platform.runLater(() -> {
                    animateKpi(lblTotalSales, fTotalSales, true);
                    animateKpi(lblTotalProfit, fTotalProfit, true);
                    animateKpi(lblOutstanding, fOutstanding, true);
                    if (lblVatCollected != null) animateKpi(lblVatCollected, fVatCollected, true);
                    lastRefundsForExport = fRefunds;
                    if (lblRefunds != null) animateKpi(lblRefunds, fRefunds, true);
                    animateKpi(lblCustomers, fCustomers, false);
                    animateKpi(lblOrders, fOrders, false);
                    animateKpi(lblAOV, fAov, true);
                    if (lblPopular != null) lblPopular.setText(fPopular != null ? fPopular : "—");

                    updateSalesGrowth(fTotalSales, fPrev);
                    buildLineChart(fTimeSeries);
                    buildPieChart(fCategoryRevenue);
                    buildTopProducts(fTopProducts);
                    buildAlerts(fLowStock, fOverdue, fAlerts, fAnomalies);
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((obs, o, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    private void animateKpi(Label label, double targetValue, boolean isCurrency) {
        if (label == null) return;
        Timeline timeline = new Timeline();
        final int frames = 20;
        for (int i = 0; i <= frames; i++) {
            final double frac = (double) i / frames;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(30.0 * i), e -> {
                double val = targetValue * frac;
                label.setText(isCurrency ? CurrencyUtil.formatCurrency(val) : String.format("%,d", (int) val));
            }));
        }
        timeline.play();
        FadeTransition fade = new FadeTransition(Duration.millis(400), label);
        fade.setFromValue(0.3);
        fade.setToValue(1);
        fade.play();
    }

    private void updateSalesGrowth(double current, double previous) {
        if (lblSalesGrowth == null) return;
        if (previous <= 0) {
            lblSalesGrowth.setText("—");
            lblSalesGrowth.setTextFill(Color.web("#6B7280"));
            return;
        }
        double pct = ((current - previous) / previous) * 100.0;
        boolean positive = pct >= 0;
        lblSalesGrowth.setText(String.format("%s%.1f%% vs last month", positive ? "+" : "", pct));
        lblSalesGrowth.setTextFill(Color.web(positive ? "#10B981" : "#EF4444"));
    }

    private void buildLineChart(List<DashboardService.DataPoint<String, Number>> timeSeries) {
        chartSales.getData().clear();

        CategoryAxis xAxis = (CategoryAxis) chartSales.getXAxis();
        List<String> categories = new ArrayList<>();
        Map<String, Number> dataMap = new LinkedHashMap<>();
        for (var p : timeSeries) {
            dataMap.put(p.x, p.y);
            categories.add(p.x);
        }

        if (categories.isEmpty()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy");
            YearMonth ym = YearMonth.now().minusMonths(11);
            for (int i = 0; i < 12; i++) {
                String label = ym.format(fmt);
                categories.add(label);
                dataMap.put(label, 0);
                ym = ym.plusMonths(1);
            }
        }

        xAxis.setCategories(FXCollections.observableArrayList(categories));
        xAxis.setAutoRanging(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");
        for (String cat : categories) {
            Number val = dataMap.getOrDefault(cat, 0);
            XYChart.Data<String, Number> data = new XYChart.Data<>(cat, val);
            series.getData().add(data);
        }
        chartSales.getData().add(series);

        for (XYChart.Data<String, Number> d : series.getData()) {
            d.nodeProperty().addListener((obs, oldN, newN) -> {
                if (newN != null) {
                    Tooltip tp = new Tooltip(d.getXValue() + "\n" + CurrencyUtil.formatCurrency(d.getYValue().doubleValue()));
                    tp.setStyle("-fx-background-color: white; -fx-text-fill: #111827; -fx-border-color: #E5E7EB; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 12;");
                    Tooltip.install(newN, tp);
                }
            });
        }

        if (chartSales.getYAxis() instanceof NumberAxis yAxis) {
            double max = timeSeries.stream().mapToDouble(p -> p.y.doubleValue()).max().orElse(100);
            double upper = max <= 0 ? 80000 : Math.ceil(max / 20000) * 20000;
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(upper);
            yAxis.setTickUnit(upper / 4);
            yAxis.setMinorTickCount(0);
            String sym = CurrencyUtil.formatCurrency(0).replaceAll("[0-9.,\\s]", "").trim();
            if (sym.isEmpty()) sym = "£";
            final String fSym = sym;
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            yAxis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
                @Override public String toString(Number n) {
                    if (n == null) return "";
                    return fSym + nf.format(n.longValue() / 1000) + "k";
                }
                @Override public Number fromString(String s) { return 0; }
            });
        }
    }

    private void buildPieChart(List<DashboardService.DataPoint<String, Number>> categoryRevenue) {
        chartRevenue.getData().clear();

        double total = categoryRevenue.stream().mapToDouble(p -> p.y.doubleValue()).sum();
        List<DashboardService.DataPoint<String, Number>> merged = new ArrayList<>();
        double otherVal = 0;

        for (var p : categoryRevenue) {
            double pct = total > 0 ? (p.y.doubleValue() / total) * 100.0 : 0;
            if (pct < 3) {
                otherVal += p.y.doubleValue();
            } else {
                merged.add(p);
            }
        }
        if (otherVal > 0) merged.add(new DashboardService.DataPoint<>("Other", otherVal));

        for (var p : merged) {
            double val = p.y.doubleValue();
            int pct = total > 0 ? (int) Math.round(100.0 * val / total) : 0;
            chartRevenue.getData().add(new PieChart.Data(p.x + " " + pct + "%", val));
        }

        for (int i = 0; i < chartRevenue.getData().size(); i++) {
            PieChart.Data slice = chartRevenue.getData().get(i);
            String color = PIE_COLORS[i % PIE_COLORS.length];
            if (slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
            slice.nodeProperty().addListener((obs, oldN, newN) -> {
                if (newN != null) {
                    Tooltip tp = new Tooltip(slice.getName() + "\n" + CurrencyUtil.formatCurrency(slice.getPieValue()));
                    tp.setStyle("-fx-background-color: white; -fx-text-fill: #111827; -fx-border-color: #E5E7EB; -fx-border-radius: 6; -fx-background-radius: 6;");
                    Tooltip.install(newN, tp);
                    newN.setOnMouseEntered(e -> newN.setScaleX(1.05));
                    newN.setOnMouseExited(e -> newN.setScaleX(1.0));
                }
            });
        }

        buildCustomPieLegend(merged, total);
    }

    private void buildCustomPieLegend(List<DashboardService.DataPoint<String, Number>> items, double total) {
        Node parent = chartRevenue.getParent();
        if (!(parent instanceof VBox container)) return;
        container.getChildren().removeIf(n -> "pie-legend".equals(n.getId()));

        HBox legend = new HBox(16);
        legend.setId("pie-legend");
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(8, 0, 0, 0));

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            String color = PIE_COLORS[i % PIE_COLORS.length];
            Circle dot = new Circle(5, Color.web(color));
            int pct = total > 0 ? (int) Math.round(100.0 * item.y.doubleValue() / total) : 0;
            Label lbl = new Label(item.x + " (" + pct + "%)");
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
            HBox entry = new HBox(6, dot, lbl);
            entry.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(entry);
        }
        container.getChildren().add(legend);
    }

    private void buildTopProducts(List<DashboardService.TopProductRow> topProducts) {
        vboxTopProducts.getChildren().clear();
        for (DashboardService.TopProductRow row : topProducts) {
            HBox line = new HBox(12);
            line.setAlignment(Pos.CENTER_LEFT);
            HBox rankBox = new HBox();
            rankBox.setAlignment(Pos.CENTER);
            rankBox.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8;");
            rankBox.setMinSize(32, 32);
            rankBox.setPrefSize(32, 32);
            Label rankLbl = new Label("#" + row.rank);
            rankLbl.setStyle("-fx-text-fill: #1E2939; -fx-font-weight: bold; -fx-font-size: 12;");
            rankBox.getChildren().add(rankLbl);
            VBox mid = new VBox(2);
            mid.setMaxWidth(Double.MAX_VALUE);
            Label nameLbl = new Label(row.name);
            nameLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 14;");
            Label unitsLbl = new Label(row.quantitySold + " units sold");
            unitsLbl.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 12;");
            mid.getChildren().addAll(nameLbl, unitsLbl);
            Label revLbl = new Label(CurrencyUtil.formatCurrency(row.revenue));
            revLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 14;");
            line.getChildren().addAll(rankBox, mid, revLbl);
            HBox.setHgrow(mid, Priority.ALWAYS);
            VBox.setMargin(line, new Insets(0, 0, 16, 0));
            vboxTopProducts.getChildren().add(line);
        }
        javafx.scene.control.Button seeMore = new javafx.scene.control.Button("See More");
        seeMore.setStyle("-fx-background-color: transparent; -fx-text-fill: #1E2939; -fx-underline: true; -fx-cursor: hand;");
        seeMore.setOnAction(e -> navigateToProductProfitability());
        vboxTopProducts.getChildren().add(seeMore);
    }

    private void buildAlerts(List<String> lowStock, List<String> overdue,
                             List<AlertDao.AlertRow> dbAlerts, List<FinancialAnomalyDao.AnomalyRow> anomalies) {
        vboxAlerts.getChildren().clear();
        for (String msg : lowStock) vboxAlerts.getChildren().add(alertRow("Low stock: " + msg));
        for (String msg : overdue) vboxAlerts.getChildren().add(alertRow(msg));
        if (dbAlerts != null) {
            for (AlertDao.AlertRow r : dbAlerts) {
                String msg = r.getMessage() != null ? r.getMessage() : (r.getAlertType() != null ? r.getAlertType() : "Alert");
                vboxAlerts.getChildren().add(alertRow(msg));
            }
        }
        if (anomalies != null) {
            for (FinancialAnomalyDao.AnomalyRow r : anomalies) {
                String msg = r.getDescription() != null ? r.getDescription() : (r.getAnomalyType() != null ? r.getAnomalyType() : "Anomaly");
                vboxAlerts.getChildren().add(alertRow(msg));
            }
        }
        if (vboxAlerts.getChildren().isEmpty()) {
            Label noAlert = new Label("No alerts at this time.");
            noAlert.setStyle("-fx-text-fill: #6B7280;");
            vboxAlerts.getChildren().add(noAlert);
        }
    }

    private static HBox alertRow(String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.TOP_LEFT);
        Circle dot = new Circle(3, Color.web("#D97706"));
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #374151;");
        row.getChildren().addAll(dot, lbl);
        VBox.setMargin(row, new Insets(0, 0, 12, 0));
        return row;
    }

    private void navigateToProductProfitability() {
        if (mainLayoutController == null) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + "ProductProfitability.fxml");
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            mainLayoutController.setContent(root);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private LocalDate[] resolveDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = cmbDateRange.getValue();
        if (val == null) val = "Last 30 days";
        switch (val) {
            case "Custom Range" -> {
                from = dpStartDate.getValue() != null ? dpStartDate.getValue() : to.minusDays(30);
                to = dpEndDate.getValue() != null ? dpEndDate.getValue() : to;
                if (from.isAfter(to)) from = to;
            }
            case "Last 7 days" -> from = to.minusDays(7);
            case "Last 90 days" -> from = to.minusDays(90);
            case "Last year" -> from = to.minusYears(1);
            default -> from = to.minusDays(30);
        }
        return new LocalDate[]{from, to};
    }

    @FXML
    private void handleExportCSV() {
        if (!SessionManager.isAdmin()) return;
        List<String[]> data = buildKpiExportData();
        javafx.stage.Window window = getWindow();
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Dashboard to CSV");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        fc.setInitialFileName("dashboard_summary.csv");
        java.io.File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            com.raez.finance.service.ExportService.exportRowsToCSV(data, file);
            if (mainLayoutController != null) mainLayoutController.showToast("success", "CSV exported to " + file.getName());
        } catch (Exception e) {
            if (mainLayoutController != null) mainLayoutController.showToast("error", "Export failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportPDF() {
        if (!SessionManager.isAdmin()) return;
        List<String[]> data = buildKpiExportData();
        List<String> summary = new ArrayList<>();
        if (data.size() > 1) {
            for (int i = 1; i <= Math.min(4, data.size() - 1); i++) {
                String[] row = data.get(i);
                if (row.length >= 2) summary.add(row[0] + ": " + row[1]);
            }
        }
        javafx.stage.Window window = getWindow();
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Dashboard to PDF");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("dashboard_summary.pdf");
        java.io.File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            com.raez.finance.service.ExportService.exportRowsToPDFProfessional("Dashboard Summary", summary, data, file);
            if (mainLayoutController != null) mainLayoutController.showToast("success", "PDF exported to " + file.getName());
        } catch (Exception e) {
            if (mainLayoutController != null) mainLayoutController.showToast("error", "Export failed: " + e.getMessage());
        }
    }

    private List<String[]> buildKpiExportData() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Metric", "Value"});
        rows.add(new String[]{"Total Sales", getText(lblTotalSales)});
        rows.add(new String[]{"Net Income", getText(lblTotalProfit)});
        rows.add(new String[]{"Outstanding Payments", getText(lblOutstanding)});
        rows.add(new String[]{"Total VAT Collected", lblVatCollected != null ? getText(lblVatCollected) : CurrencyUtil.formatCurrency(0)});
        rows.add(new String[]{"Refunds / Returns", CurrencyUtil.formatCurrency(lastRefundsForExport)});
        rows.add(new String[]{"Total Customers", getText(lblCustomers)});
        rows.add(new String[]{"Total Orders", getText(lblOrders)});
        rows.add(new String[]{"Average Order Value", getText(lblAOV)});
        rows.add(new String[]{"Most Popular Product", getText(lblPopular)});
        return rows;
    }

    private String getText(Label lbl) {
        return lbl != null && lbl.getText() != null ? lbl.getText() : "";
    }

    private javafx.stage.Window getWindow() {
        if (lblTotalSales != null && lblTotalSales.getScene() != null) return lblTotalSales.getScene().getWindow();
        return null;
    }
}
