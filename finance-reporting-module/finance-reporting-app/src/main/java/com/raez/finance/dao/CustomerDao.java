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
     * Fetch customers with order aggregates. Optional filters. Use limit &lt;= 0 for no limit.
     */
    public List<CustomerReportRow> findReportRows(String typeFilter, String countryFilter, String companyName, String search, int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT c.customerID, c.name, COALESCE(c.customerType, 'Individual') AS customerType, c.deliveryAddress, " +
                "COUNT(o.orderID) AS totalOrders, " +
                "COALESCE(SUM(o.totalAmount), 0) AS totalSpent, " +
                "COALESCE(MAX(o.orderDate), '') AS lastPurchase " +
                "FROM CustomerRegistration c " +
                "LEFT JOIN \"Order\" o ON o.customerID = c.customerID " +
                "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        if (typeFilter != null && !typeFilter.isBlank() && !"All".equalsIgnoreCase(typeFilter.trim()) && !"All Types".equalsIgnoreCase(typeFilter.trim())) {
            sql.append(" AND COALESCE(c.customerType, 'Individual') = ?");
            params.add(typeFilter.trim());
        }
        if (companyName != null && !companyName.isBlank()) {
            sql.append(" AND c.name = ?");
            params.add(companyName.trim());
        }
        if (countryFilter != null && !countryFilter.isBlank() && !"All".equalsIgnoreCase(countryFilter.trim())) {
            sql.append(" AND c.deliveryAddress LIKE ?");
            params.add("%" + countryFilter.trim() + "%");
        }
        sql.append(" GROUP BY c.customerID, c.name, c.customerType, c.deliveryAddress ORDER BY totalSpent DESC");
        if (limit > 0) {
            params.add(limit);
            params.add(offset);
        }
        String fullSql = (limit > 0)
                ? "SELECT * FROM (" + sql + ") AS sub LIMIT ? OFFSET ?"
                : sql.toString();

        List<CustomerReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(fullSql)) {
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
                        rs.getString("customerType") != null ? rs.getString("customerType") : "Individual",
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

    /** Distinct company names (customer name where customerType = 'Company') for Company filter dropdown. */
    public List<String> findCompanyNames() throws SQLException {
        String sql = "SELECT DISTINCT name FROM CustomerRegistration WHERE COALESCE(customerType, 'Individual') = 'Company' AND name IS NOT NULL ORDER BY name";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("name"));
        }
        return list;
    }

    /** Count customer report rows with same filters (for pagination). */
    public int countReportRows(String typeFilter, String countryFilter, String companyName, String search) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM (SELECT c.customerID FROM CustomerRegistration c " +
                "LEFT JOIN \"Order\" o ON o.customerID = c.customerID WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }
        if (typeFilter != null && !typeFilter.isBlank() && !"All".equalsIgnoreCase(typeFilter.trim()) && !"All Types".equalsIgnoreCase(typeFilter.trim())) {
            sql.append(" AND COALESCE(c.customerType, 'Individual') = ?");
            params.add(typeFilter.trim());
        }
        if (companyName != null && !companyName.isBlank()) {
            sql.append(" AND c.name = ?");
            params.add(companyName.trim());
        }
        if (countryFilter != null && !countryFilter.isBlank() && !"All".equalsIgnoreCase(countryFilter.trim())) {
            sql.append(" AND c.deliveryAddress LIKE ?");
            params.add("%" + countryFilter.trim() + "%");
        }
        sql.append(" GROUP BY c.customerID, c.name, c.customerType, c.deliveryAddress) AS sub");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Top buyers by total spent (for Customer Insights). Sorted descending by totalSpent.
     */
    public List<TopBuyerRow> findTopBuyers(int limit) throws SQLException {
        String sql = "SELECT c.customerID, c.name, COALESCE(c.customerType, 'Individual') AS customerType, c.deliveryAddress, " +
                "COUNT(o.orderID) AS totalOrders, COALESCE(SUM(o.totalAmount), 0) AS totalSpent, " +
                "COALESCE(MAX(o.orderDate), '') AS lastPurchase " +
                "FROM CustomerRegistration c " +
                "LEFT JOIN \"Order\" o ON o.customerID = c.customerID " +
                "GROUP BY c.customerID, c.name, c.customerType, c.deliveryAddress " +
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
                rows.add(new TopBuyerRow(rank++, rs.getString("name"),
                        rs.getString("customerType") != null ? rs.getString("customerType") : "Individual",
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

    /** Count customers where customerType = 'Company'. */
    public int getCompanyCustomerCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerRegistration WHERE COALESCE(customerType, 'Individual') = 'Company'";
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
     * Refund alert strings for Customer Insights (simple dev-friendly heuristics).
     * Uses Refund + Order + CustomerRegistration to produce human-readable alerts.
     */
    public List<String> findRefundAlerts() throws SQLException {
        String sql =
                "SELECT c.name, COUNT(r.refundID) AS refundCount, COALESCE(SUM(r.refundAmount), 0) AS totalRefunded " +
                "FROM Refund r " +
                "JOIN \"Order\" o ON r.orderID = o.orderID " +
                "JOIN CustomerRegistration c ON o.customerID = c.customerID " +
                "WHERE r.status IN ('REQUESTED','APPROVED','PROCESSED') " +
                "GROUP BY c.customerID, c.name " +
                "HAVING refundCount >= 2 OR totalRefunded >= 500 " +
                "ORDER BY totalRefunded DESC LIMIT 10";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                int cnt = rs.getInt(2);
                double amt = rs.getDouble(3);
                list.add(String.format("%s has %d refunds totalling %s", name, cnt, com.raez.finance.util.CurrencyUtil.formatCurrency(amt)));
            }
        }
        return list;
    }

    /**
     * Product issue alert strings for Customer Insights (heuristic: products with high refunds).
     */
    public List<String> findProductIssueAlerts() throws SQLException {
        String sql =
                "SELECT p.name, COUNT(r.refundID) AS refundCount, COALESCE(SUM(r.refundAmount), 0) AS totalRefunded " +
                "FROM Refund r " +
                "JOIN Product p ON r.productID = p.productID " +
                "WHERE r.productID IS NOT NULL " +
                "GROUP BY p.productID, p.name " +
                "HAVING refundCount >= 2 OR totalRefunded >= 500 " +
                "ORDER BY refundCount DESC, totalRefunded DESC LIMIT 10";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                int cnt = rs.getInt(2);
                double amt = rs.getDouble(3);
                list.add(String.format("Product \"%s\" has %d refunds totalling %s", name, cnt, com.raez.finance.util.CurrencyUtil.formatCurrency(amt)));
            }
        }
        return list;
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
