package com.raez.finance.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * DTO for Detailed Reports – Order Report table.
 */
public class OrderReportRow {

    private final SimpleStringProperty orderId;
    private final SimpleStringProperty customer;
    private final SimpleStringProperty product;
    private final SimpleDoubleProperty amount;
    private final SimpleStringProperty date;
    private final SimpleStringProperty status;

    public OrderReportRow(String orderId, String customer, String product, double amount, String date, String status) {
        this.orderId = new SimpleStringProperty(orderId);
        this.customer = new SimpleStringProperty(customer);
        this.product = new SimpleStringProperty(product);
        this.amount = new SimpleDoubleProperty(amount);
        this.date = new SimpleStringProperty(date);
        this.status = new SimpleStringProperty(status);
    }

    public SimpleStringProperty orderIdProperty() { return orderId; }
    public SimpleStringProperty customerProperty() { return customer; }
    public SimpleStringProperty productProperty() { return product; }
    public SimpleDoubleProperty amountProperty() { return amount; }
    public SimpleStringProperty dateProperty() { return date; }
    public SimpleStringProperty statusProperty() { return status; }

    public String getOrderId() { return orderId.get(); }
    public String getCustomer() { return customer.get(); }
    public String getProduct() { return product.get(); }
    public double getAmount() { return amount.get(); }
    public String getDate() { return date.get(); }
    public String getStatus() { return status.get(); }
}
