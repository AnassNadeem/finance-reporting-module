package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class InventorySupplierDao implements InventorySupplierDaoInterface {

    public record SupplierSnapshot(String name, String contact, double leadDays, double reliabilityScore) {}
    public record LowStockSnapshot(String productName, String categoryName, int currentStock, int reorderLevel) {}

    public List<SupplierSnapshot> findSuppliers() throws Exception {
        String sql = "SELECT name, contact, avgLeadDays, reliabilityScore FROM Supplier ORDER BY name";
        List<SupplierSnapshot> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new SupplierSnapshot(
                    rs.getString("name"),
                    rs.getString("contact"),
                    rs.getDouble("avgLeadDays"),
                    rs.getDouble("reliabilityScore")
                ));
            }
        }
        return rows;
    }

    public double getCurrentStockValue() throws Exception {
        String sql = "SELECT COALESCE(SUM(ir.quantityOnHand * COALESCE(ir.unitCost, p.unitCost, 0)), 0) " +
            "FROM InventoryRecord ir JOIN Product p ON p.productID = ir.productID";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }
    }

    public List<LowStockSnapshot> findLowStockItems() throws Exception {
        String sql = "SELECT p.name, COALESCE(c.categoryName, 'Uncategorized') AS categoryName, " +
            "ir.quantityOnHand, ir.minStockThreshold " +
            "FROM InventoryRecord ir " +
            "JOIN Product p ON p.productID = ir.productID " +
            "LEFT JOIN Category c ON c.categoryID = p.categoryID " +
            "WHERE ir.quantityOnHand <= ir.minStockThreshold " +
            "ORDER BY ir.quantityOnHand ASC, p.name ASC";
        List<LowStockSnapshot> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new LowStockSnapshot(
                    rs.getString("name"),
                    rs.getString("categoryName"),
                    rs.getInt("quantityOnHand"),
                    rs.getInt("minStockThreshold")
                ));
            }
        }
        return rows;
    }
}
