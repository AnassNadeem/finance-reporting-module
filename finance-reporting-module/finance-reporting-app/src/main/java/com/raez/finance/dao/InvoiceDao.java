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
public class InvoiceDao {

    public List<InvoiceRow> findInvoices(LocalDate from, LocalDate to, String statusFilter, String search, int limit, int offset) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT i.invoiceID, i.invoiceNumber, i.status, i.totalAmount, i.currency, " +
                "i.issuedAt, i.dueDate, i.paidAt, o.orderID, c.name AS customerName " +
                "FROM Invoice i " +
                "JOIN \"Order\" o ON i.orderID = o.orderID " +
                "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
                "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (from != null) {
            sql.append(" AND date(i.issuedAt) >= ? ");
            params.add(from.toString());
        }
        if (to != null) {
            sql.append(" AND date(i.issuedAt) <= ? ");
            params.add(to.toString());
        }
        if (statusFilter != null && !statusFilter.isBlank() && !"All".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND i.status = ? ");
            params.add(statusFilter.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (i.invoiceNumber LIKE ? OR CAST(o.orderID AS TEXT) LIKE ? OR c.name LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        sql.append(" ORDER BY i.issuedAt DESC, i.invoiceID DESC ");
        if (limit > 0) {
            sql.append(" LIMIT ? OFFSET ? ");
            params.add(limit);
            params.add(offset);
        }

        List<InvoiceRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
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

