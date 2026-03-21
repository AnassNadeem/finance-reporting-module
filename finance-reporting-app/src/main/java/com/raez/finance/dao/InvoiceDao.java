package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import com.raez.finance.service.GlobalSettingsService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to Invoice table for the Invoices page.
 */
public class InvoiceDao implements InvoiceDaoInterface {

    private static final String FIND_INVOICES_SQL =
        "SELECT i.invoiceID, i.invoiceNumber, i.status, i.totalAmount, i.currency, " +
        "i.issuedAt, i.dueDate, i.paidAt, i.notes, o.orderID, c.name AS customerName " +
        "FROM Invoice i " +
        "JOIN \"Order\" o ON i.orderID = o.orderID " +
        "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
        "WHERE (? IS NULL OR date(i.issuedAt) >= ?) " +
        "AND (? IS NULL OR date(i.issuedAt) <= ?) " +
        "AND (? IS NULL OR i.status = ?) " +
        "AND (? IS NULL OR (i.invoiceNumber LIKE ? OR CAST(o.orderID AS TEXT) LIKE ? OR c.name LIKE ?)) " +
        "ORDER BY i.issuedAt DESC, i.invoiceID DESC " +
        "LIMIT ? OFFSET ?";

    private static final String FIND_INVOICES_NO_LIMIT_SQL =
        "SELECT i.invoiceID, i.invoiceNumber, i.status, i.totalAmount, i.currency, " +
        "i.issuedAt, i.dueDate, i.paidAt, i.notes, o.orderID, c.name AS customerName " +
        "FROM Invoice i " +
        "JOIN \"Order\" o ON i.orderID = o.orderID " +
        "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
        "WHERE (? IS NULL OR date(i.issuedAt) >= ?) " +
        "AND (? IS NULL OR date(i.issuedAt) <= ?) " +
        "AND (? IS NULL OR i.status = ?) " +
        "AND (? IS NULL OR (i.invoiceNumber LIKE ? OR CAST(o.orderID AS TEXT) LIKE ? OR c.name LIKE ?)) " +
        "ORDER BY i.issuedAt DESC, i.invoiceID DESC";

    public List<InvoiceRow> findInvoices(LocalDate from, LocalDate to, String statusFilter, String search, int limit, int offset) throws Exception {
        String fromParam = from == null ? null : from.toString();
        String toParam = to == null ? null : to.toString();
        String statusParam = normalizeStatus(statusFilter);
        String searchParam = normalizeSearch(search);
        String sql = limit > 0 ? FIND_INVOICES_SQL : FIND_INVOICES_NO_LIMIT_SQL;

        List<InvoiceRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, fromParam);
            ps.setString(i++, fromParam);
            ps.setString(i++, toParam);
            ps.setString(i++, toParam);
            ps.setString(i++, statusParam);
            ps.setString(i++, statusParam);
            ps.setString(i++, searchParam);
            ps.setString(i++, searchParam);
            ps.setString(i++, searchParam);
            ps.setString(i++, searchParam);
            if (limit > 0) {
                ps.setInt(i++, limit);
                ps.setInt(i, offset);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(new InvoiceRow(
                        rs.getInt("invoiceID"),
                        rs.getString("invoiceNumber"),
                        rs.getString("status"),
                        rs.getDouble("totalAmount"),
                        rs.getString("currency"),
                        rs.getString("issuedAt"),
                        rs.getString("dueDate"),
                        rs.getString("paidAt"),
                        rs.getString("notes"),
                        rs.getInt("orderID"),
                        rs.getString("customerName")
                ));
            }
        }
        return rows;
    }

    private String normalizeStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) return null;
        if ("All".equalsIgnoreCase(statusFilter.trim())) return null;
        return statusFilter.trim();
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.trim() + "%";
    }

    public static class InvoiceRow {
        private final int invoiceID;
        private final String invoiceNumber;
        private final String status;
        private final double totalAmount;
        private final String currency;
        private final String issuedAt;
        private final String dueDate;
        private final String paidAt;
        private final String notes;
        private final int orderID;
        private final String customerName;

        public InvoiceRow(int invoiceID, String invoiceNumber, String status,
                          double totalAmount, String currency,
                          String issuedAt, String dueDate, String paidAt, String notes,
                          int orderID, String customerName) {
            this.invoiceID = invoiceID;
            this.invoiceNumber = invoiceNumber;
            this.status = status;
            this.totalAmount = totalAmount;
            this.currency = currency;
            this.issuedAt = issuedAt;
            this.dueDate = dueDate;
            this.paidAt = paidAt;
            this.notes = notes;
            this.orderID = orderID;
            this.customerName = customerName;
        }

        public int getInvoiceID() { return invoiceID; }
        public String getInvoiceNumber() { return invoiceNumber; }
        public String getStatus() { return status; }
        public double getTotalAmount() { return totalAmount; }
        public String getCurrency() { return currency; }
        public String getIssuedAt() { return issuedAt; }
        public String getDueDate() { return dueDate; }
        public String getPaidAt() { return paidAt; }
        public String getNotes() { return notes; }
        /** Notes for UI / PDF; never null. */
        public String getNotesSafe() { return notes != null ? notes : ""; }
        public int getOrderID() { return orderID; }
        public String getCustomerName() { return customerName; }
    }

    public record InvoiceKpiRow(double totalInvoiced, double totalPaid, double outstanding, int overdueCount) {}

    public record OrderWithoutInvoiceRow(int orderID, String customerName, double totalAmount) {}

    @Override
    public InvoiceKpiRow aggregateForRange(LocalDate from, LocalDate to, String statusFilter, String search) throws Exception {
        String fromParam = from == null ? null : from.toString();
        String toParam = to == null ? null : to.toString();
        String statusParam = normalizeStatus(statusFilter);
        String searchParam = normalizeSearch(search);

        String sql =
            "SELECT " +
            "COALESCE(SUM(i.totalAmount), 0) AS totalInvoiced, " +
            "COALESCE(SUM(CASE WHEN UPPER(i.status) = 'PAID' THEN i.totalAmount ELSE 0 END), 0) AS totalPaid, " +
            "COALESCE(SUM(CASE WHEN UPPER(i.status) <> 'PAID' THEN i.totalAmount ELSE 0 END), 0) AS outstanding, " +
            "SUM(CASE WHEN UPPER(i.status) = 'OVERDUE' OR (UPPER(i.status) <> 'PAID' AND i.dueDate IS NOT NULL AND date(i.dueDate) < date('now')) " +
            "THEN 1 ELSE 0 END) AS overdueCnt " +
            "FROM Invoice i " +
            "JOIN \"Order\" o ON i.orderID = o.orderID " +
            "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
            "WHERE (? IS NULL OR date(i.issuedAt) >= ?) " +
            "AND (? IS NULL OR date(i.issuedAt) <= ?) " +
            "AND (? IS NULL OR i.status = ?) " +
            "AND (? IS NULL OR (i.invoiceNumber LIKE ? OR CAST(o.orderID AS TEXT) LIKE ? OR c.name LIKE ?))";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, fromParam);
            ps.setString(i++, fromParam);
            ps.setString(i++, toParam);
            ps.setString(i++, toParam);
            ps.setString(i++, statusParam);
            ps.setString(i++, statusParam);
            ps.setString(i++, searchParam);
            ps.setString(i++, searchParam);
            ps.setString(i++, searchParam);
            ps.setString(i, searchParam);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new InvoiceKpiRow(0, 0, 0, 0);
                return new InvoiceKpiRow(
                    rs.getDouble("totalInvoiced"),
                    rs.getDouble("totalPaid"),
                    rs.getDouble("outstanding"),
                    rs.getInt("overdueCnt"));
            }
        }
    }

    @Override
    public List<OrderWithoutInvoiceRow> findOrdersWithoutInvoice(int limit) throws Exception {
        String sql =
            "SELECT o.orderID, c.name AS customerName, o.totalAmount " +
            "FROM \"Order\" o " +
            "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
            "WHERE NOT EXISTS (SELECT 1 FROM Invoice i WHERE i.orderID = o.orderID) " +
            "ORDER BY o.orderDate DESC LIMIT ?";
        List<OrderWithoutInvoiceRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OrderWithoutInvoiceRow(
                        rs.getInt("orderID"),
                        rs.getString("customerName"),
                        rs.getDouble("totalAmount")));
                }
            }
        }
        return rows;
    }

    @Override
    public int insertInvoiceForOrder(int orderId, LocalDate dueDate, String notes) throws Exception {
        double gross;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT totalAmount FROM \"Order\" WHERE orderID = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Order not found: " + orderId);
                gross = rs.getDouble("totalAmount");
            }
        }
        double vatPct = GlobalSettingsService.getInstance().getDefaultVatPercent() / 100.0;
        double vatAmount = gross * (vatPct / (1.0 + vatPct));
        String invoiceNumber = "INV-" + orderId + "-" + System.currentTimeMillis();

        String sql = "INSERT INTO Invoice (orderID, paymentID, invoiceNumber, status, totalAmount, vatAmount, " +
            "issuedAt, dueDate, notes) VALUES (?, NULL, ?, 'PENDING', ?, ?, datetime('now'), ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setString(2, invoiceNumber);
            ps.setDouble(3, gross);
            ps.setDouble(4, vatAmount);
            ps.setString(5, dueDate != null ? dueDate.toString() : null);
            ps.setString(6, notes != null && !notes.isBlank() ? notes : null);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    @Override
    public void updateInvoice(int invoiceId, String status, LocalDate dueDate, String notes) throws Exception {
        String sql = "UPDATE Invoice SET status = ?, dueDate = ?, notes = ?, updatedAt = datetime('now') WHERE invoiceID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status != null ? status.trim().toUpperCase() : "PENDING");
            ps.setString(2, dueDate != null ? dueDate.toString() : null);
            ps.setString(3, notes);
            ps.setInt(4, invoiceId);
            ps.executeUpdate();
        }
    }

    @Override
    public void markInvoicePaid(int invoiceId) throws Exception {
        String sql = "UPDATE Invoice SET status = 'PAID', paidAt = datetime('now'), updatedAt = datetime('now') WHERE invoiceID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            ps.executeUpdate();
        }
    }
}

