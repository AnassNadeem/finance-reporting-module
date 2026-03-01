package com.raez.finance.dao;

import com.raez.finance.model.ProductReportRow;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches product report rows for Detailed Reports (Product tab).
 * Revenue/cost/profit from OrderItem; Product has no cost column so cost is 0.
 */
public class ProductDao {

    /**
     * Fetch product aggregates: revenue = SUM(quantity*unitPrice), unitsSold = SUM(quantity).
     * Optional date range and category filter (category name).
     */
    public List<ProductReportRow> findReportRows(LocalDate from, LocalDate to, String categoryFilter, String search) throws SQLException {
        List<Object> params = new ArrayList<>();

        String orderJoin;
        if (from != null || to != null) {
            orderJoin = "LEFT JOIN (OrderItem oi2 JOIN \"Order\" o2 ON oi2.orderID = o2.orderID AND 1=1 ";
            if (from != null) { orderJoin += " AND o2.orderDate >= ?"; params.add(from + " 00:00:00"); }
            if (to != null)   { orderJoin += " AND o2.orderDate <= ?"; params.add(to + " 23:59:59"); }
            orderJoin += ") ON oi2.productID = p.productID ";
        } else {
            orderJoin = "LEFT JOIN OrderItem oi2 ON oi2.productID = p.productID ";
        }

        StringBuilder sql = new StringBuilder(
                "SELECT p.productID, p.name, COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
                "0 AS cost, p.price AS salePrice, " +
                "COALESCE(SUM(oi2.quantity * oi2.unitPrice), 0) AS revenue, " +
                "COALESCE(SUM(oi2.quantity), 0) AS unitsSold " +
                "FROM Product p " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                orderJoin +
                "WHERE 1=1 ");
        if (categoryFilter != null && !categoryFilter.isBlank() && !"All Categories".equalsIgnoreCase(categoryFilter.trim())) {
            sql.append(" AND c.categoryName = ?");
            params.add(categoryFilter.trim());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR CAST(p.productID AS TEXT) LIKE ?)");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
        }
        sql.append(" GROUP BY p.productID, p.name, c.categoryName, p.price ORDER BY revenue DESC");

        List<ProductReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double revenue = rs.getDouble("revenue");
                double cost = rs.getDouble("cost");
                double profit = revenue - cost;
                rows.add(new ProductReportRow(
                        String.valueOf(rs.getInt("productID")),
                        rs.getString("name"),
                        rs.getString("categoryName"),
                        cost,
                        rs.getDouble("salePrice"),
                        profit,
                        rs.getInt("unitsSold"),
                        revenue
                ));
            }
        }
        return rows;
    }

    /**
     * Revenue and profit by category for Product Profitability chart.
     * Profit = revenue - cost. Schema has no Product.cost column yet, so cost is 0 per unit.
     * When cost is added, use SUM(oi.quantity * COALESCE(p.cost, 0)) for cost.
     */
    public List<CategoryRevenueProfit> findCategoryRevenueProfit() throws SQLException {
        String sql = "SELECT COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
                "SUM(oi.quantity * oi.unitPrice) AS revenue, " +
                "0 AS cost, " +
                "SUM(oi.quantity * oi.unitPrice) - 0 AS profit " +
                "FROM OrderItem oi JOIN Product p ON oi.productID = p.productID " +
                "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
                "JOIN \"Order\" o ON oi.orderID = o.orderID " +
                "GROUP BY c.categoryID, c.categoryName ORDER BY revenue DESC";
        List<CategoryRevenueProfit> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double rev = rs.getDouble("revenue");
                double profit = rs.getDouble("profit");
                list.add(new CategoryRevenueProfit(rs.getString("categoryName"), rev, profit));
            }
        }
        return list;
    }

    public static final class CategoryRevenueProfit {
        public final String category;
        public final double revenue;
        public final double profit;
        public CategoryRevenueProfit(String category, double revenue, double profit) {
            this.category = category;
            this.revenue = revenue;
            this.profit = profit;
        }
    }

    /**
     * Distinct category names for the Category ComboBox (including "All Categories" in UI).
     */
    public List<String> findCategoryNames() throws SQLException {
        String sql = "SELECT categoryName FROM Category ORDER BY categoryName";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("categoryName"));
            }
        }
        return list;
    }
}
