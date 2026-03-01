package com.raez.finance.controller;

import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.ProductReportRow;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductProfitabilityController {

    private static final double MARGIN_HIGH_THRESHOLD_PERCENT = 35.0;

    private final ProductDao productDao = new ProductDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();

    @FXML private ComboBox<String> cmbCategoryFilter;

    @FXML private Label lblTotalRevenue;
    @FXML private Label lblTotalProfit;
    @FXML private Label lblAvgMargin;
    @FXML private Label lblLowMarginCount;

    @FXML private BarChart<String, Number> chartProfitability;
    @FXML private TableView<ProductReportRow> tblProducts;
    @FXML private TableColumn<ProductReportRow, String> colName;
    @FXML private TableColumn<ProductReportRow, String> colCategory;
    @FXML private TableColumn<ProductReportRow, Number> colRevenue;
    @FXML private TableColumn<ProductReportRow, Number> colCost;
    @FXML private TableColumn<ProductReportRow, Number> colProfit;
    @FXML private TableColumn<ProductReportRow, Number> colMargin;
    @FXML private TableColumn<ProductReportRow, Number> colUnits;
    @FXML private TableColumn<ProductReportRow, String> colTrend;

    @FXML private VBox vboxHighPerformers;
    @FXML private VBox vboxNeedsAttention;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colRevenue.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        colCost.setCellValueFactory(new PropertyValueFactory<>("cost"));
        colProfit.setCellValueFactory(new PropertyValueFactory<>("profit"));
        colMargin.setCellValueFactory(new PropertyValueFactory<>("marginPercent"));
        colUnits.setCellValueFactory(new PropertyValueFactory<>("unitsSold"));
        colTrend.setCellValueFactory(new PropertyValueFactory<>("trend"));

        tblProducts.setItems(productItems);

        loadCategoryOptions();
        cmbCategoryFilter.valueProperty().addListener((obs, oldV, newV) -> loadData());

        loadData();
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
                ObservableList<String> items = FXCollections.observableArrayList("All Categories");
                items.addAll(task.getValue());
                cmbCategoryFilter.setItems(items);
                cmbCategoryFilter.setValue("All Categories");
            }
        });
        executor.execute(task);
    }

    private void loadData() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String categoryFilter = cmbCategoryFilter.getValue();
                List<ProductReportRow> products = productDao.findReportRows(null, null, categoryFilter, null);
                List<ProductDao.CategoryRevenueProfit> categoryData = productDao.findCategoryRevenueProfit();

                double totalRevenue = products.stream().mapToDouble(ProductReportRow::getRevenue).sum();
                double totalProfit = products.stream().mapToDouble(ProductReportRow::getProfit).sum();
                double avgMargin = products.isEmpty() ? 0 : products.stream().mapToDouble(ProductReportRow::getMarginPercent).average().orElse(0);
                long lowMarginCount = products.stream().filter(p -> p.getMarginPercent() < MARGIN_HIGH_THRESHOLD_PERCENT).count();

                List<ProductReportRow> highPerformers = products.stream()
                        .filter(p -> p.getMarginPercent() >= MARGIN_HIGH_THRESHOLD_PERCENT)
                        .toList();
                List<ProductReportRow> needsAttention = products.stream()
                        .filter(p -> p.getMarginPercent() < MARGIN_HIGH_THRESHOLD_PERCENT && p.getRevenue() > 0)
                        .toList();

                Platform.runLater(() -> {
                    lblTotalRevenue.setText(String.format("$%,.2f", totalRevenue));
                    lblTotalProfit.setText(String.format("$%,.2f", totalProfit));
                    lblAvgMargin.setText(String.format("%.1f%%", avgMargin));
                    lblLowMarginCount.setText(String.valueOf(lowMarginCount));

                    productItems.clear();
                    productItems.addAll(products);

                    chartProfitability.getData().clear();
                    XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
                    revenueSeries.setName("Revenue");
                    XYChart.Series<String, Number> profitSeries = new XYChart.Series<>();
                    profitSeries.setName("Profit");
                    for (ProductDao.CategoryRevenueProfit c : categoryData) {
                        revenueSeries.getData().add(new XYChart.Data<>(c.category, c.revenue));
                        profitSeries.getData().add(new XYChart.Data<>(c.category, c.profit));
                    }
                    chartProfitability.getData().add(revenueSeries);
                    chartProfitability.getData().add(profitSeries);

                    vboxHighPerformers.getChildren().clear();
                    for (ProductReportRow p : highPerformers) {
                        Label lbl = new Label(String.format("%s — %.1f%% margin", p.getName(), p.getMarginPercent()));
                        lbl.setWrapText(true);
                        lbl.setStyle("-fx-text-fill: #166534;");
                        vboxHighPerformers.getChildren().add(lbl);
                    }
                    if (highPerformers.isEmpty()) {
                        vboxHighPerformers.getChildren().add(new Label("No high performers (margin ≥ " + (int) MARGIN_HIGH_THRESHOLD_PERCENT + "%)."));
                    }

                    vboxNeedsAttention.getChildren().clear();
                    for (ProductReportRow p : needsAttention) {
                        Label lbl = new Label(String.format("%s — %.1f%% margin", p.getName(), p.getMarginPercent()));
                        lbl.setWrapText(true);
                        lbl.setStyle("-fx-text-fill: #9A3412;");
                        vboxNeedsAttention.getChildren().add(lbl);
                    }
                    if (needsAttention.isEmpty()) {
                        vboxNeedsAttention.getChildren().add(new Label("No products below " + (int) MARGIN_HIGH_THRESHOLD_PERCENT + "% margin."));
                    }
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    @FXML
    private void handleExportCSV(ActionEvent event) {
        System.out.println("Exporting Product Profitability to CSV...");
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        System.out.println("Exporting Product Profitability to PDF...");
    }
}
