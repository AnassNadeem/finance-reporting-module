package com.raez.finance.dao;

import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Manages one-time password reset tokens stored in PasswordResetToken.
 * Tokens are valid for 24 hours and can only be used once.
 */
public class PasswordResetTokenDao {

    private static final int EXPIRY_HOURS = 24;
    private static final DateTimeFormatter SQLITE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new reset token for the given user.
     * Returns the plain token string (show to admin / send to user).
     * Does NOT invalidate previous unused tokens for the same user.
     */
    public String createToken(int userId) throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "");
        String expiry = LocalDateTime.now().plusHours(EXPIRY_HOURS).format(SQLITE);
        String sql =
            "INSERT INTO PasswordResetToken (userID, token, expiryTime) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, token);
            ps.setString(3, expiry);
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * Returns the userID associated with a valid (unused, not expired) token.
     * Returns -1 if the token is invalid, expired, or already used.
     */
    public int findUserIdByValidToken(String token) throws Exception {
        if (token == null || token.isBlank()) return -1;
        String sql =
            "SELECT userID FROM PasswordResetToken " +
            "WHERE token = ? AND isUsed = 0 AND expiryTime > datetime('now')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("userID") : -1;
        }
    }

    /**
     * Marks a token as used so it cannot be used again.
     * Also records the used-at timestamp if the column exists.
     */
    public void markUsed(String token) throws Exception {
        if (token == null || token.isBlank()) return;
        String sql =
            "UPDATE PasswordResetToken SET isUsed = 1 WHERE token = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token.trim());
            ps.executeUpdate();
        }
    }

    /**
     * Deletes all expired or used tokens for a user (housekeeping).
     * Call periodically or after a successful password reset.
     */
    public void deleteExpiredTokens(int userId) throws Exception {
        String sql =
            "DELETE FROM PasswordResetToken " +
            "WHERE userID = ? AND (isUsed = 1 OR expiryTime <= datetime('now'))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }
}