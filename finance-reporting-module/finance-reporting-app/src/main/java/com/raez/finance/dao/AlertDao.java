package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to Alert table for the Notifications & Alerts page.
 */
public class AlertDao {

    /**
     * Returns alerts, optionally only unresolved. Ordered by createdAt descending.
     */
    public List<AlertRow> findAlerts(boolean unresolvedOnly) throws Exception {
        String sql = "SELECT alertID, alertType, severity, message, createdAt, entityType, entityID, isResolved " +
                "FROM Alert ";
        if (unresolvedOnly) {
            sql += "WHERE isResolved = 0 ";
        }
        sql += "ORDER BY createdAt DESC LIMIT 100";

        List<AlertRow> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new AlertRow(
                        rs.getInt("alertID"),
                        rs.getString("alertType"),
                        rs.getString("severity"),
                        rs.getString("message"),
                        rs.getString("createdAt"),
                        rs.getString("entityType"),
                        rs.getObject("entityID") != null ? rs.getInt("entityID") : null,
                        rs.getInt("isResolved") == 1
                ));
            }
        }
        return list;
    }

    /**
     * Marks an alert resolved/unresolved in the database.
     * Stores resolvedAt when resolving; clears it when unresolving.
     */
    public void setResolved(int alertId, boolean resolved) throws Exception {
        String sql = resolved
                ? "UPDATE Alert SET isResolved = 1, resolvedAt = CURRENT_TIMESTAMP WHERE alertID = ?"
                : "UPDATE Alert SET isResolved = 0, resolvedAt = NULL, resolvedBy = NULL WHERE alertID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, alertId);
            ps.executeUpdate();
        }
    }

    public static class AlertRow {
        private final int alertID;
        private final String alertType;
        private final String severity;
        private final String message;
        private final String createdAt;
        private final String entityType;
        private final Integer entityID;
        private final boolean resolved;

        public AlertRow(int alertID, String alertType, String severity, String message, String createdAt,
                        String entityType, Integer entityID, boolean resolved) {
            this.alertID = alertID;
            this.alertType = alertType;
            this.severity = severity;
            this.message = message;
            this.createdAt = createdAt;
            this.entityType = entityType;
            this.entityID = entityID;
            this.resolved = resolved;
        }

        public int getAlertID() { return alertID; }
        public String getAlertType() { return alertType; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getCreatedAt() { return createdAt; }
        public String getEntityType() { return entityType; }
        public Integer getEntityID() { return entityID; }
        public boolean isResolved() { return resolved; }
    }
}
