package uk.ac.brunel.finance.app.auth;

import java.time.Duration;
import java.time.Instant;

public final class SessionManager {

    private static User currentUser;
    private static Instant lastActivity;

    // 15 minutes timeout (change if needed)
    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private SessionManager() {}

    public static void startSession(User user) {
        currentUser = user;
        lastActivity = Instant.now();
    }

    public static User getCurrentUser() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("Session expired or not logged in");
        }
        touch();
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null && !isExpired();
    }

    public static void logout() {
        currentUser = null;
        lastActivity = null;
    }

    private static void touch() {
        lastActivity = Instant.now();
    }

    private static boolean isExpired() {
        if (lastActivity == null) return true;
        return Instant.now().isAfter(lastActivity.plus(TIMEOUT));
    }
}
