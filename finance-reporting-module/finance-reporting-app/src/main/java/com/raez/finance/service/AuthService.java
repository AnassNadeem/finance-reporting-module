package com.raez.finance.service;

import com.raez.finance.dao.FUserDao;
import com.raez.finance.dao.PasswordResetTokenDao;
import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.util.DBConnection;
import com.raez.finance.util.PasswordGenerator;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AuthService {

    // ── Checked exception: caller must switch to the set-password screen ──
    public static class FirstLoginRequiredException extends RuntimeException {
        public FirstLoginRequiredException(String message) {
            super(message);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LOGIN
    //  ─────────────────────────────────────────────────────────────
    //  BUG FIX (v2): The original code updated lastLogin to
    //  CURRENT_TIMESTAMP before throwing FirstLoginRequiredException.
    //  This meant completeFirstLogin() found lastLogin != NULL and
    //  threw "already completed first-time setup."
    //
    //  Fix: only update lastLogin for NORMAL logins.
    //  For first-time logins, lastLogin stays NULL in the DB so
    //  completeFirstLogin() can safely update it after the password
    //  has been set.
    // ══════════════════════════════════════════════════════════════

    public FUser login(String usernameOrEmail, String plainPassword) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank())
            throw new IllegalArgumentException("Username or email is required.");
        if (plainPassword == null || plainPassword.isBlank())
            throw new IllegalArgumentException("Password is required.");

        String lookup = usernameOrEmail.trim();
        String sql =
            "SELECT userID, email, username, passwordHash, role, " +
            "       firstName, lastName, isActive, lastLogin " +
            "FROM FUser " +
            "WHERE (email = ? OR username = ?) AND isActive = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, lookup);
            stmt.setString(2, lookup);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next())
                throw new IllegalArgumentException("Invalid username/email or password.");

            String storedHash = rs.getString("passwordHash");

            // Guard against placeholder hashes from seed data
            if (storedHash == null || storedHash.isBlank()) {
                throw new IllegalArgumentException(
                    "This account has no password set. Contact your administrator.");
            }
            if (!storedHash.startsWith("$2")) {
                throw new IllegalArgumentException(
                    "This account's password needs to be reset. " +
                    "Run FixPlaceholderPasswordHashes or contact an administrator.");
            }

            if (!BCrypt.checkpw(plainPassword, storedHash)) {
                throw new IllegalArgumentException("Invalid username/email or password.");
            }

            int    id        = rs.getInt("userID");
            String email     = rs.getString("email");
            String username  = rs.getString("username");
            String roleStr   = rs.getString("role");
            String firstName = rs.getString("firstName");
            String lastName  = rs.getString("lastName");
            boolean isActive = rs.getInt("isActive") == 1;

            LocalDateTime lastLogin = parseLastLogin(rs.getString("lastLogin"));
            boolean firstLogin = (lastLogin == null);   // null lastLogin ⇒ first-time login

            UserRole role = parseRole(roleStr);

            FUser user = new FUser(id, email, username, storedHash,
                                   role, firstName, lastName, isActive, lastLogin);

            if (firstLogin) {
                // ── KEY FIX: do NOT update lastLogin here ──────────────
                // lastLogin stays NULL so completeFirstLogin() can detect
                // and update it after the new password has been saved.
                throw new FirstLoginRequiredException(
                    "First-time login detected — password change required.");
            }

            // Normal login: update lastLogin timestamp
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE FUser SET lastLogin = CURRENT_TIMESTAMP WHERE userID = ?")) {
                upd.setInt(1, id);
                upd.executeUpdate();
            } catch (Exception ignored) {
                // Non-fatal — do not prevent the user from logging in
            }

            SessionManager.startSession(user);
            return user;

        } catch (FirstLoginRequiredException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  COMPLETE FIRST LOGIN
    //  Called from the set-password screen after the user has
    //  chosen a new password.
    //
    //  Because login() no longer sets lastLogin for first-time users,
    //  lastLogin is still NULL here, which is the correct pre-condition.
    //  We update BOTH passwordHash and lastLogin in one atomic UPDATE
    //  so the next login is treated as a normal login.
    // ══════════════════════════════════════════════════════════════

    public FUser completeFirstLogin(String usernameOrEmail, String newPlainPassword) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank())
            throw new IllegalArgumentException("Username or email is required.");
        if (newPlainPassword == null || newPlainPassword.isBlank())
            throw new IllegalArgumentException("New password is required.");

        String lookup = usernameOrEmail.trim();
        String selectSql =
            "SELECT userID, email, username, passwordHash, role, " +
            "       firstName, lastName, isActive, lastLogin " +
            "FROM FUser " +
            "WHERE (email = ? OR username = ?) AND isActive = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSql)) {

            stmt.setString(1, lookup);
            stmt.setString(2, lookup);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next())
                throw new IllegalArgumentException(
                    "Account not found or inactive. Contact an administrator.");

            int    id        = rs.getInt("userID");
            String email     = rs.getString("email");
            String username  = rs.getString("username");
            String roleStr   = rs.getString("role");
            String firstName = rs.getString("firstName");
            String lastName  = rs.getString("lastName");
            boolean isActive = rs.getInt("isActive") == 1;

            if (!isActive)
                throw new IllegalArgumentException(
                    "This account is inactive. Contact an administrator.");

            UserRole role = parseRole(roleStr);
            String newHash = BCrypt.hashpw(newPlainPassword, BCrypt.gensalt(12));

            // Update password AND set lastLogin in one statement.
            // After this, lastLogin != NULL → subsequent logins are treated normally.
            String updateSql =
                "UPDATE FUser SET passwordHash = ?, lastLogin = CURRENT_TIMESTAMP " +
                "WHERE userID = ?";
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, newHash);
                upd.setInt(2, id);
                int updated = upd.executeUpdate();
                if (updated == 0) {
                    throw new IllegalArgumentException(
                        "Failed to update password. Please try again or contact an administrator.");
                }
            }

            FUser user = new FUser(id, email, username, newHash,
                                   role, firstName, lastName, true,
                                   LocalDateTime.now());
            SessionManager.startSession(user);
            return user;

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to complete first-time login: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  FORGOT PASSWORD
    //  Generates a temp password and clears lastLogin so the next
    //  login triggers the set-password flow again.
    //  In dev: prints temp password to console.
    //  In production: send via email.
    // ══════════════════════════════════════════════════════════════

    private final FUserDao fUserDao = new FUserDao();
    private final PasswordResetTokenDao resetTokenDao = new PasswordResetTokenDao();

    public void requestTemporaryPassword(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required.");
        try {
            FUser user = fUserDao.findByEmail(email.trim());
            if (user == null || !user.isActive()) return; // silent — never reveal existence
            String tempPassword = PasswordGenerator.generate();
            String hash = BCrypt.hashpw(tempPassword, BCrypt.gensalt(12));
            fUserDao.setTemporaryPasswordAndClearLastLogin(user.getId(), hash);
            // Dev mode: print to console. Production: send via SMTP.
            System.out.println("[ForgotPassword] Temporary password for "
                + email.trim() + " → " + tempPassword);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not process request: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  RESET PASSWORD WITH TOKEN (admin-generated one-time token)
    // ══════════════════════════════════════════════════════════════

    public void resetPasswordWithToken(String email, String token, String newPlainPassword) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email is required.");
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("Reset token is required.");
        if (newPlainPassword == null || newPlainPassword.length() < 8)
            throw new IllegalArgumentException("New password must be at least 8 characters.");

        try {
            int userId = resetTokenDao.findUserIdByValidToken(token.trim());
            if (userId <= 0)
                throw new IllegalArgumentException(
                    "Invalid or expired token. Request a new one from your administrator.");

            FUser user = fUserDao.findById(userId);
            if (user == null)
                throw new IllegalArgumentException("User not found.");

            if (!user.getEmail().trim().equalsIgnoreCase(email.trim()))
                throw new IllegalArgumentException(
                    "Email does not match the account for this token.");

            String newHash = BCrypt.hashpw(newPlainPassword, BCrypt.gensalt(12));
            fUserDao.updatePasswordByUserId(userId, newHash);
            resetTokenDao.markUsed(token.trim());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Password reset failed: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════

    private static LocalDateTime parseLastLogin(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            try { return LocalDateTime.parse(raw); }
            catch (DateTimeParseException ignored) { return null; }
        }
    }

    private static UserRole parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank())
            throw new IllegalArgumentException("User role is missing in database.");
        String normalized = roleStr.trim().toUpperCase().replace(" ", "_");
        // Backwards compatibility: older seed data used `role='USER'`
        if ("USER".equals(normalized)) return UserRole.FINANCE_USER;
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unsupported role in database: '" + roleStr + "'. " +
                "Allowed: ADMIN, FINANCE_USER (legacy: USER)");
        }
    }
}