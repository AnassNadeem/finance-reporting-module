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

    private static final String CATEGORY_EXISTS_FOR_ORDER =
        " AND (? IS NULL OR EXISTS ( " +
        "SELECT 1 FROM OrderItem oi3 " +
        "JOIN Product p3 ON oi3.productID = p3.productID " +
        "LEFT JOIN Category c3 ON p3.categoryID = c3.categoryID " +
        "WHERE oi3.orderID = o.orderID " +
        "AND (c3.categoryName = ? OR (c3.categoryName IS NULL AND ? = 'Uncategorized')) " +
        ")) ";

    /**
     * Total sales = SUM of successful payments. Optional category filter (by order items).
     */
    public double getTotalSales(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(p.amountPaid), 0) " +
            "FROM Payment p JOIN \"Order\" o ON p.orderID = o.orderID " +
            "WHERE p.paymentStatus = 'SUCCESS' " +
            "AND (? IS NULL OR p.paymentDate >= ?) " +
            "AND (? IS NULL OR p.paymentDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    public double getTotalProfit(LocalDate from, LocalDate to, String category) throws SQLException {
        double sales = getTotalSales(from, to, category);
        double refunds = getRefunds(from, to, category);
        return Math.max(0, sales - refunds);
    }

    public double getOutstandingPayments(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(o.totalAmount), 0) " +
            "FROM \"Order\" o " +
            "WHERE o.status NOT IN ('Cancelled') " +
            "AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.orderID = o.orderID AND p.paymentStatus = 'SUCCESS') " +
            "AND (? IS NULL OR o.orderDate >= ?) " +
            "AND (? IS NULL OR o.orderDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    public double getRefunds(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(r.refundAmount), 0) " +
            "FROM Refund r JOIN \"Order\" o ON r.orderID = o.orderID " +
            "WHERE (? IS NULL OR r.refundDate >= ?) " +
            "AND (? IS NULL OR r.refundDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /**
     * Approximate COGS for orders in range. The current schema does not store product cost,
     * so this uses a simple heuristic (60% of line revenue) to provide a consistent value
     * for dashboards; detailed profitability is handled by mock data.
     */
    public double getTotalCogs(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(oi.quantity * COALESCE(p2.unitCost, oi.unitPrice * 0.6)), 0) " +
                "FROM OrderItem oi JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "JOIN Product p2 ON oi.productID = p2.productID " +
                "LEFT JOIN Category c ON p2.categoryID = c.categoryID " +
                "WHERE (? IS NULL OR o.orderDate >= ?) " +
                "AND (? IS NULL OR o.orderDate <= ?) " +
                "AND (? IS NULL OR (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized')))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForJoin(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    /**
     * Total VAT collected from successful payments in period (gross amounts converted using global VAT rate).
     */
    public double getTotalVatCollected(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT p.amountPaid FROM Payment p JOIN \"Order\" o ON p.orderID = o.orderID " +
            "WHERE p.paymentStatus = 'SUCCESS' " +
            "AND (? IS NULL OR p.paymentDate >= ?) " +
            "AND (? IS NULL OR p.paymentDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        List<Double> amounts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
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
        String sql = "SELECT COUNT(DISTINCT o.orderID) FROM \"Order\" o " +
            "WHERE (? IS NULL OR o.orderDate >= ?) " +
            "AND (? IS NULL OR o.orderDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public double getAverageOrderValue(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT COALESCE(SUM(o.totalAmount), 0) / NULLIF(COUNT(DISTINCT o.orderID), 0) " +
            "FROM \"Order\" o " +
            "WHERE (? IS NULL OR o.orderDate >= ?) " +
            "AND (? IS NULL OR o.orderDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    public String getMostPopularProductName(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT p.name FROM OrderItem oi " +
                "JOIN Product p ON oi.productID = p.productID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                "WHERE (? IS NULL OR o.orderDate >= ?) " +
                "AND (? IS NULL OR o.orderDate <= ?) " +
                "AND (? IS NULL OR (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized'))) " +
                "GROUP BY p.productID ORDER BY SUM(oi.quantity) DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForJoin(ps, 5, category);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "—";
        }
    }

    /**
     * Time series: formatted date string -> total sales. Uses short date format to avoid clustering.
     */
    public List<DataPoint<String, Number>> getSalesTimeSeries(LocalDate from, LocalDate to, String category) throws SQLException {
        String sql = "SELECT DATE(o.orderDate) AS d, COALESCE(SUM(o.totalAmount), 0) " +
            "FROM \"Order\" o " +
            "WHERE (? IS NULL OR o.orderDate >= ?) " +
            "AND (? IS NULL OR o.orderDate <= ?) " +
            CATEGORY_EXISTS_FOR_ORDER +
            "GROUP BY d ORDER BY d";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForOrder(ps, 5, category);
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
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "WHERE (? IS NULL OR o.orderDate >= ?) " +
                "AND (? IS NULL OR o.orderDate <= ?) " +
                "AND (? IS NULL OR (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized'))) " +
                "GROUP BY c.categoryID, c.categoryName";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForJoin(ps, 5, category);
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
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                "WHERE (? IS NULL OR o.orderDate >= ?) " +
                "AND (? IS NULL OR o.orderDate <= ?) " +
                "AND (? IS NULL OR (c.categoryName = ? OR (c.categoryName IS NULL AND ? = 'Uncategorized'))) " +
                "GROUP BY p.productID ORDER BY qty DESC LIMIT ?";
        List<TopProductRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDate(ps, 1, from, to);
            bindCategoryForJoin(ps, 5, category);
            ps.setInt(8, Math.max(1, Math.min(limit, 20)));
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

    public static final class DataPoint<X, Y> {
        public final X x;
        public final Y y;

        public DataPoint(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }

    private void bindDate(PreparedStatement ps, int start, LocalDate from, LocalDate to) throws SQLException {
        String fromParam = from == null ? null : from + " 00:00:00";
        String toParam = to == null ? null : to + " 23:59:59";
        ps.setString(start, fromParam);
        ps.setString(start + 1, fromParam);
        ps.setString(start + 2, toParam);
        ps.setString(start + 3, toParam);
    }

    private void bindCategoryForOrder(PreparedStatement ps, int start, String category) throws SQLException {
        String value = normalizeCategory(category);
        ps.setString(start, value);
        ps.setString(start + 1, value);
        ps.setString(start + 2, value);
    }

    private void bindCategoryForJoin(PreparedStatement ps, int start, String category) throws SQLException {
        String value = normalizeCategory(category);
        ps.setString(start, value);
        ps.setString(start + 1, value);
        ps.setString(start + 2, value);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || "All Categories".equalsIgnoreCase(category.trim())) {
            return null;
        }
        return category.trim();
    }
}
