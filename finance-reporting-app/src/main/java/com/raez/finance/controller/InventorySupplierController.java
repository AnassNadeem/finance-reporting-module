package com.raez.finance.controller;

import com.raez.finance.dao.InventorySupplierDao;
import com.raez.finance.dao.InventorySupplierDaoInterface;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inventory Valuation & Supplier Performance
 *
 * New vs original:
 *  - Added ExportService wiring (CSV + PDF) with FileChooser
 *  - Added Low-Stock table (tableLowStock / LowStockRow)
 *  - Added colLeadDays, colRating columns for richer supplier data
 *  - Applied CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS to both tables
 *  - Applied row-hover factory to both tables
 *  - Animated KPI label fade-in via FadeTransition
 *  - Runs DB/mock load on a background thread; all UI updates on FX thread
 */
public class InventorySupplierController {

    // ── FXML injections ────────────────────────────────────────────────────
    @FXML private ScrollPane pageScrollPane;

    @FXML private Label lblStockValue;
    @FXML private Label lblCogs;
    @FXML private Label lblKpiProducts;
    @FXML private Label lblKpiSuppliers;
    @FXML private Label lblKpiLowStock;
    @FXML private Label lblKpiOutOfStock;

    @FXML private TableView<InventorySupplierDao.ProductInventoryRow> tableProducts;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number>  colProdId;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, String> colProdName;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, String> colProdCat;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number> colProdStock;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number> colProdReorder;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number> colProdCost;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number> colProdPrice;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, Number> colProdMargin;
    @FXML private TableColumn<InventorySupplierDao.ProductInventoryRow, String> colProdStatus;

    @FXML private TableView<SupplierRow>  tableSuppliers;
    @FXML private TableColumn<SupplierRow, String>  colSupplierName;
    @FXML private TableColumn<SupplierRow, String>  colContact;
    @FXML private TableColumn<SupplierRow, Number>  colLeadDays;
    @FXML private TableColumn<SupplierRow, Number>  colReliability;
    @FXML private TableColumn<SupplierRow, String>  colRating;
    @FXML private Label lblSupplierCount;
    @FXML private Label lblAvgReliability;

    @FXML private TableView<LowStockRow>  tableLowStock;
    @FXML private TableColumn<LowStockRow, String>  colLowStockProduct;
    @FXML private TableColumn<LowStockRow, String>  colLowStockCategory;
    @FXML private TableColumn<LowStockRow, Number>  colCurrentStock;
    @FXML private TableColumn<LowStockRow, Number>  colReorderLevel;
    @FXML private TableColumn<LowStockRow, String>  colStockStatus;
    @FXML private Label lblLowStockCount;

    @FXML private MenuButton exportMenuButton;
    @FXML private MenuItem   exportCsvItem;
    @FXML private MenuItem   exportPdfItem;

    // ── Services / helpers ─────────────────────────────────────────────────
    private MainLayoutController mainLayoutController;
    private final InventorySupplierDaoInterface inventorySupplierDao = new InventorySupplierDao();
    private final DashboardService dashboardService = new DashboardService();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ObservableList<InventorySupplierDao.ProductInventoryRow> productItems =
        FXCollections.observableArrayList();
    private final ObservableList<SupplierRow>  supplierItems  = FXCollections.observableArrayList();
    private final ObservableList<LowStockRow>  lowStockItems  = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════
    //  MODEL CLASSES
    // ══════════════════════════════════════════════════════════════════════

    public static class SupplierRow {
        private final String name;
        private final String contact;
        private final double leadDays;
        private final double reliabilityScore;
        private final String rating;
        private final boolean lowReliability;

        public SupplierRow(InventorySupplierDao.SupplierSnapshot s) {
            this.name = s.name();
            String c = s.contact() != null ? s.contact() : "";
            String e = s.email() != null && !s.email().isBlank() ? s.email() : "";
            this.contact = e.isEmpty() ? c : (c.isEmpty() ? e : c + " · " + e);
            this.leadDays = s.leadDays() > 0 ? s.leadDays() : 0;
            double rs = s.reliabilityScore();
            this.lowReliability = rs < 0.80;
            this.reliabilityScore = rs <= 1.0 ? rs * 100.0 : rs;
            if      (reliabilityScore >= 95) this.rating = "⭐⭐⭐⭐⭐ Excellent";
            else if (reliabilityScore >= 85) this.rating = "⭐⭐⭐⭐  Good";
            else if (reliabilityScore >= 70) this.rating = "⭐⭐⭐   Average";
            else                             this.rating = "⭐⭐    Poor";
        }

        public String getName()             { return name; }
        public String getContact()          { return contact; }
        public double getLeadDays()         { return leadDays; }
        public double getReliabilityScore() { return reliabilityScore; }
        public String getRating()           { return rating; }
        public boolean isLowReliability()   { return lowReliability; }
    }

    public static class LowStockRow {
        private final String name;
        private final String category;
        private final int    currentStock;
        private final int    reorderLevel;
        private final String status;

        public LowStockRow(String name, String category, int currentStock, int reorderLevel) {
            this.name         = name;
            this.category     = category;
            this.currentStock = currentStock;
            this.reorderLevel = reorderLevel;
            this.status       = currentStock == 0 ? "Out of Stock"
                              : currentStock <= reorderLevel / 2 ? "Critical"
                              : "Low";
        }

        public String getName()         { return name; }
        public String getCategory()     { return category; }
        public int    getCurrentStock() { return currentStock; }
        public int    getReorderLevel() { return reorderLevel; }
        public String getStatus()       { return status; }
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

        bindSupplierColumns();
        bindLowStockColumns();
        bindProductColumns();

        tableSuppliers.setItems(supplierItems);
        tableLowStock.setItems(lowStockItems);

        // Constrained resize — must be done in Java, not FXML
        if (tableProducts != null) {
            tableProducts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            applyRowFactory(tableProducts);
        }
        tableSuppliers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tableLowStock.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        applyRowFactory(tableSuppliers);
        applyRowFactory(tableLowStock);

        loadDataAsync();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindSupplierColumns() {
        colSupplierName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));

        colLeadDays.setCellValueFactory(new PropertyValueFactory<>("leadDays"));
        colLeadDays.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%.0f days", v.doubleValue()));
            }
        });

        colReliability.setCellValueFactory(new PropertyValueFactory<>("reliabilityScore"));
        colReliability.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(null); setText(null);
                if (empty || v == null) return;
                double pct = v.doubleValue();
                Label badge = new Label(String.format("%.1f%%", pct));
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                               "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                               (pct >= 90 ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;"
                              : pct >= 75 ? "-fx-background-color: #FEF9C3; -fx-text-fill: #92400E;"
                              :             "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;"));
                HBox w = new HBox(badge);
                w.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });

        colRating.setCellValueFactory(new PropertyValueFactory<>("rating"));
        colRating.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                int idx = getIndex();
                SupplierRow row = (idx >= 0 && idx < getTableView().getItems().size())
                    ? getTableView().getItems().get(idx) : null;
                HBox h = new HBox(8);
                h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                h.getChildren().add(new Label(item));
                if (row != null && row.isLowReliability()) {
                    Label b = new Label("LOW RELIABILITY");
                    b.setStyle("-fx-font-size: 9px; -fx-font-weight: 700; -fx-text-fill: #92400E; " +
                        "-fx-background-color: #FEF9C3; -fx-padding: 2 6; -fx-background-radius: 4;");
                    h.getChildren().add(b);
                }
                setGraphic(h);
            }
        });
    }

    private void bindProductColumns() {
        if (tableProducts == null) return;
        tableProducts.setItems(productItems);
        colProdId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().productID()));
        colProdName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        colProdCat.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().category()));
        colProdStock.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().stockLevel()));
        colProdReorder.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().reorderLevel()));
        colProdCost.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().unitCost()));
        colProdCost.setCellFactory(CurrencyUtil.currencyCellFactory());
        colProdPrice.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().salePrice()));
        colProdPrice.setCellFactory(CurrencyUtil.currencyCellFactory());
        colProdMargin.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().marginPercent()));
        colProdMargin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                if (empty || v == null) return;
                setText(String.format("%.1f%%", v.doubleValue()));
            }
        });
        colProdStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().statusLabel()));
    }

    private void bindLowStockColumns() {
        colLowStockProduct.setCellValueFactory(new PropertyValueFactory<>("name"));
        colLowStockCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colCurrentStock.setCellValueFactory(new PropertyValueFactory<>("currentStock"));
        colReorderLevel.setCellValueFactory(new PropertyValueFactory<>("reorderLevel"));

        colStockStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStockStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                               "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                               switch (item) {
                                   case "Out of Stock" -> "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;";
                                   case "Critical"     -> "-fx-background-color: #FEF9C3; -fx-text-fill: #92400E;";
                                   default             -> "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;";
                               });
                HBox w = new HBox(badge);
                w.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadDataAsync() {
        Task<Void> task = new Task<>() {
            double stockValue, cogs;
            int kpiProducts, kpiSuppliers, kpiLow, kpiOut;
            java.util.List<InventorySupplierDao.SupplierSnapshot> suppliers;
            java.util.List<InventorySupplierDao.LowStockSnapshot> lowStock;
            java.util.List<InventorySupplierDao.ProductInventoryRow> products;

            @Override protected Void call() {
                stockValue = 0;
                cogs = 0;
                kpiProducts = kpiSuppliers = kpiLow = kpiOut = 0;
                suppliers = java.util.List.of();
                lowStock = java.util.List.of();
                products = java.util.List.of();
                LocalDate to   = LocalDate.now();
                LocalDate from = to.minusMonths(3);
                try {
                    stockValue = inventorySupplierDao.getCurrentStockValue();
                    cogs = dashboardService.getTotalCogs(from, to, null);
                    suppliers = inventorySupplierDao.findSuppliers();
                    lowStock = inventorySupplierDao.findLowStockItems();
                    products = inventorySupplierDao.findProductInventoryRows();
                    kpiProducts = inventorySupplierDao.countActiveProducts();
                    kpiSuppliers = inventorySupplierDao.countSuppliers();
                    kpiLow = inventorySupplierDao.countLowStockProducts();
                    kpiOut = inventorySupplierDao.countOutOfStockProducts();
                } catch (Exception ignored) {
                }
                return null;
            }

            @Override protected void succeeded() {
                // KPI labels with fade-in animation
                animateLabel(lblStockValue, CurrencyUtil.formatCurrency(stockValue));
                animateLabel(lblCogs,       CurrencyUtil.formatCurrency(cogs));
                if (lblKpiProducts != null) lblKpiProducts.setText(String.valueOf(kpiProducts));
                if (lblKpiSuppliers != null) lblKpiSuppliers.setText(String.valueOf(kpiSuppliers));
                if (lblKpiLowStock != null) lblKpiLowStock.setText(String.valueOf(kpiLow));
                if (lblKpiOutOfStock != null) lblKpiOutOfStock.setText(String.valueOf(kpiOut));

                productItems.setAll(products != null ? products : java.util.List.of());
                setTableHeight(tableProducts, productItems.size());

                // Supplier table
                supplierItems.clear();
                for (InventorySupplierDao.SupplierSnapshot s : suppliers) {
                    supplierItems.add(new SupplierRow(s));
                }
                // Supplier count & avg reliability
                if (lblSupplierCount != null)
                    lblSupplierCount.setText(suppliers.size() + " supplier" + (suppliers.size() == 1 ? "" : "s"));
                if (lblAvgReliability != null && !suppliers.isEmpty()) {
                    double avg = supplierItems.stream()
                        .mapToDouble(SupplierRow::getReliabilityScore).average().orElse(0);
                    lblAvgReliability.setText(String.format("Average on-time rate: %.1f%%", avg));
                }

                lowStockItems.clear();
                var rawLowStock = lowStock.stream()
                    .map(i -> new LowStockRow(i.productName(), i.categoryName(), i.currentStock(), i.reorderLevel()))
                    .toList();
                lowStockItems.addAll(rawLowStock);
                if (lblLowStockCount != null)
                    lblLowStockCount.setText(lowStockItems.size() + " item" + (lowStockItems.size() == 1 ? "" : "s"));

                // Table heights
                setTableHeight(tableSuppliers, supplierItems.size());
                setTableHeight(tableLowStock,  lowStockItems.size());
            }

            @Override protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    private void animateLabel(Label lbl, String value) {
        if (lbl == null) return;
        lbl.setOpacity(0);
        lbl.setText(value);
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(500), lbl);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void setTableHeight(TableView<?> table, int rows) {
        if (table == null) return;
        double h = 38 + Math.max(rows, 3) * 44.0;
        table.setPrefHeight(h);
        table.setMinHeight(h);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROW FACTORY (hover highlight + stripes)
    // ══════════════════════════════════════════════════════════════════════

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

    @FXML private void handleExportCsv() { exportTable("csv"); }
    @FXML private void handleExportPdf() { exportTable("pdf"); }

    private void exportTable(String format) {
        if (!SessionManager.isAdmin()) return;
        javafx.stage.Window window = tableSuppliers.getScene() != null
            ? tableSuppliers.getScene().getWindow() : null;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export Inventory & Supplier Report");
        String ext = format.equals("csv") ? "*.csv" : "*.pdf";
        String desc = format.equals("csv") ? "CSV Files" : "PDF Files";
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        fc.setInitialFileName("inventory_supplier_report." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;

        try {
            if ("csv".equals(format)) {
                ExportService.exportToCSV(tableSuppliers, file);
            } else {
                ExportService.exportToPDF(tableSuppliers, "Inventory & Supplier Performance Report", file);
            }
            showToast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception e) {
            showToast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    private java.util.List<String[]> buildExportData() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[]{"Section", "Col1", "Col2", "Col3", "Col4", "Col5", "Col6", "Col7", "Col8"});
        rows.add(new String[]{"PRODUCTS", "", "", "", "", "", "", "", ""});
        rows.add(new String[]{"ID", "Name", "Category", "Stock", "Reorder", "Unit cost", "Sale price", "Margin %", "Status"});
        for (InventorySupplierDao.ProductInventoryRow p : productItems) {
            rows.add(new String[]{
                String.valueOf(p.productID()), p.name(), p.category(),
                String.valueOf(p.stockLevel()), String.valueOf(p.reorderLevel()),
                CurrencyUtil.formatCurrency(p.unitCost()),
                CurrencyUtil.formatCurrency(p.salePrice()),
                String.format("%.1f%%", p.marginPercent()), p.statusLabel()
            });
        }
        rows.add(new String[]{"SUPPLIERS", "", "", "", "", "", "", "", ""});
        rows.add(new String[]{"Name", "Contact", "Lead days", "Reliability %", "Rating", "Flags", "", "", ""});
        for (SupplierRow s : supplierItems) {
            rows.add(new String[]{
                s.getName(), s.getContact(), String.format("%.0f", s.getLeadDays()),
                String.format("%.1f", s.getReliabilityScore()), s.getRating(),
                s.isLowReliability() ? "Low reliability" : "", "", "", ""
            });
        }
        rows.add(new String[]{"LOW STOCK", "", "", "", "", "", "", "", ""});
        rows.add(new String[]{"Product", "Category", "Stock", "Reorder", "Status", "", "", "", ""});
        for (LowStockRow l : lowStockItems) {
            rows.add(new String[]{l.getName(), l.getCategory(), String.valueOf(l.getCurrentStock()),
                String.valueOf(l.getReorderLevel()), l.getStatus(), "", "", "", ""});
        }
        return rows;
    }

    private void showToast(String type, String msg) {
        if (mainLayoutController != null) {
            mainLayoutController.showToast(type, msg);
        } else {
            Alert.AlertType t = "success".equals(type) ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
            new Alert(t, msg).showAndWait();
        }
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}