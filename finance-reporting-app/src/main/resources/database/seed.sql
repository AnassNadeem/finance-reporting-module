-- ==========================================================
-- NC1605 Phase 2 – COMPREHENSIVE SEED DATA
-- Multiple scenarios (2 records per scenario) for testing Finance
-- WARNING: This script TRUNCATES tables and RESTARTS identities
-- Run while connected to: nc1605_finance
-- Assumes schema external_sim and finance exist and schema definitions applied
-- ==========================================================

BEGIN;

-- CLEAN SLATE: truncate external and finance tables (order to satisfy FKs)
TRUNCATE TABLE
  finance."Alert",
  finance."FinancialAnomalies",
  finance."Refund",
  finance."Payment",
  finance."FUser",
  external_sim."DeliveryLog",
  external_sim."Delivery",
  external_sim."Driver",
  external_sim."OrderItem",
  external_sim."Order",
  external_sim."Review",
  external_sim."InventoryRecord",
  external_sim."StockMovement",
  external_sim."Warehouse",
  external_sim."ProductImage",
  external_sim."ProductValidation",
  external_sim."Product",
  external_sim."Category",
  external_sim."CustomerPreferences",
  external_sim."CustomerUpdate",
  external_sim."AdminUser",
  external_sim."LoginCredentials",
  external_sim."CustomerRegistration"
RESTART IDENTITY CASCADE;

-- ==========================================================
-- Seed core catalog & customers
-- ==========================================================
-- Categories
INSERT INTO external_sim."Category" ("categoryName","description") VALUES
('Electronics','Electronic devices'),
('Home Appliances','Appliances for home'),
('Accessories','Accessory items');

-- Products (6 products)
INSERT INTO external_sim."Product" ("name","description","price","stock","categoryID") VALUES
('Laptop Pro','High performance laptop',1200.00,50,1),
('Smart Kettle','IoT enabled kettle',80.00,100,2),
('Wireless Mouse','Ergonomic mouse',25.00,200,3),
('4K Monitor','Ultra HD monitor',350.00,30,1),
('Coffee Maker','Drip coffee maker',60.00,40,2),
('USB-C Cable','Fast charging cable',8.00,500,3);

-- Product images and validations (lightweight)
INSERT INTO external_sim."ProductImage" ("productID","imageURL","isPrimary") VALUES
(1,'/images/laptop1.png',TRUE),(2,'/images/kettle1.png',TRUE),(3,'/images/mouse1.png',TRUE);
INSERT INTO external_sim."ProductValidation" ("productID","validatedBy","isValid") VALUES
(1,'admin',TRUE),(2,'admin',TRUE),(3,'qa',TRUE);

-- Customers (8 customers)
INSERT INTO external_sim."CustomerRegistration" ("name","email","contactNumber","deliveryAddress") VALUES
('John Smith','john.smith@example.com','07110000001','10 Baker St, London'),
('Sara Khan','sara.khan@example.com','07110000002','20 High Rd, Birmingham'),
('Tom Brown','tom.brown@example.com','07110000003','5 Market St, Leeds'),
('Aisha Ali','aisha.ali@example.com','07110000004','2 River Rd, Manchester'),
('Omar Tariq','omar.tariq@example.com','07110000005','11 King St, Bristol'),
('Lina Gomez','lina.gomez@example.com','07110000006','14 Queen Ave, London'),
('Ethan White','ethan.white@example.com','07110000007','8 Station Rd, Liverpool'),
('Maya Patel','maya.patel@example.com','07110000008','3 Church Ln, Oxford');

-- Login credentials (simple placeholder hashes)
INSERT INTO external_sim."LoginCredentials" ("customerID","passwordHash") VALUES
(1,'hash1'),(2,'hash2'),(3,'hash3'),(4,'hash4'),(5,'hash5'),(6,'hash6'),(7,'hash7'),(8,'hash8');

-- Admin user (for updates)
INSERT INTO external_sim."AdminUser" ("name","email") VALUES
('System Admin','admin@marketplace.com');

-- Preferences (couple entries)
INSERT INTO external_sim."CustomerPreferences" ("customerID","preferredCategories","notificationSettings") VALUES
(1,'Electronics;Accessories','EMAIL'),(2,'Home Appliances','SMS');

-- ==========================================================
-- Warehouses & Inventory (to create stock scenarios)
-- ==========================================================
INSERT INTO external_sim."Warehouse" ("warehouseName","location") VALUES
('Main Warehouse','London'),('Secondary Warehouse','Manchester');

INSERT INTO external_sim."InventoryRecord" ("warehouseID","productID","quantityOnHand","minStockThreshold") VALUES
(1,1,50,5),(1,2,100,10),(1,3,200,20),(2,4,30,5),(2,5,40,5),(2,6,500,50);

-- ==========================================================
-- Drivers (for delivery simulation)
-- ==========================================================
INSERT INTO external_sim."Driver" ("driverName","phoneNum") VALUES
('Ali Driver','07001110001'),('Emma Driver','07001110002');

-- ==========================================================
-- ORDERS & ORDERITEMS: create many orders covering scenarios
-- We'll create 24 orders (2 per scenario * 12 scenarios)
-- ==========================================================

-- SCENARIO GROUP A: Normal successful purchases (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(1,1200.00,'Completed'), -- orderID 1
(6,60.00,'Completed');   -- orderID 2

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(1,1,1,1200.00),(2,5,1,60.00);

-- SCENARIO GROUP B: Partial refund approved (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(2,160.00,'Completed'), -- orderID 3
(3,50.00,'Completed');  -- orderID 4

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(3,2,2,80.00),(4,3,2,25.00);

-- SCENARIO GROUP C: Full refund approved (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(4,350.00,'Completed'), -- orderID 5
(5,1200.00,'Completed');-- orderID 6

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(5,4,1,350.00),(6,1,1,1200.00);

-- SCENARIO GROUP D: Payment failed then retried & succeeded (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(7,25.00,'Completed'), -- orderID 7
(8,8.00,'Completed');  -- orderID 8

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(7,3,1,25.00),(8,6,1,8.00);

-- SCENARIO GROUP E: Multiple partial payments (installments) (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(1,2400.00,'Processing'), -- orderID 9 (expect multiple payments)
(2,700.00,'Processing');  -- orderID 10

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(9,1,2,1200.00),(10,4,2,350.00);

-- SCENARIO GROUP F: Reconciliation mismatch (payment amount ≠ order total) (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(3,100.00,'Completed'), -- orderID 11
(4,200.00,'Completed'); -- orderID 12

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(11,3,4,25.00),(12,5,2,60.00);

-- SCENARIO Group G: High-value orders flagged for anomaly (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(5,10000.00,'Completed'), -- orderID 13
(6,15000.00,'Completed'); -- orderID 14

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(13,1,8,1200.00),(14,1,12,1250.00);

-- SCENARIO Group H: Excessive refunds for a single customer (2 orders) (customer 2)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(2,500.00,'Completed'), -- orderID 15
(2,300.00,'Completed'); -- orderID 16

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(15,4,1,350.00),(16,5,5,60.00);

-- SCENARIO Group I: Multiple failed payments for same customer/card (fraud suspicion) (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(7,300.00,'PaymentFailed'), -- orderID 17
(7,450.00,'PaymentFailed'); -- orderID 18

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(17,4,1,300.00),(18,1,1,450.00);

-- SCENARIO Group J: Stock shortage -> order pending though payment made (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(8,400.00,'Pending'), -- orderID 19
(1,600.00,'Pending'); -- orderID 20

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(19,4,2,200.00),(20,1,0,1200.00); -- note: quantity zero simulates bad input / stock problem

-- SCENARIO Group K: Chargebacks (post-payment disputes) (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(3,120.00,'Completed'), -- orderID 21
(4,80.00,'Completed');  -- orderID 22

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(21,5,2,60.00),(22,2,1,80.00);

-- SCENARIO Group L: Currency mismatch / multi-currency payments (2 orders)
INSERT INTO external_sim."Order" ("customerID","totalAmount","status") VALUES
(5,500.00,'Completed'), -- orderID 23
(6,750.00,'Completed'); -- orderID 24

INSERT INTO external_sim."OrderItem" ("orderID","productID","quantity","unitPrice") VALUES
(23,4,1,500.00),(24,4,2,375.00);

-- ==========================================================
-- Deliveries for relevant orders (some delivered, some pending)
-- ==========================================================
INSERT INTO external_sim."Delivery" ("orderID","customerAddress","orderStatus","driverID","warehouseID") VALUES
(1,'10 Baker St, London','Delivered',1,1),(2,'14 Queen Ave, London','Delivered',2,1),
(3,'20 High Rd, Birmingham','Delivered',1,1),(4,'5 Market St, Leeds','Delivered',2,1),
(5,'2 River Rd, Manchester','Delivered',1,2),(6,'11 King St, Bristol','Delivered',2,2),
(7,'8 Station Rd, Liverpool','Delivered',1,1),(8,'3 Church Ln, Oxford','Delivered',2,1),
(9,'10 Baker St, London','Processing',1,1),(10,'20 High Rd, Birmingham','Processing',2,1),
(11,'5 Market St, Leeds','Delivered',1,2),(12,'2 River Rd, Manchester','Delivered',2,2),
(13,'11 King St, Bristol','Delivered',1,1),(14,'14 Queen Ave, London','Delivered',2,1),
(15,'20 High Rd, Birmingham','Delivered',1,2),(16,'20 High Rd, Birmingham','Delivered',2,2),
(17,'8 Station Rd, Liverpool','Failed',1,1),(18,'8 Station Rd, Liverpool','Failed',2,1),
(19,'3 Church Ln, Oxford','Pending',1,2),(20,'10 Baker St, London','Pending',2,2),
(21,'5 Market St, Leeds','Delivered',1,1),(22,'2 River Rd, Manchester','Delivered',2,1),
(23,'11 King St, Bristol','Delivered',1,2),(24,'14 Queen Ave, London','Delivered',2,2);

-- ==========================================================
-- FINANCE: FUsers (finance staff)
-- ==========================================================
INSERT INTO finance."FUser" ("email","username","passwordHash","role") VALUES
('finance.admin@system.com','financeAdmin','hash_admin','ADMIN'),
('finance.analyst@system.com','financeAnalyst','hash_analyst','ANALYST');

-- ==========================================================
-- PAYMENTS: insert multiple patterns per scenario (2 payments per scenario group)
-- Use distinct transactionRef values; set paymentStatus appropriately
-- ==========================================================
-- A: Normal successful purchases
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(1,1200.00,'CARD','SUCCESS','TXN-A-0001','GBP'),
(2,60.00,'CARD','SUCCESS','TXN-A-0002','GBP');

-- B: Partial refund approved (payments were full initially)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(3,160.00,'CARD','SUCCESS','TXN-B-0001','GBP'),
(4,50.00,'CARD','SUCCESS','TXN-B-0002','GBP');

-- C: Full refund approved (payments exist, then refunded)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(5,350.00,'CARD','SUCCESS','TXN-C-0001','GBP'),
(6,1200.00,'CARD','SUCCESS','TXN-C-0002','GBP');

-- D: Payment failed then retried and succeeded (we create both failed and success records)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(7,0.00,'CARD','FAILED','TXN-D-0001','GBP'),
(7,25.00,'CARD','SUCCESS','TXN-D-0002','GBP');

INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(8,0.00,'CARD','FAILED','TXN-D-0003','GBP'),
(8,8.00,'CARD','SUCCESS','TXN-D-0004','GBP');

-- E: Multiple partial payments (installments)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(9,800.00,'CARD','SUCCESS','TXN-E-0001','GBP'),
(9,800.00,'CARD','PENDING','TXN-E-0002','GBP');

INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(10,350.00,'CARD','SUCCESS','TXN-E-0003','GBP'),
(10,350.00,'CARD','PENDING','TXN-E-0004','GBP');

-- F: Reconciliation mismatch (payment amounts not equal to order totals)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(11,90.00,'CARD','SUCCESS','TXN-F-0001','GBP'), -- expected 100
(12,220.00,'CARD','SUCCESS','TXN-F-0002','GBP'); -- expected 200 (overpay)

-- G: High-value orders (payments present)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(13,10000.00,'WIRE','SUCCESS','TXN-G-0001','USD'),
(14,15000.00,'WIRE','SUCCESS','TXN-G-0002','USD');

-- H: Excessive refunds for one customer; payments exist
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(15,350.00,'CARD','SUCCESS','TXN-H-0001','GBP'),
(16,300.00,'CARD','SUCCESS','TXN-H-0002','GBP');

-- I: Multiple failed payments (fraud suspicion) -> we create failed records
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(17,0.00,'CARD','FAILED','TXN-I-0001','GBP'),
(18,0.00,'CARD','FAILED','TXN-I-0002','GBP');

-- J: Stock shortage but payment recorded (pending fulfillment)
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(19,400.00,'CARD','SUCCESS','TXN-J-0001','GBP'),
(20,600.00,'CARD','SUCCESS','TXN-J-0002','GBP');

-- K: Chargebacks (post-payment disputes) -> record original payments then chargeback refund/adjustment
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(21,120.00,'CARD','SUCCESS','TXN-K-0001','GBP'),
(22,80.00,'CARD','SUCCESS','TXN-K-0002','GBP');

-- L: Currency mismatch / multi-currency payments
INSERT INTO finance."Payment" ("orderID","amountPaid","paymentMethod","paymentStatus","transactionRef","currency") VALUES
(23,500.00,'CARD','SUCCESS','TXN-L-0001','EUR'),
(24,750.00,'CARD','SUCCESS','TXN-L-0002','USD');

-- ==========================================================
-- REFUNDS: create requested/approved/chargeback cases (2 per scenario group where applicable)
-- ==========================================================
-- B: Partial refunds for orders 3 and 4
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy","approvedAt") VALUES
(3, NULL, 2, 40.00, 'Item damaged', 'APPROVED', 1, now()),
(4, NULL, 3, 25.00, 'Late delivery', 'APPROVED', 1, now());

-- C: Full refunds for orders 5 and 6
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy","approvedAt") VALUES
(5, NULL, 4, 350.00, 'Not as described', 'APPROVED', 1, now()),
(6, NULL, 1, 1200.00, 'Customer cancellation', 'APPROVED', 1, now());

-- D: Failed then retried payments -> no refunds initially, but keep records
-- E: Installment orders -> create a small refund on order 9 and 10 (partial)
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(9, NULL, 1, 100.00, 'Partial credit', 'COMPLETED', 2),
(10, NULL, 4, 50.00, 'Discount applied', 'COMPLETED', 2);

-- F: Reconciliation mismatches -> create manual adjustment refunds to balance
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(11, NULL, 3, 10.00, 'Adjustment', 'COMPLETED', 1),
(12, NULL, 5, -20.00, 'Manual correction (overpay)', 'COMPLETED', 1);

-- H: Excessive refunds for same customer (orders 15,16) -> create 2 refunds
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(15, NULL, 4, 350.00, 'Return', 'APPROVED', 1),(16, NULL, 5, 180.00, 'Partial return', 'APPROVED', 1);

-- I: Multiple failed payments -> later one refunded (chargeback) for order 17
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(17, NULL, 4, 300.00, 'Chargeback', 'COMPLETED', 1),(18, NULL, 1, 450.00, 'Chargeback', 'COMPLETED', 1);

-- K: Chargebacks processed as refunds
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy","approvedAt") VALUES
(21,NULL,5,120.00,'Chargeback dispute','COMPLETED',1, now()),(22,NULL,2,80.00,'Chargeback dispute','COMPLETED',1, now());

-- J: Stock shortage -> customer requested refund but not approved (REQUESTED)
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(19,NULL,4,400.00,'Stock unavailable','REQUESTED',NULL),(20,NULL,1,600.00,'Stock unavailable','REQUESTED',NULL);

-- L: Currency mismatch -> small refund in local currency to correct
INSERT INTO finance."Refund" ("orderID","orderItemID","productID","refundAmount","reason","status","processedBy") VALUES
(23,NULL,4,10.00,'FX rounding', 'COMPLETED',2),(24,NULL,4,5.00,'FX adjustment','COMPLETED',2);

-- ==========================================================
-- Financial Anomalies & Alerts (two per interesting scenario)
-- ==========================================================
-- G: High value orders -> anomalies
INSERT INTO finance."FinancialAnomalies" ("anomalyType","description","severity","detectionRule","affectedOrderFK","affectedCustomerFK") VALUES
('HighValue','Order exceeds high-value threshold','CRITICAL','ORDER_GT_5000',13,5),
('HighValue','Order exceeds high-value threshold','CRITICAL','ORDER_GT_5000',14,6);

-- H: Excessive refunds by single customer -> anomaly
INSERT INTO finance."FinancialAnomalies" ("anomalyType","description","severity","detectionRule","affectedOrderFK","affectedCustomerFK") VALUES
('HighRefundRate','Multiple refunds for same customer','HIGH','REFUNDS_PER_CUSTOMER_GT_2',15,2),
('HighRefundRate','Multiple refunds for same customer','HIGH','REFUNDS_PER_CUSTOMER_GT_2',16,2);

-- I: Multiple failed payments -> anomaly
INSERT INTO finance."FinancialAnomalies" ("anomalyType","description","severity","detectionRule","affectedOrderFK","affectedCustomerFK") VALUES
('FailedPayments','Repeated payment failures','MEDIUM','FAILED_PAYMENTS_GT_2',17,7),
('FailedPayments','Repeated payment failures','MEDIUM','FAILED_PAYMENTS_GT_2',18,7);

-- F: Reconciliation mismatch -> anomaly entries
INSERT INTO finance."FinancialAnomalies" ("anomalyType","description","severity","detectionRule","affectedOrderFK","affectedCustomerFK") VALUES
('ReconcileMismatch','Payment != Order total','LOW','PAYMENT_MISMATCH',11,3),
('ReconcileMismatch','Payment != Order total','LOW','PAYMENT_MISMATCH',12,4);

-- J: Stock shortage causing payment for pending order -> anomaly
INSERT INTO finance."FinancialAnomalies" ("anomalyType","description","severity","detectionRule","affectedOrderFK","affectedCustomerFK") VALUES
('StockShortage','Payment taken but stock insufficient','MEDIUM','PAYMENT_FOR_PENDING_STOCK',19,8),
('StockShortage','Payment taken but stock insufficient','MEDIUM','PAYMENT_FOR_PENDING_STOCK',20,1);

-- ==========================================================
-- Alerts triggered from anomalies (link back to anomaly id)
-- ==========================================================
-- Note: anomaly IDs will be 1..n based on inserts above; we assume sequential ordering
INSERT INTO finance."Alert" ("alertType","severity","message","entityType","entityID","sourceAnomalyID") VALUES
('AnomalyAlert','CRITICAL','High value order detected','ORDER',13,1),
('AnomalyAlert','CRITICAL','High value order detected','ORDER',14,2),
('AnomalyAlert','HIGH','Multiple refunds by customer','CUSTOMER',2,3),
('AnomalyAlert','HIGH','Multiple refunds by customer','CUSTOMER',2,4),
('AnomalyAlert','MEDIUM','Repeated payment failures','ORDER',17,5),
('AnomalyAlert','MEDIUM','Repeated payment failures','ORDER',18,6),
('AnomalyAlert','LOW','Payment mismatch','ORDER',11,7),
('AnomalyAlert','LOW','Payment mismatch','ORDER',12,8),
('AnomalyAlert','MEDIUM','Payment taken but stock insufficient','ORDER',19,9),
('AnomalyAlert','MEDIUM','Payment taken but stock insufficient','ORDER',20,10);

COMMIT;






-- Added Later 1/26/2026

INSERT INTO external_sim."CustomerUpdate"
("customerID", "updatedField", "oldValue", "newValue")
VALUES
(1, 'deliveryAddress', 'London, UK', 'Manchester, UK'),
(2, 'contactNumber', '07222222222', '07333333333');





INSERT INTO external_sim."Review"
("rating", "reviewText", "customerID", "productID")
VALUES
(1, 'Good value for money', 1, 1),
(4, 'Delivery was fast and smooth', 2, 2);



INSERT INTO external_sim."StockMovement"
("inventoryID", "movementType", "quantityChanged", "reason")
VALUES
(1, 'OUT', -1, 'Order Fulfillment'),
(2, 'OUT', -1, 'Order Fulfillment');

w