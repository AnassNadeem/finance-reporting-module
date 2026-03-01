package com.raez.finance.controller;

import com.raez.finance.service.DashboardService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverviewController {

    private final DashboardService dashboardService = new DashboardService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // --- Filters ---
    @FXML private ComboBox<String> cmbDateRange;
    @FXML private ComboBox<String> cmbCategory;
    @FXML private VBox boxStartDate;
    @FXML private VBox boxEndDate;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;

    // --- KPI Labels ---
    @FXML private Label lblTotalSales;
    @FXML private Label lblTotalProfit;
    @FXML private Label lblOutstanding;
    @FXML private Label lblRefunds;
    @FXML private Label lblCustomers;
    @FXML private Label lblOrders;
    @FXML private Label lblAOV;
    @FXML private Label lblPopular;

    // --- Charts & Dynamic Content ---
    @FXML private LineChart<String, Number> chartSales;
    @FXML private PieChart chartRevenue;
    @FXML private VBox vboxTopProducts;
    @FXML private VBox vboxAlerts;

    @FXML
    public void initialize() {
        cmbDateRange.setItems(FXCollections.observableArrayList(
            "Last 7 days", "Last 30 days", "Last 90 days", "Last year", "Custom Range"
        ));
        cmbDateRange.setValue("Last 30 days");

        cmbCategory.setItems(FXCollections.observableArrayList(
            "All Categories", "Drones", "Robots", "Services", "Accessories"
        ));
        cmbCategory.setValue("All Categories");

        cmbDateRange.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = "Custom Range".equals(newVal);
            boxStartDate.setVisible(isCustom);
            boxStartDate.setManaged(isCustom);
            boxEndDate.setVisible(isCustom);
            boxEndDate.setManaged(isCustom);
            loadDashboardData();
        });
        dpStartDate.valueProperty().addListener((obs, o, n) -> loadDashboardData());
        dpEndDate.valueProperty().addListener((obs, o, n) -> loadDashboardData());

        loadDashboardData();
    }

    private void loadDashboardData() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                LocalDate[] range = resolveDateRange();
                LocalDate from = range[0];
                LocalDate to = range[1];

                double totalSales = dashboardService.getTotalSales(from, to);
                double totalProfit = dashboardService.getTotalProfit(from, to);
                double outstanding = dashboardService.getOutstandingPayments(from, to);
                double refunds = dashboardService.getRefunds(from, to);
                int customers = dashboardService.getTotalCustomers();
                int orders = dashboardService.getTotalOrders(from, to);
                double aov = dashboardService.getAverageOrderValue(from, to);
                String popular = dashboardService.getMostPopularProductName(from, to);
                List<DashboardService.DataPoint<String, Number>> timeSeries = dashboardService.getSalesTimeSeries(from, to);
                List<DashboardService.DataPoint<String, Number>> categoryRevenue = dashboardService.getCategoryRevenue(from, to);

                Platform.runLater(() -> {
                    lblTotalSales.setText(formatCurrency(totalSales));
                    lblTotalProfit.setText(formatCurrency(totalProfit));
                    lblOutstanding.setText(formatCurrency(outstanding));
                    lblRefunds.setText(formatCurrency(refunds));
                    lblCustomers.setText(String.format("%,d", customers));
                    lblOrders.setText(String.format("%,d", orders));
                    lblAOV.setText(formatCurrency(aov));
                    lblPopular.setText(popular != null ? popular : "—");

                    chartSales.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    for (DashboardService.DataPoint<String, Number> p : timeSeries) {
                        series.getData().add(new XYChart.Data<>(p.x, p.y));
                    }
                    chartSales.getData().add(series);

                    chartRevenue.getData().clear();
                    for (DashboardService.DataPoint<String, Number> p : categoryRevenue) {
                        chartRevenue.getData().add(new PieChart.Data(p.x, p.y.doubleValue()));
                    }
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((obs, o, ex) -> {
            if (ex != null) ex.printStackTrace();
        });
        executor.execute(task);
    }

    private LocalDate[] resolveDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = cmbDateRange.getValue();
        if (val == null) val = "Last 30 days";
        switch (val) {
            case "Custom Range":
                from = dpStartDate.getValue() != null ? dpStartDate.getValue() : to.minusDays(30);
                to = dpEndDate.getValue() != null ? dpEndDate.getValue() : to;
                if (from.isAfter(to)) from = to;
                break;
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
        return new LocalDate[]{ from, to };
    }

    private static String formatCurrency(double value) {
        return String.format("$%,.2f", value);
    }

    @FXML
    private void handleExportCSV(ActionEvent event) {
        System.out.println("Exporting Dashboard to CSV...");
        // Cursor will connect this to your Report generation logic
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        System.out.println("Exporting Dashboard to PDF...");
    }
}