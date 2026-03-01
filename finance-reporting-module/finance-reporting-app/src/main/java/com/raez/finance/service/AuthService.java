package com.raez.finance.service;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.util.DBConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AuthService {

    public static class FirstLoginRequiredException extends RuntimeException {
        public FirstLoginRequiredException(String message) {
            super(message);
        }
    }

    public FUser login(String usernameOrEmail, String plainPassword) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Username or email is required.");
        }
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        String lookup = usernameOrEmail.trim();

        String sql =
                "SELECT userID, email, username, passwordHash, role, firstName, lastName, " +
                        "isActive, lastLogin " +
                        "FROM FUser " +
                        "WHERE (email = ? OR username = ?) AND isActive = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, lookup);
            stmt.setString(2, lookup);

            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new IllegalArgumentException("Invalid username/email or password");
            }

            String storedHash = rs.getString("passwordHash");
            if (storedHash == null || storedHash.isBlank()) {
                throw new IllegalArgumentException("Account has no password set. Please use password reset or contact admin.");
            }
            if (!storedHash.startsWith("$2")) {
                throw new IllegalArgumentException("This account uses an old password format. Run DevSeedUsers to reset seed users, or use a user created via Create User.");
            }

            if (!BCrypt.checkpw(plainPassword, storedHash)) {
                throw new IllegalArgumentException("Invalid username/email or password");
            }

            int id = rs.getInt("userID");
            String email = rs.getString("email");
            String username = rs.getString("username");
            String roleStr = rs.getString("role");
            String firstName = rs.getString("firstName");
            String lastName = rs.getString("lastName");
            boolean isActive = rs.getInt("isActive") == 1;

            LocalDateTime lastLogin = null;
            String lastLoginStr = rs.getString("lastLogin");
            if (lastLoginStr != null) {
                try {
                    // SQLite CURRENT_TIMESTAMP uses "YYYY-MM-DD HH:MM:SS"
                    DateTimeFormatter sqliteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    lastLogin = LocalDateTime.parse(lastLoginStr, sqliteFormatter);
                } catch (DateTimeParseException ex) {
                    // Fallback for ISO-8601 or other formats; if this also fails, we treat as first login
                    try {
                        lastLogin = LocalDateTime.parse(lastLoginStr);
                    } catch (DateTimeParseException ignored) {
                        lastLogin = null;
                    }
                }
            }

            if (roleStr == null || roleStr.isBlank()) {
                throw new IllegalArgumentException("User role is missing in database.");
            }
            UserRole role;
            try {
                role = UserRole.valueOf(roleStr.trim().toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unsupported role in database: '" + roleStr + "'. Allowed roles: ADMIN, FINANCE_USER"
                );
            }

            FUser user = new FUser(
                    id,
                    email,
                    username,
                    storedHash,
                    role,
                    firstName,
                    lastName,
                    isActive,
                    lastLogin
            );

            boolean firstLogin = user.isFirstLogin();

            // Best-effort lastLogin update – do not fail login if this update fails
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE FUser SET lastLogin = CURRENT_TIMESTAMP WHERE userID = ?"
            )) {
                update.setInt(1, id);
                update.executeUpdate();
            } catch (Exception ignored) {
                // Intentionally ignore to avoid inconsistent 'failed but logged in' state
            }

            if (firstLogin) {
                // After marking lastLogin in the database, force password change flow
                throw new FirstLoginRequiredException("First-time login detected – password change required.");
            }

            SessionManager.startSession(user);

            return user;

        } catch (FirstLoginRequiredException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }
}
