package com.raez.finance.dao;

import com.raez.finance.model.CustomerReportRow;
import com.raez.finance.model.TopBuyerRow;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface CustomerDaoInterface {
    List<CustomerReportRow> findReportRows(LocalDate orderFrom, LocalDate orderTo, String typeFilter, String countryFilter, String companyName, String search, int limit, int offset) throws SQLException;
    List<String> findCompanyNames() throws SQLException;
    int countReportRows(LocalDate orderFrom, LocalDate orderTo, String typeFilter, String countryFilter, String companyName, String search) throws SQLException;
    List<TopBuyerRow> findTopBuyers(int limit) throws SQLException;
    List<TopBuyerRow> findTopBuyersInRange(LocalDate from, LocalDate to, String insightCustomerTypeFilter, int limit, int offset) throws SQLException;
    int countTopBuyersInRange(LocalDate from, LocalDate to, String insightCustomerTypeFilter) throws SQLException;
    double sumOrderTotalInBuyerFilterRange(LocalDate from, LocalDate to, String insightCustomerTypeFilter) throws SQLException;
    List<CustomerDao.MonthlyCount> findMonthlyOrderCounts() throws SQLException;
    List<CustomerDao.MonthlyCount> findMonthlyOrderCounts(LocalDate from, LocalDate to) throws SQLException;
    List<CustomerDao.MonthlySplit> findMonthlyOrderCountsByCustomerType(LocalDate from, LocalDate to) throws SQLException;
    int getTotalCustomerCount() throws SQLException;
    int getCompanyCustomerCount() throws SQLException;
    double getTotalRevenue() throws SQLException;
    List<String> findRefundAlerts() throws SQLException;
    List<String> findProductIssueAlerts() throws SQLException;
    List<String> findCountryOptions() throws SQLException;
}
