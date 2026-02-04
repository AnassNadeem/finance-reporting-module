package uk.ac.brunel.finance.app.auth;

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

            System.out.println("Executing auth query...");
            System.out.println("Email entered: [" + email + "]");

            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return AuthResult.failure("Invalid email or password");
            }

            String storedHash = rs.getString("passwordHash");
            String role = rs.getString("role");

            System.out.println("Hash from DB: " + storedHash);
            System.out.println("Role from DB: " + role);

            boolean valid = PasswordHasher.verifyPassword(plainPassword, storedHash);

            if (!valid) {
                return AuthResult.failure("Invalid email or password");
            }

            return AuthResult.success("Login successful", role);

        } catch (Exception e) {
            e.printStackTrace();
            return AuthResult.failure("Authentication system error");
        }
    }
}
