package uk.ac.brunel.finance.app.auth;

import uk.ac.brunel.finance.app.authz.Role;

public class CurrentUser {

    private static Role role;

    public static void login(Role r) {
        role = r;
    }

    public static Role getRole() {
        return role;
    }

    public static void logout() {
        role = null;
    }
}
