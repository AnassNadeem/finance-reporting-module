package uk.ac.brunel.finance.app.finance;

import uk.ac.brunel.finance.app.anomaly.AnomalyDetectionService;

public class RefundService {

    private final AnomalyDetectionService detector =
            new AnomalyDetectionService();

    public void processRefund(
            double refundAmount,
            double originalPayment) {

        detector.checkRefund(refundAmount, originalPayment);

        System.out.println("Refund processed: £" + refundAmount);
    }
}
