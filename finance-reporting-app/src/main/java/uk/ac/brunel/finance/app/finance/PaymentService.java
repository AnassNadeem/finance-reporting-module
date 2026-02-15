package uk.ac.brunel.finance.app.finance;

import uk.ac.brunel.finance.app.anomaly.AnomalyDetectionService;

public class PaymentService {

    private final AnomalyDetectionService detector =
            new AnomalyDetectionService();

    public void processPayment(double amount) {

        detector.checkPayment(amount);

        System.out.println("Payment processed: £" + amount);
    }
}
