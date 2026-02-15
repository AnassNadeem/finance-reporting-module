package uk.ac.brunel.finance.app.anomaly;

import uk.ac.brunel.finance.app.database.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class FinancialAnomalyService {

    public void recordAnomaly(
            String anomalyType,
            String description,
            String severity,
            String detectionRule,
            Integer affectedCustomerId,
            Integer affectedOrderId,
            Integer affectedProductId
    ) {

        String sql =
            "INSERT INTO FinancialAnomalies (" +
            "anomalyType, description, severity, detectionRule, " +
            "affectedCustomerFK, affectedOrderFK, affectedProductFK" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, anomalyType);
            stmt.setString(2, description);
            stmt.setString(3, severity);
            stmt.setString(4, detectionRule);

            if (affectedCustomerId != null) {
                stmt.setInt(5, affectedCustomerId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }

            if (affectedOrderId != null) {
                stmt.setInt(6, affectedOrderId);
            } else {
                stmt.setNull(6, java.sql.Types.INTEGER);
            }

            if (affectedProductId != null) {
                stmt.setInt(7, affectedProductId);
            } else {
                stmt.setNull(7, java.sql.Types.INTEGER);
            }

            stmt.executeUpdate();
            System.out.println("✅ Financial anomaly recorded successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to record financial anomaly");
            e.printStackTrace();
        }
    }
}
