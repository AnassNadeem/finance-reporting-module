package com.raez.finance.dao;

import com.raez.finance.model.UserRole;
import com.raez.finance.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Checks role_permissions table for RBAC.
 */
public class RolePermissionDao {

    /**
     * Returns true if the given role has the given action in role_permissions.
     */
    public boolean hasPermission(UserRole role, String action) {
        if (role == null || action == null || action.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM role_permissions WHERE role = ? AND action = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setString(2, action.trim());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
