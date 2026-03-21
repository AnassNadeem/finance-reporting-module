package com.raez.finance.service;

import com.raez.finance.util.FinanceCalculationUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory mock data simulating SQLite structure (orders, products, customers, payments, refunds,
 * suppliers, inventory). Used for standalone demo or when DB is unavailable. All calculations use
 * GlobalSettingsService (VAT, currency) via FinanceCalculationUtil.
 */
public final class MockDataProvider {

    private static final MockDataProvider INSTANCE = new MockDataProvider();

    public static MockDataProvider getInstance() {
        return INSTANCE;
    }

    // ─── Mock model types (simplified DB row equivalents) ───────────────────

    public static final class MockProduct {
        public final int productID;
        public final String name;
        public final String categoryName;
        public final double price;
        public final double unitCost;
        public final int stock;

        public MockProduct(int productID, String name, String categoryName, double price, double unitCost, int stock) {
            this.productID = productID;
            this.name = name;
            this.categoryName = categoryName != null ? categoryName : "Uncategorized";
            this.price = price;
            this.unitCost = unitCost;
            this.stock = stock;
        }
    }

    public static final class MockCustomer {
        public final int customerID;
        public final String name;
        public final String customerType;
        public final String deliveryAddress;

        public MockCustomer(int customerID, String name, String customerType, String deliveryAddress) {
            this.customerID = customerID;
            this.name = name;
            this.customerType = customerType != null ? customerType : "Individual";
            this.deliveryAddress = deliveryAddress != null ? deliveryAddress : "";
        }
    }

    public static final class MockOrderItem {
        public final int orderID;
        public final int productID;
        public final int quantity;
        public final double unitPrice;
        public final double unitCost; // for COGS

        public MockOrderItem(int orderID, int productID, int quantity, double unitPrice, double unitCost) {
            this.orderID = orderID;
            this.productID = productID;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.unitCost = unitCost;
        }

        public double getLineTotal() { return quantity * unitPrice; }
        public double getLineCost() { return quantity * unitCost; }
    }

    public static final class MockOrder {
        public final int orderID;
        public final int customerID;
        public final LocalDate orderDate;
        public final double totalAmount;
        public final String status;

        public MockOrder(int orderID, int customerID, LocalDate orderDate, double totalAmount, String status) {
            this.orderID = orderID;
            this.customerID = customerID;
            this.orderDate = orderDate;
            this.totalAmount = totalAmount;
            this.status = status != null ? status : "Processing";
        }
    }

    public static final class MockPayment {
        public final int orderID;
        public final double amountPaid;
        public final LocalDate paymentDate;
        public final String paymentStatus;

        public MockPayment(int orderID, double amountPaid, LocalDate paymentDate, String paymentStatus) {
            this.orderID = orderID;
            this.amountPaid = amountPaid;
            this.paymentDate = paymentDate;
            this.paymentStatus = paymentStatus != null ? paymentStatus : "PENDING";
        }
    }

    public static final class MockRefund {
        public final int orderID;
        public final double refundAmount;
        public final LocalDate refundDate;

        public MockRefund(int orderID, double refundAmount, LocalDate refundDate) {
            this.orderID = orderID;
            this.refundAmount = refundAmount;
            this.refundDate = refundDate;
        }
    }

    public static final class MockSupplier {
        public final int supplierID;
        public final String name;
        public final String contact;
        /** On-time delivery rate 0–1. */
        public final double reliabilityScore;
        /** Average lead time in days (for InventorySupplier view). */
        public final double leadDays;

        public MockSupplier(int supplierID, String name, String contact, double reliabilityScore, double leadDays) {
            this.supplierID = supplierID;
            this.name = name;
            this.contact = contact;
            this.reliabilityScore = Math.max(0, Math.min(1, reliabilityScore));
            this.leadDays = leadDays > 0 ? leadDays : 7;
        }
    }

    public static final class MockInventoryItem {
        public final int productID;
        public final int quantityOnHand;
        public final double unitCost;

        public MockInventoryItem(int productID, int quantityOnHand, double unitCost) {
            this.productID = productID;
            this.quantityOnHand = quantityOnHand;
            this.unitCost = unitCost;
        }

        public double getStockValue() { return quantityOnHand * unitCost; }
    }

    // ─── In-memory stores ───────────────────────────────────────────────────

    private final List<MockProduct> products = new ArrayList<>();
    private final List<MockCustomer> customers = new ArrayList<>();
    private final List<MockOrder> orders = new ArrayList<>();
    private final List<MockOrderItem> orderItems = new ArrayList<>();
    private final List<MockPayment> payments = new ArrayList<>();
    private final List<MockRefund> refunds = new ArrayList<>();
    private final List<MockSupplier> suppliers = new ArrayList<>();
    private final List<MockInventoryItem> inventory = new ArrayList<>();
    /** Synthetic low-stock rows for the Inventory view. */
    public static final class LowStockItem {
        public final String name;
        public final String category;
        public final int currentStock;
        public final int reorderLevel;

        public LowStockItem(String name, String category, int currentStock, int reorderLevel) {
            this.name = name;
            this.category = category;
            this.currentStock = currentStock;
            this.reorderLevel = reorderLevel;
        }
    }

    private MockDataProvider() {
        seedData();
    }

    private void seedData() {
        // Categories: Electronics, Office, Furniture, Uncategorized
        products.add(new MockProduct(1, "Laptop Pro", "Electronics", 999.00, 520.00, 25));
        products.add(new MockProduct(2, "Wireless Mouse", "Electronics", 29.99, 12.00, 150));
        products.add(new MockProduct(3, "Desk Lamp", "Office", 45.00, 18.00, 80));
        products.add(new MockProduct(4, "Office Chair", "Furniture", 249.00, 110.00, 40));
        products.add(new MockProduct(5, "Monitor 24\"", "Electronics", 189.00, 95.00, 60));
        products.add(new MockProduct(6, "Notebook Pack", "Office", 12.99, 4.50, 200));
        products.add(new MockProduct(7, "Bookshelf", "Furniture", 89.00, 38.00, 35));
        products.add(new MockProduct(8, "USB Hub", "Electronics", 24.99, 9.00, 120));

        customers.add(new MockCustomer(1, "Acme Corp", "Business", "London, UK"));
        customers.add(new MockCustomer(2, "Jane Smith", "Individual", "Manchester, UK"));
        customers.add(new MockCustomer(3, "TechStart Ltd", "Business", "Birmingham, UK"));
        customers.add(new MockCustomer(4, "John Doe", "Individual", "Leeds, UK"));
        customers.add(new MockCustomer(5, "Retail Co", "Business", "Edinburgh, UK"));

        LocalDate base = LocalDate.now().minusMonths(5);
        Random r = new Random(42);
        int orderId = 1;
        for (int m = 0; m < 6; m++) {
            for (int i = 0; i < 4 + r.nextInt(6); i++) {
                int custId = 1 + r.nextInt(customers.size());
                LocalDate orderDate = base.plusMonths(m).plusDays(r.nextInt(28));
                double total = 50 + r.nextDouble() * 500;
                String status = r.nextDouble() > 0.15 ? "Completed" : (r.nextDouble() > 0.5 ? "Processing" : "Pending");
                orders.add(new MockOrder(orderId, custId, orderDate, total, status));
                orderId++;
            }
        }
        // Order items for first 15 orders (simplified: one line per order)
        for (int i = 0; i < Math.min(15, orders.size()); i++) {
            MockOrder o = orders.get(i);
            MockProduct p = products.get(i % products.size());
            int qty = 1 + (i % 3);
            orderItems.add(new MockOrderItem(o.orderID, p.productID, qty, p.price, p.unitCost));
        }
        // Payments: most completed orders have a successful payment
        for (MockOrder o : orders) {
            if ("Completed".equals(o.status) && new Random(o.orderID).nextDouble() > 0.2) {
                payments.add(new MockPayment(o.orderID, o.totalAmount, o.orderDate.plusDays(1), "SUCCESS"));
            }
        }
        // Refunds: a few
        if (orders.size() >= 3) {
            refunds.add(new MockRefund(orders.get(2).orderID, 29.99, base.plusMonths(1).plusDays(10)));
            if (orders.size() >= 7)
                refunds.add(new MockRefund(orders.get(6).orderID, 45.00, base.plusMonths(2).plusDays(5)));
        }
        // Suppliers (with rough lead times for the Inventory view)
        suppliers.add(new MockSupplier(1, "ElectroSupply", "contact@electro.com", 0.95, 5));
        suppliers.add(new MockSupplier(2, "OfficeGoods Ltd", "orders@officegoods.co.uk", 0.88, 7));
        suppliers.add(new MockSupplier(3, "Furniture World", "sales@furnworld.com", 0.92, 9));
        // Inventory (match products)
        for (MockProduct p : products) {
            inventory.add(new MockInventoryItem(p.productID, p.stock, p.unitCost));
        }
    }

    // ─── Raw getters ───────────────────────────────────────────────────────

    public List<MockProduct> getProducts() { return new ArrayList<>(products); }
    public List<MockCustomer> getCustomers() { return new ArrayList<>(customers); }
    public List<MockOrder> getOrders() { return new ArrayList<>(orders); }
    public List<MockOrderItem> getOrderItems() { return new ArrayList<>(orderItems); }
    public List<MockPayment> getPayments() { return new ArrayList<>(payments); }
    public List<MockRefund> getRefunds() { return new ArrayList<>(refunds); }
    public List<MockSupplier> getSuppliers() { return new ArrayList<>(suppliers); }
    public List<MockInventoryItem> getInventory() { return new ArrayList<>(inventory); }

    // ─── Filtered getters ───────────────────────────────────────────────────

    public List<MockOrder> getOrders(LocalDate from, LocalDate to, String category) {
        List<MockOrder> list = FinanceCalculationUtil.filterByDateRange(orders, from, to, o -> o.orderDate);
        if (category == null || category.isBlank() || "All Categories".equalsIgnoreCase(category.trim()))
            return list;
        Set<Integer> orderIdsInCategory = orderItems.stream()
                .filter(oi -> {
                    MockProduct p = products.stream().filter(pr -> pr.productID == oi.productID).findFirst().orElse(null);
                    return p != null && category.trim().equalsIgnoreCase(p.categoryName);
                })
                .mapToInt(oi -> oi.orderID)
                .boxed()
                .collect(Collectors.toSet());
        return list.stream().filter(o -> orderIdsInCategory.contains(o.orderID)).collect(Collectors.toList());
    }

    public List<MockOrder> getOrdersByCustomer(int customerID) {
        return FinanceCalculationUtil.filterByCustomer(orders, customerID, o -> o.customerID);
    }

    public Optional<MockProduct> getProduct(int productID) {
        return products.stream().filter(p -> p.productID == productID).findFirst();
    }

    public Optional<MockCustomer> getCustomer(int customerID) {
        return customers.stream().filter(c -> c.customerID == customerID).findFirst();
    }

    // ─── Aggregates (use global VAT for consistency) ─────────────────────────

    /** Total sales = sum of successful payments in range. */
    public double getTotalSales(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        Set<Integer> paidOrderIds = payments.stream()
                .filter(p -> "SUCCESS".equals(p.paymentStatus))
                .filter(p -> (from == null || !p.paymentDate.isBefore(from)) && (to == null || !p.paymentDate.isAfter(to)))
                .mapToInt(p -> p.orderID)
                .boxed()
                .collect(Collectors.toSet());
        return filtered.stream()
                .filter(o -> paidOrderIds.contains(o.orderID))
                .mapToDouble(o -> o.totalAmount)
                .sum();
    }

    /** Net income = total sales - refunds in range. */
    public double getNetIncome(LocalDate from, LocalDate to, String category) {
        double sales = getTotalSales(from, to, category);
        List<MockRefund> refInRange = FinanceCalculationUtil.filterByDateRange(refunds, from, to, r -> r.refundDate);
        double refundTotal = refInRange.stream().mapToDouble(r -> r.refundAmount).sum();
        return Math.max(0, sales - refundTotal);
    }

    /** Outstanding = order total where status not Cancelled and no successful payment. */
    public double getOutstandingPayments(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        Set<Integer> paidIds = payments.stream().filter(p -> "SUCCESS".equals(p.paymentStatus)).mapToInt(p -> p.orderID).boxed().collect(Collectors.toSet());
        return filtered.stream()
                .filter(o -> !"Cancelled".equals(o.status) && !paidIds.contains(o.orderID))
                .mapToDouble(o -> o.totalAmount)
                .sum();
    }

    /** Total VAT collected (from gross sales in range). */
    public double getTotalVatCollected(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        Set<Integer> paidIds = payments.stream().filter(p -> "SUCCESS".equals(p.paymentStatus)).mapToInt(p -> p.orderID).boxed().collect(Collectors.toSet());
        List<Double> grossAmounts = filtered.stream()
                .filter(o -> paidIds.contains(o.orderID))
                .map(o -> o.totalAmount)
                .collect(Collectors.toList());
        return FinanceCalculationUtil.totalVatFromGrossAmounts(grossAmounts);
    }

    /** COGS for orders in range (from order items with unitCost). */
    public double getCogs(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        Set<Integer> orderIds = filtered.stream().mapToInt(o -> o.orderID).boxed().collect(Collectors.toSet());
        return orderItems.stream()
                .filter(oi -> orderIds.contains(oi.orderID))
                .mapToDouble(MockOrderItem::getLineCost)
                .sum();
    }

    /** Average order value in range. */
    public double getAverageOrderValue(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        List<Double> totals = filtered.stream().map(o -> o.totalAmount).collect(Collectors.toList());
        return FinanceCalculationUtil.averageOrderValue(totals);
    }

    /** Month-over-month data points (label -> total sales) for chart. */
    public List<Map.Entry<String, Number>> getSalesTimeSeries(LocalDate from, LocalDate to, String category) {
        List<MockOrder> filtered = getOrders(from, to, category);
        Set<Integer> paidIds = payments.stream().filter(p -> "SUCCESS".equals(p.paymentStatus)).mapToInt(p -> p.orderID).boxed().collect(Collectors.toSet());
        Map<String, Double> byMonth = new TreeMap<>();
        for (MockOrder o : filtered) {
            if (!paidIds.contains(o.orderID)) continue;
            String monthKey = o.orderDate.getYear() + "-" + String.format("%02d", o.orderDate.getMonthValue());
            byMonth.merge(monthKey, o.totalAmount, Double::sum);
        }
        return byMonth.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), (Number) e.getValue()))
                .collect(Collectors.toList());
    }

    /** Current stock value (inventory quantity * unitCost). */
    public double getCurrentStockValue() {
        return inventory.stream().mapToDouble(MockInventoryItem::getStockValue).sum();
    }

    /** Refunds in date range. */
    public double getRefunds(LocalDate from, LocalDate to, String category) {
        List<MockRefund> refInRange = FinanceCalculationUtil.filterByDateRange(refunds, from, to, r -> r.refundDate);
        return refInRange.stream().mapToDouble(r -> r.refundAmount).sum();
    }

    /** Total orders in range, optional category filter (used by RevenueVatSummary). */
    public int getTotalOrders(LocalDate from, LocalDate to, String category) {
        return getOrders(from, to, category).size();
    }

    /** Low-stock items derived from inventory + products (used by InventorySupplier view). */
    public List<LowStockItem> getLowStockItems() {
        List<LowStockItem> list = new ArrayList<>();
        for (MockInventoryItem inv : inventory) {
            MockProduct p = products.stream().filter(pr -> pr.productID == inv.productID).findFirst().orElse(null);
            if (p == null) continue;
            int reorderLevel = Math.max(5, p.stock / 3);
            if (inv.quantityOnHand <= reorderLevel) {
                list.add(new LowStockItem(p.name, p.categoryName, inv.quantityOnHand, reorderLevel));
            }
        }
        return list;
    }
}
