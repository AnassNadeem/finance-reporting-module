package com.raez.finance.controller;

// ── All existing imports preserved ──────────────────────────────────────
import com.raez.finance.dao.AlertDao;
import com.raez.finance.dao.CustomerDao;
import com.raez.finance.dao.CustomerDaoInterface;
import com.raez.finance.dao.FinancialAnomalyDao;
import com.raez.finance.dao.InventorySupplierDao;
import com.raez.finance.dao.OrderDao;
import com.raez.finance.dao.OrderDaoInterface;
import com.raez.finance.dao.ProductDao;
import com.raez.finance.dao.ProductDaoInterface;
import com.raez.finance.dao.RevenueVatDao;
import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.OrderReportRow;
import com.raez.finance.model.ProductReportRow;
import com.raez.finance.model.TopBuyerRow;
import com.raez.finance.service.DashboardService;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import com.raez.finance.util.StageNavigator;          // ← NEW: StageNavigator import
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MainLayoutController {

    private static final String VIEW_PATH               = "/com/raez/finance/view/";
    private static final long   SESSION_WARNING_SECONDS = 30;

    public static void queueStartupToast(String type, String message) {
        pendingToastType    = type;
        pendingToastMessage = message;
    }

    private static volatile String pendingToastType;
    private static volatile String pendingToastMessage;

    private TopBarController topBarController;
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
        safeLoad("Sidebar",   this::loadSidebar);
        safeLoad("TopBar",    this::loadTopBar);
        safeLoad("Footer",    this::loadFooter);
        safeLoad("Dashboard", this::loadDashboard);

        SessionManager.setOnTimeoutCallback(() -> {
            System.out.println("[MainLayout] Session timed out — redirecting to login.");
            handleLogout();
        });

        Platform.runLater(() -> {
            attachActivityListeners();
            refreshNotificationBadge();
            String pt = pendingToastType;
            String pm = pendingToastMessage;
            if (pm != null && !pm.isBlank()) {
                pendingToastType    = null;
                pendingToastMessage = null;
                showToast(pt != null ? pt : "success", pm);
            }
        });
        System.out.println("[MainLayout] initialize() completed");
    }

    // ── Safe load wrapper ────────────────────────────────────────────────

    @FunctionalInterface
    private interface Loader { void load() throws Exception; }

    private void safeLoad(String componentName, Loader loader) {
        try {
            System.out.println("[MainLayout] Loading " + componentName + "...");
            loader.load();
            System.out.println("[MainLayout] " + componentName + " loaded OK");
        } catch (Exception e) {
            System.err.println();
            System.err.println("╔══════════════════════════════════════════════════════╗");
            System.err.println("║  MAINLAYOUT COMPONENT FAILED: " + componentName);
            System.err.println("╠══════════════════════════════════════════════════════╣");
            Throwable t = e; int depth = 0;
            while (t != null) {
                System.err.println("║  [" + depth + "] " + t.getClass().getSimpleName() + ": " + t.getMessage());
                t = t.getCause(); depth++;
            }
            System.err.println("╚══════════════════════════════════════════════════════╝");
            e.printStackTrace(System.err);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COMPONENT LOADERS — unchanged
    // ══════════════════════════════════════════════════════════════════════

    private void loadSidebar() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Sidebar.fxml");
        if (url == null) throw new IllegalStateException("Sidebar.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Node root = loader.load();
        SidebarController ctrl = loader.getController();
        if (ctrl != null) ctrl.setMainLayoutController(this);
        sidebarContainer.getChildren().setAll(root);
        VBox.setVgrow(root, Priority.ALWAYS);
    }

    private void loadTopBar() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "TopBar.fxml");
        if (url == null) throw new IllegalStateException("TopBar.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        TopBarController ctrl = loader.getController();
        this.topBarController = ctrl;
        if (ctrl != null) ctrl.setMainLayoutController(this);
        topBarContainer.getChildren().setAll(root);
    }

    private void loadFooter() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Footer.fxml");
        if (url == null) { System.out.println("[MainLayout] Footer.fxml not found — skipping"); return; }
        Parent root = FXMLLoader.load(url);
        footerContainer.getChildren().setAll(root);
    }

    private void loadDashboard() throws Exception {
        URL url = getClass().getResource(VIEW_PATH + "Overview.fxml");
        if (url == null) throw new IllegalStateException("Overview.fxml not found");
        FXMLLoader loader = new FXMLLoader(url);
        Parent root = loader.load();
        OverviewController ctrl = loader.getController();
        if (ctrl != null) { ctrl.setMainLayoutController(this); root.setUserData(ctrl); }
        contentArea.getChildren().setAll(root);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACTIVITY LISTENERS — unchanged
    // ══════════════════════════════════════════════════════════════════════

    private void attachActivityListeners() {
        if (contentArea == null || contentArea.getScene() == null) return;
        Node sceneRoot = contentArea.getScene().getRoot();
        sceneRoot.addEventFilter(MouseEvent.ANY, e -> { SessionManager.extendSession(); checkSessionWarning(); });
        sceneRoot.addEventFilter(KeyEvent.ANY,   e -> { SessionManager.extendSession(); checkSessionWarning(); });
    }

    private void checkSessionWarning() {
        long remaining = SessionManager.getRemainingSeconds();
        if (remaining > SESSION_WARNING_SECONDS) { sessionWarningShown.set(false); return; }
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
        if (result.isPresent() && result.get() == stay) { SessionManager.extendSession(); sessionWarningShown.set(false); }
        else handleLogout();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CONTENT SWITCHING — unchanged
    // ══════════════════════════════════════════════════════════════════════

    public void setContent(Node node) {
        if (contentArea == null) return;

        if (!contentArea.getChildren().isEmpty()) {
            Object ud = contentArea.getChildren().get(0).getUserData();
            if      (ud instanceof OverviewController c)            c.shutdown();
            else if (ud instanceof DetailedReportsController c)     c.shutdown();
            else if (ud instanceof GlobalSearchResultsController c) c.shutdown();
            else if (ud instanceof InvoicesController c)            c.shutdown();
            else if (ud instanceof NotificationsAlertsController c) c.shutdown();
            else if (ud instanceof AuditLogController c)            c.shutdown();
            else if (ud instanceof AiInsightsController c)          c.shutdown();
        }

        contentArea.getChildren().setAll(node);
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(200), node);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        refreshNotificationBadge();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOGOUT — ONLY CHANGE IN THIS FILE
    //  Previously used inline FXMLLoader + manual scene swap which caused
    //  the window-shrink bug. Now delegates to StageNavigator.navigateToLogin()
    //  which handles the false→true maximize toggle correctly.
    // ══════════════════════════════════════════════════════════════════════

    public void handleLogout() {
        SessionManager.logout();
        if (contentArea == null || contentArea.getScene() == null) return;
        Stage stage = (Stage) contentArea.getScene().getWindow();
        StageNavigator.navigateToLogin(stage);   // ← replaces old inline scene-swap
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NOTIFICATION BADGE — unchanged
    // ══════════════════════════════════════════════════════════════════════

    public void updateNotificationBadge(int count) {
        Platform.runLater(() -> {
            if (topBarController != null) topBarController.setNotificationBadgeCount(count);
        });
    }

    public void refreshNotificationBadge() {
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() throws Exception {
                int a = new AlertDao().countUnresolved();
                int f = new FinancialAnomalyDao().countUnresolved();
                return a + f;
            }
        };
        task.setOnSucceeded(e -> updateNotificationBadge(task.getValue() != null ? task.getValue() : 0));
        task.setOnFailed(ev  -> updateNotificationBadge(0));
        new Thread(task, "notification-badge").start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GLOBAL SEARCH — unchanged
    // ══════════════════════════════════════════════════════════════════════

    public void showGlobalSearch(String query) {
        if (query == null || query.isBlank()) return;
        try {
            URL url = getClass().getResource(VIEW_PATH + "GlobalSearchResults.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            GlobalSearchResultsController ctrl = loader.getController();
            if (ctrl != null) { ctrl.setMainLayoutController(this); ctrl.setQuery(query); root.setUserData(ctrl); }
            setContent(root);
        } catch (Exception e) {
            System.err.println("[MainLayout] showGlobalSearch failed:"); e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NAVIGATE TO DETAILED REPORTS — unchanged
    // ══════════════════════════════════════════════════════════════════════

    public void navigateToDetailedReportsWithSearch(String tab, String searchText) {
        try {
            URL url = getClass().getResource(VIEW_PATH + "DetailedReports.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            DetailedReportsController ctrl = loader.getController();
            if (ctrl != null) {
                ctrl.setMainLayoutController(this);
                root.setUserData(ctrl);
                ctrl.openWithContext(tab != null ? tab : "orders", searchText);
            }
            setContent(root);
        } catch (Exception e) {
            System.err.println("[MainLayout] navigateToDetailedReportsWithSearch failed:"); e.printStackTrace();
        }
    }

    public void navigateToAiInsights() {
        try {
            URL url = getClass().getResource(VIEW_PATH + "AiInsights.fxml");
            if (url == null) return;
            FXMLLoader loader = new FXMLLoader(url);
            Parent root = loader.load();
            AiInsightsController ctrl = loader.getController();
            if (ctrl != null) { ctrl.setMainLayoutController(this); root.setUserData(ctrl); }
            setContent(root);
        } catch (Exception e) {
            System.err.println("[MainLayout] navigateToAiInsights failed:"); e.printStackTrace();
        }
    }

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
            System.err.println("[MainLayout] navigateToReportsAndExport failed:"); e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOAST — unchanged
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
                ctrl.setCompact(type, message, () -> {
                    if (contentArea.getChildren().contains(toast))
                        contentArea.getChildren().remove(toast);
                });
            }
            contentArea.getChildren().add(toast);
            StackPane.setAlignment(toast, Pos.TOP_RIGHT);
            StackPane.setMargin(toast, new Insets(12, 12, 0, 0));
        } catch (Exception e) {
            System.err.println("[MainLayout] showToast failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ALL EXPORT METHODS — unchanged from document 16 (MainLayoutController)
    //  Paste the full export methods here from your existing MainLayoutController.
    //  Only handleLogout() needed changing for this PR.
    // ══════════════════════════════════════════════════════════════════════

    public void exportMergedCustomerReport() {
        if (!SessionManager.isAdmin()) return;
        Window window = getWindow();
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                return buildMergedCustomerRows();
            }
        };
        finishMergedExport(task, "RAEZ Finance — Customer Report", "customer_merged_report", window);
    }

    private List<String[]> buildMergedCustomerRows() throws Exception {
        LocalDate to = LocalDate.now();
        return buildRichCustomerReportRows(to.minusDays(90), to);
    }

    private List<String[]> buildRichCustomerReportRows(LocalDate from, LocalDate to) throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        CustomerDao cDao = new CustomerDao();
        int total = cDao.getTotalCustomerCount();
        int companies = cDao.getCompanyCustomerCount();
        int individuals = total - companies;
        double totalRev = cDao.getTotalRevenue();
        double avgSpend = total > 0 ? totalRev / total : 0;
        List<TopBuyerRow> topBuyers = cDao.findTopBuyers(20);
        List<com.raez.finance.dao.CustomerDao.MonthlyCount> monthly = cDao.findMonthlyOrderCounts(from, to);
        List<String> refunds = cDao.findRefundAlerts();
        List<String> issues = cDao.findProductIssueAlerts();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__", "Customer Report", "Customer List & Insights — Combined", date});
        rows.add(new String[]{"__SECTION__", "Customer Summary", "Lifetime aggregates"});
        rows.add(new String[]{"__KPI__",
            "Total Customers", String.valueOf(total), "Companies", String.valueOf(companies),
            "Individuals", String.valueOf(individuals), "Avg Spend", CurrencyUtil.formatCurrency(avgSpend)});
        rows.add(new String[]{"__SECTION__", "Monthly Order Volume", "Orders per month"});
        List<String> chartRow = new ArrayList<>(); chartRow.add("__BARCHART__"); chartRow.add("Orders per Month");
        for (var m : monthly) { chartRow.add(m.month); chartRow.add(String.valueOf(m.count)); }
        rows.add(chartRow.toArray(new String[0]));
        rows.add(new String[]{"__BARCHART__", "Customer Types", "Companies", String.valueOf(companies), "Individuals", String.valueOf(individuals)});
        rows.add(new String[]{"__SECTION__", "Top Buyers", "Ranked by lifetime spending"});
        rows.add(new String[]{"__TABLEHEADER__", "Rank", "Customer", "Type", "Total Spent", "Orders", "Avg Order", "Last Purchase"});
        for (TopBuyerRow r : topBuyers)
            rows.add(new String[]{String.valueOf(r.getRank()), r.getName(), r.getType(),
                CurrencyUtil.formatCurrency(r.getTotalSpent()), String.valueOf(r.getTotalOrders()),
                CurrencyUtil.formatCurrency(r.getAvgOrderValue()), r.getLastPurchase() != null ? r.getLastPurchase() : "—"});
        if (!refunds.isEmpty() || !issues.isEmpty()) {
            rows.add(new String[]{"__SECTION__", "Alerts", "Refund patterns and product issues"});
            rows.add(new String[]{"__TABLEHEADER__", "Alert Type", "Detail"});
            for (String r : refunds) rows.add(new String[]{"Refund Pattern", r});
            for (String r : issues)  rows.add(new String[]{"Product Issue", r});
        }
        return rows;
    }

    public void exportMergedOrderReport() {
        if (!SessionManager.isAdmin()) return;
        Window window = getWindow();
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception { return buildMergedOrderRows(); }
        };
        finishMergedExport(task, "RAEZ Finance — Order Report", "order_merged_report", window);
    }

    private List<String[]> buildMergedOrderRows() throws Exception {
        LocalDate to = LocalDate.now(), from = to.minusDays(90);
        String date = to.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        DashboardService ds = new DashboardService(); OrderDao od = new OrderDao();
        double totalSales = ds.getTotalSales(from, to, null);
        int totalOrders = ds.getTotalOrders(from, to, null);
        double aov = ds.getAverageOrderValue(from, to, null);
        double outstanding = ds.getOutstandingPayments(from, to, null);
        String popular = ds.getMostPopularProductName(from, to, null);
        List<OrderReportRow> allOrders = od.findReportRows(from, to, "All Status", null, null, 200, 0);
        Map<String, Long> byStatus = allOrders.stream()
            .collect(Collectors.groupingBy(r -> r.getStatus() != null ? r.getStatus() : "Unknown", Collectors.counting()));
        Map<String, Double> byMonth = allOrders.stream()
            .filter(r -> r.getDate() != null && r.getDate().length() >= 7)
            .collect(Collectors.groupingBy(r -> r.getDate().substring(0, 7), Collectors.summingDouble(OrderReportRow::getAmount)));
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__", "Order Report", "Order Data & Revenue Analysis — Last 90 Days", date});
        rows.add(new String[]{"__SECTION__", "Order Summary", "Last 90 days"});
        rows.add(new String[]{"__KPI__",
            "Total Revenue", CurrencyUtil.formatCurrency(totalSales),
            "Total Orders", String.valueOf(totalOrders),
            "Avg Order Value", CurrencyUtil.formatCurrency(aov),
            "Outstanding", CurrencyUtil.formatCurrency(outstanding)});
        if (popular != null) rows.add(new String[]{"__KPI__", "Most Popular Product", popular, "", "", "", "", ""});
        rows.add(new String[]{"__SECTION__", "Order Status Breakdown", "Count by status"});
        List<String> sc = new ArrayList<>(); sc.add("__BARCHART__"); sc.add("Orders by Status");
        byStatus.forEach((s, c) -> { sc.add(s); sc.add(String.valueOf(c)); });
        rows.add(sc.toArray(new String[0]));
        rows.add(new String[]{"__SECTION__", "Monthly Revenue Trend", "Revenue by month"});
        List<String> mc = new ArrayList<>(); mc.add("__BARCHART__"); mc.add("Revenue per Month");
        byMonth.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(e -> { mc.add(e.getKey()); mc.add(String.valueOf(e.getValue().longValue())); });
        rows.add(mc.toArray(new String[0]));
        rows.add(new String[]{"__SECTION__", "Order Detail", "Individual orders"});
        rows.add(new String[]{"__TABLEHEADER__", "Order ID", "Customer", "Product", "Amount", "Date", "Status"});
        for (OrderReportRow r : allOrders)
            rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(),
                CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
        return rows;
    }

    public void exportMergedProductReport() {
        if (!SessionManager.isAdmin()) return;
        Window window = getWindow();
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception { return buildRichProductReportRows(LocalDate.now().minusDays(90), LocalDate.now()); }
        };
        finishMergedExport(task, "RAEZ Finance — Product Report", "product_merged_report", window);
    }

    private List<String[]> buildRichProductReportRows(LocalDate from, LocalDate to) throws Exception {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        ProductDao pd = new ProductDao();
        List<ProductReportRow> allProducts = pd.findReportRows(from, to, "All Categories", null, 200, 0);
        List<ProductDao.CategoryRevenueProfit> catData = pd.findCategoryRevenueProfit(from, to);
        double totalRev = allProducts.stream().mapToDouble(ProductReportRow::getRevenue).sum();
        double totalProfit = allProducts.stream().mapToDouble(ProductReportRow::getProfit).sum();
        double avgMargin = allProducts.isEmpty() ? 0 : allProducts.stream().mapToDouble(ProductReportRow::getMarginPercent).average().orElse(0);
        long lowCount = allProducts.stream().filter(p -> p.getMarginPercent() < 35).count();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__", "Product Report", "Product Performance & Profitability — Last 90 Days", date});
        rows.add(new String[]{"__SECTION__", "Product Summary", "Revenue, profit and margin"});
        rows.add(new String[]{"__KPI__",
            "Total Revenue", CurrencyUtil.formatCurrency(totalRev), "Total Profit", CurrencyUtil.formatCurrency(totalProfit),
            "Avg Margin", String.format("%.1f%%", avgMargin), "Low Margin Items", String.valueOf(lowCount)});
        List<String> rc = new ArrayList<>(); rc.add("__BARCHART__"); rc.add("Category Revenue");
        for (var c : catData) { rc.add(c.category); rc.add(String.valueOf((long) c.revenue)); }
        rows.add(rc.toArray(new String[0]));
        rows.add(new String[]{"__SECTION__", "Product Analysis", "Detailed margin and revenue"});
        rows.add(new String[]{"__TABLEHEADER__", "Product", "Category", "Units Sold", "Revenue", "Profit", "Margin %"});
        for (ProductReportRow p : allProducts)
            rows.add(new String[]{p.getName(), p.getCategory(), String.valueOf(p.getUnitsSold()),
                CurrencyUtil.formatCurrency(p.getRevenue()), CurrencyUtil.formatCurrency(p.getProfit()),
                String.format("%.1f%%", p.getMarginPercent())});
        return rows;
    }

    public void exportFullMergedReportPdf() {
        if (!SessionManager.isAdmin()) return;
        Window window = getWindow();
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception { return buildMergedReportRows(); }
        };
        finishMergedExport(task, "RAEZ Finance — Full Report", "full_report", window);
    }

    private List<String[]> buildMergedReportRows() throws Exception {
        LocalDate to = LocalDate.now(), from = to.minusDays(30);
        String date = to.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        DashboardService ds = new DashboardService();
        CustomerDao cDao = new CustomerDao(); OrderDao oDao = new OrderDao();
        ProductDao pDao = new ProductDao(); RevenueVatDao vDao = new RevenueVatDao();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"__COVER__", "RAEZ Finance — Full Report",
            "Dashboard · Orders · Customers · Products · Revenue & VAT", date});
        rows.add(new String[]{"__SECTION__", "Dashboard Overview", "Key financial indicators — last 30 days"});
        double totalSales = ds.getTotalSales(from, to, null);
        double netProfit = ds.getTotalProfit(from, to, null);
        double outstanding = ds.getOutstandingPayments(from, to, null);
        double vat = ds.getTotalVatCollected(from, to, null);
        int orders = ds.getTotalOrders(from, to, null);
        int customers = ds.getTotalCustomers();
        double aov = ds.getAverageOrderValue(from, to, null);
        String popular = ds.getMostPopularProductName(from, to, null);
        rows.add(new String[]{"__KPI__",
            "Total Revenue", CurrencyUtil.formatCurrency(totalSales),
            "Net Profit", CurrencyUtil.formatCurrency(netProfit),
            "Outstanding", CurrencyUtil.formatCurrency(outstanding),
            "VAT Collected", CurrencyUtil.formatCurrency(vat)});
        rows.add(new String[]{"__KPI__",
            "Total Orders", String.valueOf(orders), "Customers", String.valueOf(customers),
            "Avg Order Value", CurrencyUtil.formatCurrency(aov), "Top Product", popular != null ? popular : "—"});
        List<ProductDao.CategoryRevenueProfit> catData = pDao.findCategoryRevenueProfit(from, to);
        List<String> cc = new ArrayList<>(); cc.add("__BARCHART__"); cc.add("Revenue by Category");
        for (var c : catData) { cc.add(c.category); cc.add(String.valueOf((long) c.revenue)); }
        rows.add(cc.toArray(new String[0]));
        rows.add(new String[]{"__PAGEBREAK__"});
        rows.add(new String[]{"__SECTION__", "Order Report", "Orders in the last 30 days"});
        List<OrderReportRow> allOrders = oDao.findReportRows(from, to, "All Status", null, null, 100, 0);
        Map<String, Long> sm = allOrders.stream()
            .collect(Collectors.groupingBy(r -> r.getStatus() != null ? r.getStatus() : "Unknown", Collectors.counting()));
        List<String> sc = new ArrayList<>(); sc.add("__BARCHART__"); sc.add("Orders by Status");
        sm.forEach((s, c) -> { sc.add(s); sc.add(String.valueOf(c)); });
        rows.add(sc.toArray(new String[0]));
        rows.add(new String[]{"__TABLEHEADER__", "Order ID", "Customer", "Product", "Amount", "Date", "Status"});
        for (OrderReportRow r : allOrders)
            rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(),
                CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
        rows.add(new String[]{"__PAGEBREAK__"});
        rows.add(new String[]{"__SECTION__", "Customer Report", "Customer overview and top buyers"});
        int total = cDao.getTotalCustomerCount(); int cos = cDao.getCompanyCustomerCount();
        rows.add(new String[]{"__KPI__",
            "Total Customers", String.valueOf(total), "Companies", String.valueOf(cos),
            "Individuals", String.valueOf(total - cos),
            "Avg Spend", CurrencyUtil.formatCurrency(total > 0 ? cDao.getTotalRevenue() / total : 0)});
        rows.add(new String[]{"__BARCHART__", "Customer Type Split", "Companies", String.valueOf(cos), "Individuals", String.valueOf(total - cos)});
        List<TopBuyerRow> topBuyers = cDao.findTopBuyers(15);
        rows.add(new String[]{"__TABLEHEADER__", "Rank", "Customer", "Type", "Total Spent", "Orders", "Last Purchase"});
        for (TopBuyerRow r : topBuyers)
            rows.add(new String[]{String.valueOf(r.getRank()), r.getName(), r.getType(),
                CurrencyUtil.formatCurrency(r.getTotalSpent()), String.valueOf(r.getTotalOrders()),
                r.getLastPurchase() != null ? r.getLastPurchase() : "—"});
        rows.add(new String[]{"__PAGEBREAK__"});
        List<ProductReportRow> allProd = pDao.findReportRows(from, to, "All Categories", null, 100, 0);
        double tR = allProd.stream().mapToDouble(ProductReportRow::getRevenue).sum();
        double tP = allProd.stream().mapToDouble(ProductReportRow::getProfit).sum();
        double tM = allProd.isEmpty() ? 0 : allProd.stream().mapToDouble(ProductReportRow::getMarginPercent).average().orElse(0);
        rows.add(new String[]{"__SECTION__", "Product Profitability", "Margin and revenue analysis"});
        rows.add(new String[]{"__KPI__",
            "Revenue", CurrencyUtil.formatCurrency(tR), "Profit", CurrencyUtil.formatCurrency(tP),
            "Avg Margin", String.format("%.1f%%", tM), "Products", String.valueOf(allProd.size())});
        List<String> pc = new ArrayList<>(); pc.add("__BARCHART__"); pc.add("Category Profit");
        for (var c : catData) { pc.add(c.category); pc.add(String.valueOf((long) c.profit)); }
        rows.add(pc.toArray(new String[0]));
        rows.add(new String[]{"__TABLEHEADER__", "Product", "Category", "Revenue", "Profit", "Margin %", "Units"});
        for (ProductReportRow p : allProd)
            rows.add(new String[]{p.getName(), p.getCategory(),
                CurrencyUtil.formatCurrency(p.getRevenue()), CurrencyUtil.formatCurrency(p.getProfit()),
                String.format("%.1f%%", p.getMarginPercent()), String.valueOf(p.getUnitsSold())});
        rows.add(new String[]{"__PAGEBREAK__"});
        rows.add(new String[]{"__SECTION__", "Revenue & VAT Summary", "Gross vs net and VAT liabilities"});
        double gross = totalSales, vatAmt = vat, net = gross - vatAmt;
        double cogs = ds.getTotalCogs(from, to, null);
        double margin = net > 0 ? ((net - cogs) / net) * 100 : 0;
        rows.add(new String[]{"__KPI__",
            "Gross Revenue", CurrencyUtil.formatCurrency(gross),
            "VAT Collected", CurrencyUtil.formatCurrency(vatAmt),
            "Net Revenue", CurrencyUtil.formatCurrency(net),
            "Gross Margin", String.format("%.1f%%", margin)});
        List<String> vc = new ArrayList<>(); vc.add("__BARCHART__"); vc.add("Gross Revenue by Category");
        for (var r : vDao.findCategoryVatRows(from, to)) { vc.add(r.category()); vc.add(String.valueOf((long) r.gross())); }
        rows.add(vc.toArray(new String[0]));
        rows.add(new String[]{"__TABLEHEADER__", "Category", "Orders", "Gross", "VAT", "Net", "Margin %"});
        for (var r : vDao.findCategoryVatRows(from, to)) {
            double rNet = r.gross() - r.vat();
            double rMar = rNet > 0 ? ((rNet - r.cogs()) / rNet) * 100 : 0;
            rows.add(new String[]{r.category(), String.valueOf(r.orders()),
                CurrencyUtil.formatCurrency(r.gross()), CurrencyUtil.formatCurrency(r.vat()),
                CurrencyUtil.formatCurrency(rNet), String.format("%.1f%%", rMar)});
        }
        return rows;
    }

    public void exportProfileReportPdf(String reportKey) {
        Window window = getWindow(); if (window == null) return;
        LocalDate to = LocalDate.now(), from = to.minusDays(30);
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                return buildProfileExportRows(reportKey, from, to);
            }
        };
        String title = switch (reportKey) {
            case "customer_insights"    -> "Customer Insights";
            case "product_profitability"-> "Product Profitability";
            case "revenue_vat"          -> "Revenue & VAT Summary";
            case "inventory"            -> "Inventory & Suppliers";
            default -> "Report";
        };
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue();
            if (data == null || data.isEmpty()) { showToast("warning", "No data to export."); return; }
            File file = pickFile(window, title, reportKey.replace('_', '-'), "pdf");
            if (file == null) return;
            try { ExportService.exportMergedReport(title, data, file); showToast("success", "Exported: " + file.getName()); }
            catch (Exception ex) { showToast("error", "Export failed: " + ex.getMessage()); }
        });
        task.setOnFailed(ev -> showToast("error", "Export failed."));
        new Thread(task, "profile-export").start();
    }

    private List<String[]> buildProfileExportRows(String key, LocalDate from, LocalDate to) throws Exception {
        return switch (key) {
            case "customer_insights"    -> buildRichCustomerReportRows(from, to);
            case "product_profitability"-> buildRichProductReportRows(from, to);
            default -> new ArrayList<>();
        };
    }

    public void exportDashboardReport(String format) {
        Window window = getWindow(); if (window == null) return;
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                DashboardService ds = new DashboardService();
                LocalDate to = LocalDate.now(), from = to.minusDays(30);
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Metric", "Value"});
                rows.add(new String[]{"Total Sales",      CurrencyUtil.formatCurrency(ds.getTotalSales(from, to, null))});
                rows.add(new String[]{"Total Profit",     CurrencyUtil.formatCurrency(ds.getTotalProfit(from, to, null))});
                rows.add(new String[]{"Outstanding",      CurrencyUtil.formatCurrency(ds.getOutstandingPayments(from, to, null))});
                rows.add(new String[]{"Total Customers",  String.valueOf(ds.getTotalCustomers())});
                rows.add(new String[]{"Total Orders",     String.valueOf(ds.getTotalOrders(from, to, null))});
                rows.add(new String[]{"Avg Order Value",  CurrencyUtil.formatCurrency(ds.getAverageOrderValue(from, to, null))});
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
        Window window = getWindow(); if (window == null) return;
        LocalDate to = LocalDate.now(), from = to.minusDays(30);
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() throws Exception {
                List<String[]> rows = new ArrayList<>();
                switch (type) {
                    case "orders" -> {
                        List<OrderReportRow> list = new OrderDao().findReportRows(from, to, "All Status", null, null, 0, 0);
                        rows.add(new String[]{"Order ID","Customer","Product","Amount","Date","Status"});
                        for (OrderReportRow r : list)
                            rows.add(new String[]{r.getOrderId(), r.getCustomer(), r.getProduct(),
                                CurrencyUtil.formatCurrency(r.getAmount()), r.getDate(), r.getStatus()});
                    }
                    case "products" -> {
                        List<ProductReportRow> list = new ProductDao().findReportRows(from, to, "All Categories", null, 0, 0);
                        rows.add(new String[]{"Product ID","Name","Category","Cost","Sale Price","Profit","Units","Revenue"});
                        for (ProductReportRow r : list)
                            rows.add(new String[]{r.getProductId(), r.getName(), r.getCategory(),
                                CurrencyUtil.formatCurrency(r.getCost()), CurrencyUtil.formatCurrency(r.getSalePrice()),
                                CurrencyUtil.formatCurrency(r.getProfit()), String.valueOf(r.getUnitsSold()),
                                CurrencyUtil.formatCurrency(r.getRevenue())});
                    }
                    default -> {
                        List<CustomerReportRow> list = new CustomerDao().findReportRows(null, null, "All", "All", null, null, 0, 0);
                        rows.add(new String[]{"Customer ID","Name","Type","Country","Orders","Spent","Avg Order","Last Purchase"});
                        for (CustomerReportRow r : list)
                            rows.add(new String[]{r.getCustomerId(), r.getName(), r.getType(), r.getCountry(),
                                String.valueOf(r.getTotalOrders()), CurrencyUtil.formatCurrency(r.getTotalSpent()),
                                CurrencyUtil.formatCurrency(r.getAvgOrderValue()), r.getLastPurchase()});
                    }
                }
                return rows;
            }
        };
        finishExport(task, title, fileName, format, window);
    }

    private void finishMergedExport(Task<List<String[]>> task, String title, String fileName, Window window) {
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue();
            if (data == null || data.isEmpty()) { showToast("warning", "No data to export."); return; }
            File file = pickFile(window, title, fileName, "pdf");
            if (file == null) return;
            try { ExportService.exportMergedReport(title, data, file); showToast("success", "Exported: " + file.getName()); }
            catch (Exception ex) { showToast("error", "Export failed: " + ex.getMessage()); }
        });
        task.setOnFailed(ev -> { if (task.getException() != null) task.getException().printStackTrace(); showToast("error", "Export failed."); });
        new Thread(task, "merged-export").start();
    }

    private void finishExport(Task<List<String[]>> task, String title, String fileName, String format, Window window) {
        task.setOnSucceeded(e -> {
            List<String[]> data = task.getValue(); if (data == null || data.isEmpty()) return;
            File file = pickFile(window, title, fileName, format); if (file == null) return;
            try {
                if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF(title, data, file);
                else ExportService.exportRowsToCSV(data, file);
                showToast("success", "Exported: " + file.getName());
            } catch (Exception ex) { showToast("error", "Export failed: " + ex.getMessage()); }
        });
        task.setOnFailed(ev -> showToast("error", "Export failed."));
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