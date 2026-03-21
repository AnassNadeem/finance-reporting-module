package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.PredictionService;
import com.raez.finance.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Dedicated analytics page: revenue / acquisition forecasts (Commons Math regression), churn counts, charts.
 */
public class AiInsightsController {

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

    public void setMainLayoutController(@SuppressWarnings("unused") MainLayoutController mlc) {
        // Reserved for future navigation hooks
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
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
        refresh();
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

                Platform.runLater(() -> {
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
                });

                buildRevenueChart(from, to, catArg, rev);
                buildChurnChart(churn);
                buildAcquisitionChart(from, to);
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

    private void buildRevenueChart(
            LocalDate from, LocalDate to, String category,
            PredictionService.DetailedRevenueForecast rev) {
        Platform.runLater(() -> {
            if (chartRevenue == null) return;
            chartRevenue.getData().clear();
            XYChart.Series<String, Number> hist = new XYChart.Series<>();
            hist.setName("Paid revenue");
            YearMonth ymStart = YearMonth.from(from);
            YearMonth ymEnd = YearMonth.from(to);
            for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
                LocalDate f = ym.atDay(1);
                LocalDate t = ym.atEndOfMonth();
                try {
                    double d = dashboardService.getTotalSales(f, t, category);
                    hist.getData().add(new XYChart.Data<>(ym.toString(), d));
                } catch (Exception ignored) {
                    hist.getData().add(new XYChart.Data<>(ym.toString(), 0));
                }
            }
            XYChart.Series<String, Number> fc = new XYChart.Series<>();
            fc.setName("Next month (est.)");
            fc.getData().add(new XYChart.Data<>("→ next", Math.max(0, rev.nextMonthEstimate())));
            chartRevenue.getData().addAll(hist, fc);
        });
    }

    private void buildChurnChart(CustomerDao.ChurnStats churn) {
        Platform.runLater(() -> {
            if (chartChurn == null) return;
            chartChurn.getData().clear();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Customers");
            s.getData().add(new XYChart.Data<>("Idle >90d", (double) churn.dormant90()));
            s.getData().add(new XYChart.Data<>("Idle >180d", (double) churn.dormant180()));
            s.getData().add(new XYChart.Data<>("No orders", (double) churn.noOrders()));
            chartChurn.getData().add(s);
        });
    }

    private void buildAcquisitionChart(LocalDate from, LocalDate to) {
        try {
            List<CustomerDao.MonthlyCount> monthly = customerDao.findFirstOrderMonthCounts(from, to);
            Platform.runLater(() -> {
                if (chartAcquisition == null) return;
                chartAcquisition.getData().clear();
                XYChart.Series<String, Number> s = new XYChart.Series<>();
                s.setName("New buyers (first order)");
                for (CustomerDao.MonthlyCount m : monthly) {
                    s.getData().add(new XYChart.Data<>(m.month, (double) m.count));
                }
                chartAcquisition.getData().add(s);
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                if (chartAcquisition != null) chartAcquisition.getData().clear();
            });
        }
    }
}
