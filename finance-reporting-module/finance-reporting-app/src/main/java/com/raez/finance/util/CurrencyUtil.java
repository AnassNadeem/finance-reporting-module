package com.raez.finance.util;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

/**
 * Shared GBP currency formatting for the finance UI.
 */
public final class CurrencyUtil {

    public static final String CURRENCY_SYMBOL = "£";

    private CurrencyUtil() {}

    /** Format amount as GBP (e.g. "£1,234.56"). */
    public static String formatCurrency(double value) {
        return String.format("%s%,.2f", CURRENCY_SYMBOL, value);
    }

    /** Cell factory for TableColumn&lt;S, Number&gt; that displays values as GBP. */
    public static <S> Callback<TableColumn<S, Number>, TableCell<S, Number>> currencyCellFactory() {
        return col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatCurrency(item.doubleValue()));
            }
        };
    }
}
