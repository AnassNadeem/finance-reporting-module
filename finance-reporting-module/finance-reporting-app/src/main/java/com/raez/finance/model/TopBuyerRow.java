package com.raez.finance.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/** DTO for Customer Insights – Top Buyers table (rank + customer aggregates). */
public class TopBuyerRow {

    private final SimpleIntegerProperty rank;
    private final SimpleStringProperty name;
    private final SimpleStringProperty type;
    private final SimpleStringProperty country;
    private final SimpleDoubleProperty totalSpent;
    private final SimpleIntegerProperty orderCount;
    private final SimpleDoubleProperty avgOrderValue;
    private final SimpleStringProperty lastPurchase;

    public TopBuyerRow(int rank, String name, String type, String country,
                       double totalSpent, int orderCount, double avgOrderValue, String lastPurchase) {
        this.rank = new SimpleIntegerProperty(rank);
        this.name = new SimpleStringProperty(name);
        this.type = new SimpleStringProperty(type);
        this.country = new SimpleStringProperty(country);
        this.totalSpent = new SimpleDoubleProperty(totalSpent);
        this.orderCount = new SimpleIntegerProperty(orderCount);
        this.avgOrderValue = new SimpleDoubleProperty(avgOrderValue);
        this.lastPurchase = new SimpleStringProperty(lastPurchase != null ? lastPurchase : "—");
    }

    public SimpleIntegerProperty rankProperty() { return rank; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty typeProperty() { return type; }
    public SimpleStringProperty countryProperty() { return country; }
    public SimpleDoubleProperty totalSpentProperty() { return totalSpent; }
    public SimpleIntegerProperty orderCountProperty() { return orderCount; }
    public SimpleDoubleProperty avgOrderValueProperty() { return avgOrderValue; }
    public SimpleStringProperty lastPurchaseProperty() { return lastPurchase; }

    public int getRank() { return rank.get(); }
    public String getName() { return name.get(); }
    public String getType() { return type.get(); }
    public String getCountry() { return country.get(); }
    public double getTotalSpent() { return totalSpent.get(); }
    public int getOrderCount() { return orderCount.get(); }
    public double getAvgOrderValue() { return avgOrderValue.get(); }
    public String getLastPurchase() { return lastPurchase.get(); }
}
