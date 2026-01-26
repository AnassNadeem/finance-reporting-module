-- ===========================================================
-- NC1605 Phase2: Full schema for external_sim (Parts 1-6)
-- and finance (Part 7). Paste-run while connected to
-- database: nc1605_finance
-- ===========================================================

-- -------------------------
-- 1) EXTERNAL (SIMULATED) SCHEMA
-- -------------------------
-- Products (Member 1)
CREATE TABLE IF NOT EXISTS external_sim."Product" (
  "productID"            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name"                 VARCHAR(255) NOT NULL,
  "description"          TEXT,
  "price"                NUMERIC(12,2) NOT NULL DEFAULT 0.00,
  "stock"                INTEGER NOT NULL DEFAULT 0,
  "status"               VARCHAR(50) DEFAULT 'active',
  "categoryID"           INTEGER, -- FK to Category below
  "createdAt"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "updatedAt"            TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS external_sim."ProductImage" (
  "imageID"              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "productID"            INTEGER NOT NULL,
  "imageURL"             TEXT NOT NULL,
  "fileType"             VARCHAR(50),
  "sizeKB"               INTEGER,
  "isPrimary"            BOOLEAN DEFAULT FALSE,
  CONSTRAINT fk_productimage_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS external_sim."ProductValidation" (
  "validationID"         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "productID"            INTEGER NOT NULL,
  "validatedBy"          VARCHAR(255),
  "validationDate"       TIMESTAMP WITH TIME ZONE,
  "isValid"              BOOLEAN,
  "validationMessage"    TEXT,
  CONSTRAINT fk_productvalidation_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS external_sim."Category" (
  "categoryID"           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "categoryName"         VARCHAR(255) NOT NULL,
  "description"          TEXT,
  "parentID"             INTEGER, -- self FK (nullable)
  "isActive"             BOOLEAN DEFAULT TRUE,
  CONSTRAINT fk_category_parent FOREIGN KEY ("parentID")
    REFERENCES external_sim."Category"("categoryID") ON DELETE SET NULL
);

-- Add FK from Product.categoryID → Category
ALTER TABLE external_sim."Product"
ADD CONSTRAINT fk_product_category
FOREIGN KEY ("categoryID")
REFERENCES external_sim."Category"("categoryID")
ON DELETE SET NULL;


-- Customers (Member 2)
CREATE TABLE IF NOT EXISTS external_sim."CustomerRegistration" (
  "customerID"           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name"                 VARCHAR(255) NOT NULL,
  "email"                VARCHAR(255) UNIQUE NOT NULL,
  "contactNumber"        VARCHAR(50),
  "deliveryAddress"      TEXT,
  "idCardImage"          TEXT,
  "status"               VARCHAR(50) DEFAULT 'active',
  "registeredAt"         TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS external_sim."LoginCredentials" (
  "customerID"           INTEGER PRIMARY KEY, -- 1:1 with CustomerRegistration
  "passwordHash"         VARCHAR(512) NOT NULL,
  CONSTRAINT fk_login_customer FOREIGN KEY ("customerID")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS external_sim."AdminUser" (
  "adminID"              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "name"                 VARCHAR(255),
  "email"                VARCHAR(255) UNIQUE
);

CREATE TABLE IF NOT EXISTS external_sim."CustomerUpdate" (
  "updateID"             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "adminID"              INTEGER,
  "customerID"           INTEGER,
  "updatedField"         VARCHAR(255),
  "oldValue"             TEXT,
  "newValue"             TEXT,
  "updateDate"           TIMESTAMP WITH TIME ZONE DEFAULT now(),
  CONSTRAINT fk_customerupdate_admin FOREIGN KEY ("adminID")
    REFERENCES external_sim."AdminUser"("adminID") ON DELETE SET NULL,
  CONSTRAINT fk_customerupdate_customer FOREIGN KEY ("customerID")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS external_sim."CustomerPreferences" (
  "preferenceID"         INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "customerID"           INTEGER NOT NULL,
  "preferredCategories"  TEXT,
  "notificationSettings" TEXT,
  "deliveryInstructions" TEXT,
  CONSTRAINT fk_pref_customer FOREIGN KEY ("customerID")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE CASCADE
);

-- Orders (Member 3)
CREATE TABLE IF NOT EXISTS external_sim."Order" (
  "orderID"              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "customerID"           INTEGER NOT NULL,
  "orderDate"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "totalAmount"          NUMERIC(12,2) NOT NULL DEFAULT 0.00,
  "status"               VARCHAR(50) DEFAULT 'Processing',
  CONSTRAINT fk_order_customer FOREIGN KEY ("customerID")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS external_sim."OrderItem" (
  "orderItemID"          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "orderID"              INTEGER NOT NULL,
  "productID"            INTEGER NOT NULL,
  "quantity"             INTEGER NOT NULL DEFAULT 1,
  "unitPrice"            NUMERIC(12,2) NOT NULL DEFAULT 0.00,
  CONSTRAINT fk_orderitem_order FOREIGN KEY ("orderID")
    REFERENCES external_sim."Order"("orderID") ON DELETE CASCADE,
  CONSTRAINT fk_orderitem_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE RESTRICT
);

-- Delivery & Logistics (Member 4)
CREATE TABLE IF NOT EXISTS external_sim."Driver" (
  "driverID"             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "licenceNumber"        VARCHAR(100),
  "phoneNum"             VARCHAR(50),
  "email"                VARCHAR(255),
  "driverName"           VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS external_sim."Delivery" (
  "deliveryID"           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "orderID"              INTEGER NOT NULL,
  "customerAddress"      TEXT,
  "orderStatus"          VARCHAR(50) DEFAULT 'Pending',
  "orderDate"            TIMESTAMP WITH TIME ZONE,
  "numOfItems"           INTEGER DEFAULT 1,
  "driverID"             INTEGER,
  "warehouseID"          INTEGER, -- reference Inventory/Warehouse
  CONSTRAINT fk_delivery_order FOREIGN KEY ("orderID")
    REFERENCES external_sim."Order"("orderID") ON DELETE CASCADE,
  CONSTRAINT fk_delivery_driver FOREIGN KEY ("driverID")
    REFERENCES external_sim."Driver"("driverID") ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS external_sim."DeliveryLog" (
  "logID"                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "deliveryID"           INTEGER NOT NULL,
  "driverID"             INTEGER,
  "timeDelivered"        TIMESTAMP WITH TIME ZONE,
  "statusChange"         VARCHAR(100),
  "logDate"              TIMESTAMP WITH TIME ZONE DEFAULT now(),
  CONSTRAINT fk_deliverylog_delivery FOREIGN KEY ("deliveryID")
    REFERENCES external_sim."Delivery"("deliveryID") ON DELETE CASCADE,
  CONSTRAINT fk_deliverylog_driver FOREIGN KEY ("driverID")
    REFERENCES external_sim."Driver"("driverID") ON DELETE SET NULL
);

-- Inventory / Warehousing (Member 5)
CREATE TABLE IF NOT EXISTS external_sim."Warehouse" (
  "warehouseID"          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "warehouseName"        VARCHAR(255),
  "location"             TEXT,
  "contactEmail"         VARCHAR(255),
  "capacityLimit"        INTEGER
);

CREATE TABLE IF NOT EXISTS external_sim."InventoryRecord" (
  "inventoryID"          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "warehouseID"          INTEGER NOT NULL,
  "productID"            INTEGER NOT NULL,
  "quantityOnHand"       INTEGER NOT NULL DEFAULT 0,
  "minStockThreshold"    INTEGER DEFAULT 0,
  "lastRestockDate"      DATE,
  "reorderQuantity"      INTEGER DEFAULT 0,
  "lowStockFlag"         BOOLEAN DEFAULT FALSE,
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY ("warehouseID")
    REFERENCES external_sim."Warehouse"("warehouseID") ON DELETE CASCADE,
  CONSTRAINT fk_inventory_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE RESTRICT,
  CONSTRAINT uq_inventory_warehouse_product UNIQUE ("warehouseID","productID")
);

CREATE TABLE IF NOT EXISTS external_sim."StockMovement" (
  "movementID"           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "inventoryID"          INTEGER NOT NULL,
  "fromWarehouseID"      INTEGER,
  "toWarehouseID"        INTEGER,
  "quantityChanged"      INTEGER NOT NULL,
  "movementType"         VARCHAR(50),
  "movementDate"         DATE,
  CONSTRAINT fk_stockmovement_inventory FOREIGN KEY ("inventoryID")
    REFERENCES external_sim."InventoryRecord"("inventoryID") ON DELETE CASCADE,
  CONSTRAINT fk_stockmovement_fromwh FOREIGN KEY ("fromWarehouseID")
    REFERENCES external_sim."Warehouse"("warehouseID") ON DELETE SET NULL,
  CONSTRAINT fk_stockmovement_towh FOREIGN KEY ("toWarehouseID")
    REFERENCES external_sim."Warehouse"("warehouseID") ON DELETE SET NULL
);

-- Reviews & Ratings (Member 6)
CREATE TABLE IF NOT EXISTS external_sim."Review" (
  "reviewID"             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "rating"               INTEGER CHECK (rating >= 1 AND rating <= 5),
  "reviewText"           TEXT,
  "reviewDate"           DATE DEFAULT CURRENT_DATE,
  "isReported"           BOOLEAN DEFAULT FALSE,
  "customerID"           INTEGER NOT NULL,
  "productID"            INTEGER NOT NULL,
  CONSTRAINT fk_review_customer FOREIGN KEY ("customerID")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE CASCADE,
  CONSTRAINT fk_review_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE CASCADE,
  CONSTRAINT uq_review_customer_product UNIQUE ("customerID","productID")
);

-- -------------------------
-- 2) FINANCE SCHEMA (Part 7 — YOURS)
-- -------------------------
CREATE TABLE IF NOT EXISTS finance."FUser" (
  "userID"               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "email"                VARCHAR(255) UNIQUE NOT NULL,
  "username"             VARCHAR(100) UNIQUE NOT NULL,
  "passwordHash"         VARCHAR(512) NOT NULL,
  "role"                 VARCHAR(100) NOT NULL,
  "firstName"            VARCHAR(255),
  "lastName"             VARCHAR(255),
  "phone"                VARCHAR(50),
  "isActive"             BOOLEAN DEFAULT TRUE,
  "lastLogin"            TIMESTAMP WITH TIME ZONE,
  "createdAt"            TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS finance."Payment" (
  "paymentID"            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "orderID"              INTEGER NOT NULL,
  "amountPaid"           NUMERIC(12,2) NOT NULL,
  "currency"             VARCHAR(10) DEFAULT 'GBP',
  "paymentMethod"        VARCHAR(100),
  "paymentStatus"        VARCHAR(50) DEFAULT 'PENDING',
  "transactionRef"       VARCHAR(255) UNIQUE,
  "paymentDate"          TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "notes"                TEXT,
  "createdAt"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "updatedAt"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  CONSTRAINT fk_payment_order FOREIGN KEY ("orderID")
    REFERENCES external_sim."Order"("orderID") ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_payment_orderid ON finance."Payment"("orderID");

CREATE TABLE IF NOT EXISTS finance."Refund" (
  "refundID"             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "orderID"              INTEGER NOT NULL,
  "orderItemID"          INTEGER,
  "productID"            INTEGER,
  "refundAmount"         NUMERIC(12,2) NOT NULL,
  "refundDate"           TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "reason"               TEXT,
  "status"               VARCHAR(50) DEFAULT 'REQUESTED',
  "processedBy"          INTEGER, -- FK to FUser.userID
  "createdAt"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "approvedAt"           TIMESTAMP WITH TIME ZONE NULL,
  CONSTRAINT fk_refund_order FOREIGN KEY ("orderID")
    REFERENCES external_sim."Order"("orderID") ON DELETE RESTRICT,
  CONSTRAINT fk_refund_orderitem FOREIGN KEY ("orderItemID")
    REFERENCES external_sim."OrderItem"("orderItemID") ON DELETE SET NULL,
  CONSTRAINT fk_refund_product FOREIGN KEY ("productID")
    REFERENCES external_sim."Product"("productID") ON DELETE SET NULL,
  CONSTRAINT fk_refund_processedby FOREIGN KEY ("processedBy")
    REFERENCES finance."FUser"("userID") ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_refund_orderid ON finance."Refund"("orderID");

CREATE TABLE IF NOT EXISTS finance."FinancialAnomalies" (
  "anomalyID"            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "anomalyType"          VARCHAR(255),
  "description"          TEXT,
  "severity"             VARCHAR(50),               -- ADDED: severity for triage (justified)
  "detectionRule"        VARCHAR(255),              -- ADDED: which rule triggered (justified)
  "alertDate"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "isResolved"           BOOLEAN DEFAULT FALSE,
  "resolvedByUserID"     INTEGER,                   -- FK to FUser
  "affectedCustomerFK"   INTEGER,                   -- FK to CustomerRegistration
  "affectedOrderFK"      INTEGER,                   -- FK to Order
  "affectedProductFK"    INTEGER,                   -- FK to Product
  CONSTRAINT fk_anomaly_resolver FOREIGN KEY ("resolvedByUserID")
    REFERENCES finance."FUser"("userID") ON DELETE SET NULL,
  CONSTRAINT fk_anomaly_customer FOREIGN KEY ("affectedCustomerFK")
    REFERENCES external_sim."CustomerRegistration"("customerID") ON DELETE SET NULL,
  CONSTRAINT fk_anomaly_order FOREIGN KEY ("affectedOrderFK")
    REFERENCES external_sim."Order"("orderID") ON DELETE SET NULL,
  CONSTRAINT fk_anomaly_product FOREIGN KEY ("affectedProductFK")
    REFERENCES external_sim."Product"("productID") ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_anomaly_order ON finance."FinancialAnomalies"("affectedOrderFK");

CREATE TABLE IF NOT EXISTS finance."Alert" (
  "alertID"              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  "alertType"            VARCHAR(255),
  "severity"             VARCHAR(50),
  "message"              TEXT,
  "createdAt"            TIMESTAMP WITH TIME ZONE DEFAULT now(),
  "entityType"           VARCHAR(100),
  "entityID"             INTEGER,
  "isResolved"           BOOLEAN DEFAULT FALSE,
  "resolvedBy"           INTEGER,    -- FK to FUser.userID
  "resolvedAt"           TIMESTAMP WITH TIME ZONE,
  "sourceAnomalyID"      INTEGER,    -- FK to FinancialAnomalies.anomalyID (nullable)
  CONSTRAINT fk_alert_resolvedby FOREIGN KEY ("resolvedBy")
    REFERENCES finance."FUser"("userID") ON DELETE SET NULL,
  CONSTRAINT fk_alert_sourceanomaly FOREIGN KEY ("sourceAnomalyID")
    REFERENCES finance."FinancialAnomalies"("anomalyID") ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_alert_createdat ON finance."Alert"("createdAt");

-- ===========================================================
-- End of schema creation
-- ===========================================================
