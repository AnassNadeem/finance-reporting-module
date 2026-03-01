package uk.ac.brunel.finance.app.anomaly;

public class AnomalyDetectionService {

    private final FinancialAnomalyService anomalyService =
            new FinancialAnomalyService();
    private final AlertService alertService =
            new AlertService();

    /* ---------- PAYMENT ---------- */

    public void checkPayment(double amount) {

        if (amount < 0) {
            raise(
                AnomalyType.NEGATIVE_AMOUNT,
                "Negative payment detected: " + amount,
                AlertSeverity.CRITICAL
            );
        }

        if (amount > 10_000) {
            raise(
                AnomalyType.LARGE_PAYMENT,
                "Large payment detected: £" + amount,
                AlertSeverity.HIGH
            );
        }
    }

    /* ---------- REFUND ---------- */

    public void checkRefund(double refundAmount, double originalPayment) {

        if (refundAmount > originalPayment) {
            raise(
                AnomalyType.EXCESSIVE_REFUND,
                "Refund exceeds original payment",
                AlertSeverity.HIGH
            );
        }
    }

    /* ---------- ORDER ---------- */

    public void checkOrder(double totalValue) {

        if (totalValue > 50_000) {
            raise(
                AnomalyType.HIGH_ORDER_VALUE,
                "High-value order detected: £" + totalValue,
                AlertSeverity.MEDIUM
            );
        }
    }

    /* ---------- SHARED ---------- */

    private void raise(
            AnomalyType type,
            String message,
            AlertSeverity severity) {

        anomalyService.recordAnomaly(
            type.name(),                 // anomalyType → TEXT
            message,                     // description
            severity.name(),             // severity → TEXT
            "ANOMALY_DETECTION_ENGINE",   // detectionRule (documented)
            null,                         // affectedCustomerFK
            null,                         // affectedOrderFK
            null                          // affectedProductFK
        );

        alertService.createAlert(
            message,
            severity
        );
    }

}
