package com.raez.finance.controller;

import com.raez.finance.dao.AuditLogDao;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuditLogController {

    private final AuditLogDao auditLogDao = new AuditLogDao();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int LIMIT = 200;

    @FXML private TableView<AuditLogDao.CustomerUpdateRow> tblCustomerUpdates;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, Number> colUpdateId;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, Number> colCustomerId;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, String> colField;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, String> colOldVal;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, String> colNewVal;
    @FXML private TableColumn<AuditLogDao.CustomerUpdateRow, String> colUpdateDate;

    @FXML private TableView<AuditLogDao.StockMovementRow> tblStockMovements;
    @FXML private TableColumn<AuditLogDao.StockMovementRow, Number> colMovementId;
    @FXML private TableColumn<AuditLogDao.StockMovementRow, Number> colInventoryId;
    @FXML private TableColumn<AuditLogDao.StockMovementRow, Number> colQty;
    @FXML private TableColumn<AuditLogDao.StockMovementRow, String> colType;
    @FXML private TableColumn<AuditLogDao.StockMovementRow, String> colMovementDate;

    @FXML
    public void initialize() {
        colUpdateId.setCellValueFactory(new PropertyValueFactory<>("updateID"));
        colCustomerId.setCellValueFactory(new PropertyValueFactory<>("customerID"));
        colField.setCellValueFactory(new PropertyValueFactory<>("updatedField"));
        colOldVal.setCellValueFactory(new PropertyValueFactory<>("oldValue"));
        colNewVal.setCellValueFactory(new PropertyValueFactory<>("newValue"));
        colUpdateDate.setCellValueFactory(new PropertyValueFactory<>("updateDate"));

        colMovementId.setCellValueFactory(new PropertyValueFactory<>("movementID"));
        colInventoryId.setCellValueFactory(new PropertyValueFactory<>("inventoryID"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantityChanged"));
        colType.setCellValueFactory(new PropertyValueFactory<>("movementType"));
        colMovementDate.setCellValueFactory(new PropertyValueFactory<>("movementDate"));

        loadCustomerUpdates();
        loadStockMovements();
    }

    private void loadCustomerUpdates() {
        Task<List<AuditLogDao.CustomerUpdateRow>> task = new Task<>() {
            @Override
            protected List<AuditLogDao.CustomerUpdateRow> call() throws Exception {
                return auditLogDao.findCustomerUpdates(LIMIT);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                tblCustomerUpdates.getItems().setAll(task.getValue());
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }

    private void loadStockMovements() {
        Task<List<AuditLogDao.StockMovementRow>> task = new Task<>() {
            @Override
            protected List<AuditLogDao.StockMovementRow> call() throws Exception {
                return auditLogDao.findStockMovements(LIMIT);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() != null) {
                Platform.runLater(() -> tblStockMovements.getItems().setAll(task.getValue()));
            }
        });
        task.exceptionProperty().addListener((o, p, ex) -> { if (ex != null) ex.printStackTrace(); });
        executor.execute(task);
    }
}
