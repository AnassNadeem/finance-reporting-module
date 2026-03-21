package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class InventorySupplierDao implements InventorySupplierDaoInterface {

    public record SupplierSnapshot(int supplierID, String name, String contact, String email,
                                   double leadDays, double reliabilityScore) {}
    public record LowStockSnapshot(String productName, String categoryName, int currentStock, int reorderLevel) {}

    public record ProductInventoryRow(
        int productID,
        String name,
        String category,
        int stockLevel,
        int reorderLevel,
        double unitCost,
        double salePrice,
        double marginPercent,
        String statusLabel
    ) {}

    public List<SupplierSnapshot> findSuppliers() throws Exception {
        String sql = "SELECT supplierID, name, contact, email, avgLeadDays, reliabilityScore FROM Supplier ORDER BY name";
        List<SupplierSnapshot> rows = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new SupplierSnapshot(
                    rs.getInt("supplierID"),
                    rs.getString("name"),
                    rs.getString("contact"),
                    rs.getString("email"),
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

    @Override
    public int countActiveProducts() throws Exception {
        String sql = "SELECT COUNT(*) FROM Product";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public int countSuppliers() throws Exception {
        String sql = "SELECT COUNT(*) FROM Supplier";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public int countLowStockProducts() throws Exception {
        String sql = "SELECT COUNT(*) FROM InventoryRecord ir WHERE ir.quantityOnHand <= ir.minStockThreshold";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public int countOutOfStockProducts() throws Exception {
        String sql = "SELECT COUNT(*) FROM InventoryRecord ir WHERE ir.quantityOnHand = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public List<ProductInventoryRow> findProductInventoryRows() throws Exception {
        String sql =
            "SELECT p.productID, p.name, COALESCE(c.categoryName, 'Uncategorized') AS cat, " +
            "ir.quantityOnHand, ir.minStockThreshold, COALESCE(p.unitCost, 0) AS uc, p.price AS sp " +
            "FROM Product p " +
            "LEFT JOIN Category c ON p.categoryID = c.categoryID " +
            "LEFT JOIN InventoryRecord ir ON ir.productID = p.productID " +
            "ORDER BY ir.quantityOnHand ASC, p.name ASC";
        List<ProductInventoryRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int stock = rs.getInt("quantityOnHand");
                int reorder = rs.getInt("minStockThreshold");
                double uc = rs.getDouble("uc");
                double sp = rs.getDouble("sp");
                double margin = sp > 0 ? (sp - uc) / sp * 100.0 : 0;
                String status;
                if (stock <= 0) status = "Out of Stock";
                else if (stock <= reorder) status = "Low Stock";
                else status = "In Stock";
                list.add(new ProductInventoryRow(
                    rs.getInt("productID"),
                    rs.getString("name"),
                    rs.getString("cat"),
                    stock,
                    reorder,
                    uc,
                    sp,
                    margin,
                    status
                ));
            }
        }
        return list;
    }
}
