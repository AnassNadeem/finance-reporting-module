package uk.ac.brunel.finance.app.finance;

import uk.ac.brunel.finance.app.anomaly.AnomalyDetectionService;

public class OrderService {

    private final AnomalyDetectionService detector =
            new AnomalyDetectionService();

    public void placeOrder(double totalValue) {

        detector.checkOrder(totalValue);

        System.out.println("Order placed: £" + totalValue);
    }
}
