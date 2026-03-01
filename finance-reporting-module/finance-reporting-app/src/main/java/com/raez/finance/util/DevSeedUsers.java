package com.raez.finance.util;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Dev-only helper to seed known-good users into finance_raez.db.
 * Run via: mvn -DskipTests exec:java -Dexec.mainClass=com.raez.finance.util.DevSeedUsers
 */
public class DevSeedUsers {

    public static void main(String[] args) throws Exception {
        String adminEmail = "admin@raez.com";
        String adminUsername = "admin";
        String adminPassword = "Password1!";

        String finEmail = "finance@raez.com";
        String finUsername = "finance";
        String finPassword = "Password1!";

        try (Connection conn = DBConnection.getConnection()) {
            ensureFUserTable(conn);

            deleteIfExists(conn, adminEmail, adminUsername);
            deleteIfExists(conn, finEmail, finUsername);

            insertUser(conn, adminEmail, adminUsername, adminPassword, "ADMIN", "Admin", "Raez");
            insertUser(conn, finEmail, finUsername, finPassword, "FINANCE_USER", "Finance", "User");
        }

        System.out.println("Seeded 2 users into finance_raez.db");
        System.out.println();
        System.out.println("ADMIN login (Role Selection -> Super Admin Login):");
        System.out.println("  username/email: admin@raez.com  (or username: admin)");
        System.out.println("  password: Password1!");
        System.out.println("  role: ADMIN");
        System.out.println();
        System.out.println("FINANCE login (Role Selection -> Finance Admin Login):");
        System.out.println("  username/email: finance@raez.com (or username: finance)");
        System.out.println("  password: Password1!");
        System.out.println("  role: FINANCE_USER");
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
