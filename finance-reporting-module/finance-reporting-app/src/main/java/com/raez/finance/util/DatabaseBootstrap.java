package com.raez.finance.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Initializes finance_raez.db with the full schema (26+ tables) and seed data.
 * Uses the same DB as the app (see DBConnection).
 *
 * Run once from the project root:
 * mvn -DskipTests=true -q compile exec:java "-Dexec.mainClass=com.raez.finance.util.DatabaseBootstrap"
 */
public class DatabaseBootstrap {

    public static void main(String[] args) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            runSqlFromResource(conn, "/database/schema.sql");
            runSqlFromResource(conn, "/database/seed.sql");
        }
        System.out.println("DatabaseBootstrap: schema.sql and seed.sql applied to finance_raez.db");
    }

    private static void runSqlFromResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = DatabaseBootstrap.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found on classpath: " + resourcePath);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String[] statements = sb.toString().split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String raw : statements) {
                    String sql = stripLineComments(raw).trim();
                    if (sql.isEmpty()) continue;
                    stmt.execute(sql);
                }
            }
        }
    }

    /** Remove SQL line comments (-- ...) so statements after comment blocks still run. */
    private static String stripLineComments(String block) {
        StringBuilder out = new StringBuilder();
        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }
}
