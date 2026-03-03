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
(4,350,'Completed',date('now', '-3 days'));

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
(37,4,1,350);

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
(37,350,'CARD','SUCCESS','TXN037',datetime('now', '-3 days'));

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
(37,'Birmingham','Delivered',2);

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
('FINANCE_USER', 'VIEW_DASHBOARD'),
('FINANCE_USER', 'MANAGE_FINANCE_DATA');
