package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to FinancialAnomalies table for the Notifications & Alerts page.
 */
public class FinancialAnomalyDao {

    /**
     * Returns anomalies, optionally only unresolved. Ordered by alertDate descending.
     */
    public List<AnomalyRow> findAnomalies(boolean unresolvedOnly) throws Exception {
        String sql = "SELECT anomalyID, anomalyType, description, severity, detectionRule, alertDate, isResolved " +
                "FROM FinancialAnomalies ";
        if (unresolvedOnly) {
            sql += "WHERE isResolved = 0 ";
        }
        sql += "ORDER BY alertDate DESC LIMIT 100";

        List<AnomalyRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new AnomalyRow(
                        rs.getInt("anomalyID"),
                        rs.getString("anomalyType"),
                        rs.getString("description"),
                        rs.getString("severity"),
                        rs.getString("detectionRule"),
                        rs.getString("alertDate"),
                        rs.getInt("isResolved") == 1
                ));
            }
        }
        return list;
    }

    public static class AnomalyRow {
        private final int anomalyID;
        private final String anomalyType;
        private final String description;
        private final String severity;
        private final String detectionRule;
        private final String alertDate;
        private final boolean resolved;

        public AnomalyRow(int anomalyID, String anomalyType, String description, String severity,
                          String detectionRule, String alertDate, boolean resolved) {
            this.anomalyID = anomalyID;
            this.anomalyType = anomalyType;
            this.description = description;
            this.severity = severity;
            this.detectionRule = detectionRule;
            this.alertDate = alertDate;
            this.resolved = resolved;
        }

        public int getAnomalyID() { return anomalyID; }
        public String getAnomalyType() { return anomalyType; }
        public String getDescription() { return description; }
        public String getSeverity() { return severity; }
        public String getDetectionRule() { return detectionRule; }
        public String getAlertDate() { return alertDate; }
        public boolean isResolved() { return resolved; }
    }
}
