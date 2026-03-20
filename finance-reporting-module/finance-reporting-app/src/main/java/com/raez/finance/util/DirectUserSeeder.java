package com.raez.finance.util;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Minimal, direct seeder that writes admin/finance users into the existing finance_raez.db
 * without going through DBConnection/DatabaseBootstrap.
 */
public class DirectUserSeeder {

    public static void main(String[] args) throws Exception {
        String dbPath = "finance_raez.db";
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            ensureFUserTable(conn);
            seedUsers(conn);
        }
        System.out.println("DirectUserSeeder: seeded admin@raez.org.uk and finance@raez.org.uk into FUser");
    }

    private static void ensureFUserTable(Connection conn) throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS FUser (
                  userID INTEGER PRIMARY KEY AUTOINCREMENT,
                  email TEXT UNIQUE NOT NULL,
                  username TEXT UNIQUE NOT NULL,
                  passwordHash TEXT NOT NULL,
                  role TEXT NOT NULL,
                  firstName TEXT,
                  lastName TEXT,
                  phone TEXT,
                  isActive INTEGER DEFAULT 1,
                  lastLogin TEXT,
                  createdAt TEXT DEFAULT CURRENT_TIMESTAMP
                );
                """;
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }

    private static void seedUsers(Connection conn) throws Exception {
        String adminEmail = "admin@raez.org.uk";
        String adminUsername = "admin";
        String adminPassword = "Admin@123";

        String finEmail = "finance@raez.org.uk";
        String finUsername = "finance";
        String finPassword = "User@123";

        deleteIfExists(conn, adminEmail, adminUsername);
        deleteIfExists(conn, finEmail, finUsername);

        insertUser(conn, adminEmail, adminUsername, adminPassword, "ADMIN", "Admin", "Raez");
        insertUser(conn, finEmail, finUsername, finPassword, "FINANCE_USER", "Finance", "User");
    }

    private static void deleteIfExists(Connection conn, String email, String username) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM FUser WHERE email = ? OR username = ?"
        )) {
            ps.setString(1, email);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    private static void insertUser(
            Connection conn,
            String email,
            String username,
            String plainPassword,
            String role,
            String firstName,
            String lastName
    ) throws Exception {
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO FUser (email, username, passwordHash, role, firstName, lastName, isActive, lastLogin) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)"
        )) {
            ps.setString(1, email);
            ps.setString(2, username);
            ps.setString(3, hash);
            ps.setString(4, role);
            ps.setString(5, firstName);
            ps.setString(6, lastName);
            ps.executeUpdate();
        }
    }
}

