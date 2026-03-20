package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to Invoice table for the Invoices page.
 */
public class InvoiceDao implements InvoiceDaoInterface {

    private static final String FIND_INVOICES_SQL =
        "SELECT i.invoiceID, i.invoiceNumber, i.status, i.totalAmount, i.currency, " +
        "i.issuedAt, i.dueDate, i.paidAt, o.orderID, c.name AS customerName " +
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
        "i.issuedAt, i.dueDate, i.paidAt, o.orderID, c.name AS customerName " +
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
        private final int orderID;
        private final String customerName;

        public InvoiceRow(int invoiceID, String invoiceNumber, String status,
                          double totalAmount, String currency,
                          String issuedAt, String dueDate, String paidAt,
                          int orderID, String customerName) {
            this.invoiceID = invoiceID;
            this.invoiceNumber = invoiceNumber;
            this.status = status;
            this.totalAmount = totalAmount;
            this.currency = currency;
            this.issuedAt = issuedAt;
            this.dueDate = dueDate;
            this.paidAt = paidAt;
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
        public int getOrderID() { return orderID; }
        public String getCustomerName() { return customerName; }
    }
}

