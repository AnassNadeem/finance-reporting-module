package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.CustomerDaoInterface;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.OrderDaoInterface;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.dao.ProductDaoInterface;
import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.OrderReportRow;
import com.raez.finance.model.ProductReportRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DetailedReportsController implements Initializable {

    // ── DAOs ─────────────────────────────────────────────────────────────
    private final OrderDaoInterface    orderDao    = new OrderDao();
    private final ProductDaoInterface  productDao  = new ProductDao();
    private final CustomerDaoInterface customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reports-worker");
        t.setDaemon(true);
        return t;
    });

    // ── Injected ─────────────────────────────────────────────────────────
    private MainLayoutController mainLayoutController;
    private Runnable             afterLoadCallback;

    // ── FXML — shell ─────────────────────────────────────────────────────
    @FXML private VBox       rootContainer;
    @FXML private ScrollPane pageScrollPane;

    // ── FXML — tabs ──────────────────────────────────────────────────────
    @FXML private Button tabOrders;
    @FXML private Button tabProducts;
    @FXML private Button tabCustomers;

    // ── FXML — filters ───────────────────────────────────────────────────
    @FXML private TextField        searchField;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private VBox             customStartDateBox;
    @FXML private VBox             customEndDateBox;
    @FXML private DatePicker       startDatePicker;
    @FXML private DatePicker       endDatePicker;

    @FXML private VBox             orderStatusBox;
    @FXML private ComboBox<String> orderStatusCombo;
    @FXML private VBox             productCategoryBox;
    @FXML private ComboBox<String> productCategoryCombo;
    @FXML private VBox             customerTypeBox;
    @FXML private ComboBox<String> customerTypeCombo;
    @FXML private VBox             customerCountryBox;
    @FXML private ComboBox<String> customerCountryCombo;

    // ── FXML — orders table ───────────────────────────────────────────────
    @FXML private TableView<OrderReportRow>           ordersTable;
    @FXML private TableColumn<OrderReportRow, String> orderIdCol;
    @FXML private TableColumn<OrderReportRow, String> orderCustomerCol;
    @FXML private TableColumn<OrderReportRow, String> orderProductCol;
    @FXML private TableColumn<OrderReportRow, Number> orderAmountCol;
    @FXML private TableColumn<OrderReportRow, String> orderDateCol;
    @FXML private TableColumn<OrderReportRow, String> orderStatusCol;

    // ── FXML — products table ─────────────────────────────────────────────
    @FXML private TableView<ProductReportRow>            productsTable;
    @FXML private TableColumn<ProductReportRow, String>  productIdCol;
    @FXML private TableColumn<ProductReportRow, String>  productNameCol;
    @FXML private TableColumn<ProductReportRow, String>  productCategoryCol;
    @FXML private TableColumn<ProductReportRow, Number>  productCostCol;
    @FXML private TableColumn<ProductReportRow, Number>  productSalePriceCol;
    @FXML private TableColumn<ProductReportRow, Number>  productProfitCol;
    @FXML private TableColumn<ProductReportRow, Number>  productUnitsCol;
    @FXML private TableColumn<ProductReportRow, Number>  productRevenueCol;

    // ── FXML — customers table ────────────────────────────────────────────
    @FXML private TableView<CustomerReportRow>             customersTable;
    @FXML private TableColumn<CustomerReportRow, String>   custIdCol;
    @FXML private TableColumn<CustomerReportRow, String>   custNameCol;
    @FXML private TableColumn<CustomerReportRow, String>   custTypeCol;
    @FXML private TableColumn<CustomerReportRow, String>   custCountryCol;
    @FXML private TableColumn<CustomerReportRow, Number>   custOrdersCol;
    @FXML private TableColumn<CustomerReportRow, Number>   custSpentCol;
    @FXML private TableColumn<CustomerReportRow, Number>   custAvgOrderCol;
    @FXML private TableColumn<CustomerReportRow, String>   custLastPurchaseCol;

    // ── FXML — pagination ────────────────────────────────────────────────
    @FXML private ComboBox<String> rowsPerPageCombo;
    @FXML private Label            pageInfoLabel;
    @FXML private Button           prevPageBtn;
    @FXML private Button           nextPageBtn;

    // ── FXML — export ────────────────────────────────────────────────────
    @FXML private MenuButton exportMenuButton;
    @FXML private MenuItem   exportCsvItem;
    @FXML private MenuItem   exportPdfItem;

    // ── Data ─────────────────────────────────────────────────────────────
    private final ObservableList<OrderReportRow>    orderItems    = FXCollections.observableArrayList();
    private final ObservableList<ProductReportRow>  productItems  = FXCollections.observableArrayList();
    private final ObservableList<CustomerReportRow> customerItems = FXCollections.observableArrayList();

    private String activeTab   = "orders";
    private int    currentPage = 1;
    private int    totalRows   = 0;

    // ══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    public void setMainLayoutController(MainLayoutController mlc) { this.mainLayoutController = mlc; }
    public void setAfterLoadCallback(Runnable r)                  { this.afterLoadCallback = r; }
    public void switchToTab(String tab)                           { showTab(tab); }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindOrderColumns();
        bindProductColumns();
        bindCustomerColumns();

        ordersTable.setItems(orderItems);
        productsTable.setItems(productItems);
        customersTable.setItems(customerItems);

        // MUST be set in Java — never in FXML
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        applyRowFactory(ordersTable);
        applyRowFactory(productsTable);
        applyRowFactory(customersTable);

        dateRangeCombo.setItems(FXCollections.observableArrayList(
            "Last 7 Days", "Last 30 Days", "Last 1 Year", "Year to Date", "Custom"));
        dateRangeCombo.setValue("Last 30 Days");

        loadOrderStatusOptions();
        loadCategoryOptions();

        customerTypeCombo.setItems(FXCollections.observableArrayList("All Types","Company","Individual"));
        customerTypeCombo.setValue("All Types");
        loadCountryOptions();

        rowsPerPageCombo.setValue("10");

        // ── Listeners ────────────────────────────────────────────────────
        rowsPerPageCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; updateTableHeight(); loadCurrentTab(); });
        dateRangeCombo.valueProperty().addListener((obs, o, n) -> {
            boolean custom = "Custom".equals(n);
            setVisible(customStartDateBox, custom);
            setVisible(customEndDateBox,   custom);
            currentPage = 1; loadCurrentTab();
        });
        startDatePicker.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTab(); });
        endDatePicker.valueProperty().addListener((obs, o, n)   -> { currentPage = 1; loadCurrentTab(); });
        orderStatusCombo.valueProperty().addListener((obs, o, n)     -> { currentPage = 1; loadOrders();    });
        productCategoryCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadProducts();  });
        customerTypeCombo.valueProperty().addListener((obs, o, n)    -> { currentPage = 1; loadCustomers(); });
        customerCountryCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCustomers(); });
        searchField.textProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTab(); });

        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        showTab("orders");
        updateTableHeight();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindOrderColumns() {
        orderIdCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        orderCustomerCol.setCellValueFactory(new PropertyValueFactory<>("customer"));
        orderProductCol.setCellValueFactory(new PropertyValueFactory<>("product"));
        orderAmountCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        orderAmountCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        orderDateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        orderStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        orderStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label b = new Label(item);
                String lc = item.trim().toLowerCase();
                if (lc.contains("completed")) b.getStyleClass().add("status-badge-paid");
                else if (lc.contains("pending")) b.getStyleClass().add("status-badge-warning");
                else if (lc.contains("cancelled")) b.getStyleClass().add("status-badge-danger");
                else if (lc.contains("refunded")) b.getStyleClass().add("status-badge-info");
                else b.getStyleClass().add("status-badge-neutral");
                HBox w = new HBox(b); w.setAlignment(Pos.CENTER_LEFT); setGraphic(w);
            }
        });
    }

    private void bindProductColumns() {
        productIdCol.setCellValueFactory(new PropertyValueFactory<>("productId"));
        productNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        productCategoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        productCostCol.setCellValueFactory(new PropertyValueFactory<>("cost"));
        productCostCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        productSalePriceCol.setCellValueFactory(new PropertyValueFactory<>("salePrice"));
        productSalePriceCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        productProfitCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        productProfitCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty); setText(null);
                getStyleClass().removeAll("table-profit-positive", "table-profit-negative");
                if (empty || item == null) return;
                setText(CurrencyUtil.formatCurrency(item.doubleValue()));
                getStyleClass().add(item.doubleValue() >= 0 ? "table-profit-positive" : "table-profit-negative");
            }
        });
        productUnitsCol.setCellValueFactory(new PropertyValueFactory<>("unitsSold"));
        productRevenueCol.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        productRevenueCol.setCellFactory(CurrencyUtil.currencyCellFactory());
    }

    private void bindCustomerColumns() {
        custIdCol.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        custNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        custTypeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        custTypeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label b = new Label(item);
                boolean co = "Company".equalsIgnoreCase(item.trim());
                b.getStyleClass().add(co ? "status-badge-company" : "status-badge-individual");
                HBox w = new HBox(b); w.setAlignment(Pos.CENTER_LEFT); setGraphic(w);
            }
        });
        custCountryCol.setCellValueFactory(new PropertyValueFactory<>("country"));
        custOrdersCol.setCellValueFactory(new PropertyValueFactory<>("totalOrders"));
        custSpentCol.setCellValueFactory(new PropertyValueFactory<>("totalSpent"));
        custSpentCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        custAvgOrderCol.setCellValueFactory(new PropertyValueFactory<>("avgOrderValue"));
        custAvgOrderCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        custLastPurchaseCol.setCellValueFactory(new PropertyValueFactory<>("lastPurchase"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROW FACTORY
    // ══════════════════════════════════════════════════════════════════════

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
            row.setOnMouseExited(e  -> { if (!row.isEmpty()) row.setStyle(row.getIndex() % 2 == 0 ? "-fx-background-color: white;" : "-fx-background-color: #F9FAFB;"); });
            return row;
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  OPTION LOADERS
    // ══════════════════════════════════════════════════════════════════════

    private void loadOrderStatusOptions() {
        runTask(() -> orderDao.findStatusOptions(), statuses -> {
            ObservableList<String> items = FXCollections.observableArrayList("All Status");
            if (statuses != null) items.addAll(statuses);
            for (String s : List.of("Completed","Pending","Cancelled","Refunded"))
                if (!items.contains(s)) items.add(s);
            orderStatusCombo.setItems(items);
            orderStatusCombo.setValue("All Status");
        });
    }

    private void loadCategoryOptions() {
        runTask(() -> productDao.findCategoryNames(), names -> {
            ObservableList<String> items = FXCollections.observableArrayList("All Categories");
            if (names != null) items.addAll(names);
            productCategoryCombo.setItems(items);
            productCategoryCombo.setValue("All Categories");
        });
    }

    private void loadCountryOptions() {
        runTask(() -> customerDao.findCountryOptions(), countries -> {
            ObservableList<String> items = FXCollections.observableArrayList("All");
            if (countries != null) items.addAll(countries);
            customerCountryCombo.setItems(items);
            customerCountryCombo.setValue("All");
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadCurrentTab() {
        switch (activeTab) {
            case "orders"    -> loadOrders();
            case "products"  -> loadProducts();
            case "customers" -> loadCustomers();
        }
    }

    private void loadOrders() {
        int         ps     = getPageSize();
        int         offset = (currentPage - 1) * ps;
        LocalDate[] range  = resolveDateRange();
        String      status = orderStatusCombo.getValue();
        String      search = searchField.getText();

        runTask(() -> orderDao.countReportRows(range[0], range[1], status, search),
            count -> { totalRows = count != null ? count : 0; updatePagination(); });

        runTask(() -> orderDao.findReportRows(range[0], range[1], status, search, ps, offset),
            rows -> { orderItems.setAll(rows != null ? rows : List.of()); fireCallback(); });
    }

    private void loadProducts() {
        int         ps       = getPageSize();
        int         offset   = (currentPage - 1) * ps;
        LocalDate[] range    = resolveDateRange();
        String      category = productCategoryCombo.getValue();
        String      search   = searchField.getText();

        runTask(() -> productDao.countReportRows(range[0], range[1], category, search),
            count -> { totalRows = count != null ? count : 0; updatePagination(); });

        runTask(() -> productDao.findReportRows(range[0], range[1], category, search, ps, offset),
            rows -> { productItems.setAll(rows != null ? rows : List.of()); fireCallback(); });
    }

    private void loadCustomers() {
        int         ps      = getPageSize();
        int         offset  = (currentPage - 1) * ps;
        String      type    = "All Types".equals(customerTypeCombo.getValue()) ? "All" : customerTypeCombo.getValue();
        String      country = customerCountryCombo.getValue() != null ? customerCountryCombo.getValue() : "All";
        String      search  = searchField.getText();

        runTask(() -> customerDao.countReportRows(type, country, null, search),
            count -> { totalRows = count != null ? count : 0; updatePagination(); });

        runTask(() -> customerDao.findReportRows(type, country, null, search, ps, offset),
            rows -> { customerItems.setAll(rows != null ? rows : List.of()); fireCallback(); });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TAB SWITCHING
    // ══════════════════════════════════════════════════════════════════════

    private void showTab(String tab) {
        activeTab   = tab;
        currentPage = 1;

        vis(ordersTable,    tab.equals("orders"));
        vis(productsTable,  tab.equals("products"));
        vis(customersTable, tab.equals("customers"));
        vis(orderStatusBox,     tab.equals("orders"));
        vis(productCategoryBox, tab.equals("products"));
        vis(customerTypeBox,    tab.equals("customers"));
        vis(customerCountryBox, tab.equals("customers"));

        if (searchField != null) searchField.setPromptText("Search " + tab + "...");

        styleTab(tabOrders,    tab.equals("orders"));
        styleTab(tabProducts,  tab.equals("products"));
        styleTab(tabCustomers, tab.equals("customers"));

        updateTableHeight();
        loadCurrentTab();
    }

    private void styleTab(Button btn, boolean active) {
        if (btn == null) return;
        btn.setStyle(active
            ? "-fx-background-color: transparent; -fx-cursor: hand;" +
              "-fx-padding: 16 0 12 0; -fx-font-size: 14px; -fx-font-weight: 600;" +
              "-fx-text-fill: #1E2939;" +
              "-fx-border-color: transparent transparent #1E2939 transparent; -fx-border-width: 0 0 2 0;"
            : "-fx-background-color: transparent; -fx-cursor: hand;" +
              "-fx-padding: 16 0 12 0; -fx-font-size: 14px;" +
              "-fx-text-fill: #6B7280; -fx-border-color: transparent; -fx-border-width: 0 0 2 0;");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FXML HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleTabOrders()           { showTab("orders");   }
    @FXML private void handleTabProducts()         { showTab("products"); }
    @FXML private void handleTabCustomers()        { showTab("customers");}
    @FXML private void handleSearch()              { currentPage = 1; loadCurrentTab(); }
    @FXML private void handleOrderStatusChange()   { currentPage = 1; loadOrders();    }
    @FXML private void handleProductCategoryChange(){ currentPage = 1; loadProducts(); }
    @FXML private void handleCustomerTypeChange()  { currentPage = 1; loadCustomers(); }
    @FXML private void handleCustomerCountryChange(){ currentPage = 1; loadCustomers(); }
    @FXML private void handleRowsPerPageChange()   { currentPage = 1; updateTableHeight(); loadCurrentTab(); }

    @FXML private void handleDateRangeChange() {
        boolean custom = "Custom".equals(dateRangeCombo.getValue());
        setVisible(customStartDateBox, custom);
        setVisible(customEndDateBox,   custom);
        currentPage = 1; loadCurrentTab();
    }

    @FXML private void handlePrevPage() {
        if (currentPage > 1) { currentPage--; loadCurrentTab(); }
    }

    @FXML private void handleNextPage() {
        int pages = Math.max(1, (int) Math.ceil((double) totalRows / getPageSize()));
        if (currentPage < pages) { currentPage++; loadCurrentTab(); }
    }

    @FXML private void handleExportCsv() { performExportCSV(); }
    @FXML private void handleExportPdf() { performExportPDF(); }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════

    public void performExportCSV() {
        if (!SessionManager.isAdmin()) return;
        File file = pickFile(getExportBaseName() + ".csv", "CSV Files", "*.csv");
        if (file == null) return;
        try {
            ExportService.exportRowsToCSV(buildExportData(), file);
            toast("success", "CSV exported: " + file.getName());
        } catch (Exception e) {
            toast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown"));
        }
    }

    public void performExportPDF() {
        if (!SessionManager.isAdmin()) return;
        File file = pickFile(getExportBaseName() + ".pdf", "PDF Files", "*.pdf");
        if (file == null) return;
        try {
            ExportService.exportRowsToPDF(getExportTitle(), buildExportData(), file);
            toast("success", "PDF exported: " + file.getName());
        } catch (Exception e) {
            toast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown"));
        }
    }

    private List<String[]> buildExportData() {
        List<String[]> rows = new ArrayList<>();
        switch (activeTab) {
            case "orders" -> {
                rows.add(new String[]{"Order ID","Customer","Product","Amount","Date","Status"});
                for (OrderReportRow r : orderItems)
                    rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(),
                        CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
            }
            case "products" -> {
                rows.add(new String[]{"Product ID","Name","Category","Cost",
                    "Sale Price","Profit","Units Sold","Revenue"});
                for (ProductReportRow r : productItems)
                    rows.add(new String[]{r.getProductId(), r.getName(), r.getCategory(),
                        CurrencyUtil.formatCurrency(r.getCost()),
                        CurrencyUtil.formatCurrency(r.getSalePrice()),
                        CurrencyUtil.formatCurrency(r.getProfit()),
                        String.valueOf(r.getUnitsSold()),
                        CurrencyUtil.formatCurrency(r.getRevenue())});
            }
            default -> {
                rows.add(new String[]{"Customer ID","Name","Type","Country",
                    "Total Orders","Total Spent","Avg Order","Last Purchase"});
                for (CustomerReportRow r : customerItems)
                    rows.add(new String[]{r.getCustomerId(), r.getName(), r.getType(),
                        r.getCountry(), String.valueOf(r.getTotalOrders()),
                        CurrencyUtil.formatCurrency(r.getTotalSpent()),
                        CurrencyUtil.formatCurrency(r.getAvgOrderValue()),
                        r.getLastPurchase()});
            }
        }
        return rows;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GENERIC TASK RUNNER
    //  Uses java.util.function.Consumer<T> (not Callback<T,Void>) so
    //  lambdas don't need "return null" and type inference works correctly.
    // ══════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }

    /**
     * Runs supplier on executor thread; delivers result to consumer on FX thread.
     * Consumer<T> avoids the Callback<T,Void> "return null" / type-inference problem.
     */
    private <T> void runTask(ThrowingSupplier<T> supplier, Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return supplier.get(); }
        };
        task.setOnSucceeded(e  -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e     -> { if (task.getException() != null) task.getException().printStackTrace(); });
        executor.execute(task);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private LocalDate[] resolveDateRange() {
        LocalDate to   = LocalDate.now();
        LocalDate from;
        String val = dateRangeCombo != null ? dateRangeCombo.getValue() : "Last 30 Days";
        if (val == null) val = "Last 30 Days";
        from = switch (val) {
            case "Custom"       -> {
                LocalDate s = startDatePicker.getValue();
                LocalDate e = endDatePicker.getValue();
                if (e != null) to = e;
                yield s != null ? s : to.minusDays(30);
            }
            case "Last 7 Days"  -> to.minusDays(7);
            case "Last 1 Year"  -> to.minusYears(1);
            case "Year to Date" -> to.withDayOfYear(1);
            default             -> to.minusDays(30);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    private int getPageSize() {
        String v = rowsPerPageCombo != null ? rowsPerPageCombo.getValue() : "10";
        try { return Integer.parseInt(v != null ? v.trim() : "10"); }
        catch (NumberFormatException e) { return 10; }
    }

    private void updateTableHeight() {
        double h = 38 + (double) getPageSize() * 44;
        if (ordersTable    != null) ordersTable.setPrefHeight(h);
        if (productsTable  != null) productsTable.setPrefHeight(h);
        if (customersTable != null) customersTable.setPrefHeight(h);
    }

    private void updatePagination() {
        javafx.application.Platform.runLater(() -> {
            if (pageInfoLabel == null) return;
            int pages = Math.max(1, (int) Math.ceil((double) totalRows / getPageSize()));
            pageInfoLabel.setText("Page " + currentPage + " of " + pages);
            if (prevPageBtn != null) prevPageBtn.setDisable(currentPage <= 1);
            if (nextPageBtn != null) nextPageBtn.setDisable(currentPage >= pages);
        });
    }

    private void fireCallback() {
        if (afterLoadCallback != null) {
            Runnable r = afterLoadCallback;
            afterLoadCallback = null;
            r.run();
        }
    }

    private void vis(javafx.scene.Node n, boolean v) {
        if (n != null) { n.setVisible(v); n.setManaged(v); }
    }

    private void setVisible(VBox b, boolean v) {
        if (b != null) { b.setVisible(v); b.setManaged(v); }
    }

    private void toast(String type, String msg) {
        if (mainLayoutController != null) mainLayoutController.showToast(type, msg);
        else new Alert(type.equals("error") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    private File pickFile(String defaultName, String desc, String ext) {
        javafx.stage.Window window = null;
        if (rootContainer != null && rootContainer.getScene() != null)
            window = rootContainer.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + getExportTitle());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        fc.setInitialFileName(defaultName);
        return fc.showSaveDialog(window);
    }

    private String getExportTitle() {
        return switch (activeTab) {
            case "products"  -> "Product Report";
            case "customers" -> "Customer Report";
            default          -> "Order Report";
        };
    }

    private String getExportBaseName() {
        return switch (activeTab) {
            case "products"  -> "products_export";
            case "customers" -> "customers_export";
            default          -> "orders_export";
        };
    }
}