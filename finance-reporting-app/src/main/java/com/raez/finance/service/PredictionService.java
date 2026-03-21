package com.raez.finance.service;

import com.raez.finance.dao.CustomerDao;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Linear regression on monthly aggregates for dashboard and AI Insights (Commons Math).
 */
public class PredictionService {

    private final DashboardService dashboardService = new DashboardService();

    public record SalesTrendPrediction(
            double slopePerMonth,
            double nextMonthEstimate,
            double meanSales,
            String trendLabel
    ) {}

    /**
     * Revenue trend with residual scale (indicative band; not a formal prediction interval).
     */
    public record DetailedRevenueForecast(
            double slopePerMonth,
            double nextMonthEstimate,
            double meanSales,
            String trendLabel,
            double rmseResidual
    ) {}

    /**
     * First-order month counts regressed to suggest next-month acquisition volume (proxy).
     */
    public record AcquisitionForecast(
            double nextMonthEstimate,
            double meanNewPerMonth,
            String trendLabel,
            double rmseResidual
    ) {}

    /**
     * Uses calendar months between {@code from} and {@code to} (inclusive) to fit a trend on total sales.
     */
    public SalesTrendPrediction predictMonthlySales(LocalDate from, LocalDate to, String category) throws SQLException {
        DetailedRevenueForecast d = predictDetailedRevenue(from, to, category);
        return new SalesTrendPrediction(d.slopePerMonth(), d.nextMonthEstimate(), d.meanSales(), d.trendLabel());
    }

    public DetailedRevenueForecast predictDetailedRevenue(LocalDate from, LocalDate to, String category)
            throws SQLException {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusMonths(5).withDayOfMonth(1);

        YearMonth ymEnd = YearMonth.from(to);
        YearMonth ymStart = YearMonth.from(from);
        SimpleRegression reg = new SimpleRegression();
        double sum = 0;
        int n = 0;
        int idx = 0;
        for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
            LocalDate f = ym.atDay(1);
            LocalDate t = ym.atEndOfMonth();
            if (t.isAfter(to)) t = to;
            double sales = dashboardService.getTotalSales(f, t, category);
            reg.addData(idx++, sales);
            sum += sales;
            n++;
        }
        if (n == 0) {
            return new DetailedRevenueForecast(0, 0, 0, "—", 0);
        }
        double mean = sum / n;
        if (n < 2) {
            return new DetailedRevenueForecast(0, mean, mean, "stable", 0);
        }
        double next = reg.predict(idx);
        double slope = reg.getSlope();
        double rmse = Math.sqrt(Math.max(0, reg.getMeanSquareError()));
        String label;
        if (Math.abs(slope) < mean * 0.02 && mean > 0) label = "stable";
        else if (slope > 0) label = "upward";
        else label = "downward";

        return new DetailedRevenueForecast(slope, Math.max(0, next), mean, label, rmse);
    }

    /**
     * Month of first order per customer (proxy for “new customer” acquisition).
     */
    public AcquisitionForecast predictAcquisitionFromFirstOrders(LocalDate from, LocalDate to) throws SQLException {
        if (to == null) to = LocalDate.now();
        if (from == null) from = to.minusMonths(11).withDayOfMonth(1);

        CustomerDao dao = new CustomerDao();
        List<CustomerDao.MonthlyCount> monthly = dao.findFirstOrderMonthCounts(from, to);
        Map<String, Integer> byMonth = new HashMap<>();
        for (CustomerDao.MonthlyCount m : monthly) {
            byMonth.put(m.month, m.count);
        }

        YearMonth ymEnd = YearMonth.from(to);
        YearMonth ymStart = YearMonth.from(from);
        SimpleRegression reg = new SimpleRegression();
        double sum = 0;
        int n = 0;
        int idx = 0;
        for (YearMonth ym = ymStart; !ym.isAfter(ymEnd); ym = ym.plusMonths(1)) {
            String key = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            int c = byMonth.getOrDefault(key, 0);
            reg.addData(idx++, c);
            sum += c;
            n++;
        }
        if (n == 0) {
            return new AcquisitionForecast(0, 0, "—", 0);
        }
        double mean = sum / (double) n;
        if (n < 2) {
            return new AcquisitionForecast(mean, mean, "stable", 0);
        }
        double next = reg.predict(idx);
        double slope = reg.getSlope();
        double rmse = Math.sqrt(Math.max(0, reg.getMeanSquareError()));
        String label;
        if (Math.abs(slope) < 0.5 && mean > 0) label = "stable";
        else if (slope > 0) label = "upward";
        else label = "downward";

        return new AcquisitionForecast(Math.max(0, next), mean, label, rmse);
    }
}
