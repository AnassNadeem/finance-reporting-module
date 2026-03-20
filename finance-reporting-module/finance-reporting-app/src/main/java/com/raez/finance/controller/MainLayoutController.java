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
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainLayoutController {

    private static final String VIEW_PATH              = "/com/raez/finance/view/";
    private static final long   SESSION_WARNING_SECONDS = 30;

    private final AtomicBoolean sessionWarningShown = new AtomicBoolean(false);

    // ── FXML ────────────────────────────────────────────────────────────
    @FXML private VBox      sidebarContainer;
    @FXML private VBox      topBarContainer;
    @FXML private StackPane contentArea;
    @FXML private VBox      footerContainer;

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        System.out.println("[MainLayout] initialize() started");

        // Each component loaded separately so one failure doesn't stop everything.
        // Any error is printed to the terminal with full stack trace.
        safeLoad("Sidebar",   this::loadSidebar);
        safeLoad("TopBar",    this::loadTopBar);
        safeLoad("Footer",    this::loadFooter);
        safeLoad("Dashboard", this::loadDashboard);

        // Wire session auto-logout callback
        SessionManager.setOnTimeoutCallback(() -> {
            System.out.println("[MainLayout] Session timed out — redirecting to login.");
            handleLogout();
        });

        Platform.runLater(this::attachActivityListeners);
        System.out.println("[MainLayout] initialize() completed");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SAFE LOAD WRAPPER
    //  Runs a loader, catches any exception, prints the full stack trace
    //  with a clear header so you know exactly which component failed and why.
    // ══════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    private interface Loader { void load() throws Exception; }

    private void safeLoad(String componentName, Loader loader) {
        try {
            System.out.println("[MainLayout] Loading " + componentName + "...");
            loader.load();
            System.out.println("[MainLayout] " + componentName + " loaded OK");
        } catch (Exception e) {
            // ── Print full stack trace ──────────────────────────────────
            System.err.println();
            System.err.println("╔══════════════════════════════════════════════════════╗");
            System.err.println("║  MAINLAYOUT COMPONENT FAILED: " + componentName);
            System.err.println("╠══════════════════════════════════════════════════════╣");
            // Walk the full cause chain
            Throwable t = e;
            int depth = 0;
            while (t != null) {
                System.err.println("║  [" + depth + "] " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
                t = t.getCause();
                depth++;
            }
            System.err.println("╚══════════════════════════════════════════════════════╝");
            e.printStackTrace(System.err);
            System.err.println();

            // Do NOT rethrow — let the other components still load.
            // The terminal output above tells you exactly what went wrong.
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COMPONENT LOADERS
    // ══════════════════════════════════════════════════════════════════════

    private void loadSidebar() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Sidebar.fxml");
        if (url == null) throw new IllegalStateException("Sidebar.fxml not found at " + VIEW_PATH);
        FXMLLoader loader = new FXMLLoader(url);
        Node root = loader.load();
        SidebarController ctrl = loader.getController();
        if (ctrl != null) ctrl.setMainLayoutController(this);
        sidebarContainer.getChildren().setAll(root);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    private void loadTopBar() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "TopBar.fxml");
        if (url == null) throw new IllegalStateException("TopBar.fxml not found at " + VIEW_PATH);
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        TopBarController ctrl = loader.getController();
        if (ctrl != null) ctrl.setMainLayoutController(this);
        topBarContainer.getChildren().setAll(root);
    }

    private void loadFooter() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Footer.fxml");
        if (url == null) {
            System.out.println("[MainLayout] Footer.fxml not found — skipping");
            return;
        }
        Parent root = FXMLLoader.load(url);
        footerContainer.getChildren().setAll(root);
    }

    private void loadDashboard() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Overview.fxml");
        if (url == null) throw new IllegalStateException("Overview.fxml not found at " + VIEW_PATH);
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        OverviewController ctrl = loader.getController();
        if (ctrl != null) {
            ctrl.setMainLayoutController(this);
            root.setUserData(ctrl);
        }
        contentArea.getChildren().setAll(root);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACTIVITY LISTENERS
    // ══════════════════════════════════════════════════════════════════════

    private void attachActivityListeners() {
        if (contentArea == null || contentArea.getScene() == null) return;
        Node sceneRoot = contentArea.getScene().getRoot();
        sceneRoot.addEventFilter(MouseEvent.ANY, e -> {
            SessionManager.extendSession();
            checkSessionWarning();
        });
        sceneRoot.addEventFilter(KeyEvent.ANY, e -> {
            SessionManager.extendSession();
            checkSessionWarning();
        });
    }

    private void checkSessionWarning() {
        long remaining = SessionManager.getRemainingSeconds();
        if (remaining > SESSION_WARNING_SECONDS) {
            sessionWarningShown.set(false);
            return;
        }
        if (remaining <= 0) return;
        if (sessionWarningShown.getAndSet(true)) return;

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Session Expiring");
        alert.setHeaderText("Your session will expire in " + remaining + " seconds.");
        alert.setContentText("Click 'Stay logged in' to continue, or 'Log out' to sign out.");
        ButtonType stay   = new ButtonType("Stay logged in", ButtonType.OK.getButtonData());
        ButtonType logout = new ButtonType("Log out",        ButtonType.CANCEL.getButtonData());
        alert.getButtonTypes().setAll(stay, logout);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == stay) {
            SessionManager.extendSession();
            sessionWarningShown.set(false);
        } else {
            handleLogout();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTENT SWITCHING
    // ══════════════════════════════════════════════════════════════════════

    public void setContent(Node node) {
        if (contentArea == null) return;

        // Shutdown previous controller if it exposes shutdown()
        if (!contentArea.getChildren().isEmpty()) {
            Object ud = contentArea.getChildren().get(0).getUserData();
            if      (ud instanceof OverviewController c)              c.shutdown();
            else if (ud instanceof DetailedReportsController c)        c.shutdown();
            else if (ud instanceof GlobalSearchResultsController c)    c.shutdown();
        }

        contentArea.getChildren().setAll(node);

        // Fade in (200 ms)
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOGOUT
    // ══════════════════════════════════════════════════════════════════════

    public void handleLogout() {
        SessionManager.logout();
        try {
            URL url = getClass().getResource(VIEW_PATH + "RoleSelection.fxml");
            if (url == null) return;
            Parent root  = FXMLLoader.load(url);
            Scene  scene = new Scene(root);
            URL    css   = getClass().getResource("/css/app.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setWidth(1000);
            stage.setHeight(720);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            System.err.println("[MainLayout] handleLogout failed:");
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GLOBAL SEARCH
    // ══════════════════════════════════════════════════════════════════════

    public void showGlobalSearch(String query) {
        if (query == null || query.isBlank()) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + "GlobalSearchResults.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            GlobalSearchResultsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setMainLayoutController(this);
                ctrl.setQuery(query);
                root.setUserData(ctrl);
            }
            setContent(root);
        } catch (Exception e) {
            System.err.println("[MainLayout] showGlobalSearch failed:");
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  REPORTS + NAVIGATE
    // ══════════════════════════════════════════════════════════════════════

    public void navigateToReportsAndExport(String reportType, String format) {
        try {
            URL url = getClass().getResource(VIEW_PATH + "DetailedReports.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            DetailedReportsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setMainLayoutController(this);
                root.setUserData(ctrl);
                ctrl.setAfterLoadCallback(() -> {
                    if ("pdf".equalsIgnoreCase(format)) ctrl.performExportPDF();
                    else ctrl.performExportCSV();
                });
            }
            setContent(root);
            if (ctrl != null) ctrl.switchToTab(reportType != null ? reportType : "orders");
        } catch (Exception e) {
            System.err.println("[MainLayout] navigateToReportsAndExport failed:");
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOAST
    // ══════════════════════════════════════════════════════════════════════

    public void showToast(String type, String message) {
        if (contentArea == null) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + "NotificationToast.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Node toast = loader.load();
            NotificationToastController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setNotification(type, message, () -> {
                    if (contentArea.getChildren().contains(toast))
                        contentArea.getChildren().remove(toast);
                });
            }
            contentArea.getChildren().add(toast);
            StackPane.setAlignment(toast, Pos.TOP_RIGHT);
            StackPane.setMargin(toast, new Insets(20, 20, 0, 0));
        } catch (Exception e) {
            System.err.println("[MainLayout] showToast failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT HELPERS
    // ══════════════════════════════════════════════════════════════════════

    public void exportDashboardReport(String format) {
        Window window = getWindow();
        if (window == null) return;
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                DashboardService ds = new DashboardService();
                LocalDate to   = LocalDate.now();
                LocalDate from = to.minusDays(30);
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Metric", "Value"});
                rows.add(new String[]{"Total Sales",          CurrencyUtil.formatCurrency(ds.getTotalSales(from, to, null))});
                rows.add(new String[]{"Total Profit",         CurrencyUtil.formatCurrency(ds.getTotalProfit(from, to, null))});
                rows.add(new String[]{"Outstanding Payments", CurrencyUtil.formatCurrency(ds.getOutstandingPayments(from, to, null))});
                rows.add(new String[]{"Refunds",              CurrencyUtil.formatCurrency(ds.getRefunds(from, to, null))});
                rows.add(new String[]{"Total Customers",      String.valueOf(ds.getTotalCustomers())});
                rows.add(new String[]{"Total Orders",         String.valueOf(ds.getTotalOrders(from, to, null))});
                rows.add(new String[]{"Avg Order Value",      CurrencyUtil.formatCurrency(ds.getAverageOrderValue(from, to, null))});
                String pop = ds.getMostPopularProductName(from, to, null);
                rows.add(new String[]{"Most Popular Product", pop != null ? pop : "—"});
                return rows;
            }
        };
        finishExport(task, "Dashboard Summary", "dashboard_summary", format, window);
    }

    public void exportOrderReport(String format)    { doExport("orders",    "Order Report",    "order_report",    format); }
    public void exportProductReport(String format)  { doExport("products",  "Product Report",  "product_report",  format); }
    public void exportCustomerReport(String format) { doExport("customers", "Customer Report", "customer_report", format); }

    private void doExport(String type, String title, String fileName, String format) {
        Window window = getWindow();
        if (window == null) return;
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(30);

        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                List<String[]> rows = new ArrayList<>();
                switch (type) {
                    case "orders" -> {
                        List<OrderReportRow> list =
                            new OrderDao().findReportRows(from, to, "All Status", null, 0, 0);
                        rows.add(new String[]{"Order ID","Customer","Product","Amount","Date","Status"});
                        for (OrderReportRow r : list)
                            rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(),
                                CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
                    }
                    case "products" -> {
                        List<ProductReportRow> list =
                            new ProductDao().findReportRows(from, to, "All Categories", null, 0, 0);
                        rows.add(new String[]{"Product ID","Name","Category","Cost","Sale Price","Profit","Units","Revenue"});
                        for (ProductReportRow r : list)
                            rows.add(new String[]{r.getProductId(), r.getName(), r.getCategory(),
                                CurrencyUtil.formatCurrency(r.getCost()),
                                CurrencyUtil.formatCurrency(r.getSalePrice()),
                                CurrencyUtil.formatCurrency(r.getProfit()),
                                String.valueOf(r.getUnitsSold()),
                                CurrencyUtil.formatCurrency(r.getRevenue())});
                    }
                    default -> {
                        List<CustomerReportRow> list =
                            new CustomerDao().findReportRows("All", "All", null, null, 0, 0);
                        rows.add(new String[]{"Customer ID","Name","Type","Country","Orders","Spent","Avg Order","Last Purchase"});
                        for (CustomerReportRow r : list)
                            rows.add(new String[]{r.getCustomerId(), r.getName(), r.getType(),
                                r.getCountry(), String.valueOf(r.getTotalOrders()),
                                CurrencyUtil.formatCurrency(r.getTotalSpent()),
                                CurrencyUtil.formatCurrency(r.getAvgOrderValue()),
                                r.getLastPurchase()});
                    }
                }
                return rows;
            }
        };
        finishExport(task, title, fileName, format, window);
    }

    private void finishExport(Task<List<String[]>> task, String title,
                               String fileName, String format, Window window) {
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue();
            if (data == null || data.isEmpty()) return;
            File file = pickFile(window, title, fileName, format);
            if (file == null) return;
            try {
                if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF(title, data, file);
                else                                 ExportService.exportRowsToCSV(data, file);
                showToast("success", "Exported: " + file.getName());
            } catch (Exception ex) {
                System.err.println("[MainLayout] Export failed:");
                ex.printStackTrace();
                showToast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown"));
            }
        });
        task.setOnFailed(ev -> {
            System.err.println("[MainLayout] Export task failed:");
            if (task.getException() != null) task.getException().printStackTrace();
            showToast("error", "Export failed.");
        });
        new Thread(task, "export-worker").start();
    }

    private File pickFile(Window window, String title, String defaultName, String format) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export " + title);
        if ("pdf".equalsIgnoreCase(format)) {
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fc.setInitialFileName(defaultName + ".pdf");
        } else {
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            fc.setInitialFileName(defaultName + ".csv");
        }
        return fc.showSaveDialog(window);
    }

    private Window getWindow() {
        return (contentArea != null && contentArea.getScene() != null)
            ? contentArea.getScene().getWindow() : null;
    }
}