package com.raez.finance.dao;

import com.raez.finance.model.ProductReportRow;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface ProductDaoInterface {
    List<ProductReportRow> findReportRows(LocalDate from, LocalDate to, String categoryFilter, String search) throws SQLException;
    List<ProductReportRow> findReportRows(LocalDate from, LocalDate to, String categoryFilter, String search, int limit, int offset) throws SQLException;
    int countReportRows(LocalDate from, LocalDate to, String categoryFilter, String search) throws SQLException;
    List<ProductDao.CategoryRevenueProfit> findCategoryRevenueProfit() throws SQLException;
    List<ProductDao.CategoryRevenueProfit> findCategoryRevenueProfit(LocalDate from, LocalDate to) throws SQLException;
    List<String> findCategoryNames() throws SQLException;
}
