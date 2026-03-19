package com.raez.finance.dao;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class FUserDao {

    public List<FUser> findAll() throws Exception {
        String sql = "SELECT userID, email, username, passwordHash, role, firstName, lastName, phone, isActive, lastLogin " +
                "FROM FUser ORDER BY username";
        List<FUser> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Finds a user by email (case-insensitive trim). Returns null if not found. */
    public FUser findByEmail(String email) throws Exception {
        if (email == null || email.isBlank()) return null;
        String sql = "SELECT userID, email, username, passwordHash, role, firstName, lastName, phone, isActive, lastLogin " +
                "FROM FUser WHERE TRIM(LOWER(email)) = TRIM(LOWER(?))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    /** Finds a user by ID. Returns null if not found. */
    public FUser findById(int userId) throws Exception {
        String sql = "SELECT userID, email, username, passwordHash, role, firstName, lastName, phone, isActive, lastLogin " +
                "FROM FUser WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapRow(rs) : null;
        }
    }

    /** Returns the createdAt timestamp for the user, or null if not found. */
    public String getCreatedAt(int userId) throws Exception {
        String sql = "SELECT createdAt FROM FUser WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("createdAt") : null;
        }
    }

    private FUser mapRow(ResultSet rs) throws Exception {
        int id = rs.getInt("userID");
        String email = rs.getString("email");
        String username = rs.getString("username");
        String passwordHash = rs.getString("passwordHash");
        String roleStr = rs.getString("role");
        UserRole role = roleStr == null ? UserRole.FINANCE_USER : UserRole.valueOf(roleStr.trim().toUpperCase().replace(" ", "_"));
        String firstName = rs.getString("firstName");
        String lastName = rs.getString("lastName");
        String phone = rs.getString("phone");
        boolean active = rs.getInt("isActive") == 1;
        LocalDateTime lastLogin = parseLastLogin(rs.getString("lastLogin"));
        return new FUser(id, email, username, passwordHash, role, firstName, lastName, phone, active, lastLogin);
    }

    private static LocalDateTime parseLastLogin(String lastLoginStr) {
        if (lastLoginStr == null || lastLoginStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(lastLoginStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(lastLoginStr);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    public void insertUser(
            String email,
            String username,
            String passwordHash,
            String role,
            String firstName,
            String lastName,
            String phone,
            boolean isActive
    ) throws Exception {
        String sql = "INSERT INTO FUser (email, username, passwordHash, role, firstName, lastName, phone, isActive, lastLogin) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            ps.setString(3, passwordHash);
            ps.setString(4, role);
            ps.setString(5, firstName);
            ps.setString(6, lastName);
            ps.setString(7, phone);
            ps.setInt(8, isActive ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /**
     * Deletes a user by ID. Caller should confirm with the user before invoking.
     */
    public void deleteByUserId(int userId) throws Exception {
        String sql = "DELETE FROM FUser WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("User not found");
            }
        }
    }

    /**
     * Updates the password hash for the given user (e.g. after "Update Password" in My Account).
     */
    public void updatePasswordByUserId(int userId, String newPasswordHash) throws Exception {
        String sql = "UPDATE FUser SET passwordHash = ? WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("User not found");
            }
        }
    }

    /**
     * Sets a temporary password and clears lastLogin so the next login is treated as first-time (must change password).
     * Used for forgot-password flow on the login screen.
     */
    public void setTemporaryPasswordAndClearLastLogin(int userId, String newPasswordHash) throws Exception {
        String sql = "UPDATE FUser SET passwordHash = ?, lastLogin = NULL WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setInt(2, userId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("User not found");
            }
        }
    }

    /**
     * Updates user profile fields (email, username, firstName, lastName, phone, role, isActive).
     * Does not change password or lastLogin.
     */
    public void updateUser(int userId, String email, String username, String firstName, String lastName,
                           String phone, UserRole role, boolean isActive) throws Exception {
        String sql = "UPDATE FUser SET email = ?, username = ?, firstName = ?, lastName = ?, phone = ?, role = ?, isActive = ? WHERE userID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, username);
            ps.setString(3, firstName);
            ps.setString(4, lastName);
            ps.setString(5, phone);
            ps.setString(6, role.name());
            ps.setInt(7, isActive ? 1 : 0);
            ps.setInt(8, userId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("User not found");
            }
        }
    }
}
