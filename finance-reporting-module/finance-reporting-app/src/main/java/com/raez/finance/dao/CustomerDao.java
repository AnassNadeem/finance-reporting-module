package com.raez.finance.dao;

import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.TopBuyerRow;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches customer report rows for Detailed Reports (Customer tab).
 * Schema has no type/country; type defaulted to "Individual", country from deliveryAddress.
 */
public class CustomerDao {

    /**
     * Fetch customers with order aggregates: totalOrders, totalSpent, avgOrderValue, lastPurchase.
     * Optional filters: type (not in schema – can filter by name/address), country (deliveryAddress).
     */
    public List<CustomerReportRow> findReportRows(String typeFilter, String countryFilter, String search) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT c.customerID, c.name, c.deliveryAddress, " +
                "COUNT(o.orderID) AS totalOrders, " +
                "COALESCE(SUM(o.totalAmount), 0) AS totalSpent, " +
                "COALESCE(MAX(o.orderDate), '') AS lastPurchase " +
                "FROM CustomerRegistration c " +
                "LEFT JOIN \"Order\" o ON o.customerID = c.customerID " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();
        int idx = 1;

        if (search != null && !search.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            idx += 3;
        }
        if (countryFilter != null && !countryFilter.isBlank() && !"All".equalsIgnoreCase(countryFilter.trim())) {
            sql.append(" AND c.deliveryAddress LIKE ?");
            params.add("%" + countryFilter.trim() + "%");
            idx++;
        }
        sql.append(" GROUP BY c.customerID, c.name, c.deliveryAddress ORDER BY totalSpent DESC");

        List<CustomerReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int totalOrders = rs.getInt("totalOrders");
                double totalSpent = rs.getDouble("totalSpent");
                double aov = totalOrders > 0 ? totalSpent / totalOrders : 0;
                String lastPurchase = rs.getString("lastPurchase");
                if (lastPurchase != null && lastPurchase.length() >= 10) lastPurchase = lastPurchase.substring(0, 10);
                else if (lastPurchase == null || lastPurchase.isEmpty()) lastPurchase = "—";
                rows.add(new CustomerReportRow(
                        String.valueOf(rs.getInt("customerID")),
                        rs.getString("name"),
                        "Individual",
                        rs.getString("deliveryAddress") != null ? rs.getString("deliveryAddress") : "—",
                        totalOrders,
                        totalSpent,
                        aov,
                        lastPurchase
                ));
            }
        }
        return rows;
    }

    /**
     * Top buyers by total spent (for Customer Insights). Sorted descending by totalSpent.
     */
    public List<TopBuyerRow> findTopBuyers(int limit) throws SQLException {
        String sql = "SELECT c.customerID, c.name, c.deliveryAddress, " +
                "COUNT(o.orderID) AS totalOrders, COALESCE(SUM(o.totalAmount), 0) AS totalSpent, " +
                "COALESCE(MAX(o.orderDate), '') AS lastPurchase " +
                "FROM CustomerRegistration c " +
                "LEFT JOIN \"Order\" o ON o.customerID = c.customerID " +
                "GROUP BY c.customerID, c.name, c.deliveryAddress " +
                "ORDER BY totalSpent DESC LIMIT ?";
        List<TopBuyerRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit <= 0 ? 100 : limit);
            ResultSet rs = ps.executeQuery();
            int rank = 1;
            while (rs.next()) {
                int totalOrders = rs.getInt("totalOrders");
                double totalSpent = rs.getDouble("totalSpent");
                double aov = totalOrders > 0 ? totalSpent / totalOrders : 0;
                String last = rs.getString("lastPurchase");
                if (last != null && last.length() >= 10) last = last.substring(0, 10);
                else if (last == null || last.isEmpty()) last = "—";
                rows.add(new TopBuyerRow(rank++, rs.getString("name"), "Individual",
                        rs.getString("deliveryAddress") != null ? rs.getString("deliveryAddress") : "—",
                        totalSpent, totalOrders, aov, last));
            }
        }
        return rows;
    }

    /**
     * Monthly order counts for the last 12 months (for chartFrequency).
     * Returns list of (monthLabel, count).
     */
    public List<MonthlyCount> findMonthlyOrderCounts() throws SQLException {
        String sql = "SELECT strftime('%Y-%m', orderDate) AS month, COUNT(*) AS cnt " +
                "FROM \"Order\" WHERE orderDate >= date('now', '-12 months') " +
                "GROUP BY month ORDER BY month";
        List<MonthlyCount> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MonthlyCount(rs.getString("month"), rs.getInt("cnt")));
            }
        }
        return list;
    }

    public static final class MonthlyCount {
        public final String month;
        public final int count;
        public MonthlyCount(String month, int count) { this.month = month; this.count = count; }
    }

    /** Total customer count and total revenue for KPIs (avg spending = totalRevenue/count). */
    public int getTotalCustomerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerRegistration";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Sum of all order amounts (for avg spending per customer). */
    public double getTotalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(totalAmount), 0) FROM \"Order\"";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /**
     * Distinct delivery address fragments (or "countries") for filter ComboBox.
     */
    public List<String> findCountryOptions() throws SQLException {
        String sql = "SELECT DISTINCT deliveryAddress FROM CustomerRegistration WHERE deliveryAddress IS NOT NULL AND deliveryAddress != '' ORDER BY deliveryAddress";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("deliveryAddress"));
            }
        }
        return list;
    }
}
