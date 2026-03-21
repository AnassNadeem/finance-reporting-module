package com.raez.finance.controller;

import com.raez.finance.dao.AlertDao;
import com.raez.finance.dao.AlertDaoInterface;
import com.raez.finance.dao.FinancialAnomalyDao;
import com.raez.finance.dao.FinancialAnomalyDaoInterface;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.dao.ProductDaoInterface;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.PredictionService;
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OverviewController {

    private static final String VIEW_PATH  = "/com/raez/finance/view/";
    private static final String[] PIE_COLORS = {
        "#1E2939", "#10B981", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4"
    };

    // --- Services -------------------------------------------------------
    private final DashboardService       dashboardService   = new DashboardService();
    private final ProductDaoInterface             productDao         = new ProductDao();
    private final AlertDaoInterface               alertDao           = new AlertDao();
    private final FinancialAnomalyDaoInterface    anomalyDao         = new FinancialAnomalyDao();
    private final ExecutorService        executor           = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dashboard-worker");
        t.setDaemon(true);
        return t;
    });

    private MainLayoutController mainLayoutController;
    private double               lastRefunds = 0;

    private final List<String> crosshairCategories = new ArrayList<>();
    private double[] crosshairRevenue;
    private double[] crosshairCumulative;
    private Line     crosshairVLine;
    private Line     crosshairHLine;
    private Timeline alertRefreshTimeline;

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    // =====================================================================
    //  SHUTDOWN  - must exist so MainLayoutController.setContent() can call it
    // =====================================================================
    public void shutdown() {
        if (alertRefreshTimeline != null) alertRefreshTimeline.stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // --- FXML ------------------------------------------------------------
    @FXML private ComboBox<String> cmbDateRange;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private VBox             boxStartDate;
    @FXML private VBox             boxEndDate;
    @FXML private DatePicker       dpStartDate;
    @FXML private DatePicker       dpEndDate;
    @FXML private MenuButton       btnExport;

    // KPI labels
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

    // Charts & lists
    @FXML private StackPane                salesChartStack;
    @FXML private Pane                     crosshairLayer;
    @FXML private Label                    lblCrosshairHint;
    @FXML private LineChart<String, Number> chartSales;
    @FXML private PieChart                 chartRevenue;
    @FXML private VBox                     vboxTopProducts;
    @FXML private VBox                     vboxAlerts;
    @FXML private Label                    lblAiNext;
    @FXML private Label                    lblAiAvg;
    @FXML private Label                    lblAiTrend;
    @FXML private Label                    lblAiDetail;

    // =====================================================================
    //  INIT
    // =====================================================================

    @FXML
    public void initialize() {
        // Hide export for non-admins
        if (btnExport != null && !SessionManager.isAdmin()) {
            btnExport.setVisible(false);
            btnExport.setManaged(false);
        }

        // Date range filter
        if (cmbDateRange != null) {
            cmbDateRange.setItems(FXCollections.observableArrayList(
                "Last 7 days", "Last 30 days", "Last 90 days", "Last year", "Custom Range"));
            cmbDateRange.setValue("Last 30 days");
        }

        // Category filter
        if (cmbCategory != null) {
            cmbCategory.setItems(FXCollections.observableArrayList("All Categories"));
            cmbCategory.setValue("All Categories");
        }

        // Custom date pickers hidden by default
        if (boxStartDate != null) { boxStartDate.setVisible(false); boxStartDate.setManaged(false); }
        if (boxEndDate   != null) { boxEndDate.setVisible(false);   boxEndDate.setManaged(false); }

        // Chart styling
        if (chartSales != null) {
            chartSales.getStyleClass().add("dashboard-line-chart");
            chartSales.setAnimated(true);
            chartSales.setLegendVisible(false);
            if (chartSales.getXAxis() != null)
                chartSales.getXAxis().setTickLabelRotation(-45);
        }
        if (chartRevenue != null) {
            chartRevenue.getStyleClass().add("dashboard-pie-chart");
            chartRevenue.setAnimated(true);
            chartRevenue.setLegendVisible(false);
            chartRevenue.setLabelsVisible(false);
        }

        // Load category options in background
        loadCategoryOptions();

        // Listeners
        if (cmbDateRange != null) {
            cmbDateRange.valueProperty().addListener((obs, o, n) -> {
                boolean custom = "Custom Range".equals(n);
                if (boxStartDate != null) { boxStartDate.setVisible(custom); boxStartDate.setManaged(custom); }
                if (boxEndDate   != null) { boxEndDate.setVisible(custom);   boxEndDate.setManaged(custom); }
                loadDashboardData();
            });
        }
        if (cmbCategory  != null) cmbCategory.valueProperty().addListener((obs, o, n) -> loadDashboardData());
        if (dpStartDate  != null) dpStartDate.valueProperty().addListener((obs, o, n) -> loadDashboardData());
        if (dpEndDate    != null) dpEndDate.valueProperty().addListener((obs, o, n)   -> loadDashboardData());

        loadDashboardData();

        alertRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(60), e -> refreshAlertsOnly()));
        alertRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        alertRefreshTimeline.play();
    }

    /** Refreshes system alerts from DB without reloading the whole dashboard. */
    private void refreshAlertsOnly() {
        Task<Void> task = new Task<Void>() {
            List<AlertDao.AlertRow> dbAlerts;
            List<FinancialAnomalyDao.AnomalyRow> anomalies;
            @Override protected Void call() throws Exception {
                try { dbAlerts = alertDao.findAlerts(false); } catch (Exception ex) { dbAlerts = new ArrayList<>(); }
                try { anomalies = anomalyDao.findAnomalies(false); } catch (Exception ex) { anomalies = new ArrayList<>(); }
                return null;
            }
            @Override protected void succeeded() {
                buildAlerts(dbAlerts, anomalies);
            }
        };
        executor.execute(task);
    }

    // --- Category options -----------------------------------------------

    private void loadCategoryOptions() {
        Task<List<String>> task = new Task<List<String>>() {
            @Override protected List<String> call() throws Exception {
                return productDao.findCategoryNames();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null && cmbCategory != null) {
                List<String> items = new ArrayList<>();
                items.add("All Categories");
                items.addAll(task.getValue());
                String current = cmbCategory.getValue();
                cmbCategory.setItems(FXCollections.observableArrayList(items));
                cmbCategory.setValue(current != null && items.contains(current) ? current : "All Categories");
            }
        });
        executor.execute(task);
    }

    // =====================================================================
    //  DATA LOADING
    // =====================================================================

    private void loadDashboardData() {
        String category = cmbCategory != null && cmbCategory.getValue() != null
            ? cmbCategory.getValue() : "All Categories";

        Task<Void> task = new Task<Void>() {
            // Results fields (populated in call(), consumed in succeeded())
            double totalSales, totalProfit, outstanding, refunds, vatCollected, prevSales, aov;
            int    customers, orders;
            String popular;
            List<DashboardService.DataPoint<String, Number>>   timeSeries;
            List<DashboardService.DataPoint<String, Number>>   categoryRevenue;
            List<DashboardService.TopProductRow>                topProducts;
            List<AlertDao.AlertRow>                             dbAlerts;
            List<FinancialAnomalyDao.AnomalyRow>                anomalies;

            @Override
            protected Void call() {
                LocalDate[] range = resolveDateRange();
                LocalDate from = range[0];
                LocalDate to   = range[1];
                LocalDate prevFrom = from.minusMonths(1);
                LocalDate prevTo   = to.minusMonths(1);

                try {
                    totalSales      = dashboardService.getTotalSales(from, to, category);
                    totalProfit     = dashboardService.getTotalProfit(from, to, category);
                    outstanding     = dashboardService.getOutstandingPayments(from, to, category);
                    vatCollected    = dashboardService.getTotalVatCollected(from, to, category);
                    refunds         = dashboardService.getRefunds(from, to, category);
                    customers       = dashboardService.getTotalCustomers();
                    orders          = dashboardService.getTotalOrders(from, to, category);
                    aov             = dashboardService.getAverageOrderValue(from, to, category);
                    popular         = dashboardService.getMostPopularProductName(from, to, category);
                    timeSeries      = dashboardService.getSalesTimeSeries(from, to, category);
                    categoryRevenue = dashboardService.getCategoryRevenue(from, to, category);
                    topProducts     = dashboardService.getTopProductsByQuantity(from, to, category, 5);
                    prevSales       = dashboardService.getTotalSales(prevFrom, prevTo, category);
                } catch (Exception e) {
                    totalSales   = 0;
                    totalProfit  = 0;
                    outstanding  = 0;
                    vatCollected = 0;
                    refunds      = 0;
                    customers    = 0;
                    orders       = 0;
                    aov          = 0;
                    popular      = "-";
                    prevSales    = 0;
                    timeSeries = new ArrayList<>();
                    categoryRevenue = new ArrayList<>();
                    topProducts = new ArrayList<>();
                }

                // Fetch alerts (unresolved only - pass false)
                try { dbAlerts  = alertDao.findAlerts(false);    } catch (Exception ex) { dbAlerts  = new ArrayList<>(); }
                try { anomalies = anomalyDao.findAnomalies(false);} catch (Exception ex) { anomalies = new ArrayList<>(); }

                return null;
            }

            @Override
            protected void succeeded() {
                // KPI labels with count-up animation
                animateKpi(lblTotalSales,   totalSales,   true);
                animateKpi(lblTotalProfit,  totalProfit,  true);
                animateKpi(lblOutstanding,  outstanding,  true);
                animateKpi(lblVatCollected, vatCollected, true);
                animateKpi(lblRefunds,      refunds,      true);
                animateKpi(lblCustomers,    customers,    false);
                animateKpi(lblOrders,       orders,       false);
                animateKpi(lblAOV,          aov,          true);
                if (lblPopular != null) lblPopular.setText(popular != null ? popular : "-");

                lastRefunds = refunds;
                updateSalesGrowth(totalSales, prevSales);
                buildLineChart(timeSeries);
                buildPieChart(categoryRevenue);
                buildTopProducts(topProducts);
                buildAlerts(dbAlerts, anomalies);
                loadAiInsight(category);
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                if (ex != null) ex.printStackTrace();
            }
        };

        executor.execute(task);
    }

    private void loadAiInsight(String category) {
        if (lblAiNext == null && lblAiAvg == null && lblAiTrend == null && lblAiDetail == null) return;
        Task<PredictionService.SalesTrendPrediction> aiTask = new Task<>() {
            @Override
            protected PredictionService.SalesTrendPrediction call() throws Exception {
                LocalDate[] range = resolveDateRange();
                return new PredictionService().predictMonthlySales(range[0], range[1], category);
            }
        };
        aiTask.setOnSucceeded(e -> {
            PredictionService.SalesTrendPrediction p = aiTask.getValue();
            if (p == null) return;
            if (lblAiNext != null) {
                lblAiNext.setText("Next mo " + CurrencyUtil.formatCurrency(p.nextMonthEstimate()));
            }
            if (lblAiAvg != null) {
                lblAiAvg.setText("Avg " + CurrencyUtil.formatCurrency(p.meanSales()));
            }
            if (lblAiTrend != null) {
                String arrow = "upward".equals(p.trendLabel()) ? "↑ "
                    : "downward".equals(p.trendLabel()) ? "↓ " : "→ ";
                lblAiTrend.setText(arrow + p.trendLabel());
            }
            if (lblAiDetail != null) {
                lblAiDetail.setText("Simple linear trend on monthly sales in your range. Churn and charts on AI Insights.");
            }
        });
        aiTask.setOnFailed(ev -> {
            if (lblAiDetail != null) lblAiDetail.setText("Could not compute sales trend.");
            if (lblAiNext != null) lblAiNext.setText("—");
            if (lblAiAvg != null) lblAiAvg.setText("—");
            if (lblAiTrend != null) lblAiTrend.setText("—");
        });
        executor.execute(aiTask);
    }

    @FXML
    private void handleOpenAiInsights() {
        if (mainLayoutController != null) mainLayoutController.navigateToAiInsights();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  KPI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void animateKpi(Label label, double target, boolean isCurrency) {
        if (label == null) return;
        Timeline tl = new Timeline();
        int frames = 20;
        for (int i = 0; i <= frames; i++) {
            final double frac = (double) i / frames;
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(30.0 * i), e -> {
                double val = target * frac;
                label.setText(isCurrency
                    ? CurrencyUtil.formatCurrency(val)
                    : String.format("%,d", (int) val));
            }));
        }
        tl.play();
        FadeTransition ft = new FadeTransition(Duration.millis(400), label);
        ft.setFromValue(0.2); ft.setToValue(1); ft.play();
    }

    private void updateSalesGrowth(double current, double previous) {
        if (lblSalesGrowth == null) return;
        if (previous <= 0) {
            lblSalesGrowth.setText("-");
            lblSalesGrowth.setTextFill(Color.web("#6B7280"));
            return;
        }
        double pct = ((current - previous) / previous) * 100.0;
        boolean positive = pct >= 0;
        lblSalesGrowth.setText(String.format("%s%.1f%% vs last month", positive ? "UP +" : "DOWN ", pct));
        lblSalesGrowth.setTextFill(Color.web(positive ? "#10B981" : "#EF4444"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LINE CHART
    // ══════════════════════════════════════════════════════════════════════

    private void buildLineChart(List<DashboardService.DataPoint<String, Number>> timeSeries) {
        if (chartSales == null) return;
        chartSales.getData().clear();

        // Build ordered category list - always 12 months minimum
        List<String>          categories = new ArrayList<>();
        Map<String, Number>   dataMap    = new LinkedHashMap<>();

        if (timeSeries == null || timeSeries.isEmpty()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yy");
            YearMonth ym = YearMonth.now().minusMonths(11);
            for (int i = 0; i < 12; i++) {
                String lbl = ym.format(fmt);
                categories.add(lbl);
                dataMap.put(lbl, 0);
                ym = ym.plusMonths(1);
            }
        } else {
            for (DashboardService.DataPoint<String, Number> p : timeSeries) {
                if (p.x != null) {
                    categories.add(p.x);
                    dataMap.put(p.x, p.y != null ? p.y : 0);
                }
            }
        }

        // Set categories BEFORE adding data (fixes the squished axis bug)
        CategoryAxis xAxis = (CategoryAxis) chartSales.getXAxis();
        xAxis.setAutoRanging(false);
        xAxis.setCategories(FXCollections.observableArrayList(categories));

        // Revenue series
        XYChart.Series<String, Number> revSeries = new XYChart.Series<>();
        revSeries.setName("Revenue");
        for (String cat : categories) {
            Number val = dataMap.getOrDefault(cat, 0);
            revSeries.getData().add(new XYChart.Data<>(cat, val));
        }
        chartSales.getData().add(revSeries);

        crosshairCategories.clear();
        crosshairCategories.addAll(categories);
        crosshairRevenue = new double[categories.size()];
        crosshairCumulative = new double[categories.size()];
        double running = 0;
        for (int i = 0; i < categories.size(); i++) {
            double v = dataMap.getOrDefault(categories.get(i), 0).doubleValue();
            crosshairRevenue[i] = v;
            running += v;
            crosshairCumulative[i] = running;
        }

        // Cumulative series
        XYChart.Series<String, Number> cumSeries = new XYChart.Series<>();
        cumSeries.setName("Cumulative");
        for (int i = 0; i < categories.size(); i++) {
            cumSeries.getData().add(new XYChart.Data<>(categories.get(i), crosshairCumulative[i]));
        }
        chartSales.getData().add(cumSeries);

        // Style cumulative line dashed via CSS lookup (done after render)
        Platform.runLater(() -> {
            for (Node n : chartSales.lookupAll(".default-color1.chart-series-line")) {
                n.setStyle("-fx-stroke: #10B981; -fx-stroke-dash-array: 6 4; -fx-stroke-width: 1.8;");
            }
            for (Node n : chartSales.lookupAll(".default-color1.chart-line-symbol")) {
                n.setStyle("-fx-background-color: transparent;");
            }
            bindSalesCrosshair();
        });

        // Hover tooltip on each data point
        for (XYChart.Series<String, Number> series : Arrays.asList(revSeries, cumSeries)) {
            for (XYChart.Data<String, Number> d : series.getData()) {
                d.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN == null) return;
                    String label = d.getXValue() + "\n" +
                        CurrencyUtil.formatCurrency(d.getYValue().doubleValue());
                    Tooltip tp = new Tooltip(label);
                    tp.setStyle(
                        "-fx-background-color: white; -fx-text-fill: #111827;" +
                        "-fx-border-color: #E5E7EB; -fx-border-radius: 8;" +
                        "-fx-background-radius: 8; -fx-font-size: 12px;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);");
                    Tooltip.install(newN, tp);
                    // Hover scale
                    newN.setOnMouseEntered(e -> newN.setScaleX(1.4));
                    newN.setOnMouseExited(e  -> newN.setScaleX(1.0));
                });
            }
        }

        // Y-axis formatting (scale to fit both monthly revenue and cumulative line)
        if (chartSales.getYAxis() instanceof NumberAxis) {
            NumberAxis yAxis = (NumberAxis) chartSales.getYAxis();
            double maxRev = crosshairRevenue == null || crosshairRevenue.length == 0
                ? 0 : Arrays.stream(crosshairRevenue).max().orElse(0);
            double maxCum = crosshairCumulative == null || crosshairCumulative.length == 0
                ? 0 : crosshairCumulative[crosshairCumulative.length - 1];
            double max = Math.max(maxRev, maxCum);
            double upper = max <= 0 ? 80_000 : Math.ceil(max / 20_000) * 20_000;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(upper);
            yAxis.setTickUnit(upper / 4);
            yAxis.setMinorTickCount(0);

            String sym = CurrencyUtil.formatCurrency(0).replaceAll("[0-9.,\\s]", "").trim();
            if (sym.isEmpty()) sym = "£";
            final String fSym = sym;
            NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
            yAxis.setTickLabelFormatter(new StringConverter<Number>() {
                @Override public String toString(Number n) {
                    if (n == null) return "";
                    long k = n.longValue();
                    return k >= 1000 ? fSym + nf.format(k / 1000) + "k" : fSym + nf.format(k);
                }
                @Override public Number fromString(String s) { return 0; }
            });
        }
    }

    private void bindSalesCrosshair() {
        if (crosshairLayer == null || lblCrosshairHint == null) return;
        crosshairLayer.getChildren().removeIf(n -> n instanceof Rectangle || n instanceof Line);
        if (crosshairCategories.isEmpty()) {
            crosshairLayer.setOnMouseMoved(null);
            crosshairLayer.setOnMouseExited(null);
            lblCrosshairHint.setVisible(false);
            return;
        }
        Rectangle trap = new Rectangle();
        trap.widthProperty().bind(crosshairLayer.widthProperty());
        trap.heightProperty().bind(crosshairLayer.heightProperty());
        trap.setFill(Color.TRANSPARENT);
        crosshairVLine = new Line();
        crosshairVLine.setManaged(false);
        crosshairVLine.setVisible(false);
        crosshairVLine.setStroke(Color.web("#94A3B8"));
        crosshairVLine.setStrokeWidth(1);
        crosshairVLine.getStrokeDashArray().addAll(4.0, 4.0);
        crosshairHLine = new Line();
        crosshairHLine.setManaged(false);
        crosshairHLine.setVisible(false);
        crosshairHLine.setStroke(Color.web("#CBD5E1"));
        crosshairHLine.setStrokeWidth(1);
        crosshairHLine.getStrokeDashArray().addAll(3.0, 3.0);
        crosshairLayer.getChildren().addAll(trap, crosshairVLine, crosshairHLine);
        lblCrosshairHint.toFront();

        crosshairLayer.setOnMouseMoved(ev -> {
            int n = crosshairCategories.size();
            if (n == 0) return;
            double w = crosshairLayer.getWidth();
            double h = crosshairLayer.getHeight();
            if (w <= 0 || h <= 0) return;
            double mx = ev.getX();
            double my = ev.getY();
            int idx = (int) Math.floor((mx / w) * n);
            idx = Math.max(0, Math.min(n - 1, idx));
            double slotW = w / n;
            double xi = idx * slotW + slotW / 2;
            crosshairVLine.setStartX(xi);
            crosshairVLine.setEndX(xi);
            crosshairVLine.setStartY(0);
            crosshairVLine.setEndY(h);
            crosshairVLine.setVisible(true);
            crosshairHLine.setStartX(0);
            crosshairHLine.setEndX(w);
            crosshairHLine.setStartY(my);
            crosshairHLine.setEndY(my);
            crosshairHLine.setVisible(true);
            lblCrosshairHint.setVisible(true);
            lblCrosshairHint.setText(
                crosshairCategories.get(idx) + "\nRevenue: " + CurrencyUtil.formatCurrency(crosshairRevenue[idx])
                    + "\nCumulative: " + CurrencyUtil.formatCurrency(crosshairCumulative[idx]));
            lblCrosshairHint.applyCss();
            lblCrosshairHint.layout();
            double lw = lblCrosshairHint.prefWidth(-1);
            double lh = lblCrosshairHint.prefHeight(-1);
            double hx = mx + 10;
            double hy = my + 10;
            if (hx + lw > w) hx = mx - lw - 10;
            if (hy + lh > h) hy = my - lh - 10;
            lblCrosshairHint.setLayoutX(Math.max(0, hx));
            lblCrosshairHint.setLayoutY(Math.max(0, hy));
        });
        crosshairLayer.setOnMouseExited(e -> {
            crosshairVLine.setVisible(false);
            crosshairHLine.setVisible(false);
            lblCrosshairHint.setVisible(false);
        });
    }

    // =====================================================================
    //  PIE CHART
    // =====================================================================

    private void buildPieChart(List<DashboardService.DataPoint<String, Number>> categoryRevenue) {
        if (chartRevenue == null) return;
        chartRevenue.getData().clear();

        if (categoryRevenue == null || categoryRevenue.isEmpty()) return;

        double total = categoryRevenue.stream()
            .mapToDouble(p -> p.y == null ? 0 : p.y.doubleValue()).sum();
        if (total <= 0) return;

        // Merge slices < 3% into "Other"
        List<DashboardService.DataPoint<String, Number>> merged = new ArrayList<>();
        double otherVal = 0;
        for (DashboardService.DataPoint<String, Number> p : categoryRevenue) {
            double val = p.y == null ? 0 : p.y.doubleValue();
            double pct = (val / total) * 100.0;
            if (pct < 3) otherVal += val;
            else merged.add(p);
        }
        if (otherVal > 0) merged.add(new DashboardService.DataPoint<>("Other", otherVal));

        for (DashboardService.DataPoint<String, Number> p : merged) {
            double val = p.y == null ? 0 : p.y.doubleValue();
            int pct = (int) Math.round(100.0 * val / total);
            chartRevenue.getData().add(new PieChart.Data(p.x + " " + pct + "%", val));
        }

        // Apply colours + hover + tooltip after nodes exist
        Platform.runLater(() -> {
            for (int i = 0; i < chartRevenue.getData().size(); i++) {
                PieChart.Data slice = chartRevenue.getData().get(i);
                String colour = PIE_COLORS[i % PIE_COLORS.length];
                applySliceStyle(slice, colour);
            }
        });

        buildCustomPieLegend(merged, total);
    }

    private void applySliceStyle(PieChart.Data slice, String colour) {
        if (slice.getNode() != null) {
            installSliceInteraction(slice, colour);
        } else {
            slice.nodeProperty().addListener((obs, o, newN) -> {
                if (newN != null) installSliceInteraction(slice, colour);
            });
        }
    }

    private void installSliceInteraction(PieChart.Data slice, String colour) {
        Node n = slice.getNode();
        if (n == null) return;
        n.setStyle("-fx-pie-color: " + colour + ";");

        Tooltip tp = new Tooltip(
            slice.getName().replaceAll("\\s+\\d+%$", "") + "\n" +
            CurrencyUtil.formatCurrency(slice.getPieValue()));
        tp.setStyle(
            "-fx-background-color: white; -fx-text-fill: #111827;" +
            "-fx-border-color: #E5E7EB; -fx-border-radius: 8;" +
            "-fx-background-radius: 8; -fx-font-size: 12px;");
        Tooltip.install(n, tp);

        n.setOnMouseEntered(e -> {
            n.setScaleX(1.06); n.setScaleY(1.06);
        });
        n.setOnMouseExited(e -> {
            n.setScaleX(1.0); n.setScaleY(1.0);
        });
    }

    private void buildCustomPieLegend(List<DashboardService.DataPoint<String, Number>> items, double total) {
        if (chartRevenue == null) return;
        if (!(chartRevenue.getParent() instanceof VBox)) return;
        VBox container = (VBox) chartRevenue.getParent();
        container.getChildren().removeIf(n -> "pie-legend".equals(n.getId()));

        javafx.scene.layout.FlowPane legend = new javafx.scene.layout.FlowPane(12, 6);
        legend.setId("pie-legend");
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(8, 0, 0, 0));
        

        for (int i = 0; i < items.size(); i++) {
            DashboardService.DataPoint<String, Number> item = items.get(i);
            String colour = PIE_COLORS[i % PIE_COLORS.length];
            Circle dot = new Circle(5, Color.web(colour));
            double val = item.y == null ? 0 : item.y.doubleValue();
            int pct = (int) Math.round(100.0 * val / total);
            Label lbl = new Label(item.x + " (" + pct + "%)");
            lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #374151;");
            HBox entry = new HBox(6, dot, lbl);
            entry.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(entry);
        }
        container.getChildren().add(legend);
    }

    // =====================================================================
    //  TOP PRODUCTS
    // =====================================================================

    private void buildTopProducts(List<DashboardService.TopProductRow> topProducts) {
        if (vboxTopProducts == null) return;
        vboxTopProducts.getChildren().clear();

        if (topProducts == null || topProducts.isEmpty()) {
            Label empty = new Label("No product data available.");
            empty.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 13px;");
            vboxTopProducts.getChildren().add(empty);
            return;
        }

        String[] medals = {"1st", "2nd", "3rd"};

        for (int idx = 0; idx < topProducts.size(); idx++) {
            DashboardService.TopProductRow row = topProducts.get(idx);

            HBox line = new HBox(12);
            line.setAlignment(Pos.CENTER_LEFT);
            line.setPadding(new Insets(0, 0, 14, 0));

            // Rank badge
            HBox rankBox = new HBox();
            rankBox.setAlignment(Pos.CENTER);
            rankBox.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8;");
            rankBox.setMinSize(36, 36); rankBox.setPrefSize(36, 36);
            String rankText = idx < medals.length ? medals[idx] : "#" + row.rank;
            Label rankLbl = new Label(rankText);
            rankLbl.setStyle(idx < medals.length
                ? "-fx-font-size: 16px;"
                : "-fx-text-fill: #1E2939; -fx-font-weight: bold; -fx-font-size: 13px;");
            rankBox.getChildren().add(rankLbl);

            // Name + units
            VBox mid = new VBox(2);
            HBox.setHgrow(mid, Priority.ALWAYS);
            Label nameLbl = new Label(row.name);
            nameLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 13px;");
            Label unitsLbl = new Label(row.quantitySold + " units sold");
            unitsLbl.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 11px;");
            mid.getChildren().addAll(nameLbl, unitsLbl);

            // Revenue
            Label revLbl = new Label(CurrencyUtil.formatCurrency(row.revenue));
            revLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 13px;");

            line.getChildren().addAll(rankBox, mid, revLbl);
            vboxTopProducts.getChildren().add(line);
        }

        // "See more" link
        Button seeMore = new Button("View Product Profitability ->");
        seeMore.setStyle("-fx-background-color: transparent; -fx-text-fill: #1E2939;" +
            "-fx-underline: true; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 4 0 0 0;");
        seeMore.setOnAction(e -> navigateTo("ProductProfitability.fxml"));
        vboxTopProducts.getChildren().add(seeMore);
    }

    // =====================================================================
    //  ALERTS  (dynamic - reads from both alert tables)
    // =====================================================================

    private void buildAlerts(List<AlertDao.AlertRow>         dbAlerts,
                              List<FinancialAnomalyDao.AnomalyRow> anomalies) {
        if (vboxAlerts == null) return;
        vboxAlerts.getChildren().clear();

        // DB alerts
        if (dbAlerts != null) {
            for (AlertDao.AlertRow r : dbAlerts) {
                boolean critical = "CRITICAL".equalsIgnoreCase(r.getSeverity())
                                || "HIGH".equalsIgnoreCase(r.getSeverity());
                String msg = r.getMessage() != null ? r.getMessage()
                    : (r.getAlertType() != null ? r.getAlertType() : "Alert");
                vboxAlerts.getChildren().add(alertRow(msg, critical ? "#DC2626" : "#D97706"));
            }
        }

        // Financial anomalies
        if (anomalies != null) {
            for (FinancialAnomalyDao.AnomalyRow r : anomalies) {
                boolean critical = "CRITICAL".equalsIgnoreCase(r.getSeverity())
                                || "HIGH".equalsIgnoreCase(r.getSeverity());
                String msg = r.getDescription() != null ? r.getDescription()
                    : (r.getAnomalyType() != null ? r.getAnomalyType() : "Anomaly");
                vboxAlerts.getChildren().add(alertRow(msg, critical ? "#DC2626" : "#D97706"));
            }
        }

        if (vboxAlerts.getChildren().isEmpty()) {
            Label none = new Label("No active alerts.");
            none.setStyle("-fx-text-fill: #16A34A; -fx-font-size: 13px;");
            vboxAlerts.getChildren().add(none);
        }

        // "View all" link
        if (!vboxAlerts.getChildren().isEmpty()) {
            Button viewAll = new Button("View all alerts ->");
            viewAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #1E2939;" +
                "-fx-underline: true; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 8 0 0 0;");
            viewAll.setOnAction(e -> navigateTo("NotificationsAlerts.fxml"));
            vboxAlerts.getChildren().add(viewAll);
        }
    }

    private HBox alertRow(String text, String dotColour) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(0, 0, 10, 0));
        Circle dot = new Circle(4, Color.web(dotColour));
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px;");
        HBox.setHgrow(lbl, Priority.ALWAYS);
        row.getChildren().addAll(dot, lbl);
        return row;
    }

    // =====================================================================
    //  EXPORT
    // =====================================================================

    @FXML
    private void handleExportCSV() {
        if (!SessionManager.isAdmin()) return;
        File file = pickFile("dashboard_summary.csv", "CSV Files", "*.csv");
        if (file == null) return;
        try {
            ExportService.exportRowsToCSV(buildExportData(), file);
            toast("success", "CSV exported: " + file.getName());
        } catch (Exception e) {
            toast("error", "Export failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportPDF() {
        if (!SessionManager.isAdmin()) return;
        File file = pickFile("dashboard_summary.pdf", "PDF Files", "*.pdf");
        if (file == null) return;
        try {
            // Use the standard exportRowsToPDF - exportRowsToPDFProfessional may not exist
            ExportService.exportRowsToPDF("Dashboard Summary", buildExportData(), file);
            toast("success", "PDF exported: " + file.getName());
        } catch (Exception e) {
            toast("error", "Export failed: " + e.getMessage());
        }
    }

    private List<String[]> buildExportData() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Metric", "Value"});
        rows.add(new String[]{"Total Sales",          (lblTotalSales != null && lblTotalSales.getText() != null ? lblTotalSales.getText() : "-")});
        rows.add(new String[]{"Net Income / Profit",  (lblTotalProfit != null && lblTotalProfit.getText() != null ? lblTotalProfit.getText() : "-")});
        rows.add(new String[]{"Outstanding Payments", (lblOutstanding != null && lblOutstanding.getText() != null ? lblOutstanding.getText() : "-")});
        rows.add(new String[]{"VAT Collected",        (lblVatCollected != null && lblVatCollected.getText() != null ? lblVatCollected.getText() : "-")});
        rows.add(new String[]{"Refunds / Returns",    CurrencyUtil.formatCurrency(lastRefunds)});
        rows.add(new String[]{"Total Customers",      (lblCustomers != null && lblCustomers.getText() != null ? lblCustomers.getText() : "-")});
        rows.add(new String[]{"Total Orders",         (lblOrders != null && lblOrders.getText() != null ? lblOrders.getText() : "-")});
        rows.add(new String[]{"Avg Order Value",      (lblAOV != null && lblAOV.getText() != null ? lblAOV.getText() : "-")});
        rows.add(new String[]{"Most Popular Product", (lblPopular != null && lblPopular.getText() != null ? lblPopular.getText() : "-")});
        return rows;
    }

    // =====================================================================
    //  HELPERS
    // =====================================================================

    private LocalDate[] resolveDateRange() {
        LocalDate to   = LocalDate.now();
        LocalDate from;
        String val = cmbDateRange != null ? cmbDateRange.getValue() : "Last 30 days";
        if (val == null) val = "Last 30 days";

        // Classic switch (avoids switch expressions/yield for IDE parsers)
        switch (val) {
            case "Custom Range": {
                LocalDate s = dpStartDate != null ? dpStartDate.getValue() : null;
                LocalDate e = dpEndDate   != null ? dpEndDate.getValue()   : null;
                if (e != null) to = e;
                from = s != null ? s : to.minusDays(30);
                break;
            }
            case "Last 7 days":
                from = to.minusDays(7);
                break;
            case "Last 90 days":
                from = to.minusDays(90);
                break;
            case "Last year":
                from = to.minusYears(1);
                break;
            default:
                from = to.minusDays(30);
                break;
        }
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    private void navigateTo(String fxmlName) {
        if (mainLayoutController == null) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + fxmlName);
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            mainLayoutController.setContent(root);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private File pickFile(String defaultName, String desc, String ext) {
        javafx.stage.Window window = null;
        if (lblTotalSales != null && lblTotalSales.getScene() != null)
            window = lblTotalSales.getScene().getWindow();
        if (window == null && chartSales != null && chartSales.getScene() != null)
            window = chartSales.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Dashboard");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        fc.setInitialFileName(defaultName);
        return fc.showSaveDialog(window);
    }

    private void toast(String type, String msg) {
        if (mainLayoutController != null) mainLayoutController.showToast(type, msg);
    }
}