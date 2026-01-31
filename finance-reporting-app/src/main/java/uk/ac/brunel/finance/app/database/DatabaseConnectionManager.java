package uk.ac.brunel.finance.app.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnectionManager {

    private static final String DB_URL =
            "jdbc:sqlite:src/main/resources/database/nc1605_finance.db";

    private DatabaseConnectionManager() {
        // prevent instantiation
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);

        // IMPORTANT: enable foreign keys
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }

        return connection;
    }
}
