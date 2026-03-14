package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Manages password reset tokens for the PasswordResetToken table.
 * Tokens expire after 24 hours.
 */
public class PasswordResetTokenDao {

    private static final int EXPIRY_HOURS = 24;
    private static final DateTimeFormatter SQLITE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new reset token for the user. Returns the plain token string to show to the user/admin.
     * Previous tokens for this user are not invalidated; caller may optionally delete old ones.
     */
    public String createToken(int userId) throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiry = LocalDateTime.now().plusHours(EXPIRY_HOURS);
        String sql = "INSERT INTO PasswordResetToken (userID, token, expiryTime) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setString(3, expiry.format(SQLITE));
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * Returns the userID for a valid token (not used, not expired), or -1 if invalid.
     */
    public int findUserIdByValidToken(String token) throws Exception {
        if (token == null || token.isBlank()) return -1;
        String sql = "SELECT userID FROM PasswordResetToken WHERE token = ? AND isUsed = 0 AND expiryTime > datetime('now')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("userID") : -1;
        }
    }

    /** Marks the token as used. */
    public void markUsed(String token) throws Exception {
        if (token == null || token.isBlank()) return;
        String sql = "UPDATE PasswordResetToken SET isUsed = 1 WHERE token = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            ps.executeUpdate();
        }
    }
}
