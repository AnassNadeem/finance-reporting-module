package com.raez.finance.util;

import com.raez.finance.dao.FUserDao;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Temporary utility to create one FUser for testing login.
 * Run from Eclipse: Right‑click this class → Run As → Java Application.
 * Or: mvn -q exec:java -Dexec.mainClass=com.raez.finance.util.CreateTestUser -Dexec.args="email password username FirstName LastName ADMIN 1"
 *
 * Args: email password username firstName lastName role active
 *   role: ADMIN or FINANCE_USER
 *   active: 1 (active) or 0 (inactive)
 *
 * Example: test@test.com mypass testuser Test User ADMIN 1
 */
public class CreateTestUser {

    public static void main(String[] args) {
        String email, password, username, firstName, lastName, roleStr;
        boolean isActive;

        if (args != null && args.length >= 7) {
            email = args[0].trim();
            password = args[1];
            username = args[2].trim();
            firstName = args[3].trim();
            lastName = args[4].trim();
            roleStr = args[5].trim().toUpperCase();
            isActive = "1".equals(args[6].trim());
        } else {
            // Default test user when run from Eclipse without program arguments
            email = "test@raez.com";
            password = "Test123!";
            username = "testuser";
            firstName = "Test";
            lastName = "User";
            roleStr = "ADMIN";
            isActive = true;
            System.out.println("No args provided – creating default test user (email=test@raez.com, password=Test123!)");
        }

        if (!"ADMIN".equals(roleStr) && !"FINANCE_USER".equals(roleStr)) {
            System.err.println("role must be ADMIN or FINANCE_USER");
            System.exit(1);
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        FUserDao dao = new FUserDao();

        try {
            dao.insertUser(email, username, passwordHash, roleStr, firstName, lastName, "",
                null, null, null, null, isActive);
            System.out.println("Created FUser:");
            System.out.println("  email: " + email);
            System.out.println("  username: " + username);
            System.out.println("  firstName: " + firstName + ", lastName: " + lastName);
            System.out.println("  role: " + roleStr + ", active: " + isActive);
            System.out.println();
            System.out.println("You can now login with email/username: " + email + " (or " + username + ") and your password.");
            System.out.println("(Role Selection → Admin Login for ADMIN; Finance User Login for FINANCE_USER)");
        } catch (Exception e) {
            System.err.println("Failed to create user: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                System.err.println("That email or username already exists. Use different args or run with custom email/username.");
            }
            e.printStackTrace();
            System.exit(1);
        }
    }
}
