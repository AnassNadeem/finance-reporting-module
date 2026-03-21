package com.raez.finance.dao;

import com.raez.finance.service.GlobalSettingsService;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RevenueVatDao implements RevenueVatDaoInterface {

    public record CategoryVatRow(String category, int orders, double gross, double vat, double cogs) {}

    public List<CategoryVatRow> findCategoryVatRows(LocalDate from, LocalDate to) throws Exception {
        double vatPct = GlobalSettingsService.getInstance().getDefaultVatPercent();
        // Line totals are VAT-inclusive; VAT portion = gross * (vatPct / (100 + vatPct))
        String sql =
            "SELECT COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
            "COUNT(DISTINCT o.orderID) AS ordersCount, " +
            "COALESCE(SUM(oi.quantity * oi.unitPrice), 0) AS grossAmount, " +
            "COALESCE(SUM(oi.quantity * oi.unitPrice * (? / (100.0 + ?))), 0) AS vatAmount, " +
            "COALESCE(SUM(oi.quantity * COALESCE(p.unitCost, 0)), 0) AS cogsAmount " +
            "FROM OrderItem oi " +
            "JOIN Product p ON p.productID = oi.productID " +
            "LEFT JOIN Category c ON c.categoryID = p.categoryID " +
            "JOIN \"Order\" o ON o.orderID = oi.orderID " +
            "WHERE o.orderDate >= ? AND o.orderDate <= ? " +
            "GROUP BY c.categoryID, c.categoryName " +
            "ORDER BY grossAmount DESC";

        List<CategoryVatRow> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, vatPct);
            ps.setDouble(2, vatPct);
            ps.setString(3, from + " 00:00:00");
            ps.setString(4, to + " 23:59:59");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CategoryVatRow(
                        rs.getString("categoryName"),
                        rs.getInt("ordersCount"),
                        rs.getDouble("grossAmount"),
                        rs.getDouble("vatAmount"),
                        rs.getDouble("cogsAmount")
                    ));
                }
            }
        }
        return rows;
    }
}
