package com.raez.finance.controller;

import com.raez.finance.dao.ProductDao;
import com.raez.finance.service.DashboardService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OverviewController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";

    private final DashboardService dashboardService = new DashboardService();
    private final ProductDao productDao = new ProductDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

    /**
     * Shuts down the executor so the application can terminate gracefully.
     * Call this when the controller is no longer needed (e.g. when navigating away).
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

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

        loadCategoryOptions();
        cmbCategory.setValue("All Categories");

        if (chartSales != null && chartSales.getXAxis() != null) {
            chartSales.getXAxis().setTickLabelRotation(-45);
        }

        cmbDateRange.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCustom = "Custom Range".equals(newVal);
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
            @Override
            protected List<String> call() throws Exception {
                return productDao.findCategoryNames();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                List<String> items = FXCollections.observableArrayList("All Categories");
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
            @Override
            protected Void call() throws Exception {
                LocalDate[] range = resolveDateRange();
                LocalDate from = range[0];
                LocalDate to = range[1];

                double totalSales = dashboardService.getTotalSales(from, to, category);
                double totalProfit = dashboardService.getTotalProfit(from, to, category);
                double outstanding = dashboardService.getOutstandingPayments(from, to, category);
                double refunds = dashboardService.getRefunds(from, to, category);
                int customers = dashboardService.getTotalCustomers();
                int orders = dashboardService.getTotalOrders(from, to, category);
                double aov = dashboardService.getAverageOrderValue(from, to, category);
                String popular = dashboardService.getMostPopularProductName(from, to, category);
                List<DashboardService.DataPoint<String, Number>> timeSeries = dashboardService.getSalesTimeSeries(from, to, category);
                List<DashboardService.DataPoint<String, Number>> categoryRevenue = dashboardService.getCategoryRevenue(from, to, category);
                List<DashboardService.TopProductRow> topProducts = dashboardService.getTopProductsByQuantity(from, to, category, 3);
                List<String> lowStock = dashboardService.getLowStockAlerts(20);
                List<String> overdue = dashboardService.getOverduePaymentAlerts();

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

                    buildTopProducts(topProducts);
                    buildAlerts(lowStock, overdue);
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((obs, o, ex) -> {
            if (ex != null) ex.printStackTrace();
        });
        executor.execute(task);
    }

    private void buildTopProducts(List<DashboardService.TopProductRow> topProducts) {
        vboxTopProducts.getChildren().clear();
        for (DashboardService.TopProductRow row : topProducts) {
            HBox line = new HBox(12);
            line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox rankBox = new HBox();
            rankBox.setAlignment(javafx.geometry.Pos.CENTER);
            rankBox.setStyle("-fx-background-color: #F1F5F9; -fx-background-radius: 8;");
            rankBox.setMinSize(32, 32);
            rankBox.setPrefSize(32, 32);
            Label rankLbl = new Label("#" + row.rank);
            rankLbl.setStyle("-fx-text-fill: #1E293B; -fx-font-weight: bold; -fx-font-size: 12;");
            rankBox.getChildren().add(rankLbl);
            VBox mid = new VBox(2);
            mid.setMaxWidth(Double.MAX_VALUE);
            mid.getChildren().add(new Label(row.name));
            ((Label) mid.getChildren().get(0)).setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 14;");
            mid.getChildren().add(new Label(row.quantitySold + " units sold"));
            ((Label) mid.getChildren().get(1)).setStyle("-fx-text-fill: #4B5563; -fx-font-size: 12;");
            Label revLbl = new Label(formatCurrency(row.revenue));
            revLbl.setStyle("-fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 14;");
            line.getChildren().addAll(rankBox, mid, revLbl);
            HBox.setHgrow(mid, Priority.ALWAYS);
            VBox.setMargin(line, new javafx.geometry.Insets(0, 0, 16, 0));
            vboxTopProducts.getChildren().add(line);
        }
        Button seeMore = new Button("See More");
        seeMore.setStyle("-fx-background-color: transparent; -fx-text-fill: #1E2939; -fx-underline: true; -fx-cursor: hand;");
        seeMore.setOnAction(e -> navigateToProductProfitability());
        vboxTopProducts.getChildren().add(seeMore);
    }

    private void buildAlerts(List<String> lowStock, List<String> overdue) {
        vboxAlerts.getChildren().clear();
        for (String msg : lowStock) {
            vboxAlerts.getChildren().add(alertRow("Low stock: " + msg));
        }
        for (String msg : overdue) {
            vboxAlerts.getChildren().add(alertRow(msg));
        }
        if (vboxAlerts.getChildren().isEmpty()) {
            Label noAlert = new Label("No alerts at this time.");
            noAlert.setStyle("-fx-text-fill: #6B7280;");
            vboxAlerts.getChildren().add(noAlert);
        }
    }

    private static HBox alertRow(String text) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(3);
        dot.setFill(javafx.scene.paint.Color.web("#D97706"));
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #374151;");
        row.getChildren().addAll(dot, lbl);
        VBox.setMargin(row, new javafx.geometry.Insets(0, 0, 12, 0));
        return row;
    }

    private void navigateToProductProfitability() {
        if (mainLayoutController == null) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + "ProductProfitability.fxml");
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            mainLayoutController.setContent(root);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        List<String[]> data = buildKpiExportData();
        javafx.stage.Window window = getWindow();
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Dashboard to PDF");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("dashboard_summary.pdf");
        java.io.File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            com.raez.finance.service.ExportService.exportRowsToPDF("Dashboard Summary", data, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String[]> buildKpiExportData() {
        List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"Metric", "Value"});
        rows.add(new String[]{"Total Sales", getText(lblTotalSales)});
        rows.add(new String[]{"Total Profit", getText(lblTotalProfit)});
        rows.add(new String[]{"Outstanding Payments", getText(lblOutstanding)});
        rows.add(new String[]{"Refunds / Returns", getText(lblRefunds)});
        rows.add(new String[]{"Total Customers", getText(lblCustomers)});
        rows.add(new String[]{"Total Number of Orders", getText(lblOrders)});
        rows.add(new String[]{"Average Order Value", getText(lblAOV)});
        rows.add(new String[]{"Most Popular Product", getText(lblPopular)});
        return rows;
    }

    private String getText(javafx.scene.control.Label lbl) {
        return lbl != null && lbl.getText() != null ? lbl.getText() : "";
    }

    private javafx.stage.Window getWindow() {
        if (lblTotalSales != null && lblTotalSales.getScene() != null) {
            return lblTotalSales.getScene().getWindow();
        }
        return null;
    }
}