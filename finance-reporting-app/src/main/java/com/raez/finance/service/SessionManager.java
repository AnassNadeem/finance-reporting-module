package com.raez.finance.service;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SessionManager
 *
 * Tracks the currently logged-in user and enforces an inactivity timeout.
 *
 * Timeout: {@link #SESSION_TIMEOUT_SECONDS} of inactivity (default 2 minutes for demo).
 * Warning UI should appear {@link #SESSION_WARNING_LEAD_SECONDS} before expiry.
 *
 * Usage:
 *   SessionManager.startSession(user);
 *   SessionManager.setOnTimeoutCallback(() -> navigateToLogin());
 *
 * The MainLayoutController calls setOnTimeoutCallback() after loading.
 * Scene filters call {@link #touchSessionFromUserInput()} for real input (throttled mouse move).
 * The inactivity checker runs every second on the FX thread.
 */
public final class SessionManager {

    /** Total inactivity allowed before forced logout. */
    public static final long SESSION_TIMEOUT_SECONDS = 120;

    /**
     * When remaining seconds are at or below this value, the UI may show the
     * non-blocking warning (e.g. 10 seconds before logout).
     */
    public static final long SESSION_WARNING_LEAD_SECONDS = 10;

    /** How often to check for expiry on the FX thread. */
    private static final long CHECK_INTERVAL_SECONDS = 1;

    private static FUser   currentUser;
    private static Instant lastActivity;
    private static Runnable onTimeoutCallback;

    // The checker timeline — recreated on each login
    private static Timeline inactivityChecker;

    /** When positive, {@link #touchSessionFromUserInput()} does not update last activity (timed UI refresh). */
    private static final AtomicInteger automatedRefreshDepth = new AtomicInteger(0);

    private SessionManager() {}

    // ══════════════════════════════════════════════════════════════
    //  SESSION CONTROL
    // ══════════════════════════════════════════════════════════════

    /** Called immediately after a successful login. */
    public static void startSession(FUser user) {
        currentUser  = user;
        lastActivity = Instant.now();
        startInactivityChecker();
    }

    /**
     * Sets the callback that is invoked on the FX thread when the session
     * expires due to inactivity. MainLayoutController wires this up after load.
     */
    public static void setOnTimeoutCallback(Runnable callback) {
        onTimeoutCallback = callback;
    }

    /**
     * Resets the inactivity clock. Use for explicit user actions (buttons, menu choices),
     * not for scene-wide input filters — those should use {@link #touchSessionFromUserInput()}.
     */
    public static void extendSession() {
        if (currentUser != null) {
            lastActivity = Instant.now();
        }
    }

    /**
     * Call from global scene filters (mouse/key). Ignored while a timed data refresh
     * is running so background refresh does not count as user activity.
     */
    public static void touchSessionFromUserInput() {
        if (currentUser == null || automatedRefreshDepth.get() > 0) return;
        lastActivity = Instant.now();
    }

    /** Wrap timed UI refresh; must be paired with {@link #endAutomatedDataRefresh()}. */
    public static void beginAutomatedDataRefresh() {
        automatedRefreshDepth.incrementAndGet();
    }

    public static void endAutomatedDataRefresh() {
        int v = automatedRefreshDepth.decrementAndGet();
        if (v < 0) automatedRefreshDepth.set(0);
    }

    /** Clears the session and stops the inactivity checker. */
    public static void logout() {
        currentUser  = null;
        lastActivity = null;
        stopInactivityChecker();
        onTimeoutCallback = null;
    }

    // ══════════════════════════════════════════════════════════════
    //  QUERY
    // ══════════════════════════════════════════════════════════════

    public static boolean isLoggedIn() {
        return currentUser != null && !isExpired();
    }

    public static UserRole getRole() {
        return (currentUser == null || isExpired()) ? null : currentUser.getRole();
    }

    public static boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }

    public static boolean isFinanceUser() {
        return getRole() == UserRole.FINANCE_USER;
    }

    /**
     * Returns the current user. Throws if the session is expired or not started.
     * Use getCurrentUserOrNull() for safe access.
     */
    public static FUser getCurrentUser() {
        if (!isLoggedIn()) throw new IllegalStateException("Session expired or not logged in.");
        return currentUser;
    }

    /** Returns the current user, or null if not logged in / expired. */
    public static FUser getCurrentUserOrNull() {
        return isLoggedIn() ? currentUser : null;
    }

    /** "James Carter" → "James Carter", or falls back to username/email. */
    public static String getDisplayName() {
        FUser u = getCurrentUserOrNull();
        if (u == null) return "User";
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last  = u.getLastName()  != null ? u.getLastName().trim()  : "";
        String full  = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (u.getUsername() != null && !u.getUsername().isBlank()) return u.getUsername();
        return u.getEmail() != null ? u.getEmail() : "User";
    }

    /** Returns uppercase initials, e.g. "JC" for James Carter. */
    public static String getInitials() {
        String name = getDisplayName();
        if (name == null || name.isEmpty()) return "U";
        String[] parts = name.split("\\s+");
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) sb.append(Character.toUpperCase(parts[1].charAt(0)));
        return sb.toString();
    }

    /** Remaining inactivity seconds before the session expires. */
    public static long getRemainingSeconds() {
        if (currentUser == null || lastActivity == null) return 0;
        long elapsed = java.time.Duration.between(lastActivity, Instant.now()).getSeconds();
        return Math.max(0, SESSION_TIMEOUT_SECONDS - elapsed);
    }

    // ══════════════════════════════════════════════════════════════
    //  INACTIVITY CHECKER
    // ══════════════════════════════════════════════════════════════

    private static void startInactivityChecker() {
        stopInactivityChecker(); // cancel any previous one

        inactivityChecker = new Timeline(
            new KeyFrame(Duration.seconds(CHECK_INTERVAL_SECONDS), e -> checkInactivity())
        );
        inactivityChecker.setCycleCount(Timeline.INDEFINITE);
        inactivityChecker.play();
    }

    private static void stopInactivityChecker() {
        if (inactivityChecker != null) {
            inactivityChecker.stop();
            inactivityChecker = null;
        }
    }

    /**
     * Runs on the FX thread every CHECK_INTERVAL_SECONDS.
     * If the session has expired, clears it and fires the timeout callback.
     */
    private static void checkInactivity() {
        if (currentUser == null) {
            stopInactivityChecker();
            return;
        }
        if (isExpired()) {
            System.out.println("[SessionManager] Session expired due to inactivity.");
            Runnable cb = onTimeoutCallback;
            logout(); // clears state + stops checker
            if (cb != null) {
                // Already on FX thread (Timeline runs on FX thread)
                cb.run();
            }
        }
    }

    private static boolean isExpired() {
        if (lastActivity == null) return true;
        long elapsed = java.time.Duration.between(lastActivity, Instant.now()).getSeconds();
        return elapsed >= SESSION_TIMEOUT_SECONDS;
    }
}