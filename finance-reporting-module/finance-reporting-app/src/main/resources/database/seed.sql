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
-- CUSTOMERS
-- ==========================================================
INSERT INTO CustomerRegistration (name,email,contactNumber,deliveryAddress) VALUES
('John Smith','john@example.com','0711111111','London'),
('Sara Khan','sara@example.com','0722222222','Birmingham'),
('Tom Brown','tom@example.com','0733333333','Leeds'),
('Aisha Ali','aisha@example.com','0744444444','Manchester');

INSERT INTO LoginCredentials VALUES
(1,'hash1'),(2,'hash2'),(3,'hash3'),(4,'hash4');

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
-- DUMMY DATA: orders over the last 3 months (for presentable reports)
-- ==========================================================
INSERT INTO "Order" (customerID,totalAmount,status,orderDate) VALUES
(1,2400,'Completed',date('now', '-85 days')),
(2,160,'Completed',date('now', '-72 days')),
(3,700,'Completed',date('now', '-65 days')),
(1,25,'Completed',date('now', '-58 days')),
(4,80,'Completed',date('now', '-50 days')),
(2,350,'Completed',date('now', '-44 days')),
(3,1200,'Completed',date('now', '-38 days')),
(1,60,'Completed',date('now', '-30 days')),
(4,25,'Completed',date('now', '-25 days')),
(2,80,'Completed',date('now', '-18 days')),
(1,350,'Completed',date('now', '-12 days')),
(3,160,'Completed',date('now', '-7 days')),
(4,1200,'Completed',date('now', '-3 days'));

INSERT INTO OrderItem (orderID,productID,quantity,unitPrice) VALUES
(5,1,2,1200),
(6,2,2,80),
(7,4,2,350),
(8,3,1,25),
(9,2,1,80),
(10,4,1,350),
(11,1,1,1200),
(12,5,1,60),
(13,3,1,25),
(14,2,1,80),
(15,4,1,350),
(16,2,2,80),
(17,1,1,1200),
(18,4,1,350);

INSERT INTO Payment (orderID,amountPaid,paymentMethod,paymentStatus,transactionRef,paymentDate) VALUES
(5,2400,'CARD','SUCCESS','TXN005',datetime('now', '-85 days')),
(6,160,'CARD','SUCCESS','TXN006',datetime('now', '-72 days')),
(7,700,'CARD','SUCCESS','TXN007',datetime('now', '-65 days')),
(8,25,'CARD','SUCCESS','TXN008',datetime('now', '-58 days')),
(9,80,'CARD','SUCCESS','TXN009',datetime('now', '-50 days')),
(10,350,'CARD','SUCCESS','TXN010',datetime('now', '-44 days')),
(11,1200,'CARD','SUCCESS','TXN011',datetime('now', '-38 days')),
(12,60,'CARD','SUCCESS','TXN012',datetime('now', '-30 days')),
(13,25,'CARD','SUCCESS','TXN013',datetime('now', '-25 days')),
(14,80,'CARD','SUCCESS','TXN014',datetime('now', '-18 days')),
(15,350,'CARD','SUCCESS','TXN015',datetime('now', '-12 days')),
(16,160,'CARD','SUCCESS','TXN016',datetime('now', '-7 days')),
(17,1200,'CARD','SUCCESS','TXN017',datetime('now', '-3 days'));

INSERT INTO Delivery (orderID,customerAddress,orderStatus,driverID) VALUES
(5,'London','Delivered',1),
(6,'Birmingham','Delivered',2),
(7,'Manchester','Delivered',1),
(8,'London','Delivered',2),
(9,'Leeds','Delivered',1),
(10,'Birmingham','Delivered',2),
(11,'Manchester','Delivered',1),
(12,'London','Delivered',2),
(13,'Leeds','Delivered',1),
(14,'Birmingham','Delivered',2),
(15,'London','Delivered',1),
(16,'Birmingham','Delivered',2),
(17,'Manchester','Delivered',1),
(18,'Leeds','Delivered',2);

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
