package com.raez.finance.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * DTO for Detailed Reports – Customer Report table.
 */
public class CustomerReportRow {

    private final SimpleStringProperty customerId;
    private final SimpleStringProperty name;
    private final SimpleStringProperty type;
    private final SimpleStringProperty country;
    private final SimpleIntegerProperty totalOrders;
    private final SimpleDoubleProperty  totalSpent;
    private final SimpleDoubleProperty  avgOrderValue;
    private final SimpleStringProperty lastPurchase;

    public CustomerReportRow(
            String  customerId,
            String  name,
            String  type,
            String  country,
            int     totalOrders,
            double  totalSpent,
            double  avgOrderValue,
            String  lastPurchase) {

        this.customerId    = new SimpleStringProperty(customerId  != null ? customerId  : "—");
        this.name          = new SimpleStringProperty(name        != null ? name        : "—");
        this.type          = new SimpleStringProperty(type        != null ? type        : "Individual");
        this.country       = new SimpleStringProperty(country     != null ? country     : "—");
        this.totalOrders   = new SimpleIntegerProperty(totalOrders);
        this.totalSpent    = new SimpleDoubleProperty(totalSpent);
        this.avgOrderValue = new SimpleDoubleProperty(avgOrderValue);
        this.lastPurchase  = new SimpleStringProperty(lastPurchase != null ? lastPurchase : "—");
    }

    // ── Properties ───────────────────────────────────────────────────────
    public SimpleStringProperty  customerIdProperty()   { return customerId;    }
    public SimpleStringProperty  nameProperty()         { return name;          }
    public SimpleStringProperty  typeProperty()         { return type;          }
    public SimpleStringProperty  countryProperty()      { return country;       }
    public SimpleIntegerProperty totalOrdersProperty()  { return totalOrders;   }
    public SimpleDoubleProperty  totalSpentProperty()   { return totalSpent;    }
    public SimpleDoubleProperty  avgOrderValueProperty(){ return avgOrderValue; }
    public SimpleStringProperty  lastPurchaseProperty() { return lastPurchase;  }

    // ── Getters ───────────────────────────────────────────────────────────
    public String  getCustomerId()    { return customerId.get();    }
    public String  getName()          { return name.get();          }
    public String  getType()          { return type.get();          }
    public String  getCountry()       { return country.get();       }
    public int     getTotalOrders()   { return totalOrders.get();   }
    public double  getTotalSpent()    { return totalSpent.get();    }
    public double  getAvgOrderValue() { return avgOrderValue.get(); }
    public String  getLastPurchase()  { return lastPurchase.get();  }
}