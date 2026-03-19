package com.raez.finance.controller;

import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.ProductReportRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProductProfitabilityController {

    // ── Margin threshold — everything below is "needs attention" ─────────
    private static final double LOW_MARGIN_THRESHOLD = 35.0;

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbCategoryFilter;

    @FXML private Label lblTotalRevenue;
    @FXML private Label lblRevenueSub;
    @FXML private Label lblTotalProfit;
    @FXML private Label lblProfitSub;
    @FXML private Label lblAvgMargin;
    @FXML private Label lblLowMarginCount;
    @FXML private Label lblLowMarginSub;
    @FXML private Label lblProductCount;
    @FXML private Label lblTableSummary;

    @FXML private BarChart<String, Number> chartProfitability;
    @FXML private HBox chartLegend;

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
    @FXML private Label lblHighCount;
    @FXML private Label lblNoHighPerformers;
    @FXML private VBox vboxNeedsAttention;
    @FXML private Label lblLowCount;
    @FXML private Label lblNoNeedsAttention;

    @FXML private MenuButton exportMenuButton;

    // ── Services ──────────────────────────────────────────────────────────
    private final ProductDao productDao = new ProductDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();
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

        // Chart colours via CSS
        chartProfitability.setStyle(
            ".default-color0.chart-bar { -fx-bar-fill: #1E2939; }" +
            ".default-color1.chart-bar { -fx-bar-fill: #10B981; }"
        );
        buildChartLegend();

        loadCategoryOptions();
        cmbCategoryFilter.valueProperty().addListener((obs, o, n) -> loadData());
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
                if (empty || v == null) return;
                setText(CurrencyUtil.formatCurrency(v.doubleValue()));
                setStyle(v.doubleValue() >= 0 ? "-fx-text-fill: #16A34A;" : "-fx-text-fill: #DC2626;");
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
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                               "-fx-padding: 2 7 2 7; -fx-background-radius: 999;" +
                               (pct >= LOW_MARGIN_THRESHOLD
                                    ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                               : pct >= 20
                                    ? "-fx-background-color: #FEF9C3; -fx-text-fill: #92400E;"
                                    : "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;"));
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
                lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: " + colour + ";");
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
            ObservableList<String> items = FXCollections.observableArrayList("All Categories");
            if (task.getValue() != null) items.addAll(task.getValue());
            cmbCategoryFilter.setItems(items);
            cmbCategoryFilter.setValue("All Categories");
        });
        executor.execute(task);
    }

    private void loadData() {
        String categoryFilter = cmbCategoryFilter.getValue();

        Task<Void> task = new Task<>() {
            List<ProductReportRow> products;
            List<ProductDao.CategoryRevenueProfit> catData;
            double totalRevenue, totalProfit, avgMargin;
            long lowCount;

            @Override protected Void call() throws Exception {
                products   = productDao.findReportRows(null, null, categoryFilter, null);
                catData    = productDao.findCategoryRevenueProfit();
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
                animateLabel(lblTotalRevenue, CurrencyUtil.formatCurrency(totalRevenue));
                animateLabel(lblTotalProfit,  CurrencyUtil.formatCurrency(totalProfit));
                animateLabel(lblAvgMargin,    String.format("%.1f%%", avgMargin));
                animateLabel(lblLowMarginCount, String.valueOf(lowCount));

                // Dynamic sub-labels
                if (lblRevenueSub  != null) lblRevenueSub.setText(products.size() + " products");
                if (lblProfitSub   != null) lblProfitSub.setText(String.format("%.1f%% margin overall", avgMargin));
                if (lblLowMarginSub != null) lblLowMarginSub.setText("Below " + (int) LOW_MARGIN_THRESHOLD + "% threshold");

                // Table
                productItems.setAll(products);
                if (lblProductCount != null) lblProductCount.setText(products.size() + " products");
                if (lblTableSummary != null) {
                    lblTableSummary.setText(String.format(
                        "Total: %s revenue · %s profit",
                        CurrencyUtil.formatCurrency(totalRevenue),
                        CurrencyUtil.formatCurrency(totalProfit)));
                }
                setTableHeight(tblProducts, products.size());

                // Chart
                chartProfitability.getData().clear();
                XYChart.Series<String, Number> revSeries = new XYChart.Series<>();
                revSeries.setName("Revenue");
                XYChart.Series<String, Number> profSeries = new XYChart.Series<>();
                profSeries.setName("Profit");
                for (ProductDao.CategoryRevenueProfit c : catData) {
                    revSeries.getData().add(new XYChart.Data<>(c.category, c.revenue));
                    profSeries.getData().add(new XYChart.Data<>(c.category, c.profit));
                }
                chartProfitability.getData().add(revSeries);
                chartProfitability.getData().add(profSeries);
                // Re-apply bar colours after data load (JavaFX resets them)
                Platform.runLater(() -> {
                    chartProfitability.lookupAll(".default-color0.chart-bar")
                        .forEach(n -> n.setStyle("-fx-bar-fill: #1E2939;"));
                    chartProfitability.lookupAll(".default-color1.chart-bar")
                        .forEach(n -> n.setStyle("-fx-bar-fill: #10B981;"));
                });

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

    // ══════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void buildPerformerList(VBox container, Label emptyLabel, Label countLabel,
                                    List<ProductReportRow> rows,
                                    String textColor, String hoverBg) {
        container.getChildren().clear();
        if (rows.isEmpty()) {
            if (emptyLabel != null) { emptyLabel.setManaged(true); emptyLabel.setVisible(true); }
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

            // Hover highlight
            row.setOnMouseEntered(e -> row.setStyle("-fx-padding: 10 20 10 20; -fx-cursor: hand;" +
                "-fx-background-color: " + hoverBg + ";"));
            row.setOnMouseExited(e  -> row.setStyle("-fx-padding: 10 20 10 20; -fx-cursor: hand;"));

            // Separator between rows
            if (!container.getChildren().isEmpty()) {
                javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
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

    private void setTableHeight(TableView<?> t, int rows) {
        if (t == null) return;
        double h = 38 + Math.max(rows, 4) * 44.0;
        t.setPrefHeight(h); t.setMinHeight(h);
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

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleExportCSV(ActionEvent e) { doExport("csv"); }
    @FXML private void handleExportPDF(ActionEvent e) { doExport("pdf"); }

    private void doExport(String format) {
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
            if ("csv".equals(format)) ExportService.exportToCSV(tblProducts, file);
            else ExportService.exportToPDF(tblProducts, "Product Profitability Report", file);
            toast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown"));
        }
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