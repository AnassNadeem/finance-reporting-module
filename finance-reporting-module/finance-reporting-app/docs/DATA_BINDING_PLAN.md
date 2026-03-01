# RAEZ Finance – Data Binding Plan (TableViews, Charts, ComboBoxes)

This document outlines how to dynamically populate UI controls from the SQLite database (`finance_raez.db`) and schema.

---

## 1. Overview of FXML Views and Controls

| View | TableViews | Charts | ComboBoxes / Filters |
|------|------------|--------|----------------------|
| **Overview.fxml** | — | `chartSales` (LineChart), `chartPie` (PieChart) | `cmbDateRange`, `cmbCategory`, `dpStartDate`, `dpEndDate` |
| **DetailedReports.fxml** | `tblOrders`, `tblProducts`, `tblCustomers` | — | `cmbDateRange`, `cmbOrderStatus`, `cmbProductCategory`, `cmbCustomerType`, `cmbCustomerCompany`, `cmbCustomerCountry`, `dpStartDate`, `dpEndDate` |
| **CustomerInsights.fxml** | `tblTopBuyers` | `chartFrequency` (BarChart) | `cmbCustomerFilter` |
| **ProductProfitability.fxml** | `tblProducts` | `chartProfitability` (BarChart) | `cmbCategoryFilter` |
| **Settings.fxml** | `tblUsers` (User Management) | — | `cmbModalRole` (in create-user modal) |

---

## 2. Relevant SQLite Tables (Schema)

- **Order / OrderItem**: `"Order"` (orderID, customerID, orderDate, totalAmount, status), `OrderItem` (orderItemID, orderID, productID, quantity, unitPrice)
- **Customer**: `CustomerRegistration` (customerID, name, email, contactNumber, deliveryAddress, status)
- **Product / Category**: `Product` (productID, name, price, categoryID, stock, status), `Category` (categoryID, categoryName)
- **Payment / Refund**: `Payment` (orderID, amountPaid, paymentStatus, paymentDate), `Refund` (orderID, refundAmount, status)
- **App users**: `FUser` (userID, email, username, role, firstName, lastName, isActive, lastLogin)

---

## 3. Step-by-Step Implementation Plan

### Step 3.1 – DAO / Repository Layer

1. **Create or extend DAOs** for read-only reporting (no new tables):
   - `OrderDao`: query orders with optional filters (date range, status); join Order + OrderItem + Product + CustomerRegistration for table rows.
   - `CustomerDao`: list customers; aggregate total spent, order count, last order date (from Order).
   - `ProductDao`: list products with category name; aggregate revenue/cost/profit from OrderItem + Product (and Refund if needed).
   - `PaymentDao` / **reuse existing**: aggregate totals for Overview (total sales, outstanding, refunds).
   - `FUserDao` (existing): add `findAll()` for Settings User Management table.

2. **Define DTOs / model classes** for table rows (e.g. `OrderReportRow`, `CustomerInsightRow`, `ProductProfitabilityRow`, `UserTableRow`) so TableViews and charts use typed objects.

### Step 3.2 – Overview.fxml

1. **ComboBoxes**: In `OverviewController.initialize()`, populate `cmbDateRange` (e.g. "Last 7 days", "Last 30 days", "Last 90 days", "This year") and `cmbCategory` from `Category` table via a small DAO or `CategoryDao`.
2. **Metric labels**: Run aggregate queries (e.g. SUM from Payment/Order, COUNT customers, COUNT orders, AVG order value, top product by quantity/revenue) and set `lblTotalSales`, `lblTotalProfit`, `lblOutstanding`, `lblRefunds`, `lblCustomers`, `lblOrders`, `lblAOV`, `lblPopular`. Use date range and category from combos when applicable.
3. **LineChart (chartSales)**: Query time-series data (e.g. order date or payment date grouped by day/week/month). Add `XYChart.Series` and data points to the chart.
4. **PieChart (chartPie)**: Query breakdown (e.g. by category revenue or order count). Add `PieChart.Data` slices.

### Step 3.3 – DetailedReports.fxml

1. **Tab switching**: Already wired (`handleTabOrders`, `handleTabProducts`, `handleTabCustomers`). Show/hide the appropriate TableView and filter boxes per tab.
2. **ComboBoxes**: Populate `cmbOrderStatus` from distinct Order.status (or fixed list); `cmbProductCategory` from Category; `cmbCustomerType`/`cmbCustomerCompany`/`cmbCustomerCountry` from distinct CustomerRegistration columns or seed data.
3. **TableView tblOrders**: Bind columns to `OrderReportRow` (orderID, customer name, product summary, amount, date, status). Use `OrderDao` with date range and status filter. Set `tableView.setItems(FXCollections.observableList(rows))` and set column `setCellValueFactory` with property/lambda.
4. **TableView tblProducts** / **tblCustomers**: Same pattern: DAO returns list of DTOs, bind to TableView columns.

### Step 3.4 – CustomerInsights.fxml

1. **ComboBox**: Populate `cmbCustomerFilter` (e.g. "All", or list of customers from CustomerRegistration).
2. **Metric labels**: `lblTotalCustomers`, `lblAvgSpending`, `lblAvgFrequency`, `lblCompanyCustomers` from aggregates (COUNT, SUM/COUNT, order frequency, etc.).
3. **BarChart (chartFrequency)**: e.g. orders per customer segment or per month; add series and categories.
4. **TableView tblTopBuyers**: Columns (Rank, Name, Type, Country, Total Spent, Order Count, AOV, Last Purchase). Query CustomerRegistration + Order aggregates, sort by total spent, take top N; bind to observable list.

### Step 3.5 – ProductProfitability.fxml

1. **ComboBox**: Populate `cmbCategoryFilter` from Category table.
2. **Metric labels**: `lblTotalRevenue`, `lblTotalProfit`, `lblAvgMargin`, `lblLowMarginCount` from Product + OrderItem (revenue = quantity*unitPrice; cost from Product or simplified; margin %).
3. **BarChart (chartProfitability)**: e.g. profit or revenue by category/product; add series.
4. **TableView tblProducts**: Columns (Name, Category, Revenue, Cost, Profit, Margin, Units Sold, Trend). Query Product + OrderItem (and Category for name); compute derived fields; bind to observable list. Optionally bind `vboxHighPerformers` / `vboxNeedsAttention` from same data (top/bottom by margin or profit).

### Step 3.6 – Settings.fxml (User Management)

1. **TableView tblUsers**: Bind to FUser data (username, full name, email, role, status, lastLogin). Use `FUserDao.findAll()` (to add) or existing DAO extended. Columns: `colUsername`, `colName`, `colEmail`, `colRole`, `colStatus`, `colLastLogin`; Actions column for edit/delete buttons.
2. **ComboBox cmbModalRole**: Set items to `UserRole.values()` or "ADMIN", "FINANCE_USER" for the create-user modal.
3. **Create user modal**: On save, call existing `UserService.createUser(...)` with values from modal fields; refresh `tblUsers` and close modal.

### Step 3.7 – Shared Conventions

- Use **JavaFX ObservableList** for all TableView items; update list when filters change and re-run query.
- Use **background thread** (e.g. `Task` + `Platform.runLater`) for heavy queries so the UI stays responsive.
- **Date range**: Apply `dpStartDate`/`dpEndDate` or parsed "Last 90 days" to SQL `WHERE orderDate BETWEEN ? AND ?`.
- **Export CSV/PDF**: Use the same DTOs/lists as the TableView; iterate and write to file (or use a small library for PDF).

---

## 4. Suggested Order of Implementation

1. **CategoryDao** + **Overview ComboBoxes** (cmbDateRange, cmbCategory) and **Overview metric labels** (simple aggregates).
2. **Overview charts** (LineChart, PieChart) with one series each.
3. **OrderDao** + **DetailedReports Order tab** (tblOrders + filters).
4. **ProductDao** + **DetailedReports Product tab**; **CustomerDao** + **Customer tab**.
5. **CustomerInsights** (ComboBox, metrics, BarChart, tblTopBuyers).
6. **ProductProfitability** (ComboBox, metrics, BarChart, tblProducts).
7. **Settings User Management** (FUserDao.findAll, tblUsers, create-user modal with UserService).

This keeps the existing database login intact and builds data binding on top of the current routing and MainLayout shell.
