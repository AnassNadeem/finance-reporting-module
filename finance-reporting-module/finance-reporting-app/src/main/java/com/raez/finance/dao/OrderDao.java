package com.raez.finance.dao;

import com.raez.finance.model.OrderReportRow;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches order report rows for Detailed Reports (Order tab).
 */
public class OrderDao {

    /**
     * Fetch orders with optional date range, status filter, and search (customer name or order ID).
     */
    public List<OrderReportRow> findReportRows(LocalDate from, LocalDate to, String statusFilter, String search) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT o.orderID, c.name AS customerName, o.totalAmount, o.orderDate, o.status " +
                "FROM \"Order\" o " +
                "JOIN CustomerRegistration c ON o.customerID = c.customerID WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (from != null) {
            sql.append(" AND o.orderDate >= ?");
            params.add(from + " 00:00:00");
            idx++;
        }
        if (to != null) {
            sql.append(" AND o.orderDate <= ?");
            params.add(to + " 23:59:59");
            idx++;
        }
        if (statusFilter != null && !statusFilter.isBlank() && !"All Status".equalsIgnoreCase(statusFilter.trim())) {
            sql.append(" AND o.status = ?");
            params.add(statusFilter.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR CAST(o.orderID AS TEXT) LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
        }
        sql.append(" ORDER BY o.orderDate DESC, o.orderID DESC");

        List<OrderReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int orderId = rs.getInt("orderID");
                String productSummary = getProductSummary(conn, orderId);
                rows.add(new OrderReportRow(
                        String.valueOf(orderId),
                        rs.getString("customerName"),
                        productSummary,
                        rs.getDouble("totalAmount"),
                        formatDate(rs.getString("orderDate")),
                        rs.getString("status")
                ));
            }
        }
        return rows;
    }

    private String getProductSummary(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT p.name, (SELECT COUNT(*) FROM OrderItem WHERE orderID = ?) AS cnt " +
                "FROM OrderItem oi JOIN Product p ON oi.productID = p.productID " +
                "WHERE oi.orderID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String first = rs.getString("name");
                int cnt = rs.getInt("cnt");
                return cnt > 1 ? first + " +" + (cnt - 1) + " more" : first;
            }
        }
        return "—";
    }

    private static String formatDate(String dateStr) {
        if (dateStr == null) return "—";
        if (dateStr.length() >= 10) return dateStr.substring(0, 10);
        return dateStr;
    }

    /** Distinct order statuses for the Status ComboBox (UI adds "All Status"). */
    public List<String> findStatusOptions() throws SQLException {
        String sql = "SELECT DISTINCT status FROM \"Order\" WHERE status IS NOT NULL ORDER BY status";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("status"));
            }
        }
        return list;
    }
}
