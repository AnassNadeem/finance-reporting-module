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
 */
public class ProductDao implements ProductDaoInterface {

    private static final String FIND_SQL =
        "SELECT p.productID, p.name, " +
        "COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
        "COALESCE(p.unitCost, 0) AS unitCost, " +
        "p.price AS salePrice, " +
        "COALESCE(SUM(CASE WHEN (? IS NULL OR o2.orderDate >= ?) AND (? IS NULL OR o2.orderDate <= ?) " +
        "THEN oi2.quantity * oi2.unitPrice ELSE 0 END), 0) AS revenue, " +
        "COALESCE(SUM(CASE WHEN (? IS NULL OR o2.orderDate >= ?) AND (? IS NULL OR o2.orderDate <= ?) " +
        "THEN oi2.quantity ELSE 0 END), 0) AS unitsSold " +
        "FROM Product p " +
        "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
        "LEFT JOIN OrderItem oi2 ON oi2.productID = p.productID " +
        "LEFT JOIN \"Order\" o2 ON oi2.orderID = o2.orderID " +
        "WHERE (? IS NULL OR c.categoryName = ?) " +
        "AND (? IS NULL OR (p.name LIKE ? OR CAST(p.productID AS TEXT) LIKE ?)) " +
        "GROUP BY p.productID, p.name, c.categoryName, p.unitCost, p.price " +
        "ORDER BY revenue DESC " +
        "LIMIT ? OFFSET ?";

    private static final String FIND_NO_LIMIT_SQL =
        "SELECT p.productID, p.name, " +
        "COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
        "COALESCE(p.unitCost, 0) AS unitCost, " +
        "p.price AS salePrice, " +
        "COALESCE(SUM(CASE WHEN (? IS NULL OR o2.orderDate >= ?) AND (? IS NULL OR o2.orderDate <= ?) " +
        "THEN oi2.quantity * oi2.unitPrice ELSE 0 END), 0) AS revenue, " +
        "COALESCE(SUM(CASE WHEN (? IS NULL OR o2.orderDate >= ?) AND (? IS NULL OR o2.orderDate <= ?) " +
        "THEN oi2.quantity ELSE 0 END), 0) AS unitsSold " +
        "FROM Product p " +
        "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
        "LEFT JOIN OrderItem oi2 ON oi2.productID = p.productID " +
        "LEFT JOIN \"Order\" o2 ON oi2.orderID = o2.orderID " +
        "WHERE (? IS NULL OR c.categoryName = ?) " +
        "AND (? IS NULL OR (p.name LIKE ? OR CAST(p.productID AS TEXT) LIKE ?)) " +
        "GROUP BY p.productID, p.name, c.categoryName, p.unitCost, p.price " +
        "ORDER BY revenue DESC";

    private static final String COUNT_SQL =
        "SELECT COUNT(*) " +
        "FROM Product p " +
        "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
        "WHERE (? IS NULL OR c.categoryName = ?) " +
        "AND (? IS NULL OR (p.name LIKE ? OR CAST(p.productID AS TEXT) LIKE ?))";

    // ══════════════════════════════════════════════════════════════════════
    //  FIND (with optional pagination)
    // ══════════════════════════════════════════════════════════════════════

    /** Fetch product rows without pagination (limit <= 0 = no limit). */
    public List<ProductReportRow> findReportRows(
            LocalDate from, LocalDate to,
            String categoryFilter, String search) throws SQLException {
        return findReportRows(from, to, categoryFilter, search, 0, 0);
    }

    /** Fetch product rows with optional pagination. Use limit <= 0 for no limit. */
    public List<ProductReportRow> findReportRows(
            LocalDate from, LocalDate to,
            String categoryFilter, String search,
            int limit, int offset) throws SQLException {

        List<ProductReportRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(limit > 0 ? FIND_SQL : FIND_NO_LIMIT_SQL)) {
            bindFindParams(ps, from, to, categoryFilter, search, limit, offset, limit > 0);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double revenue   = rs.getDouble("revenue");
                int    unitsSold = rs.getInt("unitsSold");
                double unitCost  = rs.getDouble("unitCost");
                double totalCost = unitCost * unitsSold;
                double profit    = revenue - totalCost;
                rows.add(new ProductReportRow(
                    String.valueOf(rs.getInt("productID")),
                    rs.getString("name"),
                    rs.getString("categoryName"),
                    unitCost,
                    rs.getDouble("salePrice"),
                    profit,
                    unitsSold,
                    revenue
                ));
            }
        }
        return rows;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  COUNT (for pagination)
    // ══════════════════════════════════════════════════════════════════════

    public int countReportRows(
            LocalDate from, LocalDate to,
            String categoryFilter, String search) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(COUNT_SQL)) {
            String category = normalizeCategory(categoryFilter);
            String term = normalizeSearch(search);
            ps.setString(1, category);
            ps.setString(2, category);
            ps.setString(3, term);
            ps.setString(4, term);
            ps.setString(5, term);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CATEGORY REVENUE / PROFIT (for Product Profitability chart)
    // ══════════════════════════════════════════════════════════════════════

    public List<CategoryRevenueProfit> findCategoryRevenueProfit() throws SQLException {
        return findCategoryRevenueProfit(null, null);
    }

    public List<CategoryRevenueProfit> findCategoryRevenueProfit(LocalDate from, LocalDate to) throws SQLException {
        String sql =
            "SELECT COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
            "SUM(oi.quantity * oi.unitPrice) AS revenue, " +
            "SUM((oi.quantity * oi.unitPrice) - (oi.quantity * COALESCE(p.unitCost, 0))) AS profit " +
            "FROM OrderItem oi " +
            "JOIN Product p ON oi.productID = p.productID " +
            "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
            "JOIN \"Order\" o ON oi.orderID = o.orderID " +
            "WHERE (? IS NULL OR o.orderDate >= ?) AND (? IS NULL OR o.orderDate <= ?) " +
            "GROUP BY c.categoryID, c.categoryName ORDER BY revenue DESC";

        String fromParam = from == null ? null : from + " 00:00:00";
        String toParam = to == null ? null : to + " 23:59:59";
        List<CategoryRevenueProfit> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromParam);
            ps.setString(2, fromParam);
            ps.setString(3, toParam);
            ps.setString(4, toParam);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CategoryRevenueProfit(
                        rs.getString("categoryName"),
                        rs.getDouble("revenue"),
                        rs.getDouble("profit")));
                }
            }
        }
        return list;
    }

    public static final class CategoryRevenueProfit {
        public final String category;
        public final double revenue;
        public final double profit;
        public CategoryRevenueProfit(String cat, double rev, double profit) {
            this.category = cat; this.revenue = rev; this.profit = profit;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CATEGORY NAMES (for filter ComboBox)
    // ══════════════════════════════════════════════════════════════════════

    public List<String> findCategoryNames() throws SQLException {
        String sql = "SELECT categoryName FROM Category ORDER BY categoryName";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("categoryName"));
        }
        return list;
    }

    private void bindFindParams(PreparedStatement ps, LocalDate from, LocalDate to, String categoryFilter, String search,
                                int limit, int offset, boolean withLimit) throws SQLException {
        String fromParam = from == null ? null : from + " 00:00:00";
        String toParam = to == null ? null : to + " 23:59:59";
        String category = normalizeCategory(categoryFilter);
        String term = normalizeSearch(search);

        int i = 1;
        // revenue date condition
        ps.setString(i++, fromParam);
        ps.setString(i++, fromParam);
        ps.setString(i++, toParam);
        ps.setString(i++, toParam);
        // units date condition
        ps.setString(i++, fromParam);
        ps.setString(i++, fromParam);
        ps.setString(i++, toParam);
        ps.setString(i++, toParam);
        // filters
        ps.setString(i++, category);
        ps.setString(i++, category);
        ps.setString(i++, term);
        ps.setString(i++, term);
        ps.setString(i++, term);
        if (withLimit) {
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
        }
    }

    private String normalizeCategory(String categoryFilter) {
        if (categoryFilter == null || categoryFilter.isBlank()) return null;
        if ("All Categories".equalsIgnoreCase(categoryFilter.trim())) return null;
        return categoryFilter.trim();
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        return "%" + search.trim() + "%";
    }
}