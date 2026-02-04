
PRAGMA foreign_keys = ON;

-- ===========================================================
-- NC1605 Phase2: FULL SQLite Schema
-- Converted from PostgreSQL to SQLite (COMPLETE)
-- ===========================================================

-- =========================
-- EXTERNAL SIM (SIMULATED)
-- =========================

-- -------------------------
-- CATEGORY
-- -------------------------
CREATE TABLE IF NOT EXISTS Category (
  categoryID INTEGER PRIMARY KEY AUTOINCREMENT,
  categoryName TEXT NOT NULL,
  description TEXT,
  parentID INTEGER,
  isActive INTEGER DEFAULT 1,
  FOREIGN KEY (parentID) REFERENCES Category(categoryID) ON DELETE SET NULL
);

-- -------------------------
-- PRODUCT
-- -------------------------
CREATE TABLE IF NOT EXISTS Product (
  productID INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  description TEXT,
  price REAL NOT NULL DEFAULT 0.00,
  stock INTEGER NOT NULL DEFAULT 0,
  status TEXT DEFAULT 'active',
  categoryID INTEGER,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (categoryID) REFERENCES Category(categoryID) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ProductImage (
  imageID INTEGER PRIMARY KEY AUTOINCREMENT,
  productID INTEGER NOT NULL,
  imageURL TEXT NOT NULL,
  fileType TEXT,
  sizeKB INTEGER,
  isPrimary INTEGER DEFAULT 0,
  FOREIGN KEY (productID) REFERENCES Product(productID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ProductValidation (
  validationID INTEGER PRIMARY KEY AUTOINCREMENT,
  productID INTEGER NOT NULL,
  validatedBy TEXT,
  validationDate TEXT,
  isValid INTEGER,
  validationMessage TEXT,
  FOREIGN KEY (productID) REFERENCES Product(productID) ON DELETE CASCADE
);

-- -------------------------
-- CUSTOMER
-- -------------------------
CREATE TABLE IF NOT EXISTS CustomerRegistration (
  customerID INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  contactNumber TEXT,
  deliveryAddress TEXT,
  idCardImage TEXT,
  status TEXT DEFAULT 'active',
  registeredAt TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS LoginCredentials (
  customerID INTEGER PRIMARY KEY,
  passwordHash TEXT NOT NULL,
  FOREIGN KEY (customerID) REFERENCES CustomerRegistration(customerID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS AdminUser (
  adminID INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT,
  email TEXT UNIQUE
);

CREATE TABLE IF NOT EXISTS CustomerUpdate (
  updateID INTEGER PRIMARY KEY AUTOINCREMENT,
  adminID INTEGER,
  customerID INTEGER,
  updatedField TEXT,
  oldValue TEXT,
  newValue TEXT,
  updateDate TEXT DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (adminID) REFERENCES AdminUser(adminID) ON DELETE SET NULL,
  FOREIGN KEY (customerID) REFERENCES CustomerRegistration(customerID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS CustomerPreferences (
  preferenceID INTEGER PRIMARY KEY AUTOINCREMENT,
  customerID INTEGER NOT NULL,
  preferredCategories TEXT,
  notificationSettings TEXT,
  deliveryInstructions TEXT,
  FOREIGN KEY (customerID) REFERENCES CustomerRegistration(customerID) ON DELETE CASCADE
);

-- -------------------------
-- ORDERS
-- -------------------------
CREATE TABLE IF NOT EXISTS "Order" (
  orderID INTEGER PRIMARY KEY AUTOINCREMENT,
  customerID INTEGER NOT NULL,
  orderDate TEXT DEFAULT CURRENT_TIMESTAMP,
  totalAmount REAL NOT NULL DEFAULT 0.00,
  status TEXT DEFAULT 'Processing',
  FOREIGN KEY (customerID) REFERENCES CustomerRegistration(customerID)
);

CREATE TABLE IF NOT EXISTS OrderItem (
  orderItemID INTEGER PRIMARY KEY AUTOINCREMENT,
  orderID INTEGER NOT NULL,
  productID INTEGER NOT NULL,
  quantity INTEGER DEFAULT 1,
  unitPrice REAL DEFAULT 0.00,
  FOREIGN KEY (orderID) REFERENCES "Order"(orderID) ON DELETE CASCADE,
  FOREIGN KEY (productID) REFERENCES Product(productID)
);

-- -------------------------
-- DELIVERY & LOGISTICS
-- -------------------------
CREATE TABLE IF NOT EXISTS Driver (
  driverID INTEGER PRIMARY KEY AUTOINCREMENT,
  licenceNumber TEXT,
  phoneNum TEXT,
  email TEXT,
  driverName TEXT
);

CREATE TABLE IF NOT EXISTS Delivery (
  deliveryID INTEGER PRIMARY KEY AUTOINCREMENT,
  orderID INTEGER NOT NULL,
  customerAddress TEXT,
  orderStatus TEXT DEFAULT 'Pending',
  orderDate TEXT,
  numOfItems INTEGER DEFAULT 1,
  driverID INTEGER,
  warehouseID INTEGER,
  FOREIGN KEY (orderID) REFERENCES "Order"(orderID) ON DELETE CASCADE,
  FOREIGN KEY (driverID) REFERENCES Driver(driverID) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS DeliveryLog (
  logID INTEGER PRIMARY KEY AUTOINCREMENT,
  deliveryID INTEGER NOT NULL,
  driverID INTEGER,
  timeDelivered TEXT,
  statusChange TEXT,
  logDate TEXT DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (deliveryID) REFERENCES Delivery(deliveryID) ON DELETE CASCADE,
  FOREIGN KEY (driverID) REFERENCES Driver(driverID) ON DELETE SET NULL
);

-- -------------------------
-- WAREHOUSE & INVENTORY
-- -------------------------
CREATE TABLE IF NOT EXISTS Warehouse (
  warehouseID INTEGER PRIMARY KEY AUTOINCREMENT,
  warehouseName TEXT,
  location TEXT,
  contactEmail TEXT,
  capacityLimit INTEGER
);

CREATE TABLE IF NOT EXISTS InventoryRecord (
  inventoryID INTEGER PRIMARY KEY AUTOINCREMENT,
  warehouseID INTEGER NOT NULL,
  productID INTEGER NOT NULL,
  quantityOnHand INTEGER DEFAULT 0,
  minStockThreshold INTEGER DEFAULT 0,
  lastRestockDate TEXT,
  reorderQuantity INTEGER DEFAULT 0,
  lowStockFlag INTEGER DEFAULT 0,
  UNIQUE (warehouseID, productID),
  FOREIGN KEY (warehouseID) REFERENCES Warehouse(warehouseID) ON DELETE CASCADE,
  FOREIGN KEY (productID) REFERENCES Product(productID)
);

CREATE TABLE IF NOT EXISTS StockMovement (
  movementID INTEGER PRIMARY KEY AUTOINCREMENT,
  inventoryID INTEGER NOT NULL,
  fromWarehouseID INTEGER,
  toWarehouseID INTEGER,
  quantityChanged INTEGER NOT NULL,
  movementType TEXT,
  movementDate TEXT,
  FOREIGN KEY (inventoryID) REFERENCES InventoryRecord(inventoryID) ON DELETE CASCADE,
  FOREIGN KEY (fromWarehouseID) REFERENCES Warehouse(warehouseID) ON DELETE SET NULL,
  FOREIGN KEY (toWarehouseID) REFERENCES Warehouse(warehouseID) ON DELETE SET NULL
);

-- -------------------------
-- REVIEWS
-- -------------------------
CREATE TABLE IF NOT EXISTS Review (
  reviewID INTEGER PRIMARY KEY AUTOINCREMENT,
  rating INTEGER CHECK (rating BETWEEN 1 AND 5),
  reviewText TEXT,
  reviewDate TEXT DEFAULT CURRENT_DATE,
  isReported INTEGER DEFAULT 0,
  customerID INTEGER NOT NULL,
  productID INTEGER NOT NULL,
  UNIQUE (customerID, productID),
  FOREIGN KEY (customerID) REFERENCES CustomerRegistration(customerID) ON DELETE CASCADE,
  FOREIGN KEY (productID) REFERENCES Product(productID) ON DELETE CASCADE
);

-- =========================
-- FINANCE SCHEMA
-- =========================

CREATE TABLE IF NOT EXISTS FUser (
  userID INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT UNIQUE NOT NULL,
  username TEXT UNIQUE NOT NULL,
  passwordHash TEXT NOT NULL,
  role TEXT NOT NULL,
  firstName TEXT,
  lastName TEXT,
  phone TEXT,
  isActive INTEGER DEFAULT 1,
  lastLogin TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS Payment (
  paymentID INTEGER PRIMARY KEY AUTOINCREMENT,
  orderID INTEGER NOT NULL,
  amountPaid REAL NOT NULL,
  currency TEXT DEFAULT 'GBP',
  paymentMethod TEXT,
  paymentStatus TEXT DEFAULT 'PENDING',
  transactionRef TEXT UNIQUE,
  paymentDate TEXT DEFAULT CURRENT_TIMESTAMP,
  notes TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP,
  updatedAt TEXT DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (orderID) REFERENCES "Order"(orderID)
);

CREATE TABLE IF NOT EXISTS Refund (
  refundID INTEGER PRIMARY KEY AUTOINCREMENT,
  orderID INTEGER NOT NULL,
  orderItemID INTEGER,
  productID INTEGER,
  refundAmount REAL NOT NULL,
  refundDate TEXT DEFAULT CURRENT_TIMESTAMP,
  reason TEXT,
  status TEXT DEFAULT 'REQUESTED',
  processedBy INTEGER,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP,
  approvedAt TEXT,
  FOREIGN KEY (orderID) REFERENCES "Order"(orderID),
  FOREIGN KEY (orderItemID) REFERENCES OrderItem(orderItemID),
  FOREIGN KEY (productID) REFERENCES Product(productID),
  FOREIGN KEY (processedBy) REFERENCES FUser(userID)
);

CREATE TABLE IF NOT EXISTS FinancialAnomalies (
  anomalyID INTEGER PRIMARY KEY AUTOINCREMENT,
  anomalyType TEXT,
  description TEXT,
  severity TEXT,
  detectionRule TEXT,
  alertDate TEXT DEFAULT CURRENT_TIMESTAMP,
  isResolved INTEGER DEFAULT 0,
  resolvedByUserID INTEGER,
  affectedCustomerFK INTEGER,
  affectedOrderFK INTEGER,
  affectedProductFK INTEGER,
  FOREIGN KEY (resolvedByUserID) REFERENCES FUser(userID),
  FOREIGN KEY (affectedCustomerFK) REFERENCES CustomerRegistration(customerID),
  FOREIGN KEY (affectedOrderFK) REFERENCES "Order"(orderID),
  FOREIGN KEY (affectedProductFK) REFERENCES Product(productID)
);

CREATE TABLE IF NOT EXISTS Alert (
  alertID INTEGER PRIMARY KEY AUTOINCREMENT,
  alertType TEXT,
  severity TEXT,
  message TEXT,
  createdAt TEXT DEFAULT CURRENT_TIMESTAMP,
  entityType TEXT,
  entityID INTEGER,
  isResolved INTEGER DEFAULT 0,
  resolvedBy INTEGER,
  resolvedAt TEXT,
  sourceAnomalyID INTEGER,
  FOREIGN KEY (resolvedBy) REFERENCES FUser(userID),
  FOREIGN KEY (sourceAnomalyID) REFERENCES FinancialAnomalies(anomalyID)
);

-- ===========================================================
-- END OF SQLITE SCHEMA
-- ===========================================================

