package com.raez.finance.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {

    /** Use system property "raez.finance.db" for explicit path; else use finance_raez.db in the app directory. */
    private static final String DB_FILE = resolveDbPath();
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    private static String resolveDbPath() {
        String explicit = System.getProperty("raez.finance.db");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).toAbsolutePath().normalize().toString();
        }
        Path base = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path appDir = base;
        Path childApp = base.resolve("finance-reporting-app");
        Path nestedApp = base.resolve("finance-reporting-module").resolve("finance-reporting-app");
        if (Files.isDirectory(childApp)) {
            appDir = childApp;
        } else if (Files.isDirectory(nestedApp)) {
            appDir = nestedApp;
        }
        return appDir.resolve("finance_raez.db").toAbsolutePath().normalize().toString();
    }
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
