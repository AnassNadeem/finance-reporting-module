package com.raez.finance.service;

import com.raez.finance.dao.FUserDao;
import com.raez.finance.dao.PasswordResetTokenDao;
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
                lastLogin = parseLastLogin(lastLoginStr);
            }

            if (roleStr == null || roleStr.isBlank()) {
                throw new IllegalArgumentException("User role is missing in database.");
            }
            UserRole role;
            role = parseRole(roleStr);

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

    /**
     * Completes a first-time login by setting a new password and marking lastLogin.
     * This is used by the first-login password change screens instead of emailing a link.
     *
     * @param usernameOrEmail identifier used at login time (email or username)
     * @param newPlainPassword new password chosen by the user
     * @return authenticated FUser with an active session
     */
    public FUser completeFirstLogin(String usernameOrEmail, String newPlainPassword) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("Username or email is required.");
        }
        if (newPlainPassword == null || newPlainPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }
        String lookup = usernameOrEmail.trim();

        String selectSql =
                "SELECT userID, email, username, passwordHash, role, firstName, lastName, " +
                        "isActive, lastLogin " +
                        "FROM FUser " +
                        "WHERE (email = ? OR username = ?) AND isActive = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {

            stmt.setString(1, lookup);
            stmt.setString(2, lookup);

            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new IllegalArgumentException("Account not found or inactive. Contact an administrator.");
            }

            int id = rs.getInt("userID");
            String email = rs.getString("email");
            String username = rs.getString("username");
            String roleStr = rs.getString("role");
            String firstName = rs.getString("firstName");
            String lastName = rs.getString("lastName");
            boolean isActive = rs.getInt("isActive") == 1;

            if (!isActive) {
                throw new IllegalArgumentException("This account is inactive. Contact an administrator.");
            }

            LocalDateTime lastLogin = parseLastLogin(rs.getString("lastLogin"));
            if (lastLogin != null) {
                // Not actually a first login anymore.
                throw new IllegalStateException("This account has already completed first-time setup.");
            }

            UserRole role = parseRole(roleStr);

            String newHash = BCrypt.hashpw(newPlainPassword, BCrypt.gensalt(12));

            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE FUser SET passwordHash = ?, lastLogin = CURRENT_TIMESTAMP WHERE userID = ?"
            )) {
                update.setString(1, newHash);
                update.setInt(2, id);
                int updated = update.executeUpdate();
                if (updated == 0) {
                    throw new IllegalArgumentException("Failed to update password for this account.");
                }
            }

            FUser user = new FUser(
                    id,
                    email,
                    username,
                    newHash,
                    role,
                    firstName,
                    lastName,
                    true,
                    LocalDateTime.now()
            );

            SessionManager.startSession(user);
            return user;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to complete first-time login: " + e.getMessage(), e);
        }
    }

    private static LocalDateTime parseLastLogin(String lastLoginStr) {
        if (lastLoginStr == null || lastLoginStr.isBlank()) {
            return null;
        }
        try {
            DateTimeFormatter sqliteFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(lastLoginStr, sqliteFormatter);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(lastLoginStr);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static UserRole parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("User role is missing in database.");
        }
        try {
            return UserRole.valueOf(roleStr.trim().toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported role in database: '" + roleStr + "'. Allowed roles: ADMIN, FINANCE_USER"
            );
        }
    }

    private final FUserDao fUserDao = new FUserDao();
    private final PasswordResetTokenDao resetTokenDao = new PasswordResetTokenDao();

    /**
     * Resets password using a one-time token (from admin). Validates token, updates password, marks token used.
     */
    public void resetPasswordWithToken(String email, String token, String newPlainPassword) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }
        if (newPlainPassword == null || newPlainPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }
        try {
            int userId = resetTokenDao.findUserIdByValidToken(token.trim());
            if (userId <= 0) {
                throw new IllegalArgumentException("Invalid or expired token. Request a new one from your administrator.");
            }
            FUser user = fUserDao.findById(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found.");
            }
            if (!user.getEmail().trim().equalsIgnoreCase(email.trim())) {
                throw new IllegalArgumentException("Email does not match the account for this token.");
            }
            String newHash = BCrypt.hashpw(newPlainPassword, BCrypt.gensalt(12));
            fUserDao.updatePasswordByUserId(userId, newHash);
            resetTokenDao.markUsed(token.trim());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Password reset failed: " + e.getMessage(), e);
        }
    }
}
