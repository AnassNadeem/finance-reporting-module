package uk.ac.brunel.finance.app.ui;

import uk.ac.brunel.finance.app.auth.AuthResult;
import uk.ac.brunel.finance.app.auth.AuthService;
import uk.ac.brunel.finance.app.auth.SessionManager;

public class LoginController {

    private final AuthService authService = new AuthService();

    public boolean login(String email, String password) {

        AuthResult result = authService.authenticate(email, password);

        if (!result.isSuccess()) {
            System.out.println(result.getMessage());
            return false;
        }

        // ✅ Session already started in AuthService
        return SessionManager.isLoggedIn();
    }

    public void logout() {
        SessionManager.logout();
    }
}
