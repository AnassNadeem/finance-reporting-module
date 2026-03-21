package com.raez.finance.util;

import com.raez.finance.service.GlobalSettingsService;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Financial calculations and filter utilities using global settings (VAT, currency).
 * Use for COGS, margins, AOV, cumulative income, and VAT-aware aggregates.
 */
public final class FinanceCalculationUtil {

    private FinanceCalculationUtil() {}

    public static double getVatRate() {
        return GlobalSettingsService.getInstance().getVatRate();
    }

    /** Net amount from gross (gross includes VAT). */
    public static double grossToNet(double gross) {
        return GlobalSettingsService.getInstance().grossToNet(gross);
    }

    /** VAT amount contained in a gross value. */
    public static double vatFromGross(double gross) {
        return GlobalSettingsService.getInstance().vatFromGross(gross);
    }

    /** Cost of goods sold: sum of (quantity * unitCost) for items. */
    public static double cogs(double quantity, double unitCost) {
        return quantity * unitCost;
    }

    /** Profit margin percentage: (revenue - cost) / revenue * 100; 0 if revenue <= 0. */
    public static double profitMarginPercent(double revenue, double cost) {
        if (revenue <= 0) return 0;
        return ((revenue - cost) / revenue) * 100.0;
    }

    /** Average order value from a list of order totals. */
    public static double averageOrderValue(List<Double> orderTotals) {
        if (orderTotals == null || orderTotals.isEmpty()) return 0;
        double sum = orderTotals.stream().mapToDouble(Double::doubleValue).sum();
        return sum / orderTotals.size();
    }

    /** Cumulative income from a list of payment amounts (e.g. successful payments). */
    public static double cumulativeIncome(List<Double> paymentAmounts) {
        if (paymentAmounts == null) return 0;
        return paymentAmounts.stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Total VAT from a list of gross amounts. */
    public static double totalVatFromGrossAmounts(List<Double> grossAmounts) {
        if (grossAmounts == null) return 0;
        return grossAmounts.stream().mapToDouble(FinanceCalculationUtil::vatFromGross).sum();
    }

    /**
     * Filter a list by date range (inclusive). dateGetter extracts LocalDate from each item.
     */
    public static <T> List<T> filterByDateRange(List<T> items, LocalDate from, LocalDate to,
                                                Function<T, LocalDate> dateGetter) {
        if (items == null) return List.of();
        return items.stream()
                .filter(t -> {
                    LocalDate d = dateGetter.apply(t);
                    if (d == null) return false;
                    if (from != null && d.isBefore(from)) return false;
                    if (to != null && d.isAfter(to)) return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Filter by customer id. idGetter extracts customer identifier (e.g. id or name) from each item.
     */
    public static <T> List<T> filterByCustomer(List<T> items, Object customerIdOrName,
                                               Function<T, ?> customerGetter) {
        if (items == null || customerIdOrName == null) return items != null ? items : List.of();
        String key = customerIdOrName.toString().trim();
        if (key.isEmpty()) return items;
        return items.stream()
                .filter(t -> key.equalsIgnoreCase(String.valueOf(customerGetter.apply(t))))
                .collect(Collectors.toList());
    }

    /**
     * Filter by product category. categoryGetter extracts category name from each item.
     */
    public static <T> List<T> filterByCategory(List<T> items, String category,
                                               Function<T, String> categoryGetter) {
        if (items == null) return List.of();
        if (category == null || category.isBlank() || "All Categories".equalsIgnoreCase(category.trim()))
            return items;
        String cat = category.trim();
        return items.stream()
                .filter(t -> cat.equalsIgnoreCase(categoryGetter.apply(t)))
                .collect(Collectors.toList());
    }
}
