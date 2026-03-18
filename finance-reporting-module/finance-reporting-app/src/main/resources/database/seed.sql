-- ==========================================================
-- RAEZ Finance – Seed data (idempotent with clean slate)
-- Run after schema.sql via DatabaseBootstrap.
-- ==========================================================

PRAGMA foreign_keys = ON;

-- Clean slate: delete in FK-safe order (children before parents)
DELETE FROM Alert;
DELETE FROM FinancialAnomalies;
DELETE FROM Refund;
DELETE FROM Payment;
DELETE FROM PasswordResetToken;
DELETE FROM FUser;
DELETE FROM DeliveryLog;
DELETE FROM Delivery;
DELETE FROM Driver;
DELETE FROM OrderItem;
DELETE FROM "Order";
DELETE FROM Review;
DELETE FROM StockMovement;
DELETE FROM InventoryRecord;
DELETE FROM Warehouse;
DELETE FROM ProductImage;
DELETE FROM ProductValidation;
DELETE FROM Product;
DELETE FROM Category;
DELETE FROM CustomerPreferences;
DELETE FROM CustomerUpdate;
DELETE FROM AdminUser;
DELETE FROM LoginCredentials;
DELETE FROM CustomerRegistration;
DELETE FROM role_permissions;
DELETE FROM sqlite_sequence;

-- ==========================================================
-- CATEGORY
-- ==========================================================
INSERT INTO Category (categoryName, description) VALUES
('Electronics','Electronic devices'),
('Home Appliances','Appliances for home'),
('Accessories','Accessory items');

-- ==========================================================
-- PRODUCT
-- ==========================================================
INSERT INTO Product (name, description, price, stock, categoryID) VALUES
('Laptop Pro','High performance laptop',1200,50,1),
('Smart Kettle','IoT enabled kettle',80,100,2),
('Wireless Mouse','Ergonomic mouse',25,200,3),
('4K Monitor','Ultra HD monitor',350,30,1),
('Coffee Maker','Drip coffee maker',60,40,2),
('USB-C Cable','Fast charging cable',8,500,3);

INSERT INTO ProductImage (productID,imageURL,isPrimary) VALUES
(1,'/images/laptop.png',1),
(2,'/images/kettle.png',1),
(3,'/images/mouse.png',1);

INSERT INTO ProductValidation (productID,validatedBy,isValid) VALUES
(1,'admin',1),
(2,'admin',1),
(3,'qa',1);

-- ==========================================================
-- CUSTOMERS (Individual + Company for date filter / reports)
-- Columns match CustomerRegistration: name, email, contactNumber, deliveryAddress, customerType, status
-- ==========================================================
INSERT INTO CustomerRegistration (name, email, contactNumber, deliveryAddress, customerType, status) VALUES
('John Smith', 'john@example.com', '0711111111', 'London', 'Individual', 'active'),
('Sara Khan', 'sara@example.com', '0722222222', 'Birmingham', 'Individual', 'active'),
('Tom Brown', 'tom@example.com', '0733333333', 'Leeds', 'Individual', 'active'),
('Aisha Ali', 'aisha@example.com', '0744444444', 'Manchester', 'Individual', 'active'),
('Acme Corp Ltd', 'procurement@acmecorp.com', '0755555555', 'London', 'Company', 'active'),
('Tech Solutions Ltd', 'orders@techsolutions.co.uk', '0766666666', 'Manchester', 'Company', 'active'),
('Global Supplies Ltd', 'accounts@globalsupplies.com', '0777777777', 'Birmingham', 'Company', 'active'),
('Metro Retail Ltd', 'buying@metroretail.co.uk', '0788888888', 'Leeds', 'Company', 'active');

INSERT INTO LoginCredentials VALUES
(1,'hash1'),(2,'hash2'),(3,'hash3'),(4,'hash4'),
(5,'hash5'),(6,'hash6'),(7,'hash7'),(8,'hash8');

INSERT INTO AdminUser (name,email) VALUES
('System Admin','admin@system.com');

INSERT INTO CustomerPreferences (customerID,preferredCategories,notificationSettings) VALUES
(1,'Electronics','EMAIL'),
(2,'Home Appliances','SMS');

-- ==========================================================
-- WAREHOUSE & INVENTORY
-- ==========================================================
INSERT INTO Warehouse (warehouseName,location) VALUES
('Main Warehouse','London'),
('Secondary Warehouse','Manchester');

INSERT INTO InventoryRecord (warehouseID,productID,quantityOnHand,minStockThreshold) VALUES
(1,1,50,5),(1,2,100,10),(2,4,30,5);

-- ==========================================================
-- ORDERS
-- ==========================================================
INSERT INTO "Order" (customerID,totalAmount,status) VALUES
(1,1200,'Completed'),
(2,160,'Completed'),
(3,350,'Completed'),
(4,25,'Completed');

INSERT INTO OrderItem (orderID,productID,quantity,unitPrice) VALUES
(1,1,1,1200),
(2,2,2,80),
(3,4,1,350),
(4,3,1,25);

-- ==========================================================
-- DELIVERY
-- ==========================================================
INSERT INTO Driver (driverName,phoneNum) VALUES
('Ali Driver','0700000001'),
('Emma Driver','0700000002');

INSERT INTO Delivery (orderID,customerAddress,orderStatus,driverID) VALUES
(1,'London','Delivered',1),
(2,'Birmingham','Delivered',2),
(3,'Manchester','Delivered',1),
(4,'Leeds','Delivered',2);

-- ==========================================================
-- FINANCE USERS (app roles: ADMIN, FINANCE_USER)
-- Primary test users (known password: "password" – BCrypt hash below):
--   admin@raez.com   / ADMIN
--   finance@raez.com / FINANCE_USER
-- Other test users (same password "password" or run DevSeedUsers for stronger passwords):
--   admin.test@raez.com, finance.test@raez.com
-- ==========================================================
INSERT INTO FUser (email,username,passwordHash,role,firstName,lastName) VALUES
('admin@raez.com','admin','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','ADMIN','Admin','Raez'),
('finance@raez.com','finance','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','FINANCE_USER','Finance','User'),
('finance.admin@sys.com','finAdmin','hash_admin','ADMIN','Finance','Admin'),
('finance.user@sys.com','finUser','hash_user','FINANCE_USER','Finance','User'),
('admin.test@raez.com','admin.test','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','ADMIN','Admin','Test'),
('finance.test@raez.com','finance.test','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy','FINANCE_USER','Finance','Test');

-- ==========================================================
-- PAYMENTS
-- ==========================================================
INSERT INTO Payment (orderID,amountPaid,paymentMethod,paymentStatus,transactionRef) VALUES
(1,1200,'CARD','SUCCESS','TXN001'),
(2,160,'CARD','SUCCESS','TXN002'),
(3,350,'CARD','SUCCESS','TXN003'),
(4,0,'CARD','FAILED','TXN004');

-- ==========================================================
-- INVOICES (backfilled for existing base orders 1-4)
-- Simple rule: dueDate = orderDate + 30 days (or now+30 if orderDate null)
-- Status derived from Payment: SUCCESS -> PAID, FAILED -> FAILED, otherwise PENDING
-- For seeded base orders, orderDate is default CURRENT_TIMESTAMP, so we approximate here.
INSERT INTO Invoice (orderID, paymentID, invoiceNumber, status, totalAmount, vatAmount, currency, issuedAt, dueDate, paidAt)
SELECT
  o.orderID,
  p.paymentID,
  printf('INV-%05d', o.orderID)              AS invoiceNumber,
  CASE
    WHEN p.paymentStatus = 'SUCCESS' THEN 'PAID'
    WHEN p.paymentStatus IN ('FAILED','CANCELLED') THEN 'FAILED'
    ELSE 'PENDING'
  END                                         AS status,
  o.totalAmount                               AS totalAmount,
  0.0                                         AS vatAmount, -- will be derived in-app using GlobalSettings
  'GBP'                                       AS currency,
  COALESCE(o.orderDate, CURRENT_TIMESTAMP)    AS issuedAt,
  COALESCE(date(o.orderDate, '+30 days'), date('now', '+30 days')) AS dueDate,
  CASE WHEN p.paymentStatus = 'SUCCESS' THEN p.paymentDate ELSE NULL END AS paidAt
FROM "Order" o
LEFT JOIN Payment p ON p.orderID = o.orderID
;

-- ==========================================================
-- REFUNDS
-- ==========================================================
INSERT INTO Refund (orderID,productID,refundAmount,reason,status,processedBy) VALUES
(2,2,40,'Damaged item','APPROVED',1),
(3,4,350,'Not needed','APPROVED',1);

-- ==========================================================
-- ANOMALIES
-- ==========================================================
INSERT INTO FinancialAnomalies
(anomalyType,description,severity,detectionRule,affectedOrderFK,affectedCustomerFK)
VALUES
('HighValue','Order exceeds threshold','CRITICAL','ORDER_GT_1000',1,1),
('FailedPayment','Payment failure detected','MEDIUM','PAYMENT_FAILED',4,4);

-- ==========================================================
-- ALERTS
-- ==========================================================
INSERT INTO Alert
(alertType,severity,message,entityType,entityID,sourceAnomalyID)
VALUES
('Anomaly','CRITICAL','High value order','ORDER',1,1),
('Payment','MEDIUM','Payment failed','ORDER',4,2);

-- ==========================================================
-- HISTORICAL DATA: 5 months of diverse Orders, Payments, Refunds
-- Company customers (5–8) have high spending for date filter testing.
-- ==========================================================
INSERT INTO "Order" (customerID,totalAmount,status,orderDate) VALUES
(1,2400,'Completed',date('now', '-150 days')),
(5,18500,'Completed',date('now', '-148 days')),
(2,160,'Completed',date('now', '-142 days')),
(6,9500,'Completed',date('now', '-138 days')),
(3,700,'Completed',date('now', '-132 days')),
(7,4200,'Completed',date('now', '-128 days')),
(1,25,'Completed',date('now', '-125 days')),
(8,9500,'Completed',date('now', '-120 days')),
(4,80,'Completed',date('now', '-115 days')),
(5,12000,'Completed',date('now', '-110 days')),
(2,350,'Completed',date('now', '-105 days')),
(6,3500,'Completed',date('now', '-100 days')),
(3,1200,'Completed',date('now', '-95 days')),
(7,6800,'Completed',date('now', '-90 days')),
(1,60,'Completed',date('now', '-85 days')),
(8,2400,'Completed',date('now', '-80 days')),
(4,25,'Completed',date('now', '-75 days')),
(5,5500,'Completed',date('now', '-70 days')),
(2,80,'Completed',date('now', '-65 days')),
(6,1600,'Completed',date('now', '-60 days')),
(3,350,'Completed',date('now', '-55 days')),
(7,1200,'Completed',date('now', '-50 days')),
(1,350,'Completed',date('now', '-45 days')),
(8,4800,'Completed',date('now', '-40 days')),
(4,1200,'Completed',date('now', '-35 days')),
(5,8900,'Completed',date('now', '-30 days')),
(2,160,'Completed',date('now', '-25 days')),
(6,2500,'Completed',date('now', '-20 days')),
(3,80,'Completed',date('now', '-18 days')),
(7,350,'Completed',date('now', '-12 days')),
(1,1200,'Completed',date('now', '-8 days')),
(8,700,'Completed',date('now', '-5 days')),
(4,350,'Completed',date('now', '-3 days')),
(1,450,'Completed',date('now', '-2 days')),
(2,1200,'Completed',date('now', '-1 day')),
(3,80,'Pending',date('now', '-4 days')),
(5,2200,'Completed',date('now', '-6 days')),
(6,1600,'Completed',date('now', '-7 days')),
(7,350,'Pending',date('now', '-9 days')),
(8,1200,'Completed',date('now', '-10 days')),
(1,60,'Completed',date('now', '-11 days')),
(2,350,'Cancelled',date('now', '-13 days')),
(4,25,'Completed',date('now', '-14 days')),
(5,4800,'Completed',date('now', '-15 days')),
(6,700,'Completed',date('now', '-16 days')),
(3,1200,'Completed',date('now', '-17 days')),
(7,160,'Pending',date('now', '-19 days')),
(8,950,'Completed',date('now', '-21 days')),
(1,80,'Completed',date('now', '-22 days')),
(2,1200,'Completed',date('now', '-23 days')),
(4,350,'Completed',date('now', '-24 days')),
(5,3200,'Completed',date('now', '-26 days')),
(6,160,'Completed',date('now', '-27 days')),
(3,25,'Cancelled',date('now', '-29 days')),
(7,2400,'Completed',date('now', '-31 days')),
(8,60,'Completed',date('now', '-33 days')),
(63,1200,'Pending',date('now', '-2 days')),
(64,80,'Cancelled',date('now', '-5 days')),
(65,350,'Completed',date('now', '-1 day')),
(66,25,'Pending',date('now', '-3 days')),
(67,160,'Cancelled',date('now', '-7 days')),
(68,1200,'Completed',date('now', '-4 days')),
(69,60,'Pending',date('now', '-6 days')),
(70,350,'Cancelled',date('now', '-8 days')),
(71,950,'Completed',date('now', '-10 days')),
(72,1200,'Pending',date('now', '-11 days')),
(73,25,'Cancelled',date('now', '-12 days')),
(74,4800,'Completed',date('now', '-14 days')),
(75,80,'Pending',date('now', '-15 days'));

INSERT INTO OrderItem (orderID,productID,quantity,unitPrice) VALUES
(5,1,2,1200),
(6,1,5,1200),(6,4,10,350),
(7,4,2,350),
(8,3,1,25),
(9,2,2,80),
(10,1,3,1200),(10,4,2,350),
(11,4,2,350),
(12,1,1,1200),
(13,5,1,60),
(14,2,1,80),
(15,1,2,1200),(15,4,4,350),
(16,4,1,350),
(17,2,2,80),
(18,1,4,1200),(18,2,5,80),
(19,3,1,25),
(20,1,2,1200),(20,5,5,60),
(21,2,2,80),
(22,4,3,350),
(23,1,1,1200),
(24,4,2,350),(24,2,2,80),
(25,1,1,1200),
(26,2,1,80),
(27,4,1,350),
(28,1,2,1200),(28,4,2,350),
(29,5,1,60),
(30,1,3,1200),(30,4,2,350),
(31,4,1,350),
(32,2,2,80),(32,3,2,25),
(33,1,1,1200),
(34,4,1,350),
(35,1,1,1200),
(36,2,1,80),(36,4,1,350),
(37,4,1,350),
(38,1,1,1200),(39,4,1,350),(40,3,1,25),(41,1,2,1200),(42,2,2,80),(43,4,1,350),(44,1,1,1200),(45,5,1,60),(46,4,1,350),(47,1,1,1200),(48,3,1,25),(49,1,1,1200),(50,4,2,350),(51,2,1,80),(52,1,1,1200),(53,4,1,350),(54,1,2,1200),(55,2,2,80),(56,4,1,350),(57,1,1,1200),(58,4,2,350),(59,1,1,1200),(60,2,1,80),(61,4,1,350),(62,5,1,60),
(63,1,1,1200),(64,2,1,80),(65,4,1,350),(66,3,1,25),(67,2,2,80),(68,1,1,1200),(69,5,1,60),(70,4,1,350),(71,4,1,350),(71,2,2,80),(72,1,1,1200),(73,3,1,25),(74,1,2,1200),(74,4,2,350),(75,2,1,80);

INSERT INTO Payment (orderID,amountPaid,paymentMethod,paymentStatus,transactionRef,paymentDate) VALUES
(5,2400,'CARD','SUCCESS','TXN005',datetime('now', '-150 days')),
(6,9500,'BANK','SUCCESS','TXN006',datetime('now', '-138 days')),
(7,160,'CARD','SUCCESS','TXN007',datetime('now', '-142 days')),
(8,9500,'BANK','SUCCESS','TXN008',datetime('now', '-138 days')),
(9,700,'CARD','SUCCESS','TXN009',datetime('now', '-132 days')),
(10,4200,'BANK','SUCCESS','TXN010',datetime('now', '-128 days')),
(11,25,'CARD','SUCCESS','TXN011',datetime('now', '-125 days')),
(12,9500,'BANK','SUCCESS','TXN012',datetime('now', '-120 days')),
(13,80,'CARD','SUCCESS','TXN013',datetime('now', '-115 days')),
(14,12000,'BANK','SUCCESS','TXN014',datetime('now', '-110 days')),
(15,350,'CARD','SUCCESS','TXN015',datetime('now', '-105 days')),
(16,3500,'BANK','SUCCESS','TXN016',datetime('now', '-100 days')),
(17,1200,'CARD','SUCCESS','TXN017',datetime('now', '-95 days')),
(18,6800,'BANK','SUCCESS','TXN018',datetime('now', '-90 days')),
(19,60,'CARD','SUCCESS','TXN019',datetime('now', '-85 days')),
(20,2400,'CARD','SUCCESS','TXN020',datetime('now', '-80 days')),
(21,25,'CARD','SUCCESS','TXN021',datetime('now', '-75 days')),
(22,5500,'BANK','SUCCESS','TXN022',datetime('now', '-70 days')),
(23,80,'CARD','SUCCESS','TXN023',datetime('now', '-65 days')),
(24,1600,'BANK','SUCCESS','TXN024',datetime('now', '-60 days')),
(25,350,'CARD','SUCCESS','TXN025',datetime('now', '-55 days')),
(26,1200,'CARD','SUCCESS','TXN026',datetime('now', '-50 days')),
(27,350,'CARD','SUCCESS','TXN027',datetime('now', '-45 days')),
(28,4800,'BANK','SUCCESS','TXN028',datetime('now', '-40 days')),
(29,1200,'CARD','SUCCESS','TXN029',datetime('now', '-35 days')),
(30,8900,'BANK','SUCCESS','TXN030',datetime('now', '-30 days')),
(31,160,'CARD','SUCCESS','TXN031',datetime('now', '-25 days')),
(32,2500,'BANK','SUCCESS','TXN032',datetime('now', '-20 days')),
(33,80,'CARD','SUCCESS','TXN033',datetime('now', '-18 days')),
(34,350,'CARD','SUCCESS','TXN034',datetime('now', '-12 days')),
(35,1200,'CARD','SUCCESS','TXN035',datetime('now', '-8 days')),
(36,700,'CARD','SUCCESS','TXN036',datetime('now', '-5 days')),
(37,350,'CARD','SUCCESS','TXN037',datetime('now', '-3 days')),
(38,450,'CARD','SUCCESS','TXN038',datetime('now', '-2 days')),
(39,1200,'CARD','SUCCESS','TXN039',datetime('now', '-1 day')),
(40,80,'CARD','PENDING','TXN040',datetime('now', '-4 days')),
(41,2200,'BANK','SUCCESS','TXN041',datetime('now', '-6 days')),
(42,1600,'CARD','SUCCESS','TXN042',datetime('now', '-7 days')),
(43,350,'CARD','PENDING','TXN043',datetime('now', '-9 days')),
(44,1200,'CARD','SUCCESS','TXN044',datetime('now', '-10 days')),
(45,60,'CARD','SUCCESS','TXN045',datetime('now', '-11 days')),
(46,350,'CARD','SUCCESS','TXN046',datetime('now', '-13 days')),
(47,25,'CARD','SUCCESS','TXN047',datetime('now', '-14 days')),
(48,4800,'BANK','SUCCESS','TXN048',datetime('now', '-15 days')),
(49,700,'CARD','SUCCESS','TXN049',datetime('now', '-16 days')),
(50,1200,'CARD','SUCCESS','TXN050',datetime('now', '-17 days')),
(51,160,'CARD','PENDING','TXN051',datetime('now', '-19 days')),
(52,950,'CARD','SUCCESS','TXN052',datetime('now', '-21 days')),
(53,80,'CARD','SUCCESS','TXN053',datetime('now', '-22 days')),
(54,1200,'CARD','SUCCESS','TXN054',datetime('now', '-23 days')),
(55,350,'CARD','SUCCESS','TXN055',datetime('now', '-24 days')),
(56,3200,'BANK','SUCCESS','TXN056',datetime('now', '-26 days')),
(57,160,'CARD','SUCCESS','TXN057',datetime('now', '-27 days')),
(58,25,'CARD','SUCCESS','TXN058',datetime('now', '-29 days')),
(59,2400,'BANK','SUCCESS','TXN059',datetime('now', '-31 days')),
(60,60,'CARD','SUCCESS','TXN060',datetime('now', '-33 days')),
(61,350,'CARD','SUCCESS','TXN061',datetime('now', '-29 days')),
(62,60,'CARD','SUCCESS','TXN062',datetime('now', '-33 days')),
(63,1200,'CARD','PENDING','TXN063',datetime('now', '-2 days')),
(64,80,'CARD','CANCELLED','TXN064',datetime('now', '-5 days')),
(65,350,'CARD','SUCCESS','TXN065',datetime('now', '-1 day')),
(66,25,'CARD','PENDING','TXN066',datetime('now', '-3 days')),
(67,160,'CARD','CANCELLED','TXN067',datetime('now', '-7 days')),
(68,1200,'CARD','SUCCESS','TXN068',datetime('now', '-4 days')),
(69,60,'CARD','PENDING','TXN069',datetime('now', '-6 days')),
(70,350,'CARD','CANCELLED','TXN070',datetime('now', '-8 days')),
(71,950,'CARD','SUCCESS','TXN071',datetime('now', '-10 days')),
(72,1200,'CARD','PENDING','TXN072',datetime('now', '-11 days')),
(73,25,'CARD','CANCELLED','TXN073',datetime('now', '-12 days')),
(74,4800,'BANK','SUCCESS','TXN074',datetime('now', '-14 days')),
(75,80,'CARD','PENDING','TXN075',datetime('now', '-15 days'));

INSERT INTO Refund (orderID,productID,refundAmount,reason,status,refundDate,processedBy) VALUES
(9,2,80,'Wrong quantity','APPROVED',date('now', '-130 days'),1),
(14,4,700,'Defective batch','APPROVED',date('now', '-108 days'),1),
(22,5,60,'Duplicate order','APPROVED',date('now', '-68 days'),1),
(30,2,160,'Cancelled line','APPROVED',date('now', '-28 days'),1);

INSERT INTO Delivery (orderID,customerAddress,orderStatus,driverID) VALUES
(5,'London','Delivered',1),
(6,'London','Delivered',1),
(7,'Birmingham','Delivered',2),
(8,'Manchester','Delivered',1),
(9,'Leeds','Delivered',2),
(10,'Birmingham','Delivered',2),
(11,'Manchester','Delivered',1),
(12,'Leeds','Delivered',2),
(13,'London','Delivered',1),
(14,'London','Delivered',1),
(15,'Birmingham','Delivered',2),
(16,'Manchester','Delivered',1),
(17,'Leeds','Delivered',2),
(18,'London','Delivered',1),
(19,'Manchester','Delivered',1),
(20,'Leeds','Delivered',2),
(21,'London','Delivered',1),
(22,'London','Delivered',1),
(23,'Birmingham','Delivered',2),
(24,'Manchester','Delivered',1),
(25,'Leeds','Delivered',2),
(26,'London','Delivered',1),
(27,'Birmingham','Delivered',2),
(28,'Leeds','Delivered',2),
(29,'Manchester','Delivered',1),
(30,'London','Delivered',1),
(31,'Birmingham','Delivered',2),
(32,'Manchester','Delivered',1),
(33,'Leeds','Delivered',2),
(34,'London','Delivered',1),
(35,'Manchester','Delivered',1),
(36,'Leeds','Delivered',2),
(37,'Birmingham','Delivered',2),
(38,'London','Delivered',1),(39,'Birmingham','Delivered',2),(40,'Leeds','In transit',2),(41,'Manchester','Delivered',1),(42,'London','Delivered',1),(43,'Birmingham','In transit',2),(44,'Leeds','Delivered',2),(45,'London','Delivered',1),(46,'Manchester','Delivered',1),(47,'Birmingham','Delivered',2),(48,'Leeds','Delivered',2),(49,'London','Delivered',1),(50,'Manchester','Delivered',1),(51,'Birmingham','In transit',2),(52,'Leeds','Delivered',2),(53,'London','Delivered',1),(54,'Manchester','Delivered',1),(55,'Birmingham','Delivered',2),(56,'Leeds','Delivered',2),(57,'London','Delivered',1),(58,'Manchester','Delivered',1),(59,'Birmingham','Delivered',2),(60,'Leeds','Delivered',2),(61,'London','Delivered',1),(62,'Manchester','Delivered',1),
(63,'London','In transit',1),(64,'Birmingham','Cancelled',2),(65,'Leeds','Delivered',2),(66,'Manchester','In transit',1),(67,'London','Cancelled',2),(68,'Birmingham','Delivered',1),(69,'Leeds','In transit',2),(70,'Manchester','Cancelled',1),(71,'London','Delivered',2),(72,'Birmingham','In transit',1),(73,'Leeds','Cancelled',2),(74,'Manchester','Delivered',1),(75,'London','In transit',2);

-- ==========================================================
-- LATE ADDITIONS (Your Jan 26 data)
-- ==========================================================
INSERT INTO CustomerUpdate (customerID,updatedField,oldValue,newValue)
VALUES
(1,'deliveryAddress','London','Manchester'),
(2,'contactNumber','0722222222','0733333333');

INSERT INTO Review (rating,reviewText,customerID,productID)
VALUES
(1,'Good value for money',1,1),
(4,'Fast delivery',2,2);

INSERT INTO StockMovement (inventoryID,movementType,quantityChanged)
VALUES
(1,'OUT',-1),
(2,'OUT',-1);

INSERT OR IGNORE INTO role_permissions (role, action) VALUES
('ADMIN', 'VIEW_DASHBOARD'),
('ADMIN', 'MANAGE_FINANCE_DATA'),
('ADMIN', 'EXPORT_REPORTS'),
('ADMIN', 'MANAGE_USERS'),
('ADMIN', 'VIEW_COMPANY_FINANCIALS'),
('ADMIN', 'VIEW_USER_MANAGEMENT'),
('FINANCE_USER', 'VIEW_DASHBOARD'),
('FINANCE_USER', 'MANAGE_FINANCE_DATA');
