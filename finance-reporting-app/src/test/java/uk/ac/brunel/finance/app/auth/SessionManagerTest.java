package uk.ac.brunel.finance.app.auth;

import org.junit.Test;
import uk.ac.brunel.finance.app.authz.Role;

import static org.junit.Assert.*;

public class SessionManagerTest {

    @Test
    public void testSessionStartAndLogout() {

        User user = new User(
            1,
            "test@test.com",
            Role.FINANCE_USER   // ✅ REAL ENUM VALUE
        );

        SessionManager.startSession(user);
        assertTrue(SessionManager.isLoggedIn());

        SessionManager.logout();
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetUserWithoutLogin() {
        SessionManager.logout();
        SessionManager.getCurrentUser();
    }
}
