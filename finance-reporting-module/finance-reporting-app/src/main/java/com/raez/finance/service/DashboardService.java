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
 */
public class DashboardService {

    /**
     * Total sales = SUM of successful payments.
     */
    public double getTotalSales(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amountPaid), 0) FROM Payment WHERE paymentStatus = 'SUCCESS'";
        sql = appendDateFilter(sql, "paymentDate", from, to);
        return querySingleDouble(sql, from, to);
    }

    /**
     * Total profit = Total sales - Refunds (simplified; no cost table).
     */
    public double getTotalProfit(LocalDate from, LocalDate to) throws SQLException {
        double sales = getTotalSales(from, to);
        double refunds = getRefunds(from, to);
        return Math.max(0, sales - refunds);
    }

    /**
     * Outstanding = SUM(Order.totalAmount) for orders that have no successful payment.
     */
    public double getOutstandingPayments(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(o.totalAmount), 0) FROM \"Order\" o " +
                "WHERE o.status NOT IN ('Cancelled') " +
                "AND NOT EXISTS (SELECT 1 FROM Payment p WHERE p.orderID = o.orderID AND p.paymentStatus = 'SUCCESS')";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        return querySingleDouble(sql, from, to);
    }

    /**
     * Total refund amount (all statuses for display).
     */
    public double getRefunds(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(refundAmount), 0) FROM Refund WHERE 1=1";
        sql = appendDateFilter(sql, "refundDate", from, to);
        return querySingleDouble(sql, from, to);
    }

    public int getTotalCustomers() throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerRegistration";
        return querySingleInt(sql, null, null);
    }

    public int getTotalOrders(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COUNT(*) FROM \"Order\" WHERE 1=1";
        sql = appendDateFilter(sql, "orderDate", from, to);
        return querySingleInt(sql, from, to);
    }

    /**
     * Average order value = SUM(totalAmount) / COUNT(*) for orders in range.
     */
    public double getAverageOrderValue(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(totalAmount), 0) / NULLIF(COUNT(*), 0) FROM \"Order\" WHERE 1=1";
        sql = appendDateFilter(sql, "orderDate", from, to);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    /**
     * Most popular product by total quantity sold (or revenue if tied).
     */
    public String getMostPopularProductName(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT p.name FROM OrderItem oi " +
                "JOIN Product p ON oi.productID = p.productID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        sql += " GROUP BY p.productID ORDER BY SUM(oi.quantity * oi.unitPrice) DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "—";
        }
    }

    /**
     * Time series: date (string) -> total sales amount for that day. For LineChart.
     */
    public List<DataPoint<String, Number>> getSalesTimeSeries(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT DATE(o.orderDate) AS d, COALESCE(SUM(o.totalAmount), 0) " +
                "FROM \"Order\" o WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        sql += " GROUP BY d ORDER BY d";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataPoint<>(rs.getString(1), rs.getDouble(2)));
            }
        }
        return list;
    }

    /**
     * Category revenue for PieChart: category name -> total revenue (OrderItem quantity * unitPrice).
     */
    public List<DataPoint<String, Number>> getCategoryRevenue(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(c.categoryName, 'Uncategorized'), SUM(oi.quantity * oi.unitPrice) " +
                "FROM OrderItem oi " +
                "JOIN Product p ON oi.productID = p.productID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID WHERE 1=1 ";
        sql = appendDateFilter(sql, "o.orderDate", from, to);
        sql += " GROUP BY c.categoryID, c.categoryName";
        List<DataPoint<String, Number>> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new DataPoint<>(rs.getString(1), rs.getDouble(2)));
            }
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

    private String appendDateFilter(String sql, String column, LocalDate from, LocalDate to) {
        if (from != null) sql += " AND " + column + " >= ?";
        if (to != null) sql += " AND " + column + " <= ?";
        return sql;
    }

    private void bindDateFilter(PreparedStatement ps, int idxFrom, int idxTo, LocalDate from, LocalDate to) throws SQLException {
        int idx = 1;
        if (from != null) ps.setString(idx++, from + " 00:00:00");
        if (to != null) ps.setString(idx++, to + " 23:59:59");
    }

    private double querySingleDouble(String sql, LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    private int querySingleInt(String sql, LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindDateFilter(ps, 1, 2, from, to);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
