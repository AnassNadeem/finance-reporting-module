package com.raez.finance.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Paths;

public final class DBConnection {

    /** Use system property "raez.finance.db" for a single shared DB path; else user.dir/finance_raez.db */
    private static final String DB_FILE = System.getProperty("raez.finance.db",
            Paths.get(System.getProperty("user.dir", ".")).resolve("finance_raez.db").toAbsolutePath().normalize().toString());
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;
    private static boolean printedPath = false;

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }

        if (!printedPath) {
            printedPath = true;
            System.out.println("Using SQLite DB file: " + DB_FILE);
        }

        Connection conn = DriverManager.getConnection(DB_URL);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }

        return conn;
    }
}
