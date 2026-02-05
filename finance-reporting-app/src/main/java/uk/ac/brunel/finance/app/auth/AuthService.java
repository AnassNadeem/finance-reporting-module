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
            "SELECT passwordHash, role " +
            "FROM FUser " +
            "WHERE email = ?";

        try (Connection conn = DatabaseConnectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            // Email not found
            if (!rs.next()) {
                return AuthResult.failure("Invalid email or password");
            }

            String storedHash = rs.getString("passwordHash");
            String roleFromDb = rs.getString("role");

            boolean valid = PasswordHasher.verifyPassword(
                plainPassword,
                storedHash
            );

            // Password incorrect
            if (!valid) {
                return AuthResult.failure("Invalid email or password");
            }

            // ✅ LOGIN USER (Phase 3.3 integration)
            Role userRole = Role.valueOf(roleFromDb);
            CurrentUser.login(userRole);

            // ✅ RETURN RESULT
            return AuthResult.success("Login successful", roleFromDb);

        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.failure("Authentication system error");
        }
    }
}
