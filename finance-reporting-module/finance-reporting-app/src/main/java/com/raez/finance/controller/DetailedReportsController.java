package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.OrderReportRow;
import com.raez.finance.model.ProductReportRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.util.CurrencyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DetailedReportsController {

    private final OrderDao orderDao = new OrderDao();
    private final ProductDao productDao = new ProductDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML private StackPane rootStackPane;
    @FXML private Button btnTabOrders;
    @FXML private Button btnTabProducts;
    @FXML private Button btnTabCustomers;

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbDateRange;
    @FXML private VBox boxCustomDate;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;

    @FXML private VBox boxOrderStatus;
    @FXML private ComboBox<String> cmbOrderStatus;

    @FXML private VBox boxProductCategory;
    @FXML private ComboBox<String> cmbProductCategory;

    @FXML private VBox boxCustomerType;
    @FXML private ComboBox<String> cmbCustomerType;
    @FXML private VBox boxCustomerCompany;
    @FXML private ComboBox<String> cmbCustomerCompany;
    @FXML private VBox boxCustomerCountry;
    @FXML private ComboBox<String> cmbCustomerCountry;

    @FXML private TableView<OrderReportRow> tblOrders;
    @FXML private TableColumn<OrderReportRow, String> colOrdId;
    @FXML private TableColumn<OrderReportRow, String> colOrdCustomer;
    @FXML private TableColumn<OrderReportRow, String> colOrdProduct;
    @FXML private TableColumn<OrderReportRow, Number> colOrdAmount;
    @FXML private TableColumn<OrderReportRow, String> colOrdDate;
    @FXML private TableColumn<OrderReportRow, String> colOrdStatus;

    @FXML private TableView<ProductReportRow> tblProducts;
    @FXML private TableColumn<ProductReportRow, String> colPrdId;
    @FXML private TableColumn<ProductReportRow, String> colPrdName;
    @FXML private TableColumn<ProductReportRow, String> colPrdCat;
    @FXML private TableColumn<ProductReportRow, Number> colPrdCost;
    @FXML private TableColumn<ProductReportRow, Number> colPrdPrice;
    @FXML private TableColumn<ProductReportRow, Number> colPrdProfit;
    @FXML private TableColumn<ProductReportRow, Number> colPrdUnits;
    @FXML private TableColumn<ProductReportRow, Number> colPrdRev;

    @FXML private TableView<CustomerReportRow> tblCustomers;
    @FXML private TableColumn<CustomerReportRow, String> colCstId;
    @FXML private TableColumn<CustomerReportRow, String> colCstName;
    @FXML private TableColumn<CustomerReportRow, String> colCstType;
    @FXML private TableColumn<CustomerReportRow, String> colCstCountry;
    @FXML private TableColumn<CustomerReportRow, Number> colCstOrders;
    @FXML private TableColumn<CustomerReportRow, Number> colCstSpent;
    @FXML private TableColumn<CustomerReportRow, Number> colCstAOV;
    @FXML private TableColumn<CustomerReportRow, String> colCstLast;

    @FXML private ComboBox<Integer> cmbRowsPerPage;
    @FXML private Label lblPageInfo;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;

    private final ObservableList<OrderReportRow> orderItems = FXCollections.observableArrayList();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();
    private final ObservableList<CustomerReportRow> customerItems = FXCollections.observableArrayList();

    private String activeTab = "orders";
    private MainLayoutController mainLayoutController;
    /** One-shot callback run when the current tab's data load completes (for export-after-navigate). */
    private Runnable afterLoadCallback;

    private int currentPage = 1;
    private int totalRows = 0;

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

    /** Runs the given callback once when the current tab's data has finished loading. Used by navigateToReportsAndExport. */
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

    @FXML
    public void initialize() {
        bindOrderColumns();
        bindProductColumns();
        bindCustomerColumns();

        tblOrders.setItems(orderItems);
        tblProducts.setItems(productItems);
        tblCustomers.setItems(customerItems);

        cmbDateRange.setItems(FXCollections.observableArrayList("Last 7 Days", "Last 30 Days", "Last 1 Year", "Year to Date", "Custom"));
        cmbDateRange.setValue("Last 30 Days");

        loadOrderStatusOptions();
        cmbOrderStatus.setValue("All Status");

        loadCategoryOptions();
        cmbProductCategory.setValue("All Categories");

        cmbCustomerType.setItems(FXCollections.observableArrayList("All Types", "Company", "Individual"));
        cmbCustomerType.setValue("All Types");
        loadCountryOptions();
        cmbCustomerCountry.setValue("All");

        cmbRowsPerPage.setItems(FXCollections.observableArrayList(10, 20, 30, 40, 50));
        cmbRowsPerPage.setValue(10);
        cmbRowsPerPage.valueProperty().addListener((obs, o, n) -> {
            currentPage = 1;
            loadCurrentTabData();
        });

        cmbDateRange.valueProperty().addListener((obs, oldV, newV) -> {
            toggleCustomDate("Custom".equals(newV));
            currentPage = 1;
            loadCurrentTabData();
        });
        dpStartDate.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });
        dpEndDate.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });
        cmbOrderStatus.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadOrders(); });
        cmbProductCategory.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadProducts(); });
        cmbCustomerType.valueProperty().addListener((obs, oldV, newV) -> {
            toggleCustomerCompany("Company".equals(newV));
            currentPage = 1;
            loadCustomers();
        });
        cmbCustomerCountry.valueProperty().addListener((obs, o, n) -> { currentPage = 1; loadCustomers(); });
        txtSearch.textProperty().addListener((obs, o, n) -> { currentPage = 1; loadCurrentTabData(); });

        switchTab("orders");
    }

    private void bindOrderColumns() {
        colOrdId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("orderId"));
        colOrdCustomer.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customer"));
        colOrdProduct.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("product"));
        colOrdAmount.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("amount"));
        colOrdAmount.setCellFactory(CurrencyUtil.currencyCellFactory());
        colOrdDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("date"));
        colOrdStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
    }

    private void bindProductColumns() {
        colPrdId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productId"));
        colPrdName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colPrdCat.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("category"));
        colPrdCost.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("cost"));
        colPrdCost.setCellFactory(CurrencyUtil.currencyCellFactory());
        colPrdPrice.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("salePrice"));
        colPrdPrice.setCellFactory(CurrencyUtil.currencyCellFactory());
        colPrdProfit.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("profit"));
        colPrdProfit.setCellFactory(CurrencyUtil.currencyCellFactory());
        colPrdUnits.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("unitsSold"));
        colPrdRev.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("revenue"));
        colPrdRev.setCellFactory(CurrencyUtil.currencyCellFactory());
    }

    private void bindCustomerColumns() {
        colCstId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customerId"));
        colCstName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colCstType.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        colCstCountry.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("country"));
        colCstOrders.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalOrders"));
        colCstSpent.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalSpent"));
        colCstSpent.setCellFactory(CurrencyUtil.currencyCellFactory());
        colCstAOV.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("avgOrderValue"));
        colCstAOV.setCellFactory(CurrencyUtil.currencyCellFactory());
        colCstLast.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("lastPurchase"));
    }

    private void loadOrderStatusOptions() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return orderDao.findStatusOptions();
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                ObservableList<String> items = FXCollections.observableArrayList("All Status");
                items.addAll(task.getValue());
                cmbOrderStatus.setItems(items);
                cmbOrderStatus.setValue("All Status");
            }
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
                cmbProductCategory.setItems(items);
                cmbProductCategory.setValue("All Categories");
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
                cmbCustomerCountry.setItems(items);
                if (cmbCustomerCountry.getValue() == null) cmbCustomerCountry.setValue("All");
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
            if (lblPageInfo == null || btnPrevPage == null || btnNextPage == null) return;
            int pageSize = cmbRowsPerPage.getValue() != null ? cmbRowsPerPage.getValue() : 10;
            int totalPages = pageSize > 0 ? Math.max(1, (int) Math.ceil((double) totalRows / pageSize)) : 1;
            lblPageInfo.setText("Page " + currentPage + " of " + totalPages + " (" + totalRows + " rows)");
            btnPrevPage.setDisable(currentPage <= 1);
            btnNextPage.setDisable(currentPage >= totalPages || totalPages == 0);
        });
    }

    private void loadOrders() {
        int pageSize = cmbRowsPerPage.getValue() != null ? cmbRowsPerPage.getValue() : 10;
        int offset = (currentPage - 1) * pageSize;
        LocalDate[] range = resolveDateRange();
        Task<List<OrderReportRow>> dataTask = new Task<>() {
            @Override
            protected List<OrderReportRow> call() throws Exception {
                return orderDao.findReportRows(range[0], range[1],
                        cmbOrderStatus.getValue(), txtSearch.getText(), pageSize, offset);
            }
        };
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return orderDao.countReportRows(range[0], range[1],
                        cmbOrderStatus.getValue(), txtSearch.getText());
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
        int pageSize = cmbRowsPerPage.getValue() != null ? cmbRowsPerPage.getValue() : 10;
        int offset = (currentPage - 1) * pageSize;
        LocalDate[] range = resolveDateRange();
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return productDao.countReportRows(range[0], range[1],
                        cmbProductCategory.getValue(), txtSearch.getText());
            }
        };
        Task<List<ProductReportRow>> dataTask = new Task<>() {
            @Override
            protected List<ProductReportRow> call() throws Exception {
                return productDao.findReportRows(range[0], range[1],
                        cmbProductCategory.getValue(), txtSearch.getText(), pageSize, offset);
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
        int pageSize = cmbRowsPerPage.getValue() != null ? cmbRowsPerPage.getValue() : 10;
        int offset = (currentPage - 1) * pageSize;
        String country = cmbCustomerCountry.getValue();
        Task<Integer> countTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return customerDao.countReportRows(cmbCustomerType.getValue(), country, txtSearch.getText());
            }
        };
        Task<List<CustomerReportRow>> dataTask = new Task<>() {
            @Override
            protected List<CustomerReportRow> call() throws Exception {
                return customerDao.findReportRows(cmbCustomerType.getValue(), country, txtSearch.getText(), pageSize, offset);
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
        String val = cmbDateRange.getValue();
        if (val == null) val = "Last 30 Days";
        switch (val) {
            case "Custom":
                from = dpStartDate.getValue() != null ? dpStartDate.getValue() : to.minusDays(30);
                to = dpEndDate.getValue() != null ? dpEndDate.getValue() : to;
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

    // --- Tab Navigation Handlers ---

    @FXML
    private void handleTabOrders(ActionEvent event) { switchTab("orders"); }

    @FXML
    private void handleTabProducts(ActionEvent event) { switchTab("products"); }

    @FXML
    private void handleTabCustomers(ActionEvent event) { switchTab("customers"); }

    // --- Helper Logic ---

    /** Public for navigation/export from TopBar: switch to Orders, Products, or Customers tab. */
    public void switchToTab(String tab) {
        switchTab(tab);
    }

    private void switchTab(String tab) {
        activeTab = tab;
        
        // Reset Tab Styles
        String activeStyle = "-fx-background-color: transparent; -fx-border-color: #1E2939; -fx-border-width: 0 0 2 0; -fx-text-fill: #1E2939; -fx-cursor: hand; -fx-font-weight: bold;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #4B5563; -fx-cursor: hand;";
        
        btnTabOrders.setStyle(inactiveStyle);
        btnTabProducts.setStyle(inactiveStyle);
        btnTabCustomers.setStyle(inactiveStyle);

        // Reset Table & Filter Visibility
        tblOrders.setVisible(false);
        tblProducts.setVisible(false);
        tblCustomers.setVisible(false);
        
        boxOrderStatus.setVisible(false); boxOrderStatus.setManaged(false);
        boxProductCategory.setVisible(false); boxProductCategory.setManaged(false);
        boxCustomerType.setVisible(false); boxCustomerType.setManaged(false);
        boxCustomerCompany.setVisible(false); boxCustomerCompany.setManaged(false);
        boxCustomerCountry.setVisible(false); boxCustomerCountry.setManaged(false);

        // Update search placeholder per tab
        if (txtSearch != null) {
            switch (tab) {
                case "orders":   txtSearch.setPromptText("Search orders..."); break;
                case "products": txtSearch.setPromptText("Search products..."); break;
                case "customers": txtSearch.setPromptText("Search customers..."); break;
                default:        txtSearch.setPromptText("Search..."); break;
            }
        }

        currentPage = 1;
        // Apply Active State
        switch (tab) {
            case "orders":
                btnTabOrders.setStyle(activeStyle);
                tblOrders.setVisible(true);
                boxOrderStatus.setVisible(true); boxOrderStatus.setManaged(true);
                loadOrders();
                break;
            case "products":
                btnTabProducts.setStyle(activeStyle);
                tblProducts.setVisible(true);
                boxProductCategory.setVisible(true); boxProductCategory.setManaged(true);
                loadProducts();
                break;
            case "customers":
                btnTabCustomers.setStyle(activeStyle);
                tblCustomers.setVisible(true);
                boxCustomerType.setVisible(true); boxCustomerType.setManaged(true);
                boxCustomerCountry.setVisible(true); boxCustomerCountry.setManaged(true);
                toggleCustomerCompany(cmbCustomerType.getValue() != null && cmbCustomerType.getValue().equals("Company"));
                loadCustomers();
                break;
        }
    }

    private void toggleCustomDate(boolean show) {
        boxCustomDate.setVisible(show);
        boxCustomDate.setManaged(show);
    }

    private void toggleCustomerCompany(boolean show) {
        boxCustomerCompany.setVisible(show);
        boxCustomerCompany.setManaged(show);
    }

    // --- Export & Pagination Actions ---

    @FXML
    private void handleExportCSV(ActionEvent event) {
        performExportCSV();
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        performExportPDF();
    }

    /** Called from profile Export menu: show save dialog and export current tab to CSV. */
    public void performExportCSV() {
        TableView<?> table = getCurrentTable();
        if (table == null) return;
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        if (window == null && rootStackPane != null && rootStackPane.getScene() != null) {
            window = rootStackPane.getScene().getWindow();
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

    /** Called from profile Export menu: show save dialog and export current tab to PDF. */
    public void performExportPDF() {
        TableView<?> table = getCurrentTable();
        if (table == null) return;
        javafx.stage.Window window = table.getScene() != null ? table.getScene().getWindow() : null;
        if (window == null && rootStackPane != null && rootStackPane.getScene() != null) {
            window = rootStackPane.getScene().getWindow();
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
            case "orders": return tblOrders;
            case "products": return tblProducts;
            case "customers": return tblCustomers;
            default: return tblOrders;
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
        if (rootStackPane == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/raez/finance/view/NotificationToast.fxml"));
            Node toastNode = loader.load();
            NotificationToastController c = loader.getController();
            if (c != null) {
                c.setNotification("success", message, () -> {
                    if (rootStackPane.getChildren().contains(toastNode)) {
                        rootStackPane.getChildren().remove(toastNode);
                    }
                });
            }
            rootStackPane.getChildren().add(toastNode);
            StackPane.setAlignment(toastNode, Pos.TOP_RIGHT);
            StackPane.setMargin(toastNode, new javafx.geometry.Insets(24, 24, 0, 24));
        } catch (Exception e) {
            new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
        }
    }

    @FXML
    private void handlePrevPage(ActionEvent event) {
        if (currentPage > 1) {
            currentPage--;
            loadCurrentTabData();
        }
    }

    @FXML
    private void handleNextPage(ActionEvent event) {
        int pageSize = cmbRowsPerPage.getValue() != null ? cmbRowsPerPage.getValue() : 10;
        int totalPages = pageSize > 0 ? Math.max(1, (int) Math.ceil((double) totalRows / pageSize)) : 1;
        if (currentPage < totalPages) {
            currentPage++;
            loadCurrentTabData();
        }
    }
}
