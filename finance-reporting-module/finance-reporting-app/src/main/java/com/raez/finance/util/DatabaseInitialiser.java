package com.raez.finance.util;

import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Comprehensive database initialiser for the RAEZ Finance application.
 * Creates all tables, adds missing columns, seeds realistic data, and verifies integrity.
 *
 * Run standalone:
 *   mvn -DskipTests compile exec:java "-Dexec.mainClass=com.raez.finance.util.DatabaseInitialiser"
 *
 * Or called automatically by DBConnection on first launch (when FUser table is absent).
 */
public class DatabaseInitialiser {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final double[] PRODUCT_PRICES = {
        4200.00, 5100.00, 4300.00, 3900.00, 172000.00, 268000.00,
        68000.00, 61000.00, 890.00, 6100.00, 1800.00, 950.00
    };

    private static final double[] PRODUCT_COSTS = {
        1800.00, 2900.00, 3800.00, 2200.00, 98000.00, 155000.00,
        62000.00, 38000.00, 320.00, 5200.00, 400.00, 550.00
    };

    public static void main(String[] args) {
        try {
            initialise();
            System.out.println("[DatabaseInitialiser] Complete — all tables created and seeded.");
        } catch (Exception e) {
            System.err.println("[DatabaseInitialiser] FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void initialise() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            exec(conn, "PRAGMA foreign_keys = OFF");

            applySchemaFile(conn);
            addMissingColumns(conn);
            createIndexes(conn);
            runIntegrityCheck(conn);

            if (needsSeeding(conn)) {
                System.out.println("[DatabaseInitialiser] Fresh database detected — seeding all data...");
                clearAllData(conn);
                seedAllData(conn);
            } else {
                System.out.println("[DatabaseInitialiser] Seed data already present — skipping.");
            }

            exec(conn, "PRAGMA foreign_keys = ON");
            verifyData(conn);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCHEMA
    // ═══════════════════════════════════════════════════════════════════

    private static void applySchemaFile(Connection conn) throws Exception {
        try (InputStream is = DatabaseInitialiser.class.getResourceAsStream("/database/schema.sql")) {
            if (is == null) throw new IllegalStateException("schema.sql not found on classpath");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            }
            for (String raw : sb.toString().split(";")) {
                String sql = stripComments(raw).trim();
                if (!sql.isEmpty()) {
                    try { conn.createStatement().execute(sql); } catch (SQLException ignored) {}
                }
            }
        }
        System.out.println("[DatabaseInitialiser] schema.sql applied.");
    }

    private static void addMissingColumns(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "Product", "unitCost", "REAL DEFAULT 0");
        addColumnIfMissing(conn, "FUser", "staffId", "TEXT");
        addColumnIfMissing(conn, "FUser", "addressLine1", "TEXT");
        addColumnIfMissing(conn, "FUser", "addressLine2", "TEXT");
        addColumnIfMissing(conn, "FUser", "addressLine3", "TEXT");
        addColumnIfMissing(conn, "FUser", "firstLogin", "INTEGER DEFAULT 0");
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (!rs.next()) {
                exec(conn, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                System.out.println("[DatabaseInitialiser] Added column " + table + "." + column);
            }
        }
    }

    private static void createIndexes(Connection conn) {
        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_order_date ON \"Order\"(orderDate)",
            "CREATE INDEX IF NOT EXISTS idx_order_customer ON \"Order\"(customerID)",
            "CREATE INDEX IF NOT EXISTS idx_orderitem_product ON OrderItem(productID)",
            "CREATE INDEX IF NOT EXISTS idx_customer_type ON CustomerRegistration(customerType)",
            "CREATE INDEX IF NOT EXISTS idx_product_category ON Product(categoryID)",
            "CREATE INDEX IF NOT EXISTS idx_payment_order ON Payment(orderID)",
            "CREATE INDEX IF NOT EXISTS idx_payment_status ON Payment(paymentStatus)",
            "CREATE INDEX IF NOT EXISTS idx_refund_order ON Refund(orderID)",
            "CREATE INDEX IF NOT EXISTS idx_invoice_order ON Invoice(orderID)",
            "CREATE INDEX IF NOT EXISTS idx_invoice_status ON Invoice(status)"
        };
        for (String sql : indexes) {
            try { conn.createStatement().execute(sql); } catch (SQLException ignored) {}
        }
    }

    private static void runIntegrityCheck(Connection conn) {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
            if (rs.next()) {
                String result = rs.getString(1);
                System.out.println("[DatabaseInitialiser] Integrity check: " +
                    ("ok".equalsIgnoreCase(result) ? "OK" : "FAILED — " + result));
            }
        } catch (SQLException e) {
            System.err.println("[DatabaseInitialiser] Integrity check error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEEDING CHECK
    // ═══════════════════════════════════════════════════════════════════

    private static boolean needsSeeding(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM Product WHERE name = 'XV2 Scout Drone'")) {
            return !rs.next() || rs.getInt(1) == 0;
        } catch (SQLException e) {
            return true;
        }
    }

    private static void clearAllData(Connection conn) throws SQLException {
        String[] tables = {
            "Alert", "FinancialAnomalies", "Refund", "Invoice", "Payment",
            "PasswordResetToken", "password_reset_tokens", "DeliveryLog", "Delivery",
            "OrderItem", "\"Order\"", "Review", "StockMovement", "InventoryRecord",
            "ProductImage", "ProductValidation", "Product", "Category",
            "CustomerPreferences", "CustomerUpdate", "LoginCredentials", "CustomerRegistration",
            "FUser", "AdminUser", "Driver", "Warehouse", "Supplier", "GlobalSettings",
            "role_permissions"
        };
        for (String t : tables) {
            try { exec(conn, "DELETE FROM " + t); } catch (SQLException ignored) {}
        }
        try { exec(conn, "DELETE FROM sqlite_sequence"); } catch (SQLException ignored) {}
        System.out.println("[DatabaseInitialiser] All tables cleared.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEED ALL DATA
    // ═══════════════════════════════════════════════════════════════════

    private static void seedAllData(Connection conn) throws SQLException {
        LocalDate now = LocalDate.now();

        seedCategories(conn);
        seedProducts(conn);
        seedCustomers(conn);
        seedUsers(conn);
        seedSuppliers(conn);
        seedWarehouse(conn);
        seedOrders(conn, now);
        seedPayments(conn, now);
        seedInvoices(conn, now);
        seedRefunds(conn, now);
        seedReviews(conn, now);
        seedAlerts(conn);
        seedAnomalies(conn);
        seedInventory(conn);
        seedSettings(conn);
        seedRolePermissions(conn);
        seedMiscData(conn);

        System.out.println("[DatabaseInitialiser] All seed data inserted.");
    }

    // ───────────────────────────────────────────────────────────────────
    // 1. CATEGORIES
    // ───────────────────────────────────────────────────────────────────

    private static void seedCategories(Connection conn) throws SQLException {
        String sql = "INSERT INTO Category (categoryID, categoryName, description) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            cat(ps, 1, "Drones", "Unmanned aerial vehicles and components");
            cat(ps, 2, "Robots", "Autonomous robotic systems and units");
            cat(ps, 3, "Accessories", "Add-ons, parts, and upgrade kits");
            cat(ps, 4, "Services", "Installation, maintenance, and support services");
        }
        log("Category", 4);
    }

    private static void cat(PreparedStatement ps, int id, String name, String desc) throws SQLException {
        ps.setInt(1, id); ps.setString(2, name); ps.setString(3, desc);
        ps.executeUpdate();
    }

    // ───────────────────────────────────────────────────────────────────
    // 2. PRODUCTS (12 products with realistic margins)
    // ───────────────────────────────────────────────────────────────────

    private static void seedProducts(Connection conn) throws SQLException {
        String sql = "INSERT INTO Product (productID, name, description, price, unitCost, stock, status, categoryID) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'active', ?)";
        Object[][] products = {
            {1,  "XV2 Scout Drone",          "High-performance scout drone for surveillance and mapping",    4200.00, 1800.00, 24, 1},
            {2,  "S9 Surveillance Drone",    "Advanced surveillance drone with 4K camera system",           5100.00, 2900.00, 18, 1},
            {3,  "T7 Thermal Imaging Drone", "Thermal imaging drone for industrial inspections",            4300.00, 3800.00,  2, 1},
            {4,  "V9 Inspection Drone",      "Compact inspection drone for confined spaces",                3900.00, 2200.00, 15, 1},
            {5,  "AR7 Industrial Robot",     "Heavy-duty industrial robotic arm for manufacturing",       172000.00,98000.00,  7, 2},
            {6,  "M4 Manufacturing Robot",   "Full-scale manufacturing robot with AI-assisted operation", 268000.00,155000.00, 9, 2},
            {7,  "P3 Delivery Robot",        "Autonomous delivery robot for warehouse logistics",          68000.00,62000.00,  1, 2},
            {8,  "C5 Cleaning Robot",        "Commercial cleaning robot with multi-surface capability",    61000.00,38000.00, 12, 2},
            {9,  "Drone Sensor Upgrade Kit", "Universal sensor upgrade kit for all drone models",            890.00,  320.00, 45, 3},
            {10, "Robot Arms Extension Kit", "Extension arms kit compatible with AR7 and M4 robots",        6100.00, 5200.00,  0, 3},
            {11, "Installation & Setup",     "Professional installation, calibration, and setup service",    1800.00,  400.00,100, 4},
            {12, "Annual Maintenance Plan",  "12-month maintenance and support contract",                    950.00,  550.00, 88, 4},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] p : products) {
                ps.setInt(1, (int) p[0]);
                ps.setString(2, (String) p[1]);
                ps.setString(3, (String) p[2]);
                ps.setDouble(4, (double) p[3]);
                ps.setDouble(5, (double) p[4]);
                ps.setInt(6, (int) p[5]);
                ps.setInt(7, (int) p[6]);
                ps.executeUpdate();
            }
        }
        log("Product", 12);
    }

    // ───────────────────────────────────────────────────────────────────
    // 3. CUSTOMERS (6 companies + 8 individuals = 14)
    // ───────────────────────────────────────────────────────────────────

    private static void seedCustomers(Connection conn) throws SQLException {
        String sql = "INSERT INTO CustomerRegistration (customerID, name, email, contactNumber, deliveryAddress, customerType, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        Object[][] customers = {
            {1,  "TechCorp Industries",  "procurement@techcorp.com",     "+1 555 100 2001", "New York, US",      "Company",    "active"},
            {2,  "RoboManufacture Ltd",  "orders@robomanufacture.co.uk", "+44 20 7946 0001","London, UK",        "Company",    "active"},
            {3,  "GlobalTech Solutions", "finance@globaltech.ae",        "+971 4 300 1000", "Dubai, UAE",         "Company",    "active"},
            {4,  "AutoMate Systems",     "purchasing@automate.cn",       "+86 10 6500 1234","Beijing, China",     "Company",    "active"},
            {5,  "InnovateCo",           "accounts@innovateco.com",      "+1 555 200 3002", "San Francisco, US",  "Company",    "active"},
            {6,  "SmartFactory Inc",     "ops@smartfactory.co.uk",       "+44 121 496 0002","Birmingham, UK",     "Company",    "inactive"},
            {7,  "John Smith",           "john.smith@email.com",         "+1 555 300 4001", "Chicago, US",        "Individual", "active"},
            {8,  "Sarah Johnson",        "sarah.j@email.com",            "+44 20 7946 0003","Manchester, UK",     "Individual", "active"},
            {9,  "David Lee",            "david.lee@email.com",          "+971 4 300 2000", "Abu Dhabi, UAE",     "Individual", "active"},
            {10, "Michael Chen",         "mchen@personal.com",           "+86 21 6100 5678","Shanghai, China",    "Individual", "active"},
            {11, "Emily Rodriguez",      "emily.r@email.com",            "+1 555 400 5001", "Los Angeles, US",    "Individual", "active"},
            {12, "Anna Williams",        "anna.w@email.com",             "+44 161 200 0004","Leeds, UK",          "Individual", "active"},
            {13, "James Brown",          "jbrown@email.com",             "+1 555 500 6001", "Houston, US",        "Individual", "active"},
            {14, "Lisa Zhang",           "lzhang@personal.com",          "+86 755 8000 1234","Shenzhen, China",   "Individual", "inactive"},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] c : customers) {
                ps.setInt(1, (int) c[0]);
                ps.setString(2, (String) c[1]);
                ps.setString(3, (String) c[2]);
                ps.setString(4, (String) c[3]);
                ps.setString(5, (String) c[4]);
                ps.setString(6, (String) c[5]);
                ps.setString(7, (String) c[6]);
                ps.executeUpdate();
            }
        }
        log("CustomerRegistration", 14);
    }

    // ───────────────────────────────────────────────────────────────────
    // 4. USERS (5 finance users with BCrypt passwords)
    // ───────────────────────────────────────────────────────────────────

    private static void seedUsers(Connection conn) throws SQLException {
        String sql = "INSERT INTO FUser (userID, email, username, passwordHash, role, firstName, lastName, " +
                     "phone, isActive, lastLogin, staffId, addressLine1, addressLine2, addressLine3, firstLogin) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // jcarter — ADMIN, active, not first login
            user(ps, 1, "j.carter@raez.org.uk", "jcarter", hash("Admin123!"), "ADMIN",
                 "James", "Carter", "+44 7700 900001", true, "2026-03-01 09:00:00",
                 "RAEZ-001", "14 Ashford Road", "London", "EC1A 1BB", false);

            // smitchell — ADMIN, active, not first login
            user(ps, 2, "s.mitchell@raez.org.uk", "smitchell", hash("Admin456!"), "ADMIN",
                 "Sarah", "Mitchell", "+44 7700 900002", true, "2026-03-10 14:30:00",
                 "RAEZ-002", "7 Maple Close", "Manchester", "M1 2AB", false);

            // dhughes — FINANCE_USER, active, not first login
            user(ps, 3, "d.hughes@raez.org.uk", "dhughes", hash("User123!"), "FINANCE_USER",
                 "Daniel", "Hughes", "+44 7700 900003", true, "2026-03-12 11:15:00",
                 "RAEZ-003", "22 Victoria Street", "Birmingham", "B1 1BB", false);

            // psharma — FINANCE_USER, active, FIRST LOGIN (lastLogin=NULL, firstLogin=1)
            user(ps, 4, "p.sharma@raez.org.uk", "psharma", hash("TempPass1!"), "FINANCE_USER",
                 "Priya", "Sharma", "+44 7700 900004", true, null,
                 "RAEZ-004", "9 Queens Avenue", "Leeds", "LS1 4AP", true);

            // obennett — FINANCE_USER, INACTIVE, not first login
            user(ps, 5, "o.bennett@raez.org.uk", "obennett", hash("User456!"), "FINANCE_USER",
                 "Oliver", "Bennett", "+44 7700 900005", false, "2025-12-20 16:00:00",
                 "RAEZ-005", "31 Harbour Lane", "Bristol", "BS1 5TR", false);
        }
        log("FUser", 5);
    }

    private static void user(PreparedStatement ps, int id, String email, String username, String pwHash,
                             String role, String first, String last, String phone, boolean active,
                             String lastLogin, String staffId, String addr1, String addr2, String addr3,
                             boolean firstLogin) throws SQLException {
        ps.setInt(1, id);
        ps.setString(2, email);
        ps.setString(3, username);
        ps.setString(4, pwHash);
        ps.setString(5, role);
        ps.setString(6, first);
        ps.setString(7, last);
        ps.setString(8, phone);
        ps.setInt(9, active ? 1 : 0);
        if (lastLogin != null) ps.setString(10, lastLogin); else ps.setNull(10, Types.VARCHAR);
        ps.setString(11, staffId);
        ps.setString(12, addr1);
        ps.setString(13, addr2);
        ps.setString(14, addr3);
        ps.setInt(15, firstLogin ? 1 : 0);
        ps.executeUpdate();
    }

    // ───────────────────────────────────────────────────────────────────
    // 5. SUPPLIERS (5 suppliers)
    // ───────────────────────────────────────────────────────────────────

    private static void seedSuppliers(Connection conn) throws SQLException {
        String sql = "INSERT INTO Supplier (supplierID, name, contact, email, avgLeadDays, reliabilityScore) VALUES (?, ?, ?, ?, ?, ?)";
        Object[][] suppliers = {
            {1, "AeroTech Components",  "Mike Reynolds",  "m.reynolds@aerotech.com",    7.0, 0.96},
            {2, "RoboCore Supplies",    "Lisa Tanaka",    "l.tanaka@robocore.jp",       12.0, 0.88},
            {3, "DronePartsHub",        "Carlos Mendez",  "c.mendez@dronepartshub.com", 4.0, 0.94},
            {4, "SmartKit Europe",      "Hannah Muller",  "h.muller@smartkit.de",        9.0, 0.72},
            {5, "TechServe UK",         "Raj Patel",      "r.patel@techserve.co.uk",     3.0, 0.98},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] s : suppliers) {
                ps.setInt(1, (int) s[0]);
                ps.setString(2, (String) s[1]);
                ps.setString(3, (String) s[2]);
                ps.setString(4, (String) s[3]);
                ps.setDouble(5, (double) s[4]);
                ps.setDouble(6, (double) s[5]);
                ps.executeUpdate();
            }
        }
        log("Supplier", 5);
    }

    // ───────────────────────────────────────────────────────────────────
    // 6. WAREHOUSE (for InventoryRecord FK)
    // ───────────────────────────────────────────────────────────────────

    private static void seedWarehouse(Connection conn) throws SQLException {
        exec(conn, "INSERT INTO Warehouse (warehouseID, warehouseName, location, contactEmail, capacityLimit) " +
                   "VALUES (1, 'Main Warehouse', 'London, UK', 'warehouse@raez.org.uk', 500)");
        log("Warehouse", 1);
    }

    // ───────────────────────────────────────────────────────────────────
    // 7. ORDERS (73 orders across 14 months)
    // ───────────────────────────────────────────────────────────────────

    private static void seedOrders(Connection conn, LocalDate now) throws SQLException {
        String sqlOrder = "INSERT INTO \"Order\" (orderID, customerID, totalAmount, status, orderDate) VALUES (?, ?, ?, ?, ?)";
        String sqlItem  = "INSERT INTO OrderItem (orderID, productID, quantity, unitPrice) VALUES (?, ?, ?, ?)";

        try (PreparedStatement psO = conn.prepareStatement(sqlOrder);
             PreparedStatement psI = conn.prepareStatement(sqlItem)) {

            // Month -14 (5 orders)
            order(psO, psI, 1,  1,  d(now,14,5),  "Completed", items(5,1));
            order(psO, psI, 2,  5,  d(now,14,12), "Completed", items(6,1));
            order(psO, psI, 3,  7,  d(now,14,18), "Completed", items(2,1));
            order(psO, psI, 4,  2,  d(now,14,22), "Cancelled", items(8,1));
            order(psO, psI, 5,  8,  d(now,14,26), "Pending",   items(11,1));

            // Month -13 (5 orders)
            order(psO, psI, 6,  3,  d(now,13,3),  "Completed", items(5,2));
            order(psO, psI, 7,  11, d(now,13,8),  "Completed", items(4,1));
            order(psO, psI, 8,  9,  d(now,13,14), "Completed", items(9,1));
            order(psO, psI, 9,  12, d(now,13,20), "Pending",   items(12,2));
            order(psO, psI, 10, 4,  d(now,13,25), "Completed", items(8,1, 10,2));

            // Month -12 (5 orders)
            order(psO, psI, 11, 1,  d(now,12,2),  "Completed", items(6,2));
            order(psO, psI, 12, 6,  d(now,12,7),  "Completed", items(6,1));
            order(psO, psI, 13, 10, d(now,12,12), "Completed", items(2,1));
            order(psO, psI, 14, 2,  d(now,12,18), "Completed", items(1,2, 9,3));
            order(psO, psI, 15, 13, d(now,12,24), "Pending",   items(9,2));

            // Month -11 (5 orders)
            order(psO, psI, 16, 5,  d(now,11,4),  "Completed", items(4,2));
            order(psO, psI, 17, 7,  d(now,11,9),  "Completed", items(1,1));
            order(psO, psI, 18, 3,  d(now,11,15), "Pending",   items(5,2));
            order(psO, psI, 19, 8,  d(now,11,21), "Completed", items(12,1));
            order(psO, psI, 20, 14, d(now,11,27), "Completed", items(12,1));

            // Month -10 (5 orders)
            order(psO, psI, 21, 4,  d(now,10,3),  "Completed", items(11,3));
            order(psO, psI, 22, 1,  d(now,10,8),  "Completed", items(1,3));
            order(psO, psI, 23, 11, d(now,10,14), "Completed", items(11,1, 12,1));
            order(psO, psI, 24, 2,  d(now,10,20), "Completed", items(6,1));
            order(psO, psI, 25, 6,  d(now,10,26), "Cancelled", items(1,1));

            // Month -9 (5 orders)
            order(psO, psI, 26, 4,  d(now,9,2),   "Refunded",  items(5,1));
            order(psO, psI, 27, 9,  d(now,9,8),   "Completed", items(4,1));
            order(psO, psI, 28, 3,  d(now,9,14),  "Completed", items(8,2));
            order(psO, psI, 29, 10, d(now,9,20),  "Pending",   items(1,1));
            order(psO, psI, 30, 12, d(now,9,26),  "Pending",   items(9,1));

            // Month -8 (5 orders)
            order(psO, psI, 31, 1,  d(now,8,3),   "Completed", items(2,2));
            order(psO, psI, 32, 5,  d(now,8,8),   "Completed", items(1,2));
            order(psO, psI, 33, 7,  d(now,8,15),  "Completed", items(4,1, 9,1));
            order(psO, psI, 34, 14, d(now,8,20),  "Cancelled", items(10,1));
            order(psO, psI, 35, 2,  d(now,8,25),  "Cancelled", items(7,1));

            // Month -7 (5 orders)
            order(psO, psI, 36, 8,  d(now,7,2),   "Completed", items(1,1));
            order(psO, psI, 37, 3,  d(now,7,8),   "Completed", items(1,5, 2,3));
            order(psO, psI, 38, 4,  d(now,7,14),  "Completed", items(8,1));
            order(psO, psI, 39, 11, d(now,7,20),  "Pending",   items(4,1));
            order(psO, psI, 40, 6,  d(now,7,26),  "Completed", items(8,1, 10,3));

            // Month -6 (5 orders)
            order(psO, psI, 41, 1,  d(now,6,2),   "Completed", items(5,1));
            order(psO, psI, 42, 12, d(now,6,8),   "Pending",   items(12,2));
            order(psO, psI, 43, 14, d(now,6,14),  "Cancelled", items(12,1));
            order(psO, psI, 44, 2,  d(now,6,20),  "Completed", items(2,1));
            order(psO, psI, 45, 13, d(now,6,26),  "Completed", items(12,1));

            // Month -5 (6 orders)
            order(psO, psI, 46, 1,  d(now,5,3),   "Completed", items(1,2));
            order(psO, psI, 47, 7,  d(now,5,9),   "Completed", items(4,1));
            order(psO, psI, 48, 5,  d(now,5,15),  "Completed", items(5,1));
            order(psO, psI, 49, 10, d(now,5,21),  "Cancelled", items(3,1));
            order(psO, psI, 50, 3,  d(now,5,27),  "Completed", items(2,2));
            order(psO, psI, 51, 9,  d(now,5,12),  "Completed", items(9,1));

            // Month -4 (6 orders)
            order(psO, psI, 52, 5,  d(now,4,2),   "Refunded",  items(8,1));
            order(psO, psI, 53, 2,  d(now,4,7),   "Completed", items(8,1));
            order(psO, psI, 54, 8,  d(now,4,13),  "Completed", items(9,2));
            order(psO, psI, 55, 4,  d(now,4,19),  "Pending",   items(5,1));
            order(psO, psI, 56, 13, d(now,4,25),  "Completed", items(12,1));
            order(psO, psI, 57, 12, d(now,4,10),  "Completed", items(1,1));

            // Month -3 (6 orders)
            order(psO, psI, 58, 5,  d(now,3,2),   "Refunded",  items(11,1));
            order(psO, psI, 59, 3,  d(now,3,7),   "Completed", items(7,2));
            order(psO, psI, 60, 1,  d(now,3,12),  "Completed", items(6,1, 11,2));
            order(psO, psI, 61, 11, d(now,3,18),  "Completed", items(1,1));
            order(psO, psI, 62, 6,  d(now,3,24),  "Cancelled", items(7,1));
            order(psO, psI, 63, 10, d(now,3,10),  "Completed", items(4,1));

            // Month -2 (6 orders)
            order(psO, psI, 64, 5,  d(now,2,2),   "Refunded",  items(12,1));
            order(psO, psI, 65, 2,  d(now,2,7),   "Completed", items(2,3, 4,2));
            order(psO, psI, 66, 9,  d(now,2,12),  "Completed", items(11,1));
            order(psO, psI, 67, 4,  d(now,2,18),  "Cancelled", items(6,1));
            order(psO, psI, 68, 8,  d(now,2,24),  "Pending",   items(2,1));
            order(psO, psI, 69, 13, d(now,2,8),   "Completed", items(9,1));

            // Month -1 (5 orders)
            order(psO, psI, 70, 3,  d(now,1,2),   "Refunded",  items(6,1));
            order(psO, psI, 71, 7,  d(now,1,7),   "Refunded",  items(2,1));
            order(psO, psI, 72, 11, d(now,1,12),  "Refunded",  items(9,1));
            order(psO, psI, 73, 1,  d(now,1,18),  "Completed", items(5,1, 1,2));
        }

        log("Order", 73);
        System.out.println("[DatabaseInitialiser]   OrderItem rows inserted for all orders.");
    }

    // ───────────────────────────────────────────────────────────────────
    // 8. PAYMENTS
    // ───────────────────────────────────────────────────────────────────

    private static void seedPayments(Connection conn, LocalDate now) throws SQLException {
        String sql = "INSERT INTO Payment (orderID, amountPaid, paymentMethod, paymentStatus, transactionRef, paymentDate) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        int payCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT orderID, totalAmount, status, orderDate FROM \"Order\" ORDER BY orderID")) {
            while (rs.next()) {
                int oid = rs.getInt("orderID");
                double amount = rs.getDouble("totalAmount");
                String status = rs.getString("status");
                String orderDate = rs.getString("orderDate");

                String payStatus;
                switch (status) {
                    case "Completed": payStatus = "SUCCESS"; break;
                    case "Refunded":  payStatus = "SUCCESS"; break;
                    case "Pending":   payStatus = "PENDING"; break;
                    default: continue;
                }

                String method = amount > 50000 ? "BANK" : "CARD";
                ps.setInt(1, oid);
                ps.setDouble(2, amount);
                ps.setString(3, method);
                ps.setString(4, payStatus);
                ps.setString(5, String.format("TXN-%04d", oid));
                ps.setString(6, orderDate);
                ps.executeUpdate();
                payCount++;
            }
        }
        log("Payment", payCount);
    }

    // ───────────────────────────────────────────────────────────────────
    // 9. INVOICES (12 invoices)
    // ───────────────────────────────────────────────────────────────────

    private static void seedInvoices(Connection conn, LocalDate now) throws SQLException {
        String sql = "INSERT INTO Invoice (invoiceID, orderID, invoiceNumber, status, totalAmount, vatAmount, " +
                     "currency, issuedAt, dueDate, paidAt, notes) VALUES (?, ?, ?, ?, ?, ?, 'GBP', ?, ?, ?, ?)";
        double vatRate = 0.20;

        Object[][] invoices = {
            // {id, orderID, status, total, dueDateOffset(days from now, negative=past), paidOffset, notes}
            {1,  1,  "PAID",      172000.00, -30,  -32,  "Paid on time"},
            {2,  24, "PAID",      268000.00, -45,  -42,  "Paid 3 days late"},
            {3,  18, "PENDING",   344000.00, +15,  null, "Due soon"},
            {4,  38, "OVERDUE",    61000.00, -10,  null, "Missed payment — follow up"},
            {5,  48, "OVERDUE",   172000.00, -25,  null, "Long overdue"},
            {6,  3,  "PAID",        5100.00, -60,  -62,  "Individual — paid promptly"},
            {7,  5,  "PENDING",     1800.00,  +7,  null, "Individual — services invoice"},
            {8,  8,  "PAID",         890.00, -90,  -92,  "Small accessories order"},
            {9,  11, "OVERDUE",   536000.00,  -5,  null, "Large order, payment delayed"},
            {10, 61, "PENDING",     4200.00, +20,  null, "Drone order"},
            {11, 12, "PAID",      268000.00,-120, -122,  "Historical — company now inactive"},
            {12, 49, "CANCELLED",      0.00,   0,  null, "Invoice cancelled before issue"},
        };

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] inv : invoices) {
                int id = (int) inv[0];
                int orderId = (int) inv[1];
                String status = (String) inv[2];
                double total = (double) inv[3];
                int dueDaysOffset = (int) inv[4];
                Integer paidOffset = (Integer) inv[5];
                String notes = (String) inv[6];

                double vat = total / (1 + vatRate) * vatRate;
                String invoiceNum = String.format("INV-2025-%04d", id);
                String issuedAt = now.minusDays(Math.abs(dueDaysOffset) + 30).format(ISO);
                String dueDate = now.plusDays(dueDaysOffset).format(ISO);
                String paidAt = paidOffset != null ? now.plusDays(paidOffset).format(ISO) : null;

                ps.setInt(1, id);
                ps.setInt(2, orderId);
                ps.setString(3, invoiceNum);
                ps.setString(4, status);
                ps.setDouble(5, total);
                ps.setDouble(6, Math.round(vat * 100.0) / 100.0);
                ps.setString(7, issuedAt);
                ps.setString(8, dueDate);
                if (paidAt != null) ps.setString(9, paidAt); else ps.setNull(9, Types.VARCHAR);
                ps.setString(10, notes);
                ps.executeUpdate();
            }
        }
        log("Invoice", 12);
    }

    // ───────────────────────────────────────────────────────────────────
    // 10. REFUNDS (8 refunds, 3 for InnovateCo to trigger alerts)
    // ───────────────────────────────────────────────────────────────────

    private static void seedRefunds(Connection conn, LocalDate now) throws SQLException {
        String sql = "INSERT INTO Refund (orderID, productID, refundAmount, reason, status, refundDate, processedBy) " +
                     "VALUES (?, ?, ?, ?, 'APPROVED', ?, 1)";
        Object[][] refunds = {
            // {orderID, productID, amount, reason, daysAgo}
            {26, 5,  172000.00, "Product fault reported by AutoMate Systems",      240},
            {52, 8,   61000.00, "Delivered damaged — InnovateCo",                   90},
            {58, 11,   1800.00, "Service not delivered — InnovateCo",                60},
            {64, 12,    950.00, "Duplicate order — InnovateCo",                      30},
            {46, 1,    4200.00, "Changed mind — TechCorp Industries",                21},
            {71, 2,    5100.00, "Product fault — John Smith",                        14},
            {72, 9,     890.00, "Wrong item delivered — Emily Rodriguez",              7},
            {70, 6,  268000.00, "Contract cancelled — GlobalTech Solutions",           4},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : refunds) {
                ps.setInt(1, (int) r[0]);
                ps.setInt(2, (int) r[1]);
                ps.setDouble(3, (double) r[2]);
                ps.setString(4, (String) r[3]);
                ps.setString(5, now.minusDays((int) r[4]).format(ISO));
                ps.executeUpdate();
            }
        }
        log("Refund", 8);
    }

    // ───────────────────────────────────────────────────────────────────
    // 11. REVIEWS (10 reviews, 4 with rating ≤ 2)
    // ───────────────────────────────────────────────────────────────────

    private static void seedReviews(Connection conn, LocalDate now) throws SQLException {
        String sql = "INSERT INTO Review (productID, customerID, rating, reviewText, reviewDate) VALUES (?, ?, ?, ?, ?)";
        Object[][] reviews = {
            {3,  2,  1, "Drone overheats after 10 minutes of flight, completely unusable for inspections",         60},
            {3,  7,  2, "Poor thermal accuracy, not worth the premium price point compared to competitors",        42},
            {7,  4,  1, "Delivery robot broke down within the first week of deployment in our warehouse",          90},
            {10, 5,  2, "Arms extension kit does not fit the AR7 model as advertised — poor quality control",      30},
            {1,  11, 5, "Excellent scout drone, very reliable in all weather conditions — highly recommended",      35},
            {5,  1,  5, "AR7 exceeded all expectations, fantastic ROI within the first quarter of operation",       21},
            {6,  2,  4, "M4 manufacturing robot performing well after 3 months of continuous operation",            14},
            {11, 8,  5, "Installation team was extremely professional, completed ahead of schedule",                 7},
            {9,  13, 4, "Sensor upgrade kit works great with our XV2 fleet, easy to install and calibrate",          4},
            {12, 7,  3, "Maintenance plan is acceptable but response time for urgent issues could be faster",        2},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : reviews) {
                ps.setInt(1, (int) r[0]);
                ps.setInt(2, (int) r[1]);
                ps.setInt(3, (int) r[2]);
                ps.setString(4, (String) r[3]);
                ps.setString(5, now.minusDays((int) r[4]).format(ISO));
                ps.executeUpdate();
            }
        }
        log("Review", 10);
    }

    // ───────────────────────────────────────────────────────────────────
    // 12. ALERTS (6 alerts)
    // ───────────────────────────────────────────────────────────────────

    private static void seedAlerts(Connection conn) throws SQLException {
        String sql = "INSERT INTO Alert (alertType, severity, message, entityType, entityID, isResolved, createdAt) " +
                     "VALUES (?, ?, ?, ?, ?, ?, datetime('now', ?))";
        Object[][] alerts = {
            {"High Refund Rate",  "CRITICAL", "InnovateCo has triggered 3 refunds in 30 days — immediate review needed",  "CUSTOMER", 5, 0, "-2 days"},
            {"Low Profit Margin", "HIGH",     "T7 Thermal Imaging Drone margin is 11.6% — below 15% threshold",          "PRODUCT",  3, 0, "-3 days"},
            {"Low Profit Margin", "HIGH",     "P3 Delivery Robot margin is 8.8% — below 15% threshold",                  "PRODUCT",  7, 0, "-3 days"},
            {"Overdue Payment",   "WARNING",  "AutoMate Systems invoice INV-2025-0004 overdue by 10 days",                "INVOICE",  4, 0, "-1 day"},
            {"Low Stock",         "WARNING",  "P3 Delivery Robot stock at 1 unit — reorder level is 5",                   "PRODUCT",  7, 0, "-4 days"},
            {"System Backup",     "INFO",     "Scheduled database backup completed successfully",                         "SYSTEM",   0, 1, "-7 days"},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] a : alerts) {
                ps.setString(1, (String) a[0]);
                ps.setString(2, (String) a[1]);
                ps.setString(3, (String) a[2]);
                ps.setString(4, (String) a[3]);
                ps.setInt(5, (int) a[4]);
                ps.setInt(6, (int) a[5]);
                ps.setString(7, (String) a[6]);
                ps.executeUpdate();
            }
        }
        log("Alert", 6);
    }

    // ───────────────────────────────────────────────────────────────────
    // 13. FINANCIAL ANOMALIES (4 anomalies)
    // ───────────────────────────────────────────────────────────────────

    private static void seedAnomalies(Connection conn) throws SQLException {
        String sql = "INSERT INTO FinancialAnomalies (anomalyType, description, severity, detectionRule, " +
                     "alertDate, isResolved, affectedCustomerFK, affectedProductFK) " +
                     "VALUES (?, ?, ?, ?, datetime('now', ?), ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            anomaly(ps, "Unusual Refund Pattern", "Customer InnovateCo — 3 refunds totalling £63,750 in last 30 days",
                    "CRITICAL", "REFUND_COUNT_GT_2", "-1 day", 0, 5, null);
            anomaly(ps, "Revenue Drop", "Drones category revenue down 23% vs previous 30-day period",
                    "HIGH", "CATEGORY_REVENUE_DROP", "-2 days", 0, null, null);
            anomaly(ps, "Margin Compression", "Average margin across Robots category fell from 41% to 37% this quarter",
                    "WARNING", "MARGIN_BELOW_THRESHOLD", "-5 days", 0, null, null);
            anomaly(ps, "Inactive Customer Spend", "SmartFactory Inc (inactive) still has 1 unpaid invoice outstanding",
                    "INFO", "INACTIVE_CUSTOMER_OUTSTANDING", "-10 days", 1, 6, null);
        }
        log("FinancialAnomalies", 4);
    }

    private static void anomaly(PreparedStatement ps, String type, String desc, String severity,
                                String rule, String dateOffset, int resolved,
                                Integer custFK, Integer prodFK) throws SQLException {
        ps.setString(1, type);
        ps.setString(2, desc);
        ps.setString(3, severity);
        ps.setString(4, rule);
        ps.setString(5, dateOffset);
        ps.setInt(6, resolved);
        if (custFK != null) ps.setInt(7, custFK); else ps.setNull(7, Types.INTEGER);
        if (prodFK != null) ps.setInt(8, prodFK); else ps.setNull(8, Types.INTEGER);
        ps.executeUpdate();
    }

    // ───────────────────────────────────────────────────────────────────
    // 14. INVENTORY (InventoryRecord — one per product)
    // ───────────────────────────────────────────────────────────────────

    private static void seedInventory(Connection conn) throws SQLException {
        String sql = "INSERT INTO InventoryRecord (warehouseID, productID, quantityOnHand, minStockThreshold, reorderQuantity) " +
                     "VALUES (1, ?, ?, ?, ?)";
        int[][] inv = {
            // {productID, currentStock, reorderLevel, reorderQty}
            {1,  24, 10, 20},
            {2,  18, 10, 15},
            {3,   2, 10, 15},    // LOW — triggers alert
            {4,  15, 10, 12},
            {5,   7,  5,  3},
            {6,   9,  5,  3},
            {7,   1,  5,  5},    // CRITICAL — triggers alert
            {8,  12,  8,  5},
            {9,  45, 20, 30},
            {10,  0,  3,  5},    // OUT OF STOCK — triggers alert
            {11,100, 10, 20},
            {12, 88, 10, 20},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int[] row : inv) {
                ps.setInt(1, row[0]);
                ps.setInt(2, row[1]);
                ps.setInt(3, row[2]);
                ps.setInt(4, row[3]);
                ps.executeUpdate();
            }
        }
        log("InventoryRecord", 12);
    }

    // ───────────────────────────────────────────────────────────────────
    // 15. GLOBAL SETTINGS
    // ───────────────────────────────────────────────────────────────────

    private static void seedSettings(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO GlobalSettings (key, value) VALUES (?, ?)";
        String[][] settings = {
            {"vat_rate",                "20.0"},
            {"company_name",            "Raez Finance & Reporting Ltd"},
            {"company_address_line1",   "100 Canary Wharf"},
            {"company_address_line2",   "London"},
            {"company_address_line3",   "E14 5AB"},
            {"company_email",           "finance@raez.org.uk"},
            {"company_phone",           "+44 20 7946 0000"},
            {"currency_symbol",         "\u00A3"},
            {"date_format",             "dd/MM/yyyy"},
            {"session_timeout_minutes", "2"},
            {"low_margin_threshold",    "15.0"},
            {"high_refund_threshold",   "10.0"},
            {"app_version",             "1.0.0"},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] kv : settings) {
                ps.setString(1, kv[0]);
                ps.setString(2, kv[1]);
                ps.executeUpdate();
            }
        }
        log("GlobalSettings", settings.length);
    }

    // ───────────────────────────────────────────────────────────────────
    // 16. ROLE PERMISSIONS
    // ───────────────────────────────────────────────────────────────────

    private static void seedRolePermissions(Connection conn) throws SQLException {
        String sql = "INSERT OR IGNORE INTO role_permissions (role, action) VALUES (?, ?)";
        String[][] perms = {
            {"ADMIN", "VIEW_DASHBOARD"},
            {"ADMIN", "MANAGE_FINANCE_DATA"},
            {"ADMIN", "EXPORT_REPORTS"},
            {"ADMIN", "MANAGE_USERS"},
            {"ADMIN", "VIEW_COMPANY_FINANCIALS"},
            {"ADMIN", "VIEW_USER_MANAGEMENT"},
            {"ADMIN", "CREATE_ALERTS"},
            {"ADMIN", "MANAGE_INVOICES"},
            {"FINANCE_USER", "VIEW_DASHBOARD"},
            {"FINANCE_USER", "MANAGE_FINANCE_DATA"},
            {"FINANCE_USER", "VIEW_REPORTS"},
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] p : perms) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.executeUpdate();
            }
        }
        log("role_permissions", perms.length);
    }

    // ───────────────────────────────────────────────────────────────────
    // 17. MISC (LoginCredentials, AdminUser, Drivers for existing FKs)
    // ───────────────────────────────────────────────────────────────────

    private static void seedMiscData(Connection conn) throws SQLException {
        // LoginCredentials for each customer
        String credSql = "INSERT INTO LoginCredentials (customerID, passwordHash) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(credSql)) {
            for (int i = 1; i <= 14; i++) {
                ps.setInt(1, i);
                ps.setString(2, hash("Customer" + i + "!"));
                ps.executeUpdate();
            }
        }

        // AdminUser row (needed by CustomerUpdate FK)
        exec(conn, "INSERT INTO AdminUser (adminID, name, email) VALUES (1, 'System Admin', 'admin@raez.org.uk')");

        // Drivers (for Delivery FK)
        exec(conn, "INSERT INTO Driver (driverID, driverName, phoneNum, email) VALUES (1, 'Ali Khan', '+44 7700 800001', 'a.khan@raez.org.uk')");
        exec(conn, "INSERT INTO Driver (driverID, driverName, phoneNum, email) VALUES (2, 'Emma Taylor', '+44 7700 800002', 'e.taylor@raez.org.uk')");
    }

    // ═══════════════════════════════════════════════════════════════════
    // VERIFICATION
    // ═══════════════════════════════════════════════════════════════════

    private static void verifyData(Connection conn) throws SQLException {
        System.out.println("\n[DatabaseInitialiser] ═══ DATA VERIFICATION ═══");

        // Table row counts
        String[] tables = {"Category", "Product", "CustomerRegistration", "FUser", "\"Order\"",
                           "OrderItem", "Payment", "Invoice", "Refund", "Review",
                           "Alert", "FinancialAnomalies", "Supplier", "InventoryRecord", "GlobalSettings"};
        for (String t : tables) {
            int count = queryInt(conn, "SELECT COUNT(*) FROM " + t);
            String name = t.replace("\"", "");
            if (count == 0) {
                System.err.println("  [FAIL] " + name + ": 0 rows!");
            } else {
                System.out.println("  [OK]   " + name + ": " + count + " rows");
            }
        }

        // Revenue in all 4 categories
        int catRevCount = queryInt(conn,
            "SELECT COUNT(DISTINCT c.categoryName) FROM OrderItem oi " +
            "JOIN Product p ON oi.productID = p.productID " +
            "JOIN Category c ON p.categoryID = c.categoryID");
        System.out.println("  [" + (catRevCount >= 4 ? "OK" : "FAIL") + "]   Revenue categories: " + catRevCount);

        // Order status mix
        int statusCount = queryInt(conn, "SELECT COUNT(DISTINCT status) FROM \"Order\"");
        System.out.println("  [" + (statusCount >= 4 ? "OK" : "FAIL") + "]   Distinct order statuses: " + statusCount);

        // Low stock items
        int lowStock = queryInt(conn, "SELECT COUNT(*) FROM Product WHERE stock <= 3");
        System.out.println("  [" + (lowStock >= 3 ? "OK" : "FAIL") + "]   Low stock products: " + lowStock);

        // Low-rated reviews
        int lowRated = queryInt(conn, "SELECT COUNT(*) FROM Review WHERE rating <= 2");
        System.out.println("  [" + (lowRated >= 4 ? "OK" : "FAIL") + "]   Low-rated reviews: " + lowRated);

        // Refunds triggering alert (customer with ≥3 refunds)
        int refundAlert = queryInt(conn,
            "SELECT COUNT(*) FROM (SELECT o.customerID, COUNT(*) AS cnt FROM Refund r " +
            "JOIN \"Order\" o ON r.orderID = o.orderID GROUP BY o.customerID HAVING cnt >= 3)");
        System.out.println("  [" + (refundAlert >= 1 ? "OK" : "FAIL") + "]   Customers with 3+ refunds: " + refundAlert);

        // Invoice status mix
        int invStatuses = queryInt(conn, "SELECT COUNT(DISTINCT status) FROM Invoice WHERE status != 'CANCELLED'");
        System.out.println("  [" + (invStatuses >= 3 ? "OK" : "FAIL") + "]   Invoice statuses (excl cancelled): " + invStatuses);

        // Monthly order distribution
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                "SELECT strftime('%Y-%m', orderDate) AS month, COUNT(*) AS cnt " +
                "FROM \"Order\" GROUP BY month ORDER BY month")) {
            boolean allGood = true;
            while (rs.next()) {
                if (rs.getInt("cnt") < 4) {
                    System.err.println("  [WARN] Month " + rs.getString("month") + " has only " + rs.getInt("cnt") + " orders");
                    allGood = false;
                }
            }
            if (allGood) System.out.println("  [OK]   All months have >= 4 orders");
        }

        // User email validation
        int badEmails = queryInt(conn, "SELECT COUNT(*) FROM FUser WHERE email NOT LIKE '%@raez.org.uk'");
        System.out.println("  [" + (badEmails == 0 ? "OK" : "FAIL") + "]   Users with non-@raez.org.uk emails: " + badEmails);

        System.out.println("[DatabaseInitialiser] ═══ VERIFICATION COMPLETE ═══\n");
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private static void order(PreparedStatement psO, PreparedStatement psI,
                              int orderId, int custId, String date, String status, int[][] items) throws SQLException {
        double total = 0;
        for (int[] it : items) {
            total += PRODUCT_PRICES[it[0] - 1] * it[1];
        }
        psO.setInt(1, orderId);
        psO.setInt(2, custId);
        psO.setDouble(3, total);
        psO.setString(4, status);
        psO.setString(5, date);
        psO.executeUpdate();

        for (int[] it : items) {
            psI.setInt(1, orderId);
            psI.setInt(2, it[0]);
            psI.setInt(3, it[1]);
            psI.setDouble(4, PRODUCT_PRICES[it[0] - 1]);
            psI.executeUpdate();
        }
    }

    private static int[][] items(int... args) {
        int[][] result = new int[args.length / 2][2];
        for (int i = 0; i < args.length; i += 2) {
            result[i / 2] = new int[]{args[i], args[i + 1]};
        }
        return result;
    }

    private static String d(LocalDate now, int monthsAgo, int day) {
        LocalDate month = now.minusMonths(monthsAgo);
        int safeDay = Math.min(day, month.lengthOfMonth());
        return month.withDayOfMonth(safeDay).format(ISO);
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    private static void exec(Connection conn, String sql) throws SQLException {
        try (Statement s = conn.createStatement()) { s.execute(sql); }
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void log(String table, int count) {
        System.out.println("[DatabaseInitialiser]   " + table + ": " + count + " rows seeded");
    }

    private static String stripComments(String block) {
        StringBuilder out = new StringBuilder();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }
}
