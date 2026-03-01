package uk.ac.brunel.finance.app.auth;

import uk.ac.brunel.finance.app.authz.Role;
import uk.ac.brunel.finance.app.database.DatabaseConnectionManager;
import uk.ac.brunel.finance.app.security.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AuthService {

    public AuthResult authenticate(String email, String plainPassword) {

        String sql =
            "SELECT userID, passwordHash, role " +
            "FROM FUser " +
            "WHERE email = ? AND isActive = 1";

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return AuthResult.failure("Invalid email or password");
            }

            int userId = rs.getInt("userID");
            String storedHash = rs.getString("passwordHash");
            String roleStr = rs.getString("role");

            if (!PasswordHasher.verifyPassword(plainPassword, storedHash)) {
                return AuthResult.failure("Invalid email or password");
            }

            Role role = Role.valueOf(roleStr.toUpperCase());

            // ✅ Create domain User
            User user = new User(userId, email, role);

            // ✅ Start session
            SessionManager.startSession(user);

            return AuthResult.success("Login successful", role.name());

        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.failure("Authentication system error");
        }
    }
}
