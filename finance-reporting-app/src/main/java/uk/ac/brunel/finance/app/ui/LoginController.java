package uk.ac.brunel.finance.app.ui;

import uk.ac.brunel.finance.app.auth.AuthResult;
import uk.ac.brunel.finance.app.auth.AuthService;
import uk.ac.brunel.finance.app.auth.CurrentUser;
import uk.ac.brunel.finance.app.authz.Role;

public class LoginController {

    private final AuthService authService = new AuthService();

    public boolean login(String email, String password) {

        AuthResult result = authService.authenticate(email, password);

        if (!result.isSuccess()) {
            System.out.println(result.getMessage());
            return false;
        }

        Role role = Role.valueOf(result.getRole());
        CurrentUser.login(role);

        return true;
    }
}
