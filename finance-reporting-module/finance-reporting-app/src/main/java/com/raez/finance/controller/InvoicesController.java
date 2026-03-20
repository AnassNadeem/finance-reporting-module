package com.raez.finance.controller;

import com.raez.finance.dao.InvoiceDao;
import com.raez.finance.dao.InvoiceDaoInterface;
import com.raez.finance.dao.InvoiceDao.InvoiceRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InvoicesController {

    // ── Injected from parent ─────────────────────────────────────────────
    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController mlc) {
        this.mainLayoutController = mlc;
    }

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private TableView<InvoiceRow>             invoiceTable;
    @FXML private TableColumn<InvoiceRow, String>   colInvoiceNumber;
    @FXML private TableColumn<InvoiceRow, Number>   colOrderId;
    @FXML private TableColumn<InvoiceRow, String>   colCustomer;
    @FXML private TableColumn<InvoiceRow, String>   colAmount;
    @FXML private TableColumn<InvoiceRow, String>   colStatus;
    @FXML private TableColumn<InvoiceRow, String>   colDueDate;
    @FXML private TableColumn<InvoiceRow, String>   colPaidAt;

    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private VBox             customDateBox;
    @FXML private DatePicker       dpStart;
    @FXML private DatePicker       dpEnd;
    @FXML private TextField        searchField;
    @FXML private MenuButton       exportMenuButton;

    // ── Services ─────────────────────────────────────────────────────────
    private final InvoiceDaoInterface                 invoiceDao = new InvoiceDao();
    private final ObservableList<InvoiceRow> items      = FXCollections.observableArrayList();
    private final ExecutorService            executor   = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoices-worker");
        t.setDaemon(true);
        return t;
    });
    private PauseTransition searchDebounce;

    // ══════════════════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // Export button: admin only
        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        bindColumns();
        invoiceTable.setItems(items);
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Empty state
        VBox ph = new VBox(8);
        ph.setAlignment(Pos.CENTER);
        Label phTitle = new Label("No invoices found");
        phTitle.getStyleClass().add("table-placeholder-title");
        Label phSub = new Label("Try adjusting your filters");
        phSub.getStyleClass().add("table-placeholder-subtitle");
        ph.getChildren().addAll(phTitle, phSub);
        invoiceTable.setPlaceholder(ph);

        // Status filter
        if (statusCombo != null) {
            statusCombo.setItems(FXCollections.observableArrayList("All", "Paid", "Unpaid", "Overdue"));
            statusCombo.setValue("All");
            statusCombo.valueProperty().addListener((obs, o, n) -> loadInvoices());
        }

        // Date range filter
        if (dateRangeCombo != null) {
            dateRangeCombo.setItems(FXCollections.observableArrayList(
                "Last 7 Days", "Last 30 Days", "Last 90 Days", "Custom"));
            dateRangeCombo.setValue("Last 90 Days");
            dateRangeCombo.valueProperty().addListener((obs, o, n) -> {
                boolean custom = "Custom".equals(n);
                if (customDateBox != null) {
                    customDateBox.setVisible(custom);
                    customDateBox.setManaged(custom);
                }
                loadInvoices();
            });
        }

        if (dpStart != null) dpStart.valueProperty().addListener((obs, o, n) -> loadInvoices());
        if (dpEnd   != null) dpEnd.valueProperty().addListener((obs, o, n)   -> loadInvoices());

        // Debounced search
        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(e -> loadInvoices());
        if (searchField != null)
            searchField.textProperty().addListener((obs, o, n) -> searchDebounce.playFromStart());

        loadInvoices();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindColumns() {
        colInvoiceNumber.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderID"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));

        // Amount: computed from row value
        colAmount.setCellValueFactory(cell -> {
            double v = cell.getValue().getTotalAmount();
            return javafx.beans.binding.Bindings.createStringBinding(
                () -> CurrencyUtil.formatCurrency(v));
        });

        // Status badge
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                String normalized = item.trim().toUpperCase();
                if ("PAID".equals(normalized)) badge.getStyleClass().add("status-badge-paid");
                else if ("OVERDUE".equals(normalized) || "FAILED".equals(normalized)) badge.getStyleClass().add("status-badge-danger");
                else badge.getStyleClass().add("status-badge-warning");
                HBox w = new HBox(badge);
                w.setAlignment(Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });

        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colPaidAt.setCellValueFactory(new PropertyValueFactory<>("paidAt"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadInvoices() {
        LocalDate[] range       = resolveRange();
        String      status      = statusCombo  != null ? statusCombo.getValue()  : "All";
        String      mappedStatus = mapStatus(status);
        String      search      = searchField  != null ? searchField.getText()   : null;

        Task<List<InvoiceRow>> task = new Task<>() {
            @Override protected List<InvoiceRow> call() throws Exception {
                return invoiceDao.findInvoices(range[0], range[1], mappedStatus, search, 200, 0);
            }
        };
        task.setOnSucceeded(e ->
            items.setAll(task.getValue() != null ? task.getValue() : List.of()));
        task.setOnFailed(e -> {
            items.clear();
            toast("error", "Failed to load invoices.");
        });
        executor.execute(task);
    }

    private String mapStatus(String ui) {
        if (ui == null || "All".equals(ui)) return "All";
        return switch (ui) {
            case "Paid"    -> "PAID";
            case "Unpaid"  -> "PENDING";
            case "Overdue" -> "OVERDUE";
            default        -> "All";
        };
    }

    private LocalDate[] resolveRange() {
        LocalDate to   = LocalDate.now();
        LocalDate from;
        String val = dateRangeCombo != null ? dateRangeCombo.getValue() : "Last 90 Days";
        if (val == null) val = "Last 90 Days";
        from = switch (val) {
            case "Last 7 Days"  -> to.minusDays(7);
            case "Last 30 Days" -> to.minusDays(30);
            case "Custom" -> {
                LocalDate s = dpStart != null ? dpStart.getValue() : null;
                LocalDate e = dpEnd   != null ? dpEnd.getValue()   : null;
                if (e != null) to = e;
                yield s != null ? s : to.minusDays(90);
            }
            default -> to.minusDays(90);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════

    @FXML private void handleExportCsv() { if (SessionManager.isAdmin()) doExport("csv"); }
    @FXML private void handleExportPdf() { if (SessionManager.isAdmin()) doExport("pdf"); }

    private void doExport(String format) {
        if (!SessionManager.isAdmin()) return;
        javafx.stage.Window window =
            invoiceTable.getScene() != null ? invoiceTable.getScene().getWindow() : null;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Invoices");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            format.toUpperCase() + " Files", "*." + format));
        fc.setInitialFileName("invoices_export." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;

        try {
            List<String[]> data = new ArrayList<>();
            data.add(new String[]{"Invoice #","Order ID","Customer","Amount","Status","Due Date","Paid At"});
            for (InvoiceRow r : items) {
                data.add(new String[]{
                    r.getInvoiceNumber(),
                    String.valueOf(r.getOrderID()),
                    r.getCustomerName(),
                    CurrencyUtil.formatCurrency(r.getTotalAmount()),
                    r.getStatus(),
                    r.getDueDate()  != null ? r.getDueDate()  : "",
                    r.getPaidAt()   != null ? r.getPaidAt()   : ""
                });
            }
            if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF("Invoices", data, file);
            else                                 ExportService.exportRowsToCSV(data, file);
            toast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TOAST HELPER
    // ══════════════════════════════════════════════════════════════════════

    private void toast(String type, String message) {
        if (mainLayoutController != null) {
            mainLayoutController.showToast(type, message);
        } else {
            new Alert(
                type.equals("error") ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION,
                message).showAndWait();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHUTDOWN
    // ══════════════════════════════════════════════════════════════════════

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}