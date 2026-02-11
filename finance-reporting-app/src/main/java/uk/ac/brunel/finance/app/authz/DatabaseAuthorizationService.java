package uk.ac.brunel.finance.app.authz;

import uk.ac.brunel.finance.app.database.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseAuthorizationService extends AuthorizationService {

    @Override
    protected boolean hasPermission(Role role, Action action) {

        String sql =
            "SELECT COUNT(*) " +
            "FROM role_permissions " +
            "WHERE role = ? AND action = ?";
        System.out.println("AUTHZ CHECK: " + role + " → " + action);


        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, role.name());
            stmt.setString(2, action.name());

            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
