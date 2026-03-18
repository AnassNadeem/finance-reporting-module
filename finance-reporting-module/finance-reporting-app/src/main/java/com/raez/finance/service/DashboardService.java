package com.raez.finance.service;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates and time-series data for the Overview dashboard.
 * All methods use the shared finance_raez.db (see DBConnection).
 * Category filter: pass "All Categories" or null to include all; otherwise category name.
 */
public class DashboardService {

    /**
     * Total sales = SUM of successful payments. Optional category filter (by order items).
     */
    public double getTotalSales(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(p.amountPaid), 0) FROM Payment p " +
                "JOIN \"Order\" o ON p.orderID = o.orderID ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE p.paymentStatus = 'SUCCESS' ";
        sql = appendDateFilter(sql, "p.paymentDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        return querySingleDoubleWithCategory(sql, from, to, category);
    }

    public double getTotalProfit(LocalDate from, LocalDate to, String category) throws SQLException {
        double sales = getTotalSales(from, to, category);
        double refunds = getRefunds(from, to, category);
        return Math.max(0, sales - refunds);
    }

    public double getOutstandingPayments(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(o.totalAmount), 0) FROM \"Order\" o ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE o.status NOT IN ('Cancelled') " +
                "AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.orderID = o.orderID AND p.paymentStatus = 'SUCCESS') ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        return querySingleDoubleWithCategory(sql, from, to, category);
    }

    public double getRefunds(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r JOIN \"Order\" o ON r.orderID = o.orderID ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE 1=1 ";
        sql = appendDateFilter(sql, "r.refundDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        return querySingleDoubleWithCategory(sql, from, to, category);
    }

    /**
     * Approximate COGS for orders in range. The current schema does not store product cost,
     * so this uses a simple heuristic (60% of line revenue) to provide a consistent value
     * for dashboards; detailed profitability is handled by mock data.
     */
    public double getTotalCogs(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(oi.quantity * COALESCE(p2.unitCost, oi.unitPrice * 0.6)), 0) " +
                "FROM OrderItem oi JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "JOIN Product p2 ON oi.productID = p2.productID ";
        if (hasCategoryFilter(category)) {
            sql += "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        return querySingleDoubleWithCategory(sql, from, to, category);
    }

    /**
     * Total VAT collected from successful payments in period (gross amounts converted using global VAT rate).
     */
    public double getTotalVatCollected(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT p.amountPaid FROM Payment p JOIN \"Order\" o ON p.orderID = o.orderID ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE p.paymentStatus = 'SUCCESS' ";
        sql = appendDateFilter(sql, "p.paymentDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        List<Double> amounts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) amounts.add(rs.getDouble(1));
        }
        double vat = 0;
        for (Double gross : amounts) vat += GlobalSettingsService.getInstance().vatFromGross(gross);
        return vat;
    }

    public int getTotalCustomers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerRegistration";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int getTotalOrders(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT o.orderID) FROM \"Order\" o ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        return querySingleIntWithCategory(sql, from, to, category);
    }

    public double getAverageOrderValue(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(o.totalAmount), 0) / NULLIF(COUNT(DISTINCT o.orderID), 0) FROM \"Order\" o ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    public String getMostPopularProductName(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT p.name FROM OrderItem oi " +
                "JOIN Product p ON oi.productID = p.productID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        sql += " GROUP BY p.productID ORDER BY SUM(oi.quantity * oi.unitPrice) DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "—";
        }
    }

    /**
     * Time series: formatted date string -> total sales. Uses short date format to avoid clustering.
     */
    public List<DataPoint<String, Number>> getSalesTimeSeries(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT DATE(o.orderDate) AS d, COALESCE(SUM(o.totalAmount), 0) " +
                "FROM \"Order\" o ";
        if (hasCategoryFilter(category)) {
            sql += "JOIN OrderItem oi ON oi.orderID = o.orderID JOIN Product p2 ON oi.productID = p2.productID " +
                    "LEFT JOIN Category c ON p2.categoryID = c.categoryID ";
        }
        sql += "WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        sql += " GROUP BY d ORDER BY d";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String raw = rs.getString(1);
                list.add(new DataPoint<>(formatChartDate(raw), rs.getDouble(2)));
            }
        }
        return list;
    }

    private static final java.time.format.DateTimeFormatter CHART_DATE = java.time.format.DateTimeFormatter.ofPattern("MMM d");

    private static String formatChartDate(String yyyyMmDd) {
        if (yyyyMmDd == null || yyyyMmDd.isBlank()) return yyyyMmDd;
        try {
            return java.time.LocalDate.parse(yyyyMmDd).format(CHART_DATE);
        } catch (Exception e) {
            return yyyyMmDd;
        }
    }

    public List<DataPoint<String, Number>> getCategoryRevenue(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(c.categoryName, 'Uncategorized'), SUM(oi.quantity * oi.unitPrice) " +
                "FROM OrderItem oi " +
                "JOIN Product p ON oi.productID = p.productID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        sql += " GROUP BY c.categoryID, c.categoryName";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataPoint<>(rs.getString(1), rs.getDouble(2)));
            }
        }
        return list;
    }

    /**
     * Top N products by quantity sold in the date range. Optional category filter.
     */
    public List<TopProductRow> getTopProductsByQuantity(LocalDate from, LocalDate to, String category, int limit) throws SQLException {
        String sql = "SELECT p.name, SUM(oi.quantity) AS qty, SUM(oi.quantity * oi.unitPrice) AS revenue " +
                "FROM OrderItem oi JOIN Product p ON oi.productID = p.productID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        if (hasCategoryFilter(category)) sql += " AND (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')) ";
        sql += " GROUP BY p.productID ORDER BY qty DESC LIMIT " + Math.max(1, Math.min(limit, 20));
        List<TopProductRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            int rank = 1;
            while (rs.next()) {
                list.add(new TopProductRow(rank++, rs.getString(1), rs.getInt(2), rs.getDouble(3)));
            }
        }
        return list;
    }

    public static final class TopProductRow {
        public final int rank;
        public final String name;
        public final int quantitySold;
        public final double revenue;

        public TopProductRow(int rank, String name, int quantitySold, double revenue) {
            this.rank = rank;
            this.name = name;
            this.quantitySold = quantitySold;
            this.revenue = revenue;
        }
    }

    /**
     * Products with stock below threshold (uses Product.stock).
     */
    public List<String> getLowStockAlerts(int threshold) throws SQLException {
        String sql = "SELECT name || ' (stock: ' || stock || ')' FROM Product WHERE stock < ? AND status = 'active' ORDER BY stock";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    /**
     * Overdue: orders with status not Cancelled/Completed and no successful payment, order date older than 30 days.
     */
    public List<String> getOverduePaymentAlerts() throws SQLException {
        String sql = "SELECT 'Order #' || o.orderID || ': $' || COALESCE(ROUND(o.totalAmount, 2), 0) || ' outstanding' " +
                "FROM \"Order\" o " +
                "WHERE o.status NOT IN ('Cancelled', 'Completed') " +
                "AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.orderID = o.orderID AND p.paymentStatus = 'SUCCESS') " +
                "AND date(o.orderDate) <= date('now', '-30 days')";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    private static boolean hasCategoryFilter(String category) {
        return category != null && !category.isBlank() && !"All Categories".equalsIgnoreCase(category.trim());
    }

    private double querySingleDoubleWithCategory(String sql, LocalDate from, LocalDate to, String category) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    private int querySingleIntWithCategory(String sql, LocalDate from, LocalDate to, String category) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = bindDateFilter(ps, 1, from, to);
            if (hasCategoryFilter(category)) { ps.setString(idx++, category); ps.setString(idx++, category); }
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static final class DataPoint<X, Y> {
        public final X x;
        public final Y y;

        public DataPoint(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }

    private String appendDateFilter(String sql, String column, LocalDate from, LocalDate to) {
        if (from != null) sql += " AND " + column + " >= ?";
        if (to != null) sql += " AND " + column + " <= ?";
        return sql;
    }

    /** Binds from/to date params and returns the next parameter index. */
    private int bindDateFilter(PreparedStatement ps, int startIdx, LocalDate from, LocalDate to) throws SQLException {
        int idx = startIdx;
        if (from != null) ps.setString(idx++, from + " 00:00:00");
        if (to != null) ps.setString(idx++, to + " 23:59:59");
        return idx;
    }
}
