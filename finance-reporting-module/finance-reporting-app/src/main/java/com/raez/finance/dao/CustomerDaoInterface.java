package com.raez.finance.dao;

import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.TopBuyerRow;

import java.sql.SQLException;
import java.util.List;

public interface CustomerDaoInterface {
    List<CustomerReportRow> findReportRows(String typeFilter, String countryFilter, String companyName, String search, int limit, int offset) throws SQLException;
    List<String> findCompanyNames() throws SQLException;
    int countReportRows(String typeFilter, String countryFilter, String companyName, String search) throws SQLException;
    List<TopBuyerRow> findTopBuyers(int limit) throws SQLException;
    List<CustomerDao.MonthlyCount> findMonthlyOrderCounts() throws SQLException;
    int getTotalCustomerCount() throws SQLException;
    int getCompanyCustomerCount() throws SQLException;
    double getTotalRevenue() throws SQLException;
    List<String> findRefundAlerts() throws SQLException;
    List<String> findProductIssueAlerts() throws SQLException;
    List<String> findCountryOptions() throws SQLException;
}
