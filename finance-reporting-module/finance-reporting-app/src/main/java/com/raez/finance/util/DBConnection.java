package com.raez.finance.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
        // Default to the finance_raez.db that already exists in the app directory
        // (do NOT create a second database in a different location).
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
    private static boolean bootstrapping = false;
    private static boolean integrityChecked = false;

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

        Path dbPath = Paths.get(DB_FILE);
        Path parent = dbPath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception e) {
                throw new SQLException("Cannot create database directory for " + DB_FILE + ": " + e.getMessage(), e);
            }
        }

        Connection conn;
        try {
            conn = DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            throw new SQLException("Error opening connection to database at " + DB_FILE + ". " + e.getMessage(), e);
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }

        if (!integrityChecked) {
            integrityChecked = true;
            try (Statement icStmt = conn.createStatement();
                 ResultSet icRs = icStmt.executeQuery("PRAGMA integrity_check")) {
                if (icRs.next()) {
                    String result = icRs.getString(1);
                    if ("ok".equalsIgnoreCase(result)) {
                        System.out.println("[DB] Integrity check: OK");
                    } else {
                        System.err.println("[DB] Integrity check FAILED: " + result);
                    }
                }
            } catch (SQLException ignored) {}
        }

        if (!bootstrapping) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='FUser' LIMIT 1")) {
                if (!rs.next()) {
                    conn.close();
                    bootstrapping = true;
                    try {
                        DatabaseInitialiser.initialise();
                    } catch (Exception ex) {
                        throw new SQLException("Failed to initialise database", ex);
                    } finally {
                        bootstrapping = false;
                    }
                    return getConnection();
                }
            }
        }

        return conn;
    }
}
