package com.raez.finance.util;

import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Database initialiser for the RAEZ Finance application.
 * Net-First convention:
 * - Product.price and OrderItem.unitPrice are NET amounts (VAT excluded).
 * - Order.totalAmount and Invoice.totalAmount are GROSS amounts (NET + VAT).
 */
public class DatabaseInitialiser {

    private static final double VAT_RATE = 0.20;
    private static final int REQUIRED_PRODUCT_COUNT = 16;
    private static final int REQUIRED_CUSTOMER_COUNT = 24;
    private static final int REQUIRED_SUPPLIER_COUNT = 5;
    private static final int REQUIRED_ORDER_COUNT = 120;
    private static final int REQUIRED_INVOICE_COUNT = 10;
    private static final int REQUIRED_LOW_STOCK_COUNT = 3;
    private static final int REQUIRED_NEGATIVE_REVIEW_COUNT = 4;
    private static final int REQUIRED_REFUND_COUNT = 5;
    private static final int REQUIRED_ACTIVE_ALERT_COUNT = 3;

    private record ProductSeed(int id, String name, String description, double netPrice, double unitCost, int categoryId) {}
    private record CustomerSeed(int id, String name, String email, String phone, String address, String type) {}
    private record OrderSeed(int id, int customerId, String status, String orderDate, double grossTotal) {}

    public static void main(String[] args) {
        try {
            initialise();
            System.out.println("[DatabaseInitialiser] Complete.");
        } catch (Exception e) {
            System.err.println("[DatabaseInitialiser] FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void initialise() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            exec(conn, "PRAGMA foreign_keys = ON");
            applySchemaFile(conn);
            ensureCompatibilityColumns(conn);
            ensureCanonicalCompatibilityTables(conn);

            if (isInitialisationRequired(conn)) {
                exec(conn, "PRAGMA foreign_keys = OFF");
                clearAllData(conn);
                seedAllData(conn, LocalDate.now());
                exec(conn, "PRAGMA foreign_keys = ON");
            }
            verifyData(conn);
        }
    }

    public static boolean isInitialisationRequired(Connection conn) throws SQLException {
        if (!tableExists(conn, "FUser") || !tableExists(conn, "Product") || !tableExists(conn, "\"Order\"")) {
            return true;
        }
        if (!columnExists(conn, "Product", "unitCost")) {
            return true;
        }
        return isBelowThreshold(conn, "Product", REQUIRED_PRODUCT_COUNT)
            || isBelowThreshold(conn, "CustomerRegistration", REQUIRED_CUSTOMER_COUNT)
            || isBelowThreshold(conn, "Supplier", REQUIRED_SUPPLIER_COUNT)
            || isBelowThreshold(conn, "\"Order\"", REQUIRED_ORDER_COUNT)
            || isBelowThreshold(conn, "Invoice", REQUIRED_INVOICE_COUNT)
            || isBelowThreshold(conn, "Refund", REQUIRED_REFUND_COUNT)
            || isBelowThreshold(conn, "Review", REQUIRED_NEGATIVE_REVIEW_COUNT + 2)
            || isBelowThreshold(conn, "Alert", REQUIRED_ACTIVE_ALERT_COUNT);
    }

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
                    try (Statement st = conn.createStatement()) {
                        st.execute(sql);
                    }
                }
            }
        }
    }

    private static void ensureCompatibilityColumns(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "Product", "unitCost", "REAL DEFAULT 0");
        addColumnIfMissing(conn, "InventoryRecord", "supplierID", "INTEGER");
        addColumnIfMissing(conn, "InventoryRecord", "unitCost", "REAL DEFAULT 0");
        addColumnIfMissing(conn, "InventoryRecord", "isActive", "INTEGER DEFAULT 1");
        addColumnIfMissing(conn, "Invoice", "vatAmount", "REAL DEFAULT 0");
    }

    private static void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        if (columnExists(conn, table, column)) return;
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private static void ensureCanonicalCompatibilityTables(Connection conn) throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS roles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL)",
            "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE NOT NULL, username TEXT UNIQUE, password_hash TEXT NOT NULL, role_id INTEGER, is_active INTEGER DEFAULT 1, created_at TEXT DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(role_id) REFERENCES roles(id))",
            "CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT)",
            "CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT UNIQUE, customer_type TEXT, status TEXT DEFAULT 'active')",
            "CREATE TABLE IF NOT EXISTS suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT, contact TEXT)",
            "CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category_id INTEGER, sale_price REAL NOT NULL, unit_cost REAL DEFAULT 0, is_active INTEGER DEFAULT 1, FOREIGN KEY(category_id) REFERENCES categories(id))",
            "CREATE TABLE IF NOT EXISTS inventory (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, supplier_id INTEGER, current_stock INTEGER DEFAULT 0, reorder_level INTEGER DEFAULT 0, unit_cost REAL DEFAULT 0, FOREIGN KEY(product_id) REFERENCES products(id), FOREIGN KEY(supplier_id) REFERENCES suppliers(id))",
            "CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL, order_date TEXT NOT NULL, status TEXT NOT NULL, total_amount REAL NOT NULL, FOREIGN KEY(customer_id) REFERENCES customers(id))",
            "CREATE TABLE IF NOT EXISTS order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_price REAL NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(product_id) REFERENCES products(id))",
            "CREATE TABLE IF NOT EXISTS invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, invoice_number TEXT UNIQUE NOT NULL, status TEXT NOT NULL, total_amount REAL NOT NULL, vat_amount REAL DEFAULT 0, issued_at TEXT, due_date TEXT, paid_at TEXT, FOREIGN KEY(order_id) REFERENCES orders(id))",
            "CREATE TABLE IF NOT EXISTS alerts (id INTEGER PRIMARY KEY AUTOINCREMENT, severity TEXT, message TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP, is_resolved INTEGER DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS financial_anomalies (id INTEGER PRIMARY KEY AUTOINCREMENT, anomaly_type TEXT, description TEXT, severity TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP, is_resolved INTEGER DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS password_reset_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, token TEXT UNIQUE NOT NULL, expires_at TEXT NOT NULL, used INTEGER DEFAULT 0, FOREIGN KEY(user_id) REFERENCES users(id))",
            "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)",
            "CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER NOT NULL, product_id INTEGER NOT NULL, rating INTEGER NOT NULL, review_text TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP)",
            "CREATE TABLE IF NOT EXISTS refunds (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, product_id INTEGER, refund_amount REAL NOT NULL, reason TEXT, status TEXT DEFAULT 'APPROVED', refund_date TEXT DEFAULT CURRENT_TIMESTAMP)"
        };
        for (String sql : ddl) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        }
    }

    private static void clearAllData(Connection conn) throws SQLException {
        String[] tables = {
            "Alert", "FinancialAnomalies", "Refund", "Invoice", "Payment", "PasswordResetToken", "password_reset_tokens",
            "DeliveryLog", "Delivery", "OrderItem", "\"Order\"", "Review", "StockMovement", "InventoryRecord",
            "ProductImage", "ProductValidation", "Product", "Category", "CustomerPreferences", "CustomerUpdate",
            "LoginCredentials", "CustomerRegistration", "FUser", "AdminUser", "Driver", "Warehouse", "Supplier",
            "GlobalSettings", "role_permissions"
        };
        conn.setAutoCommit(false);
        for (String t : tables) {
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM " + t);
            } catch (SQLException ignored) {
                // ignore missing tables
            }
        }
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM sqlite_sequence");
        }
        conn.commit();
        conn.setAutoCommit(true);
    }

    private static void seedAllData(Connection conn, LocalDate today) throws SQLException {
        conn.setAutoCommit(false);
        try {
            seedSettings(conn);
            seedRolePermissions(conn);
            seedCategories(conn);
            List<ProductSeed> products = seedProducts(conn);
            List<CustomerSeed> customers = seedCustomers(conn);
            seedUsers(conn);
            seedSuppliers(conn);
            seedWarehouseAndDrivers(conn);
            seedInventory(conn, products);
            List<OrderSeed> orders = seedOrdersAndItems(conn, today, customers, products);
            seedPayments(conn, orders);
            seedInvoices(conn, today, orders);
            seedRefunds(conn, today, orders);
            seedReviews(conn, today, customers, products);
            seedAnomalies(conn, today);
            seedAlerts(conn, today);
            seedAuxiliaryRows(conn, customers);
            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void seedSettings(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO GlobalSettings (key, value) VALUES (?, ?)";
        String[][] settings = {
            {"vat_rate", "20.0"},
            {"company_name", "RAEZ Finance Ltd"},
            {"currency_symbol", "£"},
            {"session_timeout_minutes", "2"},
            {"low_stock_threshold", "5"}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] kv : settings) {
                ps.setString(1, kv[0]);
                ps.setString(2, kv[1]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedRolePermissions(Connection conn) throws SQLException {
        String sql = "INSERT OR IGNORE INTO role_permissions (role, action) VALUES (?, ?)";
        String[][] perms = {
            {"ADMIN", "VIEW_DASHBOARD"},
            {"ADMIN", "MANAGE_FINANCE_DATA"},
            {"ADMIN", "EXPORT_REPORTS"},
            {"ADMIN", "MANAGE_USERS"},
            {"ADMIN", "VIEW_COMPANY_FINANCIALS"},
            {"FINANCE_USER", "VIEW_DASHBOARD"},
            {"FINANCE_USER", "MANAGE_FINANCE_DATA"}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] p : perms) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedCategories(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Category (categoryID, categoryName, description, isActive) VALUES (?, ?, ?, 1)";
        String[][] categories = {
            {"1", "Drones", "Unmanned systems and aerial platforms"},
            {"2", "Robotics", "Industrial and service robots"},
            {"3", "Accessories", "Add-ons and replacement kits"},
            {"4", "Services", "Maintenance and consulting offerings"}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] c : categories) {
                ps.setInt(1, Integer.parseInt(c[0]));
                ps.setString(2, c[1]);
                ps.setString(3, c[2]);
                ps.executeUpdate();
            }
        }
    }

    private static List<ProductSeed> seedProducts(Connection conn) throws SQLException {
        List<ProductSeed> list = List.of(
            new ProductSeed(1, "XV2 Scout Drone", "Survey and mapping drone", 4200, 2700, 1),
            new ProductSeed(2, "S9 Surveillance Drone", "4K surveillance drone", 5100, 3200, 1),
            new ProductSeed(3, "T7 Thermal Drone", "Thermal inspection drone", 4600, 3300, 1),
            new ProductSeed(4, "V9 Inspection Drone", "Compact inspection drone", 3900, 2500, 1),
            new ProductSeed(5, "A2 Cargo Drone", "Heavy-lift logistics drone", 6800, 4700, 1),
            new ProductSeed(6, "AR7 Industrial Robot", "Assembly line robot", 172000, 121000, 2),
            new ProductSeed(7, "M4 Manufacturing Robot", "High precision manufacturing robot", 268000, 195000, 2),
            new ProductSeed(8, "P3 Delivery Robot", "Autonomous warehouse delivery robot", 68000, 49000, 2),
            new ProductSeed(9, "C5 Cleaning Robot", "Commercial cleaning robot", 61000, 43000, 2),
            new ProductSeed(10, "Q1 Picker Robot", "Automated stock picker robot", 94000, 67000, 2),
            new ProductSeed(11, "Drone Sensor Kit", "Multi-sensor upgrade kit", 890, 420, 3),
            new ProductSeed(12, "Robot Arm Extension", "Extension and grip kit", 6100, 3900, 3),
            new ProductSeed(13, "Battery Pack XL", "High-density battery module", 1200, 700, 3),
            new ProductSeed(14, "Safety Shield Module", "Shielding and safety package", 1450, 820, 3),
            new ProductSeed(15, "Installation & Setup", "Professional setup service", 1800, 750, 4),
            new ProductSeed(16, "Annual Maintenance Plan", "12 month service coverage", 950, 520, 4)
        );
        String sql = "INSERT INTO Product (productID, name, description, price, unitCost, stock, status, categoryID) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'active', ?) " +
            "ON CONFLICT(productID) DO UPDATE SET " +
            "name=excluded.name, description=excluded.description, price=excluded.price, " +
            "unitCost=excluded.unitCost, stock=excluded.stock, status=excluded.status, categoryID=excluded.categoryID";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ProductSeed p : list) {
                ps.setInt(1, p.id);
                ps.setString(2, p.name);
                ps.setString(3, p.description);
                ps.setDouble(4, p.netPrice);
                ps.setDouble(5, p.unitCost);
                ps.setInt(6, 40);
                ps.setInt(7, p.categoryId);
                ps.executeUpdate();
            }
        }
        return list;
    }

    private static List<CustomerSeed> seedCustomers(Connection conn) throws SQLException {
        List<CustomerSeed> list = List.of(
            new CustomerSeed(1, "TechCorp Industries", "procurement@techcorp.com", "+44 20 1100 0001", "London, UK", "Company"),
            new CustomerSeed(2, "RoboManufacture Ltd", "orders@robomfg.co.uk", "+44 20 1100 0002", "Birmingham, UK", "Company"),
            new CustomerSeed(3, "Global Systems Plc", "accounts@globalsystems.com", "+44 20 1100 0003", "Leeds, UK", "Company"),
            new CustomerSeed(4, "Northwind Logistics", "supply@northwind.com", "+44 20 1100 0004", "Manchester, UK", "Company"),
            new CustomerSeed(5, "Innovate Holdings", "finance@innovate.io", "+44 20 1100 0005", "Bristol, UK", "Company"),
            new CustomerSeed(6, "Metro Retail Group", "buying@metroretail.com", "+44 20 1100 0006", "Liverpool, UK", "Company"),
            new CustomerSeed(7, "Orion Builders", "ops@orionbuild.com", "+44 20 1100 0007", "Sheffield, UK", "Company"),
            new CustomerSeed(8, "Vertex Foods", "purchasing@vertexfoods.com", "+44 20 1100 0008", "Nottingham, UK", "Company"),
            new CustomerSeed(9, "Ava Wilson", "ava.wilson@email.com", "+44 7700 900101", "York, UK", "Individual"),
            new CustomerSeed(10, "Noah Evans", "noah.evans@email.com", "+44 7700 900102", "Leicester, UK", "Individual"),
            new CustomerSeed(11, "Sophia Khan", "sophia.khan@email.com", "+44 7700 900103", "Cambridge, UK", "Individual"),
            new CustomerSeed(12, "Liam Patel", "liam.patel@email.com", "+44 7700 900104", "Oxford, UK", "Individual"),
            new CustomerSeed(13, "Mia Chen", "mia.chen@email.com", "+44 7700 900105", "Reading, UK", "Individual"),
            new CustomerSeed(14, "Ethan Brown", "ethan.brown@email.com", "+44 7700 900106", "Derby, UK", "Individual"),
            new CustomerSeed(15, "Isla Scott", "isla.scott@email.com", "+44 7700 900107", "Glasgow, UK", "Individual"),
            new CustomerSeed(16, "Oliver Green", "oliver.green@email.com", "+44 7700 900108", "Edinburgh, UK", "Individual"),
            new CustomerSeed(17, "Emily Hall", "emily.hall@email.com", "+44 7700 900109", "Cardiff, UK", "Individual"),
            new CustomerSeed(18, "Jack Turner", "jack.turner@email.com", "+44 7700 900110", "Swansea, UK", "Individual"),
            new CustomerSeed(19, "Grace Bell", "grace.bell@email.com", "+44 7700 900111", "Newcastle, UK", "Individual"),
            new CustomerSeed(20, "Henry Adams", "henry.adams@email.com", "+44 7700 900112", "Norwich, UK", "Individual"),
            new CustomerSeed(21, "Amelia Reed", "amelia.reed@email.com", "+44 7700 900113", "Plymouth, UK", "Individual"),
            new CustomerSeed(22, "Leo Cooper", "leo.cooper@email.com", "+44 7700 900114", "Southampton, UK", "Individual"),
            new CustomerSeed(23, "Ella Moore", "ella.moore@email.com", "+44 7700 900115", "Brighton, UK", "Individual"),
            new CustomerSeed(24, "Mason Gray", "mason.gray@email.com", "+44 7700 900116", "Exeter, UK", "Individual")
        );
        String sql = "INSERT OR REPLACE INTO CustomerRegistration (customerID, name, email, contactNumber, deliveryAddress, customerType, status) VALUES (?, ?, ?, ?, ?, ?, 'active')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CustomerSeed c : list) {
                ps.setInt(1, c.id);
                ps.setString(2, c.name);
                ps.setString(3, c.email);
                ps.setString(4, c.phone);
                ps.setString(5, c.address);
                ps.setString(6, c.type);
                ps.executeUpdate();
            }
        }
        return list;
    }

    private static void seedUsers(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO FUser (userID, email, username, passwordHash, role, firstName, lastName, phone, isActive, lastLogin) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, 1);
            ps.setString(2, "admin@raez.org.uk");
            ps.setString(3, "admin");
            ps.setString(4, hash("Admin@123"));
            ps.setString(5, "ADMIN");
            ps.setString(6, "System");
            ps.setString(7, "Admin");
            ps.setString(8, "+44 7700 900001");
            ps.setInt(9, 1);
            ps.setString(10, LocalDateTime.now().minusDays(1).toString());
            ps.executeUpdate();

            ps.setInt(1, 2);
            ps.setString(2, "finance@raez.org.uk");
            ps.setString(3, "finance");
            ps.setString(4, hash("User@123"));
            ps.setString(5, "FINANCE_USER");
            ps.setString(6, "Finance");
            ps.setString(7, "User");
            ps.setString(8, "+44 7700 900002");
            ps.setInt(9, 1);
            ps.setNull(10, Types.VARCHAR);
            ps.executeUpdate();
        }
    }

    private static void seedSuppliers(Connection conn) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Supplier (supplierID, name, contact, email, avgLeadDays, reliabilityScore) VALUES (?, ?, ?, ?, ?, ?)";
        Object[][] suppliers = {
            {1, "AeroTech Components", "Morgan Reed", "morgan@aerotech.com", 6.0, 0.95},
            {2, "RoboCore Parts", "Linda Zhao", "linda@robocore.com", 8.0, 0.91},
            {3, "Nimbus Supply", "Samuel Park", "samuel@nimbus.com", 4.0, 0.93},
            {4, "Prime Industrial", "Nina Shah", "nina@primeindustrial.com", 7.0, 0.89},
            {5, "Vertex Distributors", "Ibrahim Noor", "ibrahim@vertexdist.com", 5.0, 0.94}
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
    }

    private static void seedWarehouseAndDrivers(Connection conn) throws SQLException {
        exec(conn, "INSERT INTO Warehouse (warehouseID, warehouseName, location, contactEmail, capacityLimit) VALUES (1, 'Main Warehouse', 'London, UK', 'warehouse@raez.com', 5000)");
        exec(conn, "INSERT INTO Driver (driverID, licenceNumber, phoneNum, email, driverName) VALUES (1, 'DRV-1001', '+44 7700 111001', 'driver1@raez.com', 'Ali Turner')");
        exec(conn, "INSERT INTO Driver (driverID, licenceNumber, phoneNum, email, driverName) VALUES (2, 'DRV-1002', '+44 7700 111002', 'driver2@raez.com', 'Emma Collins')");
    }

    private static void seedInventory(Connection conn, List<ProductSeed> products) throws SQLException {
        String sql = "INSERT OR REPLACE INTO InventoryRecord (inventoryID, warehouseID, productID, quantityOnHand, minStockThreshold, reorderQuantity, lowStockFlag, supplierID, unitCost, isActive) VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int id = 1;
            for (ProductSeed p : products) {
                int qty = switch (p.id) {
                    case 3 -> 2;
                    case 8 -> 1;
                    case 12 -> 0;
                    default -> 35 + (p.id % 9);
                };
                int reorder = 5;
                ps.setInt(1, id++);
                ps.setInt(2, p.id);
                ps.setInt(3, qty);
                ps.setInt(4, reorder);
                ps.setInt(5, 12);
                ps.setInt(6, qty <= reorder ? 1 : 0);
                ps.setInt(7, ((p.id - 1) % 5) + 1);
                ps.setDouble(8, p.unitCost);
                ps.executeUpdate();
            }
        }
        // Keep Product.stock aligned with inventory.
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE Product SET stock = (SELECT quantityOnHand FROM InventoryRecord i WHERE i.productID = Product.productID LIMIT 1)"
        )) {
            ps.executeUpdate();
        }
    }

    private static List<OrderSeed> seedOrdersAndItems(Connection conn, LocalDate today, List<CustomerSeed> customers, List<ProductSeed> products) throws SQLException {
        List<OrderSeed> orderSeeds = new ArrayList<>();
        String orderSql = "INSERT OR REPLACE INTO \"Order\" (orderID, customerID, orderDate, totalAmount, status) VALUES (?, ?, ?, ?, ?)";
        String itemSql = "INSERT OR REPLACE INTO OrderItem (orderItemID, orderID, productID, quantity, unitPrice) VALUES (?, ?, ?, ?, ?)";
        Random random = new Random(20260319L);
        int itemId = 1;

        try (PreparedStatement orderPs = conn.prepareStatement(orderSql);
             PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
            for (int orderId = 1; orderId <= REQUIRED_ORDER_COUNT; orderId++) {
                int monthOffset = (orderId - 1) % 12;
                int day = 1 + ((orderId * 7) % 27);
                LocalDate orderDate = today.minusMonths(monthOffset).withDayOfMonth(Math.min(day, today.minusMonths(monthOffset).lengthOfMonth()));
                CustomerSeed customer = customers.get((orderId - 1) % customers.size());
                String status = statusForOrder(orderId);
                int itemCount = 1 + random.nextInt(3);
                double netSubtotal = 0;

                for (int i = 0; i < itemCount; i++) {
                    ProductSeed product = products.get((orderId + i + random.nextInt(products.size())) % products.size());
                    int qty = 1 + random.nextInt(product.categoryId == 3 ? 4 : 2);
                    itemPs.setInt(1, itemId++);
                    itemPs.setInt(2, orderId);
                    itemPs.setInt(3, product.id);
                    itemPs.setInt(4, qty);
                    itemPs.setDouble(5, product.netPrice);
                    itemPs.executeUpdate();
                    netSubtotal += (product.netPrice * qty);
                }

                double grossTotal = round2(netSubtotal * (1.0 + VAT_RATE));
                orderPs.setInt(1, orderId);
                orderPs.setInt(2, customer.id);
                orderPs.setString(3, orderDate.toString());
                orderPs.setDouble(4, grossTotal);
                orderPs.setString(5, status);
                orderPs.executeUpdate();

                orderSeeds.add(new OrderSeed(orderId, customer.id, status, orderDate.toString(), grossTotal));
            }
        }
        return orderSeeds;
    }

    private static void seedPayments(Connection conn, List<OrderSeed> orders) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Payment (paymentID, orderID, amountPaid, currency, paymentMethod, paymentStatus, transactionRef, paymentDate) VALUES (?, ?, ?, 'GBP', ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int id = 1;
            for (OrderSeed order : orders) {
                if ("Cancelled".equals(order.status)) continue;
                ps.setInt(1, id++);
                ps.setInt(2, order.id);
                ps.setDouble(3, order.grossTotal);
                ps.setString(4, order.grossTotal > 10000 ? "BANK" : "CARD");
                ps.setString(5, "Pending".equals(order.status) ? "PENDING" : "SUCCESS");
                ps.setString(6, "TXN-" + String.format("%05d", order.id));
                ps.setString(7, order.orderDate);
                ps.executeUpdate();
            }
        }
    }

    private static void seedInvoices(Connection conn, LocalDate today, List<OrderSeed> orders) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Invoice (invoiceID, orderID, customerID, paymentID, invoiceNumber, status, subtotal, vatAmount, totalAmount, currency, issuedAt, dueDate, paidAt, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'GBP', ?, ?, ?, ?)";
        String[] statuses = {"PAID", "PAID", "PENDING", "OVERDUE", "PAID", "OVERDUE", "PENDING", "PAID", "OVERDUE", "PENDING"};
        int invoiceCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OrderSeed order : orders) {
                if ("Cancelled".equals(order.status)) continue;
                if (invoiceCount >= REQUIRED_INVOICE_COUNT) break;
                int idx = invoiceCount;
                LocalDate issued = LocalDate.parse(order.orderDate);
                LocalDate due = issued.plusDays(30);
                String status = statuses[idx];
                String paidAt = "PAID".equals(status) ? issued.plusDays(5).toString() : null;

                ps.setInt(1, idx + 1);
                ps.setInt(2, order.id);
                ps.setInt(3, order.customerId);
                ps.setInt(4, idx + 1); // paymentID follows seeded payment order for early rows
                ps.setString(5, "INV-2026-" + String.format("%04d", idx + 1));
                ps.setString(6, status);
                ps.setDouble(7, round2(order.grossTotal / (1 + VAT_RATE)));
                ps.setDouble(8, round2(order.grossTotal - (order.grossTotal / (1 + VAT_RATE))));
                ps.setDouble(9, order.grossTotal);
                ps.setString(10, issued.toString());
                ps.setString(11, due.toString());
                if (paidAt == null) ps.setNull(12, Types.VARCHAR); else ps.setString(12, paidAt);
                ps.setString(13, "Auto-seeded invoice");
                ps.executeUpdate();
                invoiceCount++;
            }
        }
    }

    private static void seedRefunds(Connection conn, LocalDate today, List<OrderSeed> orders) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Refund (refundID, orderID, productID, refundAmount, refundDate, reason, status, processedBy) VALUES (?, ?, ?, ?, ?, ?, 'APPROVED', 1)";
        int[] refundOrderIds = {10, 24, 37, 55, 88};
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < refundOrderIds.length; i++) {
                int orderId = refundOrderIds[i];
                OrderSeed seed = orders.stream().filter(o -> o.id == orderId).findFirst().orElse(null);
                if (seed == null) continue;
                ps.setInt(1, i + 1);
                ps.setInt(2, orderId);
                ps.setInt(3, 1 + (i % REQUIRED_PRODUCT_COUNT));
                ps.setDouble(4, round2(seed.grossTotal * 0.18));
                ps.setString(5, today.minusDays(5L * (i + 1)).toString());
                ps.setString(6, "Seeded refund case " + (i + 1));
                ps.executeUpdate();
            }
        }
    }

    private static void seedReviews(Connection conn, LocalDate today, List<CustomerSeed> customers, List<ProductSeed> products) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Review (reviewID, rating, reviewText, reviewDate, isReported, customerID, productID) VALUES (?, ?, ?, ?, ?, ?, ?)";
        int[][] reviewPairs = {
            {9, 3, 1}, {10, 8, 2}, {11, 6, 1}, {12, 7, 2},
            {13, 5, 5}, {14, 2, 4}, {15, 10, 4}, {16, 12, 5}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < reviewPairs.length; i++) {
                int rating = reviewPairs[i][2];
                ps.setInt(1, i + 1);
                ps.setInt(2, rating);
                ps.setString(3, rating <= 2 ? "Negative seeded review " + (i + 1) : "Positive seeded review " + (i + 1));
                ps.setString(4, today.minusDays(2L + i).toString());
                ps.setInt(5, rating <= 2 ? 1 : 0);
                ps.setInt(6, reviewPairs[i][0]);
                ps.setInt(7, reviewPairs[i][1]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedAnomalies(Connection conn, LocalDate today) throws SQLException {
        String sql = "INSERT OR REPLACE INTO FinancialAnomalies (anomalyID, anomalyType, description, severity, detectionRule, alertDate, isResolved, affectedCustomerFK, affectedOrderFK, affectedProductFK) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 3; i++) {
                ps.setInt(1, i);
                ps.setString(2, "ANOMALY_" + i);
                ps.setString(3, "Seeded financial anomaly " + i);
                ps.setString(4, i == 1 ? "CRITICAL" : "HIGH");
                ps.setString(5, "RULE_" + i);
                ps.setString(6, today.minusDays(i).toString());
                ps.setInt(7, i);
                ps.setInt(8, i * 8);
                ps.setInt(9, i * 3);
                ps.executeUpdate();
            }
        }
    }

    private static void seedAlerts(Connection conn, LocalDate today) throws SQLException {
        String sql = "INSERT OR REPLACE INTO Alert (alertID, alertType, severity, message, createdAt, entityType, entityID, isResolved, sourceAnomalyID) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)";
        String[][] rows = {
            {"1", "Low Stock", "WARNING", "Three inventory items are at or below reorder threshold", "PRODUCT", "3", "1"},
            {"2", "Refund Spike", "HIGH", "Refund ratio exceeded threshold for customer segment", "CUSTOMER", "5", "2"},
            {"3", "Invoice Overdue", "CRITICAL", "Multiple invoices are overdue beyond due date", "INVOICE", "4", "3"}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] row : rows) {
                ps.setInt(1, Integer.parseInt(row[0]));
                ps.setString(2, row[1]);
                ps.setString(3, row[2]);
                ps.setString(4, row[3]);
                ps.setString(5, today.minusDays(1).toString());
                ps.setString(6, row[4]);
                ps.setInt(7, Integer.parseInt(row[5]));
                ps.setInt(8, Integer.parseInt(row[6]));
                ps.executeUpdate();
            }
        }
    }

    private static void seedAuxiliaryRows(Connection conn, List<CustomerSeed> customers) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO LoginCredentials (customerID, passwordHash) VALUES (?, ?)")) {
            for (CustomerSeed c : customers) {
                ps.setInt(1, c.id);
                ps.setString(2, hash("Customer@" + c.id));
                ps.executeUpdate();
            }
        }
        exec(conn, "INSERT INTO AdminUser (adminID, name, email) VALUES (1, 'System Admin', 'admin@raez.org.uk')");
        exec(conn, "INSERT INTO CustomerPreferences (preferenceID, customerID, preferredCategories, notificationSettings, deliveryInstructions) VALUES (1, 1, 'Drones', 'EMAIL', 'Leave at reception')");
    }

    private static void verifyData(Connection conn) throws SQLException {
        assertAtLeast(conn, "Product", REQUIRED_PRODUCT_COUNT);
        assertAtLeast(conn, "CustomerRegistration", REQUIRED_CUSTOMER_COUNT);
        assertAtLeast(conn, "Supplier", REQUIRED_SUPPLIER_COUNT);
        assertAtLeast(conn, "\"Order\"", REQUIRED_ORDER_COUNT);
        assertAtLeast(conn, "Invoice", REQUIRED_INVOICE_COUNT);
        assertAtLeast(conn, "Refund", REQUIRED_REFUND_COUNT);
        assertAtLeast(conn, "Alert", REQUIRED_ACTIVE_ALERT_COUNT);

        int lowStock = queryInt(conn, "SELECT COUNT(*) FROM InventoryRecord WHERE quantityOnHand <= minStockThreshold");
        if (lowStock < REQUIRED_LOW_STOCK_COUNT) {
            throw new SQLException("Seed validation failed: low stock count < " + REQUIRED_LOW_STOCK_COUNT);
        }
        int lowRated = queryInt(conn, "SELECT COUNT(*) FROM Review WHERE rating <= 2");
        if (lowRated < REQUIRED_NEGATIVE_REVIEW_COUNT) {
            throw new SQLException("Seed validation failed: negative review count < " + REQUIRED_NEGATIVE_REVIEW_COUNT);
        }
        int activeAlerts = queryInt(conn, "SELECT COUNT(*) FROM Alert WHERE isResolved = 0");
        if (activeAlerts < REQUIRED_ACTIVE_ALERT_COUNT) {
            throw new SQLException("Seed validation failed: active alert count < " + REQUIRED_ACTIVE_ALERT_COUNT);
        }
        validateOrderTotals(conn);
    }

    private static void validateOrderTotals(Connection conn) throws SQLException {
        String sql =
            "SELECT o.orderID, o.totalAmount, COALESCE(SUM(oi.quantity * oi.unitPrice),0) AS netSubtotal " +
            "FROM \"Order\" o LEFT JOIN OrderItem oi ON oi.orderID = o.orderID " +
            "GROUP BY o.orderID, o.totalAmount";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double expectedGross = round2(rs.getDouble("netSubtotal") * (1.0 + VAT_RATE));
                double actual = rs.getDouble("totalAmount");
                if (Math.abs(expectedGross - actual) > 0.01) {
                    throw new SQLException("Order total mismatch for order " + rs.getInt("orderID")
                        + ": expected " + expectedGross + ", found " + actual);
                }
            }
        }
    }

    private static String statusForOrder(int orderId) {
        if (orderId % 17 == 0) return "Cancelled";
        if (orderId % 11 == 0) return "Pending";
        if (orderId % 23 == 0) return "Refunded";
        return "Completed";
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String normalized = tableName.replace("\"", "");
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1"
        )) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private static boolean isBelowThreshold(Connection conn, String table, int threshold) throws SQLException {
        return queryInt(conn, "SELECT COUNT(*) FROM " + table) < threshold;
    }

    private static void assertAtLeast(Connection conn, String table, int minCount) throws SQLException {
        int count = queryInt(conn, "SELECT COUNT(*) FROM " + table);
        if (count < minCount) {
            throw new SQLException("Seed validation failed: " + table + " has " + count + ", expected at least " + minCount);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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
