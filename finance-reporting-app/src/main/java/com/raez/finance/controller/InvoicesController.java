package com.raez.finance.controller;

import com.raez.finance.dao.InvoiceDao;
import com.raez.finance.dao.InvoiceDaoInterface;
import com.raez.finance.dao.InvoiceDao.InvoiceRow;
import com.raez.finance.dao.InvoiceDao.InvoiceKpiRow;
import com.raez.finance.dao.InvoiceDao.OrderWithoutInvoiceRow;
import com.raez.finance.service.ExportService;
import com.raez.finance.service.SessionManager;
import com.raez.finance.util.CurrencyUtil;
import com.raez.finance.util.UiAutoRefreshable;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InvoicesController implements UiAutoRefreshable {

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
    @FXML private TableColumn<InvoiceRow, Void>     colActions;

    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> dateRangeCombo;
    @FXML private TextField        searchField;
    @FXML private MenuButton       exportMenuButton;
    @FXML private Button           btnGenerateInvoice;

    @FXML private Label              lblKpiInvoiced;
    @FXML private Label              lblKpiPaid;
    @FXML private Label              lblKpiOutstanding;
    @FXML private Label              lblKpiOverdue;
    @FXML private ProgressIndicator  loadingIndicator;

    // ── Services ─────────────────────────────────────────────────────────
    private final InvoiceDaoInterface            invoiceDao     = new InvoiceDao();
    private final ObservableList<InvoiceRow>     items          = FXCollections.observableArrayList();
    private final ExecutorService                executor       = Executors.newSingleThreadExecutor(r -> {
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
        boolean admin = SessionManager.isAdmin();

        if (exportMenuButton != null && !admin) {
            exportMenuButton.setVisible(false);
            exportMenuButton.setManaged(false);
        }
        if (btnGenerateInvoice != null) {
            btnGenerateInvoice.setVisible(admin);
            btnGenerateInvoice.setManaged(admin);
        }

        bindColumns();
        invoiceTable.setItems(items);
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        VBox ph = new VBox(8);
        ph.setAlignment(Pos.CENTER);
        Label phTitle = new Label("No invoices found");
        phTitle.getStyleClass().add("table-placeholder-title");
        Label phSub = new Label("Try adjusting your filters");
        phSub.getStyleClass().add("table-placeholder-subtitle");
        ph.getChildren().addAll(phTitle, phSub);
        invoiceTable.setPlaceholder(ph);

        if (statusCombo != null) {
            statusCombo.setItems(FXCollections.observableArrayList("All", "Paid", "Unpaid", "Overdue"));
            statusCombo.setValue("All");
            statusCombo.valueProperty().addListener((obs, o, n) -> loadInvoices());
        }
        if (dateRangeCombo != null) {
            dateRangeCombo.setItems(FXCollections.observableArrayList(
                "Last 7 Days", "Last 30 Days", "Last 90 Days"));
            dateRangeCombo.setValue("Last 90 Days");
            dateRangeCombo.valueProperty().addListener((obs, o, n) -> loadInvoices());
        }

        searchDebounce = new PauseTransition(Duration.millis(300));
        searchDebounce.setOnFinished(e -> loadInvoices());
        if (searchField != null)
            searchField.textProperty().addListener((obs, o, n) -> searchDebounce.playFromStart());

        if (!admin) {
            invoiceTable.getColumns().remove(colActions);
        } else {
            bindActionsColumn();
        }

        loadInvoices();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COLUMN BINDING
    // ══════════════════════════════════════════════════════════════════════

    private void bindColumns() {
        colInvoiceNumber.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderID"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));

        colAmount.setCellValueFactory(cell -> {
            double v = cell.getValue().getTotalAmount();
            return javafx.beans.binding.Bindings.createStringBinding(
                () -> CurrencyUtil.formatCurrency(v));
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                Label badge = new Label(item);
                String normalized = item.trim().toUpperCase();
                if ("PAID".equals(normalized))
                    badge.getStyleClass().add("status-badge-paid");
                else if ("OVERDUE".equals(normalized) || "FAILED".equals(normalized))
                    badge.getStyleClass().add("status-badge-danger");
                else
                    badge.getStyleClass().add("status-badge-warning");
                HBox w = new HBox(badge);
                w.setAlignment(Pos.CENTER_LEFT);
                setGraphic(w);
            }
        });

        colDueDate.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        colPaidAt.setCellValueFactory(new PropertyValueFactory<>("paidAt"));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACTIONS COLUMN  — SVG icon buttons: view · edit · send reminder
    // ══════════════════════════════════════════════════════════════════════

    private void bindActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {

            // ── Eye — view invoice detail ──────────────────────────────
            private final Button btnView = iconBtn(
                "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z " +
                "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z",
                "#4B5563", "View invoice");

            // ── Pencil — edit invoice ──────────────────────────────────
            private final Button btnEdit = iconBtn(
                "M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 " +
                "M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z",
                "#4B5563", "Edit invoice");

            // ── Mail — send payment reminder ──────────────────────────
            private final Button btnReminder = iconBtn(
                "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z " +
                "M22 6l-10 7L2 6",
                "#4B5563", "Send payment reminder");

            // ── Check — mark paid ─────────────────────────────────────
            private final Button btnPaid = iconBtn(
                "M20 6L9 17l-5-5",
                "#10B981", "Mark as paid");

            private final HBox box = new HBox(4, btnView, btnEdit, btnReminder, btnPaid);
            { box.setAlignment(Pos.CENTER_LEFT); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) return;

                InvoiceRow r = getTableRow().getItem();
                boolean isPaid = "PAID".equalsIgnoreCase(
                    r.getStatus() != null ? r.getStatus().trim() : "");

                btnView.setOnAction(e -> showViewDialog(r));
                btnEdit.setOnAction(e -> openEditDialog(r));

                // Reminder only makes sense for non-paid invoices
                btnReminder.setDisable(isPaid);
                btnReminder.setOpacity(isPaid ? 0.35 : 1.0);
                btnReminder.setOnAction(e -> sendReminder(r));

                btnPaid.setVisible(!isPaid);
                btnPaid.setManaged(!isPaid);
                btnPaid.setOnAction(e -> handleMarkPaid(r));

                setGraphic(box);
            }
        });
    }

    private void handleMarkPaid(InvoiceRow r) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Mark paid");
        confirm.setHeaderText("Mark invoice " + r.getInvoiceNumber() + " as paid?");
        confirm.setContentText("The invoice status will be set to PAID with today’s date.");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override protected Void call() throws Exception {
                    invoiceDao.markInvoicePaid(r.getInvoiceID());
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                toast("success", "Invoice marked as paid.");
                loadInvoices();
            });
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                toast("error", ex != null ? ex.getMessage() : "Could not update invoice.");
            });
            executor.execute(task);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VIEW DIALOG  — read-only invoice detail
    // ══════════════════════════════════════════════════════════════════════

    private void showViewDialog(InvoiceRow r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Invoice " + r.getInvoiceNumber());
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(440);

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 24, 8, 24));

        // Status badge at the top
        Label statusBadge = new Label(r.getStatus() != null ? r.getStatus() : "—");
        String normalized = r.getStatus() != null ? r.getStatus().trim().toUpperCase() : "";
        if ("PAID".equals(normalized))
            statusBadge.getStyleClass().add("status-badge-paid");
        else if ("OVERDUE".equals(normalized) || "FAILED".equals(normalized))
            statusBadge.getStyleClass().add("status-badge-danger");
        else
            statusBadge.getStyleClass().add("status-badge-warning");

        HBox statusRow = new HBox(statusBadge);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(new Label("Status"), 0, 0);
        grid.add(statusRow, 1, 0);

        String[][] fields = {
            {"Invoice #",   r.getInvoiceNumber()},
            {"Order ID",    String.valueOf(r.getOrderID())},
            {"Customer",    r.getCustomerName()},
            {"Amount",      CurrencyUtil.formatCurrency(r.getTotalAmount())},
            {"Issued",      nvl(r.getIssuedAt())},
            {"Due Date",    nvl(r.getDueDate())},
            {"Paid At",     nvl(r.getPaidAt())},
            {"Notes",       nvl(r.getNotesSafe())}
        };

        for (int i = 0; i < fields.length; i++) {
            Label key = new Label(fields[i][0]);
            key.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #6B7280; -fx-min-width: 90px;");
            Label val = new Label(fields[i][1]);
            val.setStyle("-fx-font-size: 13px; -fx-text-fill: #111827;");
            val.setWrapText(true);
            val.setMaxWidth(280);
            grid.add(key, 0, i + 1);
            grid.add(val, 1, i + 1);
        }

        // Export PDF button in dialog footer
        ButtonType exportType = new ButtonType("Export PDF", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().add(0, exportType);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(btn -> {
            if (btn == exportType) exportSingleInvoice(r);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEND REMINDER  — payment reminder confirmation dialog
    // ══════════════════════════════════════════════════════════════════════

    private void sendReminder(InvoiceRow r) {
        boolean overdue = "OVERDUE".equalsIgnoreCase(
            r.getStatus() != null ? r.getStatus().trim() : "");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Send Payment Reminder");
        confirm.setHeaderText("Send reminder for invoice " + r.getInvoiceNumber() + "?");

        String dueInfo = r.getDueDate() != null && !r.getDueDate().isBlank()
            ? "\nDue date:  " + r.getDueDate() : "";
        confirm.setContentText(
            "Customer:  " + r.getCustomerName() +
            "\nAmount:    " + CurrencyUtil.formatCurrency(r.getTotalAmount()) +
            dueInfo +
            (overdue ? "\n\n⚠  This invoice is OVERDUE." : ""));

        ButtonType btnSend = new ButtonType("Send Reminder", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(btnSend, ButtonType.CANCEL);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == btnSend) {
                // Persist reminder-sent flag async (extend InvoiceDao.markReminderSent
                // when email integration is added in Phase 4)
                Task<Void> task = new Task<>() {
                    @Override protected Void call() throws Exception {
                        // Phase 4: call email service here
                        // For now, update a notes field or log
                        return null;
                    }
                };
                task.setOnSucceeded(e ->
                    toast("success", "Reminder queued for " + r.getCustomerName() +
                        " — invoice " + r.getInvoiceNumber()));
                task.setOnFailed(e ->
                    toast("error", "Could not send reminder."));
                executor.execute(task);
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EDIT DIALOG  — update status / due date / notes
    // ══════════════════════════════════════════════════════════════════════

    private void openEditDialog(InvoiceRow r) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Update invoice " + r.getInvoiceNumber());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> st = new ComboBox<>(
            FXCollections.observableArrayList("PENDING", "PAID", "OVERDUE", "PARTIAL", "CANCELLED"));
        st.setValue(r.getStatus() != null ? r.getStatus().toUpperCase() : "PENDING");
        st.setPrefWidth(200);

        DatePicker dpDue = new DatePicker();
        try {
            if (r.getDueDate() != null && !r.getDueDate().isBlank())
                dpDue.setValue(LocalDate.parse(
                    r.getDueDate().trim().substring(0, Math.min(10, r.getDueDate().length()))));
        } catch (Exception ignored) { }

        TextArea notes = new TextArea(r.getNotesSafe());
        notes.setPromptText("Optional notes");
        notes.setPrefRowCount(3);
        notes.setPrefWidth(340);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.add(new Label("Status"),   0, 0); grid.add(st,     1, 0);
        grid.add(new Label("Due date"), 0, 1); grid.add(dpDue,  1, 1);
        grid.add(new Label("Notes"),    0, 2); grid.add(notes,  1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            Task<Void> upd = new Task<>() {
                @Override protected Void call() throws Exception {
                    invoiceDao.updateInvoice(
                        r.getInvoiceID(), st.getValue(), dpDue.getValue(), notes.getText());
                    return null;
                }
            };
            upd.setOnSucceeded(ev -> { toast("success", "Invoice updated."); loadInvoices(); });
            upd.setOnFailed(ev   -> toast("error", "Update failed."));
            executor.execute(upd);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GENERATE INVOICE DIALOG
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleGenerateInvoice() {
        if (!SessionManager.isAdmin()) return;
        Task<List<OrderWithoutInvoiceRow>> task = new Task<>() {
            @Override protected List<OrderWithoutInvoiceRow> call() throws Exception {
                return invoiceDao.findOrdersWithoutInvoice(80);
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> showGenerateDialog(task.getValue())));
        task.setOnFailed(ev  -> toast("error", "Could not load orders."));
        executor.execute(task);
    }

    private void showGenerateDialog(List<OrderWithoutInvoiceRow> orders) {
        if (orders == null || orders.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION,
                "No orders are available without an invoice.").showAndWait();
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Generate invoice");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<OrderWithoutInvoiceRow> cmb =
            new ComboBox<>(FXCollections.observableArrayList(orders));
        cmb.setConverter(new StringConverter<>() {
            @Override public String toString(OrderWithoutInvoiceRow o) {
                if (o == null) return "";
                return "#" + o.orderID() + " — " + o.customerName() +
                    " (" + CurrencyUtil.formatCurrency(o.totalAmount()) + ")";
            }
            @Override public OrderWithoutInvoiceRow fromString(String s) { return null; }
        });
        cmb.setPrefWidth(420);
        cmb.getSelectionModel().selectFirst();

        DatePicker dpDue = new DatePicker(LocalDate.now().plusDays(30));
        TextArea   taNotes = new TextArea();
        taNotes.setPromptText("Optional notes");
        taNotes.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.add(new Label("Order"),    0, 0); grid.add(cmb,     1, 0);
        grid.add(new Label("Due date"), 0, 1); grid.add(dpDue,   1, 1);
        grid.add(new Label("Notes"),    0, 2); grid.add(taNotes, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            OrderWithoutInvoiceRow sel = cmb.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Task<Integer> ins = new Task<>() {
                @Override protected Integer call() throws Exception {
                    return invoiceDao.insertInvoiceForOrder(
                        sel.orderID(), dpDue.getValue(), taNotes.getText());
                }
            };
            ins.setOnSucceeded(ev -> { toast("success", "Invoice created."); loadInvoices(); });
            ins.setOnFailed(ev   -> toast("error", ins.getException() != null
                ? ins.getException().getMessage() : "Could not create invoice."));
            executor.execute(ins);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DATA LOADING
    // ══════════════════════════════════════════════════════════════════════

    private void loadInvoices() {
        LocalDate[] range        = resolveRange();
        String      status       = statusCombo  != null ? statusCombo.getValue()  : "All";
        String      mappedStatus = mapStatus(status);
        String      search       = searchField  != null ? searchField.getText()   : null;

        setLoading(true);

        Task<List<InvoiceRow>> task = new Task<>() {
            @Override protected List<InvoiceRow> call() throws Exception {
                return invoiceDao.findInvoices(range[0], range[1], mappedStatus, search, 500, 0);
            }
        };
        task.setOnSucceeded(e -> {
            items.setAll(task.getValue() != null ? task.getValue() : List.of());
            setLoading(false);
        });
        task.setOnFailed(ev -> {
            items.clear();
            setLoading(false);
            toast("error", "Failed to load invoices.");
        });
        executor.execute(task);

        Task<InvoiceKpiRow> kpiTask = new Task<>() {
            @Override protected InvoiceKpiRow call() throws Exception {
                return invoiceDao.aggregateForRange(range[0], range[1], mappedStatus, search);
            }
        };
        kpiTask.setOnSucceeded(e -> {
            InvoiceKpiRow k = kpiTask.getValue();
            if (k == null) return;
            if (lblKpiInvoiced    != null) lblKpiInvoiced.setText(CurrencyUtil.formatCurrency(k.totalInvoiced()));
            if (lblKpiPaid        != null) lblKpiPaid.setText(CurrencyUtil.formatCurrency(k.totalPaid()));
            if (lblKpiOutstanding != null) lblKpiOutstanding.setText(CurrencyUtil.formatCurrency(k.outstanding()));
            if (lblKpiOverdue     != null) lblKpiOverdue.setText(String.valueOf(k.overdueCount()));
        });
        executor.execute(kpiTask);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EXPORT
    // ══════════════════════════════════════════════════════════════════════

    private void exportSingleInvoice(InvoiceRow r) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export invoice");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName(sanitizeFileName(r.getInvoiceNumber()) + ".pdf");
        File file = invoiceTable.getScene() != null
            ? fc.showSaveDialog(invoiceTable.getScene().getWindow()) : null;
        if (file == null) return;
        try {
            ExportService.exportRowsToPDF(
                "Invoice " + r.getInvoiceNumber(), buildInvoiceDetailRows(r), file);
            toast("success", "Exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " + nvl(ex.getMessage()));
        }
    }

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
            List<String[]> data = buildExportData();
            if ("pdf".equalsIgnoreCase(format)) ExportService.exportRowsToPDF("Invoices", data, file);
            else                                 ExportService.exportRowsToCSV(data, file);
            toast("success", format.toUpperCase() + " exported: " + file.getName());
        } catch (Exception ex) {
            toast("error", "Export failed: " + nvl(ex.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Builds an SVG icon button following the same pattern as SettingsController.
     * All styling is inline so the button works without any CSS class dependency.
     */
    private Button iconBtn(String svgContent, String strokeHex, String tooltip) {
        Button btn = new Button();
        btn.setStyle(
            "-fx-background-color: transparent; -fx-cursor: hand;" +
            "-fx-border-color: transparent; -fx-padding: 5;");
        btn.setTooltip(new Tooltip(tooltip));

        SVGPath svg = new SVGPath();
        svg.setContent(svgContent);
        svg.setFill(Color.TRANSPARENT);
        svg.setStroke(Color.web(strokeHex));
        svg.setStrokeWidth(1.7);
        svg.setStrokeLineCap(StrokeLineCap.ROUND);
        svg.setStrokeLineJoin(StrokeLineJoin.ROUND);
        svg.setScaleX(0.85);
        svg.setScaleY(0.85);
        btn.setGraphic(svg);

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: #F3F4F6; -fx-cursor: hand;" +
            "-fx-border-color: transparent; -fx-padding: 5;" +
            "-fx-background-radius: 6;"));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent; -fx-cursor: hand;" +
            "-fx-border-color: transparent; -fx-padding: 5;"));
        return btn;
    }

    private List<String[]> buildExportData() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Invoice #","Order ID","Customer","Amount","Status","Due Date","Paid At"});
        for (InvoiceRow r : items) {
            rows.add(new String[]{
                r.getInvoiceNumber(),
                String.valueOf(r.getOrderID()),
                r.getCustomerName(),
                CurrencyUtil.formatCurrency(r.getTotalAmount()),
                r.getStatus(),
                nvl(r.getDueDate()),
                nvl(r.getPaidAt())
            });
        }
        return rows;
    }

    private List<String[]> buildInvoiceDetailRows(InvoiceRow r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Field", "Value"});
        rows.add(new String[]{"Invoice number", r.getInvoiceNumber()});
        rows.add(new String[]{"Order ID",       String.valueOf(r.getOrderID())});
        rows.add(new String[]{"Customer",       r.getCustomerName()});
        rows.add(new String[]{"Amount (incl. VAT)", CurrencyUtil.formatCurrency(r.getTotalAmount())});
        rows.add(new String[]{"Status",         r.getStatus()});
        rows.add(new String[]{"Issued",         nvl(r.getIssuedAt())});
        rows.add(new String[]{"Due",            nvl(r.getDueDate())});
        rows.add(new String[]{"Paid at",        nvl(r.getPaidAt())});
        rows.add(new String[]{"Notes",          nvl(r.getNotesSafe())});
        return rows;
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
            default             -> to.minusDays(90);
        };
        if (from.isAfter(to)) from = to;
        return new LocalDate[]{from, to};
    }

    private void setLoading(boolean loading) {
        if (loadingIndicator == null) return;
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
    }

    private static String sanitizeFileName(String s) {
        if (s == null) return "invoice";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String nvl(String s) { return s != null ? s : "—"; }

    private void toast(String type, String message) {
        if (mainLayoutController != null) {
            mainLayoutController.showToast(type, message);
        } else {
            new Alert(
                "error".equals(type) ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION,
                message).showAndWait();
        }
    }

    public void shutdown() {
        executor.shutdown();
        try { if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    @Override
    public void refreshVisibleData() {
        loadInvoices();
    }
}