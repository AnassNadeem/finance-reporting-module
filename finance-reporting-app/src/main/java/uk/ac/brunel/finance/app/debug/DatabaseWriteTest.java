package uk.ac.brunel.finance.app.debug;
import uk.ac.brunel.finance.app.anomaly.FinancialAnomalyService;


public class DatabaseWriteTest {

    public static void main(String[] args) {

        System.out.println("=== DB WRITE TEST START ===");

        FinancialAnomalyService service = new FinancialAnomalyService();

        service.recordAnomaly(
            "PAYMENT_MISMATCH",
            "Payment amount does not match order total",
            "HIGH",
            "amountPaid != order.totalAmount",
            null,
            1,
            null
        );

        System.out.println("=== DB WRITE TEST END ===");
    }
}
