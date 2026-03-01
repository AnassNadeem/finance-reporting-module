package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.OrderReportRow;
import com.raez.finance.model.ProductReportRow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailedReportsController {

    private final OrderDao orderDao = new OrderDao();
    private final ProductDao productDao = new ProductDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        cmbDateRange.valueProperty().addListener((obs, oldV, newV) -> {
            toggleCustomDate("Custom".equals(newV));
            loadCurrentTabData();
        });
        dpStartDate.valueProperty().addListener((obs, o, n) -> loadCurrentTabData());
        dpEndDate.valueProperty().addListener((obs, o, n) -> loadCurrentTabData());
        cmbOrderStatus.valueProperty().addListener((obs, o, n) -> loadOrders());
        cmbProductCategory.valueProperty().addListener((obs, o, n) -> loadProducts());
        cmbCustomerType.valueProperty().addListener((obs, oldV, newV) -> {
            toggleCustomerCompany("Company".equals(newV));
            loadCustomers();
        });
        cmbCustomerCountry.valueProperty().addListener((obs, o, n) -> loadCustomers());
        txtSearch.textProperty().addListener((obs, o, n) -> loadCurrentTabData());

        switchTab("orders");
    }

    private void bindOrderColumns() {
        colOrdId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("orderId"));
        colOrdCustomer.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customer"));
        colOrdProduct.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("product"));
        colOrdAmount.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("amount"));
        colOrdDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("date"));
        colOrdStatus.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("status"));
    }

    private void bindProductColumns() {
        colPrdId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productId"));
        colPrdName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colPrdCat.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("category"));
        colPrdCost.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("cost"));
        colPrdPrice.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("salePrice"));
        colPrdProfit.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("profit"));
        colPrdUnits.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("unitsSold"));
        colPrdRev.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("revenue"));
    }

    private void bindCustomerColumns() {
        colCstId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("customerId"));
        colCstName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        colCstType.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("type"));
        colCstCountry.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("country"));
        colCstOrders.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalOrders"));
        colCstSpent.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalSpent"));
        colCstAOV.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("avgOrderValue"));
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

    private void loadOrders() {
        Task<List<OrderReportRow>> task = new Task<>() {
            @Override
            protected List<OrderReportRow> call() throws Exception {
                LocalDate[] range = resolveDateRange();
                return orderDao.findReportRows(range[0], range[1],
                        cmbOrderStatus.getValue(), txtSearch.getText());
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                orderItems.clear();
                orderItems.addAll(task.getValue());
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    private void loadProducts() {
        Task<List<ProductReportRow>> task = new Task<>() {
            @Override
            protected List<ProductReportRow> call() throws Exception {
                LocalDate[] range = resolveDateRange();
                return productDao.findReportRows(range[0], range[1],
                        cmbProductCategory.getValue(), txtSearch.getText());
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                productItems.clear();
                productItems.addAll(task.getValue());
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    private void loadCustomers() {
        Task<List<CustomerReportRow>> task = new Task<>() {
            @Override
            protected List<CustomerReportRow> call() throws Exception {
                String country = cmbCustomerCountry.getValue();
                return customerDao.findReportRows(cmbCustomerType.getValue(), country, txtSearch.getText());
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                customerItems.clear();
                customerItems.addAll(task.getValue());
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
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
        System.out.println("Exporting " + activeTab + " to CSV...");
    }

    @FXML
    private void handleExportPDF(ActionEvent event) {
        System.out.println("Exporting " + activeTab + " to PDF...");
    }

    @FXML
    private void handlePrevPage(ActionEvent event) {
        System.out.println("Loading previous page...");
    }

    @FXML
    private void handleNextPage(ActionEvent event) {
        System.out.println("Loading next page...");
    }
}