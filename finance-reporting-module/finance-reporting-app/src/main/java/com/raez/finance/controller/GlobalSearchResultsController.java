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
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GlobalSearchResultsController {

    @FXML private Label lblQuery;
    @FXML private Label lblOrdersCount;
    @FXML private Label lblProductsCount;
    @FXML private Label lblCustomersCount;

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
    @FXML private TableColumn<ProductReportRow, Number> colPrdUnits;
    @FXML private TableColumn<ProductReportRow, Number> colPrdRev;
    @FXML private TableColumn<ProductReportRow, Number> colPrdProfit;

    @FXML private TableView<CustomerReportRow> tblCustomers;
    @FXML private TableColumn<CustomerReportRow, String> colCstId;
    @FXML private TableColumn<CustomerReportRow, String> colCstName;
    @FXML private TableColumn<CustomerReportRow, String> colCstType;
    @FXML private TableColumn<CustomerReportRow, String> colCstCountry;
    @FXML private TableColumn<CustomerReportRow, Number> colCstOrders;
    @FXML private TableColumn<CustomerReportRow, Number> colCstSpent;

    private final ObservableList<OrderReportRow> orderItems = FXCollections.observableArrayList();
    private final ObservableList<ProductReportRow> productItems = FXCollections.observableArrayList();
    private final ObservableList<CustomerReportRow> customerItems = FXCollections.observableArrayList();

    private final OrderDao orderDao = new OrderDao();
    private final ProductDao productDao = new ProductDao();
    private final CustomerDao customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private MainLayoutController mainLayoutController;
    private String query;
    private boolean initialized;

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

    public void setQuery(String query) {
        this.query = query;
        if (lblQuery != null) {
            lblQuery.setText(query == null || query.isBlank()
                    ? "Type in the search bar above to find data."
                    : "Results for \"" + query + "\"");
        }
        if (initialized) {
            runSearch();
        }
    }

    @FXML
    public void initialize() {
        if (tblOrders != null) {
            tblOrders.setItems(orderItems);
            colOrdId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
            colOrdCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
            colOrdProduct.setCellValueFactory(new PropertyValueFactory<>("product"));
            colOrdAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
            colOrdDate.setCellValueFactory(new PropertyValueFactory<>("date"));
            colOrdStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            tblOrders.setPlaceholder(new Label("No orders match this search."));
        }
        if (tblProducts != null) {
            tblProducts.setItems(productItems);
            colPrdId.setCellValueFactory(new PropertyValueFactory<>("productId"));
            colPrdName.setCellValueFactory(new PropertyValueFactory<>("name"));
            colPrdCat.setCellValueFactory(new PropertyValueFactory<>("category"));
            colPrdUnits.setCellValueFactory(new PropertyValueFactory<>("unitsSold"));
            colPrdRev.setCellValueFactory(new PropertyValueFactory<>("revenue"));
            colPrdProfit.setCellValueFactory(new PropertyValueFactory<>("profit"));
            tblProducts.setPlaceholder(new Label("No products match this search."));
        }
        if (tblCustomers != null) {
            tblCustomers.setItems(customerItems);
            colCstId.setCellValueFactory(new PropertyValueFactory<>("customerId"));
            colCstName.setCellValueFactory(new PropertyValueFactory<>("name"));
            colCstType.setCellValueFactory(new PropertyValueFactory<>("type"));
            colCstCountry.setCellValueFactory(new PropertyValueFactory<>("country"));
            colCstOrders.setCellValueFactory(new PropertyValueFactory<>("totalOrders"));
            colCstSpent.setCellValueFactory(new PropertyValueFactory<>("totalSpent"));
            tblCustomers.setPlaceholder(new Label("No customers match this search."));
        }

        if (lblOrdersCount != null) {
            lblOrdersCount.setText("0 Orders");
        }
        if (lblProductsCount != null) {
            lblProductsCount.setText("0 Products");
        }
        if (lblCustomersCount != null) {
            lblCustomersCount.setText("0 Customers");
        }

        initialized = true;

        if (query != null && !query.isBlank()) {
            runSearch();
        } else if (lblQuery != null) {
            lblQuery.setText("Type in the search bar above to find data.");
        }
    }

    private void runSearch() {
        String trimmed = query != null ? query.trim() : "";
        if (trimmed.isEmpty()) {
            orderItems.clear();
            productItems.clear();
            customerItems.clear();
            updateCounts();
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusYears(1);

                List<OrderReportRow> orders = orderDao.findReportRows(from, to, "All Status", trimmed);
                List<ProductReportRow> products = productDao.findReportRows(from, to, "All Categories", trimmed);
                List<CustomerReportRow> customers = customerDao.findReportRows("All", "All", trimmed);

                javafx.application.Platform.runLater(() -> {
                    orderItems.setAll(orders);
                    productItems.setAll(products);
                    customerItems.setAll(customers);
                    updateCounts();
                });
                return null;
            }
        };
        task.exceptionProperty().addListener((obs, oldEx, ex) -> {
            if (ex != null) {
                ex.printStackTrace();
            }
        });
        executor.execute(task);
    }

    private void updateCounts() {
        if (lblOrdersCount != null) {
            lblOrdersCount.setText(orderItems.size() + (orderItems.size() == 1 ? " Order" : " Orders"));
        }
        if (lblProductsCount != null) {
            lblProductsCount.setText(productItems.size() + (productItems.size() == 1 ? " Product" : " Products"));
        }
        if (lblCustomersCount != null) {
            lblCustomersCount.setText(customerItems.size() + (customerItems.size() == 1 ? " Customer" : " Customers"));
        }
    }
}

