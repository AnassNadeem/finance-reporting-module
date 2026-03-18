package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.ProductDao;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DetailedReportsController implements Initializable {

    private final OrderDao orderDao = new OrderDao();
    private final ProductDao productDao = new ProductDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML private VBox rootContainer;
    @FXML private ScrollPane pageScrollPane;
    @FXML private Button tabOrders;
    @FXML private Button tabProducts;
    @FXML private Button tabCustomers;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private VBox customStartDateBox;
    @FXML private VBox customEndDateBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;

    @FXML private VBox orderStatusBox;
    @FXML private ComboBox<String> orderStatusCombo;

    @FXML private VBox productCategoryBox;
    @FXML private ComboBox<String> productCategoryCombo;

    @FXML private VBox customerTypeBox;
    @FXML private ComboBox<String> customerTypeCombo;
    @FXML private VBox customerCountryBox;
    @FXML private ComboBox<String> customerCountryCombo;

    @FXML private TableView<OrderReportRow> ordersTable;
    @FXML private TableColumn<OrderReportRow, String> orderIdCol;
    @FXML private TableColumn<OrderReportRow, String> orderCustomerCol;
    @FXML private TableColumn<OrderReportRow, String> orderProductCol;
    @FXML private TableColumn<OrderReportRow, Number> orderAmountCol;
    @FXML private TableColumn<OrderReportRow, String> orderDateCol;
    @FXML private TableColumn<OrderReportRow, String> orderStatusCol;

    @FXML private TableView<ProductReportRow> productsTable;
    @FXML private TableColumn<ProductReportRow, String> productIdCol;
    @FXML private TableColumn<ProductReportRow, String> productNameCol;
    @FXML private TableColumn<ProductReportRow, String> productCategoryCol;
    @FXML private TableColumn<ProductReportRow, Number> productCostCol;
    @FXML private TableColumn<ProductReportRow, Number> productSalePriceCol;
    @FXML private TableColumn<ProductReportRow, Number> productProfitCol;
    @FXML private TableColumn<ProductReportRow, Number> productUnitsCol;
    @FXML private TableColumn<ProductReportRow, Number> productRevenueCol;

    @FXML private TableView<CustomerReportRow> customersTable;
    @FXML private TableColumn<CustomerReportRow, String> custIdCol;
    @FXML private TableColumn<CustomerReportRow, String> custNameCol;
    @FXML private TableColumn<CustomerReportRow, String> custTypeCol;
    @FXML private TableColumn<CustomerReportRow, String> custCountryCol;
    @FXML private TableColumn<CustomerReportRow, Number> custOrdersCol;
    @FXML private TableColumn<CustomerReportRow, Number> custSpentCol;
    @FXML private TableColumn<CustomerReportRow, Number> custAvgOrderCol;
    @FXML private TableColumn<CustomerReportRow, String> custLastPurchaseCol;

    @FXML private ComboBox<String> rowsPerPageCombo;
    @FXML private Label pageInfoLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;

    @FXML private MenuButton exportMenuButton;
    @FXML private MenuItem exportCsvItem;
    @FXML private MenuItem exportPdfItem;

    private final ObservableList<OrderReportRow> orderItems = FXCollections.observableArrayList();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();
    private final ObservableList<CustomerReportRow> customerItems = FXCollections.observableArrayList();

    private String activeTab = "orders";
    private MainLayoutController mainLayoutController;
    private Runnable afterLoadCallback;

    private int currentPage = 1;
    private int totalRows = 0;

    public void setMainLayoutController(MainLayoutController mainLayoutController) {
        this.mainLayoutController = mainLayoutController;
    }

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

    public void setAfterLoadCallback(Runnable runnable) {
        this.afterLoadCallback = runnable;
    }

    private void runAfterLoadCallback() {
        if (afterLoadCallback != null) {
            Runnable r = afterLoadCallback;
            afterLoadCallback = null;
            r.run();
        }
    }

    private int getPageSize() {
        String v = rowsPerPageCombo.getValue();
        if (v == null || v.isBlank()) return 10;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindOrderColumns();
        bindProductColumns();
        bindCustomerColumns();

        ordersTable.setItems(orderItems);
        productsTable.setItems(productItems);
        customersTable.setItems(customerItems);

        // Constrained resize: columns share the full table width — no horizontal scroll.
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        customersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Row hover highlight (alternating + hover).
        applyRowFactory(ordersTable);
        applyRowFactory(productsTable);
        applyRowFactory(customersTable);

        dateRangeCombo.setItems(FXCollections.observableArrayList("Last 7 Days", "Last 30 Days", "Last 1 Year", "Year to Date", "Custom"));
        dateRangeCombo.setValue("Last 30 Days");

        loadOrderStatusOptions();
        orderStatusCombo.setValue("All Status");

        loadCategoryOptions();
        productCategoryCombo.setValue("All Categories");

        customerTypeCombo.setItems(FXCollections.observableArrayList("All Types", "Company", "Individual"));
        customerTypeCombo.setValue("All Types");
        loadCountryOptions();
        customerCountryCombo.setValue("All");

        rowsPerPageCombo.setValue("10");
        rowsPerPageCombo.valueProperty().addListener((obs, o, n) -> {
            currentPage = 1;
            loadCurrentTabData();
        });

        dateRangeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            boolean isCustom = "Custom".equals(newV);
            customStartDateBox.setVisible(isCustom);
            customStartDateBox.setManaged(isCustom);
            customEndDateBox.setVisible(isCustom);
            customEndDateBox.setManaged(isCustom);
            currentPage = 1;
            loadCurrentTabData();
        });
        startDatePicker.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });
        endDatePicker.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });
        orderStatusCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadOrders(); });
        productCategoryCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadProducts(); });
        customerTypeCombo.valueProperty().addListener((obs, oldV, newV) -> { currentPage = 1; loadCustomers(); });
        customerCountryCombo.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCustomers(); });
        searchField.textProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });

        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        showTab("orders");
        updateTablePreferredHeight();
    }

    /** Sets table preferred height to show exactly rows-per-page rows (no table scroll; page scrolls). */
    private void updateTablePreferredHeight() {
        int pageSize = getPageSize();
        double rowHeight = 44;   // matches -fx-fixed-cell-size: 44 in FXML
        double headerHeight = 38;
        double h = headerHeight + pageSize * rowHeight;
        if (ordersTable != null) ordersTable.setPrefHeight(h);
        if (productsTable != null) productsTable.setPrefHeight(h);
        if (customersTable != null) customersTable.setPrefHeight(h);
    }

    /**
     * Applies a row factory that gives each table:
     *   • alternating stripe colour (even rows: white, odd rows: #F9FAFB)
     *   • a subtle blue-grey hover highlight
     * Works for any TableView type, hence the raw-type suppression.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyRowFactory(TableView table) {
        table.setRowFactory(tv -> {
            TableRow row = new TableRow() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("-fx-background-color: transparent;");
                    } else {
                        String base = getIndex() % 2 == 0
                                ? "-fx-background-color: white;"
                                : "-fx-background-color: #F9FAFB;";
                        setStyle(base);
                    }
                }
            };
            row.setOnMouseEntered(e -> {
                if (!row.isEmpty()) {
                    row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;");
                }
            });
            row.setOnMouseExited(e -> {
                if (!row.isEmpty()) {
                    String base = row.getIndex() % 2 == 0
                            ? "-fx-background-color: white;"
                            : "-fx-background-color: #F9FAFB;";
                    row.setStyle(base);
                }
            });
            return row;
        });
    }

    private void bindOrderColumns() {
        orderIdCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("orderId"));
        orderCustomerCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customer"));
        orderProductCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("product"));
        orderAmountCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("amount"));
        orderAmountCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        orderDateCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("date"));
        orderStatusCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
        orderStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;

                Label badge = new Label(item);
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; " +
                               "-fx-padding: 2 8 2 8; -fx-background-radius: 999;");

                String s = item.trim().toLowerCase();
                if (s.contains("completed")) {
                    badge.setStyle(badge.getStyle() +
                        "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");
                } else if (s.contains("pending")) {
                    badge.setStyle(badge.getStyle() +
                        "-fx-background-color: #FEF9C3; -fx-text-fill: #92400E;");
                } else if (s.contains("cancelled")) {
                    badge.setStyle(badge.getStyle() +
                        "-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B;");
                } else {
                    badge.setStyle(badge.getStyle() +
                        "-fx-background-color: #F3F4F6; -fx-text-fill: #6B7280;");
                }
                HBox wrapper = new HBox(badge);
                wrapper.setAlignment(Pos.CENTER_LEFT);
                setGraphic(wrapper);
            }
        });
    }

    private void bindProductColumns() {
        productIdCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productId"));
        productNameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        productCategoryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("category"));
        productCostCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("cost"));
        productCostCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        productSalePriceCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("salePrice"));
        productSalePriceCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        productProfitCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("profit"));
        productProfitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(CurrencyUtil.formatCurrency(item.doubleValue()));
                setStyle(item.doubleValue() >= 0 ? "-fx-text-fill: #16A34A;" : "-fx-text-fill: #991B1B;");
            }
        });
        productUnitsCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("unitsSold"));
        productRevenueCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("revenue"));
        productRevenueCol.setCellFactory(CurrencyUtil.currencyCellFactory());
    }

    private void bindCustomerColumns() {
        custIdCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customerId"));
        custNameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        custTypeCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        custTypeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;

                Label badge = new Label(item);
                if ("Company".equalsIgnoreCase(item.trim())) {
                    badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                        "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;");
                } else {
                    badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700;" +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 999;" +
                        "-fx-background-color: #F3F4F6; -fx-text-fill: #4B5563;");
                }
                HBox wrapper = new HBox(badge);
                wrapper.setAlignment(Pos.CENTER_LEFT);
                setGraphic(wrapper);
            }
        });
        custCountryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("country"));
        custOrdersCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalOrders"));
        custSpentCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalSpent"));
        custSpentCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        custAvgOrderCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("avgOrderValue"));
        custAvgOrderCol.setCellFactory(CurrencyUtil.currencyCellFactory());
        custLastPurchaseCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("lastPurchase"));
    }

    private void loadOrderStatusOptions() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return orderDao.findStatusOptions();
            }
        };
        task.setOnSucceeded(e -> {
            ObservableList<String> items = FXCollections.observableArrayList("All Status");
            if (task.getValue() != null) items.addAll(task.getValue());
            if (!items.contains("Completed")) items.add("Completed");
            if (!items.contains("Pending")) items.add("Pending");
            if (!items.contains("Cancelled")) items.add("Cancelled");
            orderStatusCombo.setItems(items);
            orderStatusCombo.setValue("All Status");
        });
        executor.execute(task);
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
                productCategoryCombo.setItems(items);
                productCategoryCombo.setValue("All Categories");
            }
        });
        executor.execute(task);
    }

    private void loadCountryOptions() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return customerDao.findCountryOptions();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                ObservableList<String> items = FXCollections.observableArrayList("All");
                items.addAll(task.getValue());
                customerCountryCombo.setItems(items);
                if (customerCountryCombo.getValue() == null) customerCountryCombo.setValue("All");
            }
        });
        executor.execute(task);
    }

    private void loadCurrentTabData() {
        switch (activeTab) {
            case "orders": loadOrders(); break;
            case "products": loadProducts(); break;
            case "customers": loadCustomers(); break;
        }
    }

    private void updatePaginationUI() {
        javafx.application.Platform.runLater(() -> {
            if (pageInfoLabel == null || prevPageBtn == null || nextPageBtn == null) return;
            int pageSize = getPageSize();
            int totalPages = pageSize > 0 ? Math.max(1, (int) Math.ceil((double) totalRows / pageSize)) : 1;
            pageInfoLabel.setText("Page " + currentPage + " of " + totalPages);
            prevPageBtn.setDisable(currentPage <= 1);
            nextPageBtn.setDisable(currentPage >= totalPages || totalPages == 0);
        });
    }

    private void loadOrders() {
        int pageSize = getPageSize();
        int offset = (currentPage - 1) * pageSize;
        LocalDate[] range = resolveDateRange();
        Task<List<OrderReportRow>> dataTask = new Task<>() {
            @Override
            protected List<OrderReportRow> call() throws Exception {
                return orderDao.findReportRows(range[0], range[1],
                        orderStatusCombo.getValue(), searchField.getText(), pageSize, offset);
            }
        };
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return orderDao.countReportRows(range[0], range[1],
                        orderStatusCombo.getValue(), searchField.getText());
            }
        };
        countTask.setOnSucceeded(ev -> {
            totalRows = countTask.getValue() != null ? countTask.getValue() : 0;
            updatePaginationUI();
        });
        dataTask.setOnSucceeded(e -> {
            if (dataTask.getValue() != null) {
                orderItems.clear();
                orderItems.addAll(dataTask.getValue());
            }
            runAfterLoadCallback();
        });
        dataTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        countTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(countTask);
        executor.execute(dataTask);
    }

    private void loadProducts() {
        int pageSize = getPageSize();
        int offset = (currentPage - 1) * pageSize;
        LocalDate[] range = resolveDateRange();
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return productDao.countReportRows(range[0], range[1],
                        productCategoryCombo.getValue(), searchField.getText());
            }
        };
        Task<List<ProductReportRow>> dataTask = new Task<>() {
            @Override
            protected List<ProductReportRow> call() throws Exception {
                return productDao.findReportRows(range[0], range[1],
                        productCategoryCombo.getValue(), searchField.getText(), pageSize, offset);
            }
        };
        countTask.setOnSucceeded(ev -> {
            totalRows = countTask.getValue() != null ? countTask.getValue() : 0;
            updatePaginationUI();
        });
        dataTask.setOnSucceeded(e -> {
            if (dataTask.getValue() != null) {
                productItems.clear();
                productItems.addAll(dataTask.getValue());
            }
            runAfterLoadCallback();
        });
        dataTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        countTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(countTask);
        executor.execute(dataTask);
    }

    private void loadCustomers() {
        int pageSize = getPageSize();
        int offset = (currentPage - 1) * pageSize;
        String countryRaw = customerCountryCombo.getValue() != null ? customerCountryCombo.getValue() : "All";
        final String country = "All Countries".equals(countryRaw) ? "All" : countryRaw;
        String typeFilter = customerTypeCombo.getValue();
        if ("All Types".equals(typeFilter)) typeFilter = "All";
        String type = typeFilter;
        String company = null;
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return customerDao.countReportRows(type, country, company, searchField.getText());
            }
        };
        Task<List<CustomerReportRow>> dataTask = new Task<>() {
            @Override
            protected List<CustomerReportRow> call() throws Exception {
                return customerDao.findReportRows(type, country, company, searchField.getText(), pageSize, offset);
            }
        };
        countTask.setOnSucceeded(ev -> {
            totalRows = countTask.getValue() != null ? countTask.getValue() : 0;
            updatePaginationUI();
        });
        dataTask.setOnSucceeded(e -> {
            if (dataTask.getValue() != null) {
                customerItems.clear();
                customerItems.addAll(dataTask.getValue());
            }
            runAfterLoadCallback();
        });
        dataTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        countTask.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(countTask);
        executor.execute(dataTask);
    }

    private LocalDate[] resolveDateRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = dateRangeCombo.getValue();
        if (val == null) val = "Last 30 Days";
        switch (val) {
            case "Custom":
                from = startDatePicker.getValue() != null ? startDatePicker.getValue() : to.minusDays(30);
                to = endDatePicker.getValue() != null ? endDatePicker.getValue() : to;
                if (from.isAfter(to)) from = to;
                break;
            case "Last 7 Days":
                from = to.minusDays(7);
                break;
            case "Last 1 Year":
                from = to.minusYears(1);
                break;
            case "Year to Date":
                from = to.withDayOfYear(1);
                break;
            default:
                from = to.minusDays(30);
                break;
        }
        return new LocalDate[]{ from, to };
    }

    // --- Tab handlers (FXML: handleTabOrders, handleTabProducts, handleTabCustomers) ---

    @FXML
    private void handleTabOrders() {
        showTab("orders");
    }

    @FXML
    private void handleTabProducts() {
        showTab("products");
    }

    @FXML
    private void handleTabCustomers() {
        showTab("customers");
    }

    public void switchToTab(String tab) {
        showTab(tab);
    }

    private void showTab(String tab) {
        activeTab = tab;
        currentPage = 1;

        ordersTable.setVisible(tab.equals("orders"));
        ordersTable.setManaged(tab.equals("orders"));
        productsTable.setVisible(tab.equals("products"));
        productsTable.setManaged(tab.equals("products"));
        customersTable.setVisible(tab.equals("customers"));
        customersTable.setManaged(tab.equals("customers"));

        orderStatusBox.setVisible(tab.equals("orders"));
        orderStatusBox.setManaged(tab.equals("orders"));
        productCategoryBox.setVisible(tab.equals("products"));
        productCategoryBox.setManaged(tab.equals("products"));
        customerTypeBox.setVisible(tab.equals("customers"));
        customerTypeBox.setManaged(tab.equals("customers"));
        customerCountryBox.setVisible(tab.equals("customers"));
        customerCountryBox.setManaged(tab.equals("customers"));

        if (searchField != null) {
            searchField.setPromptText("Search " + tab + "...");
        }

        styleTabButton(tabOrders, tab.equals("orders"));
        styleTabButton(tabProducts, tab.equals("products"));
        styleTabButton(tabCustomers, tab.equals("customers"));

        updateTablePreferredHeight();
        switch (tab) {
            case "orders": loadOrders(); break;
            case "products": loadProducts(); break;
            case "customers": loadCustomers(); break;
        }
    }

    private void styleTabButton(Button btn, boolean active) {
        if (active) {
            btn.setStyle(
                "-fx-background-color: transparent; -fx-cursor: hand;" +
                "-fx-padding: 16 0 12 0; -fx-font-size: 14px;" +
                "-fx-text-fill: #1E2939;" +
                "-fx-border-color: transparent transparent #1E2939 transparent;" +
                "-fx-border-width: 0 0 2 0;");
        } else {
            btn.setStyle(
                "-fx-background-color: transparent; -fx-cursor: hand;" +
                "-fx-padding: 16 0 12 0; -fx-font-size: 14px;" +
                "-fx-text-fill: #6B7280; -fx-border-color: transparent;" +
                "-fx-border-width: 0 0 2 0;");
        }
    }

    // --- Filter handlers (FXML) ---

    @FXML
    private void handleSearch() {
        currentPage = 1;
        loadCurrentTabData();
    }

    @FXML
    private void handleDateRangeChange() {
        boolean isCustom = "Custom".equals(dateRangeCombo.getValue());
        customStartDateBox.setVisible(isCustom);
        customStartDateBox.setManaged(isCustom);
        customEndDateBox.setVisible(isCustom);
        customEndDateBox.setManaged(isCustom);
        currentPage = 1;
        loadCurrentTabData();
    }

    @FXML
    private void handleOrderStatusChange() {
        currentPage = 1;
        loadOrders();
    }

    @FXML
    private void handleProductCategoryChange() {
        currentPage = 1;
        loadProducts();
    }

    @FXML
    private void handleCustomerTypeChange() {
        currentPage = 1;
        loadCustomers();
    }

    @FXML
    private void handleCustomerCountryChange() {
        currentPage = 1;
        loadCustomers();
    }

    // --- Pagination handlers (FXML) ---

    @FXML
    private void handleRowsPerPageChange() {
        currentPage = 1;
        updateTablePreferredHeight();
        loadCurrentTabData();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadCurrentTabData();
        }
    }

    @FXML
    private void handleNextPage() {
        int pageSize = getPageSize();
        int totalPages = pageSize > 0 ? Math.max(1, (int) Math.ceil((double) totalRows / pageSize)) : 1;
        if (currentPage < totalPages) {
            currentPage++;
            loadCurrentTabData();
        }
    }

    // --- Export handlers (FXML: handleExportCsv, handleExportPdf) ---

    @FXML
    private void handleExportCsv() {
        performExportCSV();
    }

    @FXML
    private void handleExportPdf() {
        performExportPDF();
    }

    public void performExportCSV() {
        TableView<?> table = getCurrentTable();
        if (table == null) return;
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        if (window == null && rootContainer != null && rootContainer.getScene() != null) {
            window = rootContainer.getScene().getWindow();
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export to CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        fc.setInitialFileName(getExportBaseName() + ".csv");
        File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            ExportService.exportToCSV(table, file);
            showSuccessToast("CSV exported successfully to " + file.getName());
        } catch (Exception e) {
            if (mainLayoutController != null) {
                mainLayoutController.showToast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            } else {
                new Alert(Alert.AlertType.ERROR, "Export failed: " + e.getMessage()).showAndWait();
            }
        }
    }

    public void performExportPDF() {
        TableView<?> table = getCurrentTable();
        if (table == null) return;
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        if (window == null && rootContainer != null && rootContainer.getScene() != null) {
            window = rootContainer.getScene().getWindow();
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export to PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(getExportBaseName() + ".pdf");
        File file = fc.showSaveDialog(window);
        if (file == null) return;
        try {
            ExportService.exportToPDF(table, getExportTitle(), file);
            showSuccessToast("PDF exported successfully to " + file.getName());
        } catch (Exception e) {
            if (mainLayoutController != null) {
                mainLayoutController.showToast("error", "Export failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            } else {
                new Alert(Alert.AlertType.ERROR, "Export failed: " + e.getMessage()).showAndWait();
            }
        }
    }

    private TableView<?> getCurrentTable() {
        switch (activeTab) {
            case "orders": return ordersTable;
            case "products": return productsTable;
            case "customers": return customersTable;
            default: return ordersTable;
        }
    }

    private String getExportTitle() {
        switch (activeTab) {
            case "orders": return "Order Report";
            case "products": return "Product Report";
            case "customers": return "Customer Report";
            default: return "Report";
        }
    }

    private String getExportBaseName() {
        switch (activeTab) {
            case "orders": return "orders_export";
            case "products": return "products_export";
            case "customers": return "customers_export";
            default: return "export";
        }
    }

    private void showSuccessToast(String message) {
        if (mainLayoutController != null) {
            mainLayoutController.showToast("success", message);
        } else {
            new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
        }
    }
}
