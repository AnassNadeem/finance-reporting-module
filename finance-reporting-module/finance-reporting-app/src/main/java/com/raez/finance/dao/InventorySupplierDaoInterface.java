package com.raez.finance.dao;

import java.util.List;

public interface InventorySupplierDaoInterface {
    List<InventorySupplierDao.SupplierSnapshot> findSuppliers() throws Exception;
    double getCurrentStockValue() throws Exception;
    List<InventorySupplierDao.LowStockSnapshot> findLowStockItems() throws Exception;
}
