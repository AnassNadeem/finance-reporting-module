package com.raez.finance.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * DTO for Detailed Reports – Product Report table.
 * Cost is optional (schema has no cost column; use 0 or future cost source).
 */
public class ProductReportRow {

    private final SimpleStringProperty productId;
    private final SimpleStringProperty name;
    private final SimpleStringProperty category;
    private final SimpleDoubleProperty cost;
    private final SimpleDoubleProperty salePrice;
    private final SimpleDoubleProperty profit;
    private final SimpleIntegerProperty unitsSold;
    private final SimpleDoubleProperty revenue;
    private final SimpleDoubleProperty marginPercent;
    private final SimpleStringProperty trend;

    public ProductReportRow(String productId, String name, String category,
                            double cost, double salePrice, double profit, int unitsSold, double revenue) {
        this(productId, name, category, cost, salePrice, profit, unitsSold, revenue, "—");
    }

    public ProductReportRow(String productId, String name, String category,
                            double cost, double salePrice, double profit, int unitsSold, double revenue, String trend) {
        this.productId = new SimpleStringProperty(productId);
        this.name = new SimpleStringProperty(name);
        this.category = new SimpleStringProperty(category);
        this.cost = new SimpleDoubleProperty(cost);
        this.salePrice = new SimpleDoubleProperty(salePrice);
        this.profit = new SimpleDoubleProperty(profit);
        this.unitsSold = new SimpleIntegerProperty(unitsSold);
        this.revenue = new SimpleDoubleProperty(revenue);
        this.marginPercent = new SimpleDoubleProperty(revenue > 0 ? (profit / revenue) * 100 : 0);
        this.trend = new SimpleStringProperty(trend != null ? trend : "—");
    }

    public SimpleStringProperty productIdProperty() { return productId; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty categoryProperty() { return category; }
    public SimpleDoubleProperty costProperty() { return cost; }
    public SimpleDoubleProperty salePriceProperty() { return salePrice; }
    public SimpleDoubleProperty profitProperty() { return profit; }
    public SimpleIntegerProperty unitsSoldProperty() { return unitsSold; }
    public SimpleDoubleProperty revenueProperty() { return revenue; }
    public SimpleDoubleProperty marginPercentProperty() { return marginPercent; }
    public SimpleStringProperty trendProperty() { return trend; }

    public String getProductId() { return productId.get(); }
    public String getName() { return name.get(); }
    public String getCategory() { return category.get(); }
    public double getCost() { return cost.get(); }
    public double getSalePrice() { return salePrice.get(); }
    public double getProfit() { return profit.get(); }
    public int getUnitsSold() { return unitsSold.get(); }
    public double getRevenue() { return revenue.get(); }
    public double getMarginPercent() { return marginPercent.get(); }
    public String getTrend() { return trend.get(); }
}
