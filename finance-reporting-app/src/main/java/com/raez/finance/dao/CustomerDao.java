package com.raez.finance.dao;

import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.TopBuyerRow;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches customer report rows for Detailed Reports (Customer tab).
 * Schema has no type/country; type defaulted to "Individual", country from deliveryAddress.
 */
public class CustomerDao implements CustomerDaoInterface {

    private static final String ORDER_DATE_FILTER =
        " AND (? IS NULL OR o.orderDate >= ?) AND (? IS NULL OR o.orderDate <= ?) ";

    private static final String FIND_SQL =
        "SELECT c.customerID, c.name, COALESCE(c.customerType, 'Individual') AS customerType, c.deliveryAddress, " +
        "(SELECT COUNT(*) FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS totalOrders, " +
        "(SELECT COALESCE(SUM(o.totalAmount), 0) FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS totalSpent, " +
        "(SELECT COALESCE(MAX(o.orderDate), '') FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS lastPurchase " +
        "FROM CustomerRegistration c " +
        "WHERE (? IS NULL OR (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)) " +
        "AND (? IS NULL OR COALESCE(c.customerType, 'Individual') = ?) " +
        "AND (? IS NULL OR c.name = ?) " +
        "AND (? IS NULL OR c.deliveryAddress LIKE ?) " +
        "ORDER BY totalSpent DESC " +
        "LIMIT ? OFFSET ?";

    private static final String FIND_NO_LIMIT_SQL =
        "SELECT c.customerID, c.name, COALESCE(c.customerType, 'Individual') AS customerType, c.deliveryAddress, " +
        "(SELECT COUNT(*) FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS totalOrders, " +
        "(SELECT COALESCE(SUM(o.totalAmount), 0) FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS totalSpent, " +
        "(SELECT COALESCE(MAX(o.orderDate), '') FROM \"Order\" o WHERE o.customerID = c.customerID" + ORDER_DATE_FILTER + ") AS lastPurchase " +
        "FROM CustomerRegistration c " +
        "WHERE (? IS NULL OR (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)) " +
        "AND (? IS NULL OR COALESCE(c.customerType, 'Individual') = ?) " +
        "AND (? IS NULL OR c.name = ?) " +
        "AND (? IS NULL OR c.deliveryAddress LIKE ?) " +
        "ORDER BY totalSpent DESC";

    private static final String COUNT_SQL =
        "SELECT COUNT(*) FROM (" +
        "SELECT c.customerID " +
        "FROM CustomerRegistration c " +
        "WHERE (? IS NULL OR (c.name LIKE ? OR c.email LIKE ? OR CAST(c.customerID AS TEXT) LIKE ?)) " +
        "AND (? IS NULL OR COALESCE(c.customerType, 'Individual') = ?) " +
        "AND (? IS NULL OR c.name = ?) " +
        "AND (? IS NULL OR c.deliveryAddress LIKE ?) " +
        ") AS sub";

    /**
     * Fetch customers with order aggregates (optionally limited to orders between orderFrom and orderTo).
     */
    public List<CustomerReportRow> findReportRows(LocalDate orderFrom, LocalDate orderTo, String typeFilter, String countryFilter, String companyName, String search, int limit, int offset) throws SQLException {
        String searchTerm = normalizeSearch(search);
        String normalizedType = normalizeType(typeFilter);
        String normalizedCompany = normalizeValue(companyName);
        String countryLike = normalizeCountry(countryFilter);
        String sql = limit > 0 ? FIND_SQL : FIND_NO_LIMIT_SQL;

        List<CustomerReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            i = bindOrderDateFilter(ps, i, orderFrom, orderTo);
            i = bindOrderDateFilter(ps, i, orderFrom, orderTo);
            i = bindOrderDateFilter(ps, i, orderFrom, orderTo);
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, normalizedType);
            ps.setString(i++, normalizedType);
            ps.setString(i++, normalizedCompany);
            ps.setString(i++, normalizedCompany);
            ps.setString(i++, countryLike);
            ps.setString(i++, countryLike);
            if (limit > 0) {
                ps.setInt(i++, limit);
                ps.setInt(i, offset);
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
    public int countReportRows(LocalDate orderFrom, LocalDate orderTo, String typeFilter, String countryFilter, String companyName, String search) throws SQLException {
        String searchTerm = normalizeSearch(search);
        String normalizedType = normalizeType(typeFilter);
        String normalizedCompany = normalizeValue(companyName);
        String countryLike = normalizeCountry(countryFilter);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_SQL)) {
            int i = 1;
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, searchTerm);
            ps.setString(i++, normalizedType);
            ps.setString(i++, normalizedType);
            ps.setString(i++, normalizedCompany);
            ps.setString(i++, normalizedCompany);
            ps.setString(i++, countryLike);
            ps.setString(i, countryLike);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Binds (? IS NULL OR o.orderDate >= ?) AND (? IS NULL OR o.orderDate <= ?) — four parameters. */
    private static int bindOrderDateFilter(PreparedStatement ps, int i, LocalDate from, LocalDate to) throws SQLException {
        if (from == null) {
            ps.setNull(i++, Types.VARCHAR);
            ps.setNull(i++, Types.VARCHAR);
        } else {
            String f = from + " 00:00:00";
            ps.setString(i++, f);
            ps.setString(i++, f);
        }
        if (to == null) {
            ps.setNull(i++, Types.VARCHAR);
            ps.setNull(i++, Types.VARCHAR);
        } else {
            String t = to + " 23:59:59";
            ps.setString(i++, t);
            ps.setString(i++, t);
        }
        return i;
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
     * Monthly order counts for the last 12 months (default chart range).
     */
    public List<MonthlyCount> findMonthlyOrderCounts() throws SQLException {
        return findMonthlyOrderCounts(LocalDate.now().minusMonths(12), LocalDate.now());
    }

    /**
     * Monthly order counts between {@code from} and {@code end} (inclusive), grouped by YYYY-MM.
     */
    public List<MonthlyCount> findMonthlyOrderCounts(LocalDate from, LocalDate end) throws SQLException {
        String sql = "SELECT strftime('%Y-%m', orderDate) AS month, COUNT(*) AS cnt " +
                "FROM \"Order\" " +
                "WHERE (? IS NULL OR orderDate >= ?) AND (? IS NULL OR orderDate <= ?) " +
                "GROUP BY month ORDER BY month";
        List<MonthlyCount> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindOrderDateFilter(ps, 1, from, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MonthlyCount(rs.getString("month"), rs.getInt("cnt")));
                }
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

    /**
     * Customers with at least one order: how many have last order older than {@code days90} / {@code days180} days,
     * plus customers with no orders yet.
     */
    public ChurnStats findChurnStats(int days90, int days180) throws SQLException {
        String sql =
                "WITH last_purchase AS ( "
                + "SELECT customerID, MAX(substr(orderDate, 1, 10)) AS lastDay FROM \"Order\" GROUP BY customerID "
                + ") "
                + "SELECT "
                + "(SELECT COUNT(*) FROM last_purchase) AS withOrders, "
                + "(SELECT COUNT(*) FROM last_purchase WHERE julianday('now') - julianday(lastDay) > ?) AS dormant90, "
                + "(SELECT COUNT(*) FROM last_purchase WHERE julianday('now') - julianday(lastDay) > ?) AS dormant180, "
                + "(SELECT COUNT(*) FROM CustomerRegistration c WHERE NOT EXISTS ( "
                + "SELECT 1 FROM \"Order\" o WHERE o.customerID = c.customerID)) AS noOrders";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, days90);
            ps.setInt(2, days180);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new ChurnStats(0, 0, 0, 0);
                return new ChurnStats(
                        rs.getInt("withOrders"),
                        rs.getInt("dormant90"),
                        rs.getInt("dormant180"),
                        rs.getInt("noOrders"));
            }
        }
    }

    /**
     * Count of customers by calendar month of their <strong>first order</strong> (proxy for acquisition when
     * {@code registrationDate} is not stored).
     */
    public List<MonthlyCount> findFirstOrderMonthCounts(LocalDate from, LocalDate to) throws SQLException {
        String sql =
                "SELECT strftime('%Y-%m', fo.firstOrder) AS month, COUNT(*) AS cnt FROM ( "
                + "SELECT customerID, MIN(orderDate) AS firstOrder FROM \"Order\" GROUP BY customerID "
                + ") fo "
                + "WHERE (? IS NULL OR date(fo.firstOrder) >= date(?)) "
                + "AND (? IS NULL OR date(fo.firstOrder) <= date(?)) "
                + "GROUP BY month ORDER BY month";
        List<MonthlyCount> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (from == null) {
                ps.setNull(1, Types.VARCHAR);
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(1, from.toString());
                ps.setString(2, from.toString());
            }
            if (to == null) {
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(3, to.toString());
                ps.setString(4, to.toString());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MonthlyCount(rs.getString("month"), rs.getInt("cnt")));
                }
            }
        }
        return list;
    }

    public record ChurnStats(int customersWithOrders, int dormant90, int dormant180, int noOrders) {}

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.trim() + "%";
    }

    private String normalizeType(String typeFilter) {
        if (typeFilter == null || typeFilter.isBlank()) return null;
        if ("All".equalsIgnoreCase(typeFilter.trim()) || "All Types".equalsIgnoreCase(typeFilter.trim())) return null;
        return typeFilter.trim();
    }

    private String normalizeCountry(String countryFilter) {
        if (countryFilter == null || countryFilter.isBlank()) return null;
        if ("All".equalsIgnoreCase(countryFilter.trim())) return null;
        return "%" + countryFilter.trim() + "%";
    }

    private String normalizeValue(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
