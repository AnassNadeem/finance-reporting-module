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
import com.raez.finance.util.CurrencyUtil;
import com.raez.finance.util.UiAutoRefreshable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GlobalSearchResultsController implements UiAutoRefreshable {

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private Label lblQuery;
    @FXML private Label lblOrdersCount;
    @FXML private Label lblProductsCount;
    @FXML private Label lblCustomersCount;

    @FXML private TableView<OrderReportRow>             tblOrders;
    @FXML private TableColumn<OrderReportRow, String>   colOrdId;
    @FXML private TableColumn<OrderReportRow, String>   colOrdCustomer;
    @FXML private TableColumn<OrderReportRow, String>   colOrdProduct;
    @FXML private TableColumn<OrderReportRow, Number>   colOrdAmount;
    @FXML private TableColumn<OrderReportRow, String>   colOrdDate;
    @FXML private TableColumn<OrderReportRow, String>   colOrdStatus;

    @FXML private TableView<ProductReportRow>            tblProducts;
    @FXML private TableColumn<ProductReportRow, String>  colPrdId;
    @FXML private TableColumn<ProductReportRow, String>  colPrdName;
    @FXML private TableColumn<ProductReportRow, String>  colPrdCat;
    @FXML private TableColumn<ProductReportRow, Number>  colPrdUnits;
    @FXML private TableColumn<ProductReportRow, Number>  colPrdRev;
    @FXML private TableColumn<ProductReportRow, Number>  colPrdProfit;

    @FXML private TableView<CustomerReportRow>              tblCustomers;
    @FXML private TableColumn<CustomerReportRow, String>    colCstId;
    @FXML private TableColumn<CustomerReportRow, String>    colCstName;
    @FXML private TableColumn<CustomerReportRow, String>    colCstType;
    @FXML private TableColumn<CustomerReportRow, String>    colCstCountry;
    @FXML private TableColumn<CustomerReportRow, Number>    colCstOrders;
    @FXML private TableColumn<CustomerReportRow, Number>    colCstSpent;

    // ── Data ─────────────────────────────────────────────────────────────
    private final ObservableList<OrderReportRow>    orderItems    = FXCollections.observableArrayList();
    private final ObservableList<ProductReportRow>  productItems  = FXCollections.observableArrayList();
    private final ObservableList<CustomerReportRow> customerItems = FXCollections.observableArrayList();

    private final OrderDaoInterface    orderDao    = new OrderDao();
    private final ProductDaoInterface  productDao  = new ProductDao();
    private final CustomerDaoInterface customerDao = new CustomerDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-results-worker");
        t.setDaemon(true);
        return t;
    });

    @SuppressWarnings("unused")
    private MainLayoutController mainLayoutController;
    private String  query;
    private boolean initialized;

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    @Override
    public void refreshVisibleData() {
        if (query != null && !query.isBlank()) runSearch();
    }

    public void setQuery(String q) {
        this.query = q;
        if (lblQuery != null)
            lblQuery.setText(q == null || q.isBlank()
                ? "Type in the search bar to find data."
                : "Results for \"" + q + "\"");
        if (initialized) runSearch();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupOrdersTable();
        setupProductsTable();
        setupCustomersTable();
        updateCounts();
        initialized = true;
        if (query != null && !query.isBlank()) runSearch();
        else if (lblQuery != null) lblQuery.setText("Type in the search bar to find data.");
    }

    private void setupOrdersTable() {
        if (tblOrders == null) return;
        tblOrders.setItems(orderItems);
        tblOrders.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblOrders, r -> {
            if (mainLayoutController == null) return;
            String id = r.getOrderId();
            if (id != null && id.startsWith("#")) id = id.substring(1);
            String q = (id != null && !id.isBlank()) ? id : r.getCustomer();
            mainLayoutController.navigateToDetailedReportsWithSearch("orders", q);
        });

        colOrdId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colOrdCustomer.setCellValueFactory(new PropertyValueFactory<>("customer"));
        colOrdProduct.setCellValueFactory(new PropertyValueFactory<>("product"));
        colOrdAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colOrdAmount.setCellFactory(CurrencyUtil.currencyCellFactory());
        colOrdDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colOrdStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colOrdStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                String lc = item.trim().toLowerCase();
                if (lc.contains("completed")) badge.getStyleClass().add("status-badge-paid");
                else if (lc.contains("pending")) badge.getStyleClass().add("status-badge-warning");
                else if (lc.contains("cancelled")) badge.getStyleClass().add("status-badge-danger");
                else badge.getStyleClass().add("status-badge-neutral");
                HBox w = new HBox(badge); w.setAlignment(Pos.CENTER_LEFT); setGraphic(w);
            }
        });
        tblOrders.setPlaceholder(emptyState("No orders match this search"));
    }

    private void setupProductsTable() {
        if (tblProducts == null) return;
        tblProducts.setItems(productItems);
        tblProducts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblProducts, r -> {
            if (mainLayoutController == null) return;
            String q = r.getName();
            if (q == null || q.isBlank()) q = r.getProductId();
            mainLayoutController.navigateToDetailedReportsWithSearch("products", q);
        });

        colPrdId.setCellValueFactory(new PropertyValueFactory<>("productId"));
        colPrdName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrdCat.setCellValueFactory(new PropertyValueFactory<>("category"));
        colPrdUnits.setCellValueFactory(new PropertyValueFactory<>("unitsSold"));
        colPrdRev.setCellValueFactory(new PropertyValueFactory<>("revenue"));
        colPrdRev.setCellFactory(CurrencyUtil.currencyCellFactory());
        colPrdProfit.setCellValueFactory(new PropertyValueFactory<>("profit"));
        colPrdProfit.setCellFactory(CurrencyUtil.currencyCellFactory());
        tblProducts.setPlaceholder(emptyState("No products match this search"));
    }

    private void setupCustomersTable() {
        if (tblCustomers == null) return;
        tblCustomers.setItems(customerItems);
        tblCustomers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        applyRowFactory(tblCustomers, r -> {
            if (mainLayoutController == null) return;
            String q = r.getName();
            if (q == null || q.isBlank()) q = r.getCustomerId();
            mainLayoutController.navigateToDetailedReportsWithSearch("customers", q);
        });

        colCstId.setCellValueFactory(new PropertyValueFactory<>("customerId"));
        colCstName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCstType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colCstType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty); setGraphic(null); setText(null);
                if (empty || v == null) return;
                Label badge = new Label(v);
                boolean co = "Company".equalsIgnoreCase(v.trim());
                badge.getStyleClass().add(co ? "status-badge-company" : "status-badge-individual");
                HBox w = new HBox(badge); w.setAlignment(Pos.CENTER_LEFT); setGraphic(w);
            }
        });
        colCstCountry.setCellValueFactory(new PropertyValueFactory<>("country"));
        colCstOrders.setCellValueFactory(new PropertyValueFactory<>("totalOrders"));
        colCstSpent.setCellValueFactory(new PropertyValueFactory<>("totalSpent"));
        colCstSpent.setCellFactory(CurrencyUtil.currencyCellFactory());
        tblCustomers.setPlaceholder(emptyState("No customers match this search"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEARCH
    // ══════════════════════════════════════════════════════════════════════

    private void runSearch() {
        String q = query != null ? query.trim() : "";
        if (q.isEmpty()) {
            orderItems.clear(); productItems.clear(); customerItems.clear();
            updateCounts(); return;
        }

        Task<Void> task = new Task<>() {
            List<OrderReportRow>    orders;
            List<ProductReportRow>  products;
            List<CustomerReportRow> customers;

            @Override protected Void call() throws Exception {
                LocalDate to   = LocalDate.now();
                LocalDate from = to.minusYears(2);   // wider range for search
                orders    = orderDao.findReportRows(from, to, "All Status", null, q, 0, 0);
                products  = productDao.findReportRows(from, to, "All Categories", q);
                customers = customerDao.findReportRows(null, null, "All", "All", null, q, 0, 0);
                return null;
            }

            @Override protected void succeeded() {
                orderItems.setAll(orders    != null ? orders    : List.of());
                productItems.setAll(products  != null ? products  : List.of());
                customerItems.setAll(customers != null ? customers : List.of());
                updateCounts();
            }

            @Override protected void failed() {
                if (getException() != null) getException().printStackTrace();
            }
        };
        executor.execute(task);
    }

    private void updateCounts() {
        int o = orderItems.size();
        int p = productItems.size();
        int c = customerItems.size();
        if (lblOrdersCount   != null) lblOrdersCount.setText(o + (o == 1 ? " Order"    : " Orders"));
        if (lblProductsCount != null) lblProductsCount.setText(p + (p == 1 ? " Product"  : " Products"));
        if (lblCustomersCount!= null) lblCustomersCount.setText(c + (c == 1 ? " Customer": " Customers"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private <T> void applyRowFactory(TableView<T> table, Consumer<T> onDoubleClick) {
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>() {
                @Override protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    setStyle(empty || item == null ? "-fx-background-color: transparent;"
                        : getIndex() % 2 == 0 ? "-fx-background-color: white;"
                                               : "-fx-background-color: #F9FAFB;");
                }
            };
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty() && onDoubleClick != null) {
                    onDoubleClick.accept(row.getItem());
                }
            });
            row.setOnMouseEntered(e -> { if (!row.isEmpty()) row.setStyle("-fx-background-color: #EFF6FF; -fx-cursor: hand;"); });
            row.setOnMouseExited(e  -> { if (!row.isEmpty()) row.setStyle(row.getIndex() % 2 == 0
                ? "-fx-background-color: white;" : "-fx-background-color: #F9FAFB;"); });
            return row;
        });
    }

    private javafx.scene.layout.VBox emptyState(String message) {
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(6);
        box.setAlignment(Pos.CENTER);
        Label title = new Label(message);
        title.getStyleClass().add("table-placeholder-title");
        Label sub = new Label("Try a different search term");
        sub.getStyleClass().add("table-placeholder-subtitle");
        box.getChildren().addAll(title, sub);
        return box;
    }
}