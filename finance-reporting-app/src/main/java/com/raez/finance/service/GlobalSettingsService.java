package com.raez.finance.service;

import javafx.beans.property.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

/**
 * Global app-wide financial settings (VAT, company, currency, financial year).
 * Persists to a properties file and notifies listeners so dashboard and reports update dynamically.
 */
public final class GlobalSettingsService {

    private static final GlobalSettingsService INSTANCE = new GlobalSettingsService();
    private static final String FILENAME = "raez-finance-settings.properties";

    private static final String DEFAULT_COMPANY_NAME = "RAEZ ltd";
    private static final String DEFAULT_COMPANY_ADDRESS = "Brunel University of London";

    private final DoubleProperty defaultVatPercent = new SimpleDoubleProperty(20.0);
    private final StringProperty companyName = new SimpleStringProperty(DEFAULT_COMPANY_NAME);
    private final StringProperty companyAddress = new SimpleStringProperty(DEFAULT_COMPANY_ADDRESS);
    private final StringProperty defaultCurrencySymbol = new SimpleStringProperty("£");
    private final IntegerProperty financialYearStartMonth = new SimpleIntegerProperty(1);

    /** Outgoing mail (password reset). Stored in the same properties file as VAT/company. */
    private boolean smtpEnabled;
    private String smtpHost = "";
    private int smtpPort = 587;
    private String smtpUser = "";
    private String smtpPassword = "";
    private String smtpFrom = "";
    private boolean smtpUseTls = true;

    public static GlobalSettingsService getInstance() {
        return INSTANCE;
    }

    private GlobalSettingsService() {
        load();
    }

    // ─── Getters (property access for binding) ─────────────────────────────
    public double getDefaultVatPercent() { return defaultVatPercent.get(); }
    public DoubleProperty defaultVatPercentProperty() { return defaultVatPercent; }
    public void setDefaultVatPercent(double v) { defaultVatPercent.set(v); }

    public String getCompanyName() { return companyName.get(); }
    public StringProperty companyNameProperty() { return companyName; }
    public void setCompanyName(String v) { companyName.set(v != null ? v : ""); }

    public String getCompanyAddress() { return companyAddress.get(); }
    public StringProperty companyAddressProperty() { return companyAddress; }
    public void setCompanyAddress(String v) { companyAddress.set(v != null ? v : ""); }

    public String getDefaultCurrencySymbol() { return defaultCurrencySymbol.get(); }
    public StringProperty defaultCurrencySymbolProperty() { return defaultCurrencySymbol; }
    public void setDefaultCurrencySymbol(String v) { defaultCurrencySymbol.set(v != null && !v.isEmpty() ? v : "£"); }

    public int getFinancialYearStartMonth() { return financialYearStartMonth.get(); }
    public IntegerProperty financialYearStartMonthProperty() { return financialYearStartMonth; }
    public void setFinancialYearStartMonth(int v) { financialYearStartMonth.set(Math.max(1, Math.min(12, v))); }

    public boolean isSmtpEnabled() { return smtpEnabled; }
    public void setSmtpEnabled(boolean smtpEnabled) { this.smtpEnabled = smtpEnabled; }

    public String getSmtpHost() { return smtpHost != null ? smtpHost : ""; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost != null ? smtpHost : ""; }

    public int getSmtpPort() { return smtpPort > 0 ? smtpPort : 587; }
    public void setSmtpPort(int smtpPort) { this.smtpPort = smtpPort > 0 ? smtpPort : 587; }

    public String getSmtpUser() { return smtpUser != null ? smtpUser : ""; }
    public void setSmtpUser(String smtpUser) { this.smtpUser = smtpUser != null ? smtpUser : ""; }

    public String getSmtpPassword() { return smtpPassword != null ? smtpPassword : ""; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword != null ? smtpPassword : ""; }

    public String getSmtpFrom() { return smtpFrom != null ? smtpFrom : ""; }
    public void setSmtpFrom(String smtpFrom) { this.smtpFrom = smtpFrom != null ? smtpFrom : ""; }

    public boolean isSmtpUseTls() { return smtpUseTls; }
    public void setSmtpUseTls(boolean smtpUseTls) { this.smtpUseTls = smtpUseTls; }

    /** Currency symbol for display (never null). */
    public String getCurrencySymbol() {
        String s = getDefaultCurrencySymbol();
        return s != null && !s.isEmpty() ? s : "£";
    }

    /** VAT rate as decimal (e.g. 0.20 for 20%). */
    public double getVatRate() {
        return getDefaultVatPercent() / 100.0;
    }

    /** Net from gross: net = gross / (1 + vatRate). */
    public double grossToNet(double gross) {
        return gross / (1.0 + getVatRate());
    }

    /** VAT amount from gross: vat = gross - net. */
    public double vatFromGross(double gross) {
        return gross - grossToNet(gross);
    }

    /** First day of current financial year. */
    public LocalDate getFinancialYearStart() {
        int year = LocalDate.now().getYear();
        int month = getFinancialYearStartMonth();
        int startYear = (LocalDate.now().getMonthValue() >= month) ? year : year - 1;
        return LocalDate.of(startYear, month, 1);
    }

    private Path getPropertiesPath() {
        String userHome = System.getProperty("user.home");
        Path dir = Paths.get(userHome, ".raez-finance");
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            // fallback to current dir
            return Paths.get(System.getProperty("user.dir", "."), FILENAME);
        }
        return dir.resolve(FILENAME);
    }

    public void load() {
        Path path = getPropertiesPath();
        if (!Files.isRegularFile(path)) return;
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        setDefaultVatPercent(parseDouble(p.getProperty("defaultVatPercent"), 20.0));
        setCompanyName(p.getProperty("companyName", DEFAULT_COMPANY_NAME));
        setCompanyAddress(p.getProperty("companyAddress", DEFAULT_COMPANY_ADDRESS));
        setDefaultCurrencySymbol(p.getProperty("defaultCurrencySymbol", "£"));
        setFinancialYearStartMonth(parseInt(p.getProperty("financialYearStartMonth"), 1));
        setSmtpEnabled("true".equalsIgnoreCase(p.getProperty("smtpEnabled", "false")));
        setSmtpHost(p.getProperty("smtpHost", ""));
        setSmtpPort(parseInt(p.getProperty("smtpPort"), 587));
        setSmtpUser(p.getProperty("smtpUser", ""));
        setSmtpPassword(p.getProperty("smtpPassword", ""));
        setSmtpFrom(p.getProperty("smtpFrom", ""));
        setSmtpUseTls(!"false".equalsIgnoreCase(p.getProperty("smtpUseTls", "true")));
    }

    public void save() {
        Path path = getPropertiesPath();
        Properties p = new Properties();
        p.setProperty("defaultVatPercent", String.valueOf(getDefaultVatPercent()));
        p.setProperty("companyName", getCompanyName() != null ? getCompanyName() : "");
        p.setProperty("companyAddress", getCompanyAddress() != null ? getCompanyAddress() : "");
        p.setProperty("defaultCurrencySymbol", getDefaultCurrencySymbol() != null ? getDefaultCurrencySymbol() : "£");
        p.setProperty("financialYearStartMonth", String.valueOf(getFinancialYearStartMonth()));
        p.setProperty("smtpEnabled", String.valueOf(isSmtpEnabled()));
        p.setProperty("smtpHost", getSmtpHost());
        p.setProperty("smtpPort", String.valueOf(getSmtpPort()));
        p.setProperty("smtpUser", getSmtpUser());
        p.setProperty("smtpPassword", getSmtpPassword());
        p.setProperty("smtpFrom", getSmtpFrom());
        p.setProperty("smtpUseTls", String.valueOf(isSmtpUseTls()));
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            p.store(w, "RAEZ Finance – Global Settings");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save settings: " + e.getMessage());
        }
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
