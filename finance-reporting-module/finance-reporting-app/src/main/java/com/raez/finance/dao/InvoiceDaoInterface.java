package com.raez.finance.dao;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceDaoInterface {
    List<InvoiceDao.InvoiceRow> findInvoices(LocalDate from, LocalDate to, String statusFilter, String search, int limit, int offset) throws Exception;
}
