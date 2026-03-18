package com.raez.finance.controller;

import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.OrderReportRow;
import com.raez.finance.model.ProductReportRow;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainLayoutController {

    private static final String VIEW_PATH = "/com/raez/finance/view/";
    private static final int SESSION_CHECK_PERIOD_SECONDS = 60;
    private static final int SESSION_WARNING_THRESHOLD_SECONDS = 120;

    private final ScheduledExecutorService sessionChecker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-checker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean sessionWarningShown = new AtomicBoolean(false);

    @FXML private VBox sidebarContainer;
    @FXML private VBox topBarContainer;
    @FXML private StackPane contentArea;
    @FXML private VBox footerContainer;

    @FXML
    public void initialize() {
        try {
            URL sidebarUrl = getClass().getResource(VIEW_PATH + "Sidebar.fxml");
            if (sidebarUrl != null) {
                FXMLLoader sidebarLoader = new FXMLLoader(sidebarUrl);
                Node sidebarRoot = sidebarLoader.load();
                SidebarController sidebarController = sidebarLoader.getController();
                if (sidebarController != null) {
                    sidebarController.setMainLayoutController(this);
                }
                sidebarContainer.getChildren().clear();
                sidebarContainer.getChildren().add(sidebarRoot);
                VBox.setVgrow(sidebarRoot, Priority.ALWAYS);
            }

            URL topBarUrl = getClass().getResource(VIEW_PATH + "TopBar.fxml");
            if (topBarUrl != null) {
                FXMLLoader topBarLoader = new FXMLLoader(topBarUrl);
                Parent topBarRoot = topBarLoader.load();
                TopBarController topBarController = topBarLoader.getController();
                if (topBarController != null) {
                    topBarController.setMainLayoutController(this);
                }
                topBarContainer.getChildren().clear();
                topBarContainer.getChildren().add(topBarRoot);
            }

            URL footerUrl = getClass().getResource(VIEW_PATH + "Footer.fxml");
            if (footerUrl != null) {
                Parent footerRoot = FXMLLoader.load(footerUrl);
                footerContainer.getChildren().clear();
                footerContainer.getChildren().add(footerRoot);
            }

            URL overviewUrl = getClass().getResource(VIEW_PATH + "Overview.fxml");
            if (overviewUrl != null) {
                FXMLLoader overviewLoader = new FXMLLoader(overviewUrl);
                Parent overviewRoot = overviewLoader.load();
                OverviewController overviewController = overviewLoader.getController();
                if (overviewController != null) {
                    overviewController.setMainLayoutController(this);
                    overviewRoot.setUserData(overviewController);
                }
                contentArea.getChildren().clear();
                contentArea.getChildren().add(overviewRoot);
            }

            startSessionTimeoutChecker();
            Platform.runLater(this::attachSessionActivityListeners);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MainLayout", e);
        }
    }

    /** Extend session only on user input (mouse/key) in the main window; do not extend from the periodic checker. */
    private void attachSessionActivityListeners() {
        Node root = contentArea != null && contentArea.getScene() != null ? contentArea.getScene().getRoot() : null;
        if (root == null) return;
        root.addEventFilter(MouseEvent.ANY, e -> SessionManager.extendSession());
        root.addEventFilter(KeyEvent.ANY, e -> SessionManager.extendSession());
    }

    private void startSessionTimeoutChecker() {
        sessionChecker.scheduleAtFixedRate(() -> {
            Platform.runLater(this::checkSessionExpiry);
        }, SESSION_CHECK_PERIOD_SECONDS, SESSION_CHECK_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    private void checkSessionExpiry() {
        if (contentArea == null || contentArea.getScene() == null) return;
        long remaining = SessionManager.getRemainingSeconds();
        if (remaining <= 0) {
            handleLogout();
            return;
        }
        if (remaining > SESSION_WARNING_THRESHOLD_SECONDS) {
            sessionWarningShown.set(false);
            return;
        }
        if (sessionWarningShown.getAndSet(true)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Session Expiring");
        alert.setHeaderText("Your session will expire in about 2 minutes.");
        alert.setContentText("Click 'Stay logged in' to continue or 'Log out' to sign out now.");
        ButtonType stayButton = new ButtonType("Stay logged in", ButtonType.OK.getButtonData());
        ButtonType logoutButton = new ButtonType("Log out", ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(stayButton, logoutButton);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == stayButton) {
            SessionManager.extendSession();
            sessionWarningShown.set(false);
        } else {
            handleLogout();
        }
    }

    /** Swaps the center content area to the given node. */
    public void setContent(Node node) {
        if (contentArea != null) {
            if (!contentArea.getChildren().isEmpty()) {
                Node current = contentArea.getChildren().get(0);
                Object userData = current.getUserData();
                if (userData instanceof GlobalSearchResultsController) {
                    ((GlobalSearchResultsController) userData).shutdown();
                } else if (userData instanceof DetailedReportsController) {
                    ((DetailedReportsController) userData).shutdown();
                } else if (userData instanceof OverviewController) {
                    ((OverviewController) userData).shutdown();
                }
            }
            contentArea.getChildren().clear();
            contentArea.getChildren().add(node);
        }
    }

    /** Logs out and navigates back to RoleSelection. */
    public void handleLogout() {
        com.raez.finance.service.SessionManager.logout();
        try {
            URL url = getClass().getResource(VIEW_PATH + "RoleSelection.fxml");
            if (url == null) return;
            Parent root = FXMLLoader.load(url);
            Scene scene = new Scene(root, 1000, 700);
            URL cssUrl = getClass().getResource("/css/app.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();
        } catch (Exception e) {
            throw new RuntimeException("Failed to navigate to RoleSelection", e);
        }
    }

    /**
     * Navigates to Detailed Reports, switches to the given tab, and triggers export (CSV or PDF).
     * @param reportType "orders", "products", or "customers"
     * @param format "csv" or "pdf"
     */
    public void navigateToReportsAndExport(String reportType, String format) {
        try {
            URL url = getClass().getResource(VIEW_PATH + "DetailedReports.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            DetailedReportsController controller = loader.getController();
            if (controller != null) {
                controller.setMainLayoutController(this);
                root.setUserData(controller);
                final String fmt = format;
                controller.setAfterLoadCallback(() -> {
                    if ("pdf".equalsIgnoreCase(fmt)) {
                        controller.performExportPDF();
                    } else {
                        controller.performExportCSV();
                    }
                });
            }
            setContent(root);
            if (controller != null) {
                controller.switchToTab(reportType != null ? reportType : "orders");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to navigate and export", e);
        }
    }

    /**
     * Shows a toast notification on top of the content area (success, error, or info).
     */
    public void showToast(String type, String message) {
        if (contentArea == null) return;
        try {
            URL toastUrl = getClass().getResource(VIEW_PATH + "NotificationToast.fxml");
            if (toastUrl == null) return;
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(toastUrl);
            Node toastNode = loader.load();
            NotificationToastController c = loader.getController();
            if (c != null) {
                c.setNotification(type, message, () -> {
                    if (contentArea.getChildren().contains(toastNode)) {
                        contentArea.getChildren().remove(toastNode);
                    }
                });
            }
            contentArea.getChildren().add(toastNode);
            StackPane.setAlignment(toastNode, Pos.TOP_RIGHT);
            StackPane.setMargin(toastNode, new Insets(24, 24, 0, 24));
        } catch (Exception ignored) {
        }
    }

    /**
     * Export dashboard (overview) data to file without navigating. Stays on current page.
     */
    public void exportDashboardReport(String format) {
        Window window = contentArea != null && contentArea.getScene() != null ? contentArea.getScene().getWindow() : null;
        if (window == null) return;
        Task<List<String[]>> task = new Task<>() {
            @Override
            protected List<String[]> call() throws Exception {
                DashboardService ds = new DashboardService();
                LocalDate to = LocalDate.now();
                LocalDate from = to.minusDays(30);
                String cat = "All Categories";
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Metric", "Value"});
                rows.add(new String[]{"Total Sales", CurrencyUtil.formatCurrency(ds.getTotalSales(from, to, cat))});
                rows.add(new String[]{"Total Profit", CurrencyUtil.formatCurrency(ds.getTotalProfit(from, to, cat))});
                rows.add(new String[]{"Outstanding Payments", CurrencyUtil.formatCurrency(ds.getOutstandingPayments(from, to, cat))});
                rows.add(new String[]{"Refunds", CurrencyUtil.formatCurrency(ds.getRefunds(from, to, cat))});
                rows.add(new String[]{"Total Customers", String.valueOf(ds.getTotalCustomers())});
                rows.add(new String[]{"Total Orders", String.valueOf(ds.getTotalOrders(from, to, cat))});
                rows.add(new String[]{"Average Order Value", CurrencyUtil.formatCurrency(ds.getAverageOrderValue(from, to, cat))});
                rows.add(new String[]{"Most Popular Product", ds.getMostPopularProductName(from, to, cat) != null ? ds.getMostPopularProductName(from, to, cat) : "—"});
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue();
            if (data == null) return;
            File file = chooseFile(window, "Dashboard Summary", "dashboard_summary", format);
            if (file == null) return;
            try {
                if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF("Dashboard Summary", data, file);
                else ExportService.exportRowsToCSV(data, file);
                showToast("success", "Exported to " + file.getName());
            } catch (Exception ex) {
                showToast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
            }
        });
        task.setOnFailed(ev -> showToast("error", "Export failed."));
        new Thread(task).start();
    }

    /**
     * Export order report data to file without navigating.
     */
    public void exportOrderReport(String format) {
        exportReportWithoutNavigate("orders", "Order Report", "order_report", format);
    }

    /**
     * Export product report data to file without navigating.
     */
    public void exportProductReport(String format) {
        exportReportWithoutNavigate("products", "Product Report", "product_report", format);
    }

    /**
     * Export customer report data to file without navigating.
     */
    public void exportCustomerReport(String format) {
        exportReportWithoutNavigate("customers", "Customer Report", "customer_report", format);
    }

    private void exportReportWithoutNavigate(String reportType, String title, String defaultName, String format) {
        Window window = contentArea != null && contentArea.getScene() != null ? contentArea.getScene().getWindow() : null;
        if (window == null) return;
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        Task<List<String[]>> task = new Task<>() {
            @Override
            protected List<String[]> call() throws Exception {
                List<String[]> rows = new ArrayList<>();
                if ("orders".equals(reportType)) {
                    OrderDao dao = new OrderDao();
                    List<OrderReportRow> list = dao.findReportRows(from, to, "All Status", null, 0, 0);
                    rows.add(new String[]{"Order ID", "Customer", "Product", "Amount", "Date", "Status"});
                    for (OrderReportRow r : list) {
                        rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(), CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
                    }
                } else if ("products".equals(reportType)) {
                    ProductDao dao = new ProductDao();
                    List<ProductReportRow> list = dao.findReportRows(from, to, "All Categories", null, 0, 0);
                    rows.add(new String[]{"Product ID", "Name", "Category", "Cost", "Sale Price", "Profit", "Units Sold", "Revenue"});
                    for (ProductReportRow r : list) {
                        rows.add(new String[]{r.getProductId(), r.getName(), r.getCategory(), CurrencyUtil.formatCurrency(r.getCost()), CurrencyUtil.formatCurrency(r.getSalePrice()), CurrencyUtil.formatCurrency(r.getProfit()), String.valueOf(r.getUnitsSold()), CurrencyUtil.formatCurrency(r.getRevenue())});
                    }
                } else {
                    CustomerDao dao = new CustomerDao();
                    List<CustomerReportRow> list = dao.findReportRows("All", "All", null, null, 0, 0);
                    rows.add(new String[]{"Customer ID", "Name", "Type", "Country", "Total Orders", "Total Spent", "Avg Order Value", "Last Purchase"});
                    for (CustomerReportRow r : list) {
                        rows.add(new String[]{r.getCustomerId(), r.getName(), r.getType(), r.getCountry(), String.valueOf(r.getTotalOrders()), CurrencyUtil.formatCurrency(r.getTotalSpent()), CurrencyUtil.formatCurrency(r.getAvgOrderValue()), r.getLastPurchase()});
                    }
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue();
            if (data == null) return;
            File file = chooseFile(window, title, defaultName, format);
            if (file == null) return;
            try {
                if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF(title, data, file);
                else ExportService.exportRowsToCSV(data, file);
                showToast("success", "Exported to " + file.getName());
            } catch (Exception ex) {
                showToast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
            }
        });
        task.setOnFailed(ev -> showToast("error", "Export failed."));
        new Thread(task).start();
    }

    private File chooseFile(Window window, String dialogTitle, String defaultName, String format) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + dialogTitle);
        if ("pdf".equalsIgnoreCase(format)) {
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            fc.setInitialFileName(defaultName + ".pdf");
        } else {
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            fc.setInitialFileName(defaultName + ".csv");
        }
        return fc.showSaveDialog(window);
    }

    /**
     * Shows the unified global search results view for the given query.
     */
    public void showGlobalSearch(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        try {
            URL url = getClass().getResource(VIEW_PATH + "GlobalSearchResults.fxml");
            if (url == null) {
                return;
            }
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            GlobalSearchResultsController controller = loader.getController();
            if (controller != null) {
                controller.setMainLayoutController(this);
                controller.setQuery(query);
                root.setUserData(controller);
            }
            setContent(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to show global search results", e);
        }
    }
}
