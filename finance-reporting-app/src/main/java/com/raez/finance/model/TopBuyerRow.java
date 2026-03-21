package com.raez.finance.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * DTO for Customer Insights – Top Buyers table.
 */
public class TopBuyerRow {

    private final SimpleIntegerProperty rank;
    private final SimpleStringProperty  name;
    private final SimpleStringProperty  type;
    private final SimpleStringProperty  country;
    private final SimpleDoubleProperty  totalSpent;
    private final SimpleIntegerProperty totalOrders;
    private final SimpleDoubleProperty  avgOrderValue;
    private final SimpleStringProperty  lastPurchase;

    public TopBuyerRow(
            int    rank,
            String name,
            String type,
            String country,
            double totalSpent,
            int    totalOrders,
            double avgOrderValue,
            String lastPurchase) {

        this.rank          = new SimpleIntegerProperty(rank);
        this.name          = new SimpleStringProperty(name         != null ? name         : "—");
        this.type          = new SimpleStringProperty(type         != null ? type         : "Individual");
        this.country       = new SimpleStringProperty(country      != null ? country      : "—");
        this.totalSpent    = new SimpleDoubleProperty(totalSpent);
        this.totalOrders   = new SimpleIntegerProperty(totalOrders);
        this.avgOrderValue = new SimpleDoubleProperty(avgOrderValue);
        this.lastPurchase  = new SimpleStringProperty(lastPurchase != null ? lastPurchase : "—");
    }

    // ── Properties ───────────────────────────────────────────────────────
    public SimpleIntegerProperty rankProperty()         { return rank;          }
    public SimpleStringProperty  nameProperty()         { return name;          }
    public SimpleStringProperty  typeProperty()         { return type;          }
    public SimpleStringProperty  countryProperty()      { return country;       }
    public SimpleDoubleProperty  totalSpentProperty()   { return totalSpent;    }
    public SimpleIntegerProperty totalOrdersProperty()  { return totalOrders;   }
    public SimpleDoubleProperty  avgOrderValueProperty(){ return avgOrderValue; }
    public SimpleStringProperty  lastPurchaseProperty() { return lastPurchase;  }

    // ── Getters ───────────────────────────────────────────────────────────
    public int    getRank()          { return rank.get();          }
    public String getName()          { return name.get();          }
    public String getType()          { return type.get();          }
    public String getCountry()       { return country.get();       }
    public double getTotalSpent()    { return totalSpent.get();    }
    public int    getTotalOrders()   { return totalOrders.get();   }
    public double getAvgOrderValue() { return avgOrderValue.get(); }
    public String getLastPurchase()  { return lastPurchase.get();  }
}