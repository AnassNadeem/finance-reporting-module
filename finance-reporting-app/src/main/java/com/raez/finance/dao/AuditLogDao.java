package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Read-only access to CustomerUpdate and StockMovement for the Audit Log view.
 */
public class AuditLogDao {

    public record MergedAuditRow(
            String occurredAt,
            String source,
            String userLabel,
            String action,
            String entity,
            String details
    ) {}

    /**
     * Merges customer updates and stock movements, sorted by date descending.
     *
     * @param sourceFilter "All", "Customer", or "Stock"
     */
    public List<MergedAuditRow> findMerged(LocalDate from, LocalDate to, String sourceFilter) throws Exception {
        String f = sourceFilter == null ? "All" : sourceFilter;
        List<MergedAuditRow> rows = new ArrayList<>();
        if ("All".equals(f) || "Customer".equals(f)) {
            for (CustomerUpdateRow c : findCustomerUpdates(from, to, 1000)) {
                rows.add(new MergedAuditRow(
                        c.getUpdateDate() != null ? c.getUpdateDate() : "",
                        "Customer update",
                        c.getAdminID() != null ? "User #" + c.getAdminID() : "—",
                        c.getUpdatedField() != null ? c.getUpdatedField() : "",
                        c.getCustomerID() != null ? "Customer #" + c.getCustomerID() : "—",
                        summarizeChange(c.getOldValue(), c.getNewValue())
                ));
            }
        }
        if ("All".equals(f) || "Stock".equals(f)) {
            for (StockMovementRow s : findStockMovements(from, to, 1000)) {
                rows.add(new MergedAuditRow(
                        s.getMovementDate() != null ? s.getMovementDate() : "",
                        "Stock movement",
                        "—",
                        s.getMovementType() != null ? s.getMovementType() : "",
                        "Inventory #" + s.getInventoryID(),
                        summarizeStock(s)
                ));
            }
        }
        rows.sort(Comparator.comparing(MergedAuditRow::occurredAt, Comparator.nullsLast(String::compareTo)).reversed());
        return rows;
    }

    private static String summarizeChange(String oldV, String newV) {
        String o = oldV != null ? oldV : "";
        String n = newV != null ? newV : "";
        if (o.isEmpty() && n.isEmpty()) return "—";
        return o + " → " + n;
    }

    private static String summarizeStock(StockMovementRow s) {
        StringBuilder b = new StringBuilder();
        b.append("Qty ").append(s.getQuantityChanged() > 0 ? "+" : "").append(s.getQuantityChanged());
        if (s.getFromWarehouseID() != null || s.getToWarehouseID() != null) {
            b.append(" · ");
            if (s.getFromWarehouseID() != null) b.append("from ").append(s.getFromWarehouseID());
            if (s.getToWarehouseID() != null) {
                if (s.getFromWarehouseID() != null) b.append(" ");
                b.append("to ").append(s.getToWarehouseID());
            }
        }
        return b.toString();
    }

    public List<CustomerUpdateRow> findCustomerUpdates(LocalDate from, LocalDate to, int limit) throws Exception {
        boolean bounded = from != null && to != null;
        String sql = "SELECT updateID, adminID, customerID, updatedField, oldValue, newValue, updateDate FROM CustomerUpdate ";
        if (bounded) {
            sql += "WHERE date(COALESCE(updateDate, '1970-01-01')) >= date(?) AND date(COALESCE(updateDate, '1970-01-01')) <= date(?) ";
        }
        sql += "ORDER BY updateDate DESC LIMIT ?";
        List<CustomerUpdateRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (bounded) {
                ps.setString(i++, from.toString());
                ps.setString(i++, to.toString());
            }
            ps.setInt(i, limit);
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

    public List<StockMovementRow> findStockMovements(LocalDate from, LocalDate to, int limit) throws Exception {
        boolean bounded = from != null && to != null;
        String sql = "SELECT movementID, inventoryID, fromWarehouseID, toWarehouseID, quantityChanged, movementType, movementDate FROM StockMovement ";
        if (bounded) {
            sql += "WHERE date(COALESCE(movementDate, '1970-01-01')) >= date(?) AND date(COALESCE(movementDate, '1970-01-01')) <= date(?) ";
        }
        sql += "ORDER BY movementDate DESC LIMIT ?";
        List<StockMovementRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (bounded) {
                ps.setString(i++, from.toString());
                ps.setString(i++, to.toString());
            }
            ps.setInt(i, limit);
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

    /** @deprecated use {@link #findCustomerUpdates(LocalDate, LocalDate, int)} */
    @Deprecated
    public List<CustomerUpdateRow> findCustomerUpdates(int limit) throws Exception {
        return findCustomerUpdates(null, null, limit);
    }

    /** @deprecated use {@link #findStockMovements(LocalDate, LocalDate, int)} */
    @Deprecated
    public List<StockMovementRow> findStockMovements(int limit) throws Exception {
        return findStockMovements(null, null, limit);
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
