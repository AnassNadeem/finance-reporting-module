package com.raez.finance.util;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Dev utility: converts placeholder password hashes in the SQLite DB into real BCrypt hashes.
 *
 * The current DB appears to store values like:
 *   $2a$12$PlaceholderBCryptHashForJamesCarter.Admin123AAAA
 *
 * into FUser.passwordHash. BCrypt verification will always fail for these, so login is impossible.
 *
 * Heuristic:
 * - Extract the substring after the last '.' (e.g. Admin123AAAA)
 * - Strip trailing 'A' characters
 * - Append '!' (matches the app's demo credentials: Admin123!, User123!)
 *
 * After this migration, users can login normally and will still follow the existing first-login
 * password-change flow if lastLogin is NULL.
 */
public class FixPlaceholderPasswordHashes {

    private static final String PLACEHOLDER_MARKER = "PlaceholderBCryptHash";

    public static void main(String[] args) throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            int updated = 0;

            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT userID, email, passwordHash FROM FUser WHERE passwordHash LIKE ?")) {
                sel.setString(1, "%" + PLACEHOLDER_MARKER + "%");

                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        int userId = rs.getInt("userID");
                        String email = rs.getString("email");
                        String storedHash = rs.getString("passwordHash");

                        String derivedPlain = derivePlainPassword(storedHash);
                        if (derivedPlain == null) {
                            System.err.println("[FixPlaceholderPasswordHashes] Skipping userId=" + userId +
                                    " email=" + email + " (could not derive plain password)");
                            continue;
                        }

                        String newHash = BCrypt.hashpw(derivedPlain, BCrypt.gensalt(12));
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE FUser SET passwordHash = ? WHERE userID = ?")) {
                            upd.setString(1, newHash);
                            upd.setInt(2, userId);
                            int count = upd.executeUpdate();
                            if (count > 0) updated++;
                        }

                        System.out.println("[FixPlaceholderPasswordHashes] Updated " + email +
                                " -> derivedPlainPassword='" + derivedPlain + "'");
                    }
                }
            }

            System.out.println("[FixPlaceholderPasswordHashes] Done. Updated " + updated + " users.");
        }
    }

    private static String derivePlainPassword(String storedHash) {
        if (storedHash == null) return null;
        if (!storedHash.contains(PLACEHOLDER_MARKER)) return null;

        int lastDot = storedHash.lastIndexOf('.');
        if (lastDot < 0 || lastDot + 1 >= storedHash.length()) return null;
        String token = storedHash.substring(lastDot + 1); // e.g. Admin123AAAA

        int end = token.length();
        while (end > 0 && token.charAt(end - 1) == 'A') {
            end--;
        }
        String base = token.substring(0, end); // e.g. Admin123
        if (base.isBlank()) return null;

        // App demo passwords end with '!'. The placeholder strings appear to replace '!' with 'A's.
        return base + "!";
    }
}

