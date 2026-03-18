package com.raez.finance.controller;

import com.raez.finance.dao.InvoiceDao;
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

    @FXML private TableView<InvoiceRow> invoiceTable;
    @FXML private TableColumn<InvoiceRow, String> colInvoiceNumber;
    @FXML private TableColumn<InvoiceRow, Number> colOrderId;
    @FXML private TableColumn<InvoiceRow, String> colCustomer;
    @FXML private TableColumn<InvoiceRow, String> colAmount;
    @FXML private TableColumn<InvoiceRow, String> colStatus;
    @FXML private TableColumn<InvoiceRow, String> colDueDate;
    @FXML private TableColumn<InvoiceRow, String> colPaidAt;

    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private VBox customDateBox;
    @FXML private DatePicker dpStart;
    @FXML private DatePicker dpEnd;
    @FXML private TextField searchField;
    @FXML private MenuButton exportMenuButton;
    @FXML private MenuItem exportCsvItem;
    @FXML private MenuItem exportPdfItem;

    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final ObservableList<InvoiceRow> items = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoices-worker");
        t.setDaemon(true);
        return t;
    });
    private PauseTransition searchDebounce;

    @FXML
    public void initialize() {
        if (exportMenuButton != null && !SessionManager.isAdmin()) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }

        colInvoiceNumber.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderID"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colAmount.setCellValueFactory(cell -> {
            double v = cell.getValue().getTotalAmount();
            return javafx.beans.binding.Bindings.createStringBinding(() -> CurrencyUtil.formatCurrency(v));
        });
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                String s = item.trim().toUpperCase();
                String badgeStyle = switch (s) {
                    case "PAID" -> "badge-paid";
                    case "OVERDUE", "FAILED" -> "badge-overdue";
                    default -> "badge-unpaid";
                };
                badge.getStyleClass().add(badgeStyle);
                HBox w = new HBox(badge);
                w.setAlignment(Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });
        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colPaidAt.setCellValueFactory(new PropertyValueFactory<>("paidAt"));

        invoiceTable.setItems(items);
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        VBox placeholder = new VBox(8);
        placeholder.setAlignment(Pos.CENTER);
        Label phTitle = new Label("No invoices found");
        phTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #374151;");
        Label phSub = new Label("Try adjusting your filters");
        phSub.setStyle("-fx-font-size: 12px; -fx-text-fill: #9CA3AF;");
        placeholder.getChildren().addAll(phTitle, phSub);
        invoiceTable.setPlaceholder(placeholder);

        if (statusCombo != null) {
            statusCombo.setItems(FXCollections.observableArrayList("All", "Paid", "Unpaid", "Overdue"));
            statusCombo.setValue("All");
            statusCombo.valueProperty().addListener((obs, o, n) -> loadInvoices());
        }

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
        if (dpEnd != null) dpEnd.valueProperty().addListener((obs, o, n) -> loadInvoices());

        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(e -> loadInvoices());
        if (searchField != null) {
            searchField.textProperty().addListener((obs, o, n) -> searchDebounce.playFromStart());
        }

        loadInvoices();
    }

    private void loadInvoices() {
        LocalDate[] range = resolveRange();
        String status = statusCombo != null ? statusCombo.getValue() : "All";
        String mappedStatus = mapStatusFilter(status);
        String search = searchField != null ? searchField.getText() : null;

        Task<List<InvoiceRow>> task = new Task<>() {
            @Override
            protected List<InvoiceRow> call() throws Exception {
                return invoiceDao.findInvoices(range[0], range[1], mappedStatus, search, 200, 0);
            }
        };
        task.setOnSucceeded(e -> items.setAll(task.getValue() != null ? task.getValue() : List.of()));
        task.setOnFailed(e -> items.clear());
        executor.execute(task);
    }

    private String mapStatusFilter(String uiFilter) {
        if (uiFilter == null || "All".equals(uiFilter)) return "All";
        return switch (uiFilter) {
            case "Paid" -> "PAID";
            case "Unpaid" -> "PENDING";
            case "Overdue" -> "OVERDUE";
            default -> "All";
        };
    }

    private LocalDate[] resolveRange() {
        LocalDate to = LocalDate.now();
        LocalDate from;
        String val = dateRangeCombo != null ? dateRangeCombo.getValue() : "Last 90 Days";
        if (val == null) val = "Last 90 Days";
        switch (val) {
            case "Last 7 Days" -> from = to.minusDays(7);
            case "Last 30 Days" -> from = to.minusDays(30);
            case "Custom" -> {
                from = dpStart != null && dpStart.getValue() != null ? dpStart.getValue() : to.minusDays(90);
                to = dpEnd != null && dpEnd.getValue() != null ? dpEnd.getValue() : to;
                if (from.isAfter(to)) from = to;
            }
            default -> from = to.minusDays(90);
        }
        return new LocalDate[]{from, to};
    }

    @FXML
    private void handleExportCsv() {
        if (!SessionManager.isAdmin()) return;
        exportWithChooser("csv");
    }

    @FXML
    private void handleExportPdf() {
        if (!SessionManager.isAdmin()) return;
        exportWithChooser("pdf");
    }

    private void exportWithChooser(String format) {
        javafx.stage.Window window = invoiceTable.getScene() != null ? invoiceTable.getScene().getWindow() : null;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Invoices");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.toUpperCase() + " Files", "*." + format));
        fc.setInitialFileName("invoices_export." + format);
        File file = fc.showSaveDialog(window);
        if (file == null) return;

        try {
            List<String[]> data = new ArrayList<>();
            data.add(new String[]{"Invoice #", "Order ID", "Customer", "Amount", "Status", "Due Date", "Paid At"});
            for (InvoiceRow r : items) {
                data.add(new String[]{
                        r.getInvoiceNumber(),
                        String.valueOf(r.getOrderID()),
                        r.getCustomerName(),
                        CurrencyUtil.formatCurrency(r.getTotalAmount()),
                        r.getStatus(),
                        r.getDueDate() != null ? r.getDueDate() : "",
                        r.getPaidAt() != null ? r.getPaidAt() : ""
                });
            }
            if ("pdf".equalsIgnoreCase(format)) {
                ExportService.exportRowsToPDF("Invoices", data, file);
            } else {
                ExportService.exportRowsToCSV(data, file);
            }
        } catch (Exception ignored) {}
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
