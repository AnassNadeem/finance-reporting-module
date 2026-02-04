PRAGMA foreign_keys = ON;



-- ==========================================================
-- CLEAN SLATE (SQLite way)
-- ==========================================================
DELETE FROM Alert;
DELETE FROM FinancialAnomalies;
DELETE FROM Refund;
DELETE FROM Payment;
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
-- FINANCE USERS (ROLE LEVELS)
-- ==========================================================
INSERT INTO FUser (email,username,passwordHash,role) VALUES
('finance.admin@sys.com','finAdmin','hash_admin','ADMIN'),
('finance.user@sys.com','finUser','hash_user','ANALYST');

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

COMMIT;
