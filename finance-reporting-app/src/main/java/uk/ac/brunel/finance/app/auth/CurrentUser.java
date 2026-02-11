package uk.ac.brunel.finance.app.auth;

import uk.ac.brunel.finance.app.authz.Role;

public class CurrentUser {

    private static Role role;
    private static long lastActivityTime;

    private static final long TIMEOUT_MS = 10 * 1000;
// 15 minutes

    private CurrentUser() {}

    public static void login(Role userRole) {
        role = userRole;
        touch();
    }

    public static void logout() {
        role = null;
    }

    public static Role getRole() {
        if (isSessionExpired()) {
            logout();
            return null;
        }
        touch();
        return role;
    }

    public static boolean isLoggedIn() {
        return role != null && !isSessionExpired();
    }

    private static void touch() {
        lastActivityTime = System.currentTimeMillis();
    }

    private static boolean isSessionExpired() {
        return role != null &&
               (System.currentTimeMillis() - lastActivityTime) > TIMEOUT_MS;
    }
}
