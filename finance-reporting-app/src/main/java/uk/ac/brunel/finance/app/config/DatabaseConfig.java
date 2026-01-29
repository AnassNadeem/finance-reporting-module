package uk.ac.brunel.finance.app.config;

public final class DatabaseConfig {

    public static final String DB_URL =
            "jdbc:postgresql://localhost:5432/nc1605_finance";

    public static final String DB_USER = "finance_app";
    public static final String DB_PASSWORD = "2048";

    private DatabaseConfig() {
        // Utility class
    }
}
