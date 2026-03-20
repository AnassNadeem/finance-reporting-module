package com.raez.finance.dao;

import java.util.List;

public interface AlertDaoInterface {
    List<AlertDao.AlertRow> findAlerts(boolean unresolvedOnly) throws Exception;
    void setResolved(int alertId, boolean resolved) throws Exception;
}
