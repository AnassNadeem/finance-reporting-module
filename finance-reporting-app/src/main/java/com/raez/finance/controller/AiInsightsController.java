package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.PredictionService;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated analytics page: revenue / acquisition forecasts (Commons Math regression), churn counts, charts.
 */
public class AiInsightsController {

    private static final DateTimeFormatter CHART_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final String NEXT_MONTH_LABEL = "Next mo. (est.)";
    /** Delay between revealing each point — stock-style line draw */
    private static final int LINE_POINT_DELAY_MS = 42;

    private final PredictionService predictionService = new PredictionService();
    private final DashboardService    dashboardService  = new DashboardService();
    private final CustomerDao       customerDao       = new CustomerDao();
    private final ProductDao        productDao        = new ProductDao();
    private final ExecutorService   executor          = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-insights");
        t.setDaemon(true);
        return t;
    });

    @FXML private ComboBox<String> cmbCategory;
    @FXML private Label lblRevForecast;
    @FXML private Label lblRevTrend;
    @FXML private Label lblAcqForecast;
    @FXML private Label lblAcqNote;
    @FXML private Label lblChurn90;
    @FXML private Label lblChurn180;
    @FXML private Label lblNoOrders;
    @FXML private Label lblDisclaimer;
    @FXML private LineChart<String, Number> chartRevenue;
    @FXML private LineChart<String, Number> chartAcquisition;
    @FXML private BarChart<String, Number>   chartChurn;

    private Timeline revenueAnimTimeline;
    private Timeline acquisitionAnimTimeline;

    public void setMainLayoutController(@SuppressWarnings("unused") MainLayoutController mlc) {
        // Reserved for future navigation hooks
    }

    public void shutdown() {
        stopChartTimelines();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void stopChartTimelines() {
        if (revenueAnimTimeline != null) {
            revenueAnimTimeline.stop();
            revenueAnimTimeline = null;
        }
        if (acquisitionAnimTimeline != null) {
            acquisitionAnimTimeline.stop();
            acquisitionAnimTimeline = null;
        }
    }

    @FXML
    public void initialize() {
        try {
            List<String> cats = new ArrayList<>();
            cats.add("All Categories");
            cats.addAll(productDao.findCategoryNames());
            cmbCategory.setItems(FXCollections.observableArrayList(cats));
            cmbCategory.setValue("All Categories");
        } catch (Exception e) {
            cmbCategory.setItems(FXCollections.observableArrayList("All Categories"));
            cmbCategory.setValue("All Categories");
        }
        cmbCategory.valueProperty().addListener((o, a, b) -> refresh());
        if (lblDisclaimer != null) {
            lblDisclaimer.setText(
                    "Acquisition uses each customer’s first order month as a proxy (no registration date in schema). "
                  + "Churn uses days since last order. Forecasts use simple linear regression — indicative only.");
        }
        configureChartShells();
        refresh();
    }

    /** Axes, gaps; line charts use custom draw timelines (setAnimated false). */
    private void configureChartShells() {
        if (chartRevenue != null) {
            chartRevenue.setAnimated(false);
            chartRevenue.setCreateSymbols(true);
            chartRevenue.setLegendVisible(true);
            configureCategoryTimelineAxis(chartRevenue, -38);
            configureCompactCurrencyYAxis(chartRevenue);
        }
        if (chartAcquisition != null) {
            chartAcquisition.setAnimated(false);
            chartAcquisition.setCreateSymbols(true);
            configureCategoryTimelineAxis(chartAcquisition, -38);
            configureCountYAxis(chartAcquisition);
        }
        if (chartChurn != null) {
            chartChurn.setAnimated(true);
            chartChurn.setBarGap(4);
            chartChurn.setCategoryGap(14);
            configureCategoryTimelineAxis(chartChurn, 0);
            configureCountYAxis(chartChurn);
        }
    }

    private void configureCategoryTimelineAxis(XYChart<String, Number> chart, double rotationDeg) {
        if (chart.getXAxis() instanceof CategoryAxis cx) {
            cx.setGapStartAndEnd(true);
            cx.setTickLabelGap(6);
            cx.setAnimated(false);
            cx.setTickLabelRotation(rotationDeg);
        }
    }

    private void configureCompactCurrencyYAxis(LineChart<String, Number> chart) {
        if (chart.getYAxis() instanceof NumberAxis na) {
            na.setForceZeroInRange(true);
            na.setMinorTickVisible(false);
            na.setAnimated(false);
            na.setAutoRanging(true);
            na.setTickLabelFormatter(new StringConverter<Number>() {
                @Override
                public String toString(Number n) {
                    double v = n.doubleValue();
                    if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
                    double av = Math.abs(v);
                    if (av >= 1_000_000) return String.format(Locale.UK, "%.1fM", v / 1_000_000);
                    if (av >= 10_000) return String.format(Locale.UK, "%.1fk", v / 1000);
                    return String.format(Locale.UK, "%.0f", v);
                }
                @Override
                public Number fromString(String s) { return 0; }
            });
        }
    }

    /** Integer-ish counts; auto tick spacing for small or large values (churn / acquisition). */
    private void configureCountYAxis(XYChart<String, Number> chart) {
        if (chart.getYAxis() instanceof NumberAxis na) {
            na.setForceZeroInRange(true);
            na.setMinorTickVisible(false);
            na.setAnimated(true);
            na.setAutoRanging(true);
            na.setTickLabelFormatter(new StringConverter<Number>() {
                @Override
                public String toString(Number n) {
                    return String.format(Locale.UK, "%.0f", n.doubleValue());
                }
                @Override
                public Number fromString(String s) { return 0; }
            });
        }
    }

    private static String formatYearMonth(YearMonth ym) {
        return ym.format(CHART_MONTH);
    }

    private List<XYChart.Data<String, Number>> computeRevenueHistory(
            LocalDate from, LocalDate to, String category) {
        YearMonth ymStart = YearMonth.from(from);
        YearMonth ymEnd = YearMonth.from(to);
        List<XYChart.Data<String, Number>> histPoints = new ArrayList<>();
        for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
            LocalDate f = ym.atDay(1);
            LocalDate t = ym.atEndOfMonth();
            try {
                double d = dashboardService.getTotalSales(f, t, category);
                histPoints.add(new XYChart.Data<>(formatYearMonth(ym), d));
            } catch (Exception ignored) {
                histPoints.add(new XYChart.Data<>(formatYearMonth(ym), 0));
            }
        }
        return histPoints;
    }

    private List<XYChart.Data<String, Number>> computeAcquisitionSeries(LocalDate from, LocalDate to)
            throws Exception {
        List<CustomerDao.MonthlyCount> monthly = customerDao.findFirstOrderMonthCounts(from, to);
        Map<String, Integer> byYm = new HashMap<>();
        for (CustomerDao.MonthlyCount m : monthly) {
            if (m.month != null) {
                byYm.put(m.month.trim(), m.count);
            }
        }
        YearMonth ymStart = YearMonth.from(from);
        YearMonth ymEnd = YearMonth.from(to);
        List<XYChart.Data<String, Number>> points = new ArrayList<>();
        for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
            int c = byYm.getOrDefault(ym.toString(), 0);
            points.add(new XYChart.Data<>(formatYearMonth(ym), (double) c));
        }
        return points;
    }

    private List<XYChart.Data<String, Number>> loadAcquisitionPointsSafe(LocalDate from, LocalDate to) {
        try {
            return computeAcquisitionSeries(from, to);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private void refresh() {
        String cat = cmbCategory != null && cmbCategory.getValue() != null ? cmbCategory.getValue() : "All Categories";
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusMonths(11).withDayOfMonth(1);
                String catArg = "All Categories".equals(cat) ? null : cat;

                PredictionService.DetailedRevenueForecast rev =
                        predictionService.predictDetailedRevenue(from, to, catArg);
                PredictionService.AcquisitionForecast acq =
                        predictionService.predictAcquisitionFromFirstOrders(from, to);
                CustomerDao.ChurnStats churn = customerDao.findChurnStats(90, 180);

                List<XYChart.Data<String, Number>> revHist = computeRevenueHistory(from, to, catArg);
                double nextEst = Math.max(0, rev.nextMonthEstimate());
                final List<XYChart.Data<String, Number>> acqPoints = loadAcquisitionPointsSafe(from, to);

                Platform.runLater(() -> {
                    stopChartTimelines();

                    if (lblRevForecast != null)
                        lblRevForecast.setText(CurrencyUtil.formatCurrency(rev.nextMonthEstimate()));
                    if (lblRevTrend != null) {
                        lblRevTrend.setText(String.format(
                                "%s trend · mean %s · residual RMSE ≈ %s",
                                rev.trendLabel(),
                                CurrencyUtil.formatCurrency(rev.meanSales()),
                                CurrencyUtil.formatCurrency(rev.rmseResidual())));
                    }
                    if (lblAcqForecast != null)
                        lblAcqForecast.setText(String.format("%.0f", acq.nextMonthEstimate()));
                    if (lblAcqNote != null) {
                        lblAcqNote.setText(String.format(
                                "%s · avg %.1f new buyers/mo (first-order proxy) · RMSE ≈ %.2f",
                                acq.trendLabel(), acq.meanNewPerMonth(), acq.rmseResidual()));
                    }
                    if (lblChurn90 != null) lblChurn90.setText(String.valueOf(churn.dormant90()));
                    if (lblChurn180 != null) lblChurn180.setText(String.valueOf(churn.dormant180()));
                    if (lblNoOrders != null)
                        lblNoOrders.setText("Customers with no orders: " + churn.noOrders());

                    configureChartShells();
                    applyChurnChart(churn);
                    startRevenueLineAnimation(revHist, nextEst);
                    startAcquisitionLineAnimation(acqPoints);
                });
                return null;
            }
        };
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            Platform.runLater(() -> {
                if (lblRevTrend != null) lblRevTrend.setText("Could not load predictions.");
            });
        });
        executor.execute(task);
    }

    private void applyChurnChart(CustomerDao.ChurnStats churn) {
        if (chartChurn == null) return;
        chartChurn.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Customers");
        s.getData().add(new XYChart.Data<>("Idle >90d", (double) churn.dormant90()));
        s.getData().add(new XYChart.Data<>("Idle >180d", (double) churn.dormant180()));
        s.getData().add(new XYChart.Data<>("No orders", (double) churn.noOrders()));
        chartChurn.getData().add(s);
        chartChurn.requestLayout();
    }

    private void startRevenueLineAnimation(List<XYChart.Data<String, Number>> histPoints, double nextEst) {
        if (chartRevenue == null) return;
        chartRevenue.getData().clear();
        XYChart.Series<String, Number> hist = new XYChart.Series<>();
        hist.setName("Paid revenue");
        XYChart.Series<String, Number> fc = new XYChart.Series<>();
        fc.setName("Next month (est.)");
        chartRevenue.getData().add(hist);
        chartRevenue.getData().add(fc);

        XYChart.Data<String, Number> fcPoint = new XYChart.Data<>(NEXT_MONTH_LABEL, nextEst);
        Timeline tl = new Timeline();
        if (histPoints.isEmpty()) {
            tl.getKeyFrames().add(new KeyFrame(Duration.ZERO, e -> fc.getData().add(fcPoint)));
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(80), e -> layoutCategoryAxis(chartRevenue)));
        } else {
            for (int i = 0; i < histPoints.size(); i++) {
                final int idx = i;
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(LINE_POINT_DELAY_MS * (i + 1)),
                        e -> hist.getData().add(histPoints.get(idx))));
            }
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis(LINE_POINT_DELAY_MS * (histPoints.size() + 2)),
                    e -> fc.getData().add(fcPoint)));
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis(LINE_POINT_DELAY_MS * (histPoints.size() + 4)),
                    e -> layoutCategoryAxis(chartRevenue)));
        }
        revenueAnimTimeline = tl;
        tl.play();
    }

    private void startAcquisitionLineAnimation(List<XYChart.Data<String, Number>> points) {
        if (chartAcquisition == null) return;
        chartAcquisition.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("New buyers (first order)");
        chartAcquisition.getData().add(s);

        Timeline tl = new Timeline();
        if (points.isEmpty()) {
            tl.getKeyFrames().add(new KeyFrame(Duration.millis(50), e -> layoutCategoryAxis(chartAcquisition)));
        } else {
            for (int i = 0; i < points.size(); i++) {
                final int idx = i;
                tl.getKeyFrames().add(new KeyFrame(Duration.millis(LINE_POINT_DELAY_MS * (i + 1)),
                        e -> s.getData().add(points.get(idx))));
            }
            tl.getKeyFrames().add(new KeyFrame(
                    Duration.millis(LINE_POINT_DELAY_MS * (points.size() + 3)),
                    e -> layoutCategoryAxis(chartAcquisition)));
        }
        acquisitionAnimTimeline = tl;
        tl.play();
    }

    private static void layoutCategoryAxis(LineChart<String, Number> chart) {
        chart.requestLayout();
        if (chart.getXAxis() instanceof CategoryAxis cx) {
            cx.requestLayout();
        }
    }
}
