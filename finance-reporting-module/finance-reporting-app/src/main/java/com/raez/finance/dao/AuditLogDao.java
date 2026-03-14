package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to CustomerUpdate and StockMovement for the Audit Log view.
 */
public class AuditLogDao {

    public List<CustomerUpdateRow> findCustomerUpdates(int limit) throws Exception {
        String sql = "SELECT updateID, adminID, customerID, updatedField, oldValue, newValue, updateDate FROM CustomerUpdate ORDER BY updateDate DESC LIMIT ?";
        List<CustomerUpdateRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new CustomerUpdateRow(
                        rs.getInt("updateID"),
                        rs.getObject("adminID") != null ? rs.getInt("adminID") : null,
                        rs.getObject("customerID") != null ? rs.getInt("customerID") : null,
                        rs.getString("updatedField"),
                        rs.getString("oldValue"),
                        rs.getString("newValue"),
                        rs.getString("updateDate")
                ));
            }
        }
        return list;
    }

    public List<StockMovementRow> findStockMovements(int limit) throws Exception {
        String sql = "SELECT movementID, inventoryID, fromWarehouseID, toWarehouseID, quantityChanged, movementType, movementDate FROM StockMovement ORDER BY movementDate DESC LIMIT ?";
        List<StockMovementRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new StockMovementRow(
                        rs.getInt("movementID"),
                        rs.getInt("inventoryID"),
                        rs.getObject("fromWarehouseID") != null ? rs.getInt("fromWarehouseID") : null,
                        rs.getObject("toWarehouseID") != null ? rs.getInt("toWarehouseID") : null,
                        rs.getInt("quantityChanged"),
                        rs.getString("movementType"),
                        rs.getString("movementDate")
                ));
            }
        }
        return list;
    }

    public static class CustomerUpdateRow {
        private final int updateID;
        private final Integer adminID;
        private final Integer customerID;
        private final String updatedField;
        private final String oldValue;
        private final String newValue;
        private final String updateDate;

        public CustomerUpdateRow(int updateID, Integer adminID, Integer customerID, String updatedField, String oldValue, String newValue, String updateDate) {
            this.updateID = updateID;
            this.adminID = adminID;
            this.customerID = customerID;
            this.updatedField = updatedField;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.updateDate = updateDate;
        }
        public int getUpdateID() { return updateID; }
        public Integer getAdminID() { return adminID; }
        public Integer getCustomerID() { return customerID; }
        public String getUpdatedField() { return updatedField; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public String getUpdateDate() { return updateDate; }
    }

    public static class StockMovementRow {
        private final int movementID;
        private final int inventoryID;
        private final Integer fromWarehouseID;
        private final Integer toWarehouseID;
        private final int quantityChanged;
        private final String movementType;
        private final String movementDate;

        public StockMovementRow(int movementID, int inventoryID, Integer fromWarehouseID, Integer toWarehouseID, int quantityChanged, String movementType, String movementDate) {
            this.movementID = movementID;
            this.inventoryID = inventoryID;
            this.fromWarehouseID = fromWarehouseID;
            this.toWarehouseID = toWarehouseID;
            this.quantityChanged = quantityChanged;
            this.movementType = movementType;
            this.movementDate = movementDate;
        }
        public int getMovementID() { return movementID; }
        public int getInventoryID() { return inventoryID; }
        public Integer getFromWarehouseID() { return fromWarehouseID; }
        public Integer getToWarehouseID() { return toWarehouseID; }
        public int getQuantityChanged() { return quantityChanged; }
        public String getMovementType() { return movementType; }
        public String getMovementDate() { return movementDate; }
    }
}
