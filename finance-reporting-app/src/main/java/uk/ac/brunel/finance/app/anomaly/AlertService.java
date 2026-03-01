package uk.ac.brunel.finance.app.anomaly;

import uk.ac.brunel.finance.app.database.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AlertService {

    public void createAlert(String message, AlertSeverity severity) {

        String sql =
            "INSERT INTO Alert (" +
            "alertType, severity, message, createdAt, isResolved" +
            ") VALUES (?, ?, ?, CURRENT_TIMESTAMP, 0)";

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "FINANCIAL_ANOMALY");
            stmt.setString(2, severity.name());
            stmt.setString(3, message);

            stmt.executeUpdate();
            System.out.println("🚨 Alert created successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to create alert");
            e.printStackTrace();
        }
    }
}
