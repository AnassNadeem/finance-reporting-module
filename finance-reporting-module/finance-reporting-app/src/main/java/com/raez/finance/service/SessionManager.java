package com.raez.finance.service;

import com.raez.finance.model.FUser;
import com.raez.finance.model.UserRole;

import java.time.Duration;
import java.time.Instant;

public final class SessionManager {

    private static FUser currentUser;
    private static Instant lastActivity;

    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    private SessionManager() {
    }

    public static void startSession(FUser user) {
        currentUser = user;
        lastActivity = Instant.now();
    }

    public static boolean isLoggedIn() {
        return currentUser != null && !isExpired();
    }

    /** Returns the current user's role, or null if not logged in or session expired. */
    public static UserRole getRole() {
        if (currentUser == null || isExpired()) {
            return null;
        }
        touch();
        return currentUser.getRole();
    }

    public static FUser getCurrentUser() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("Session expired or not logged in");
        }
        touch();
        return currentUser;
    }

    public static void logout() {
        currentUser = null;
        lastActivity = null;
    }

    private static void touch() {
        lastActivity = Instant.now();
    }

    private static boolean isExpired() {
        if (lastActivity == null) {
            return true;
        }
        return Instant.now().isAfter(lastActivity.plus(TIMEOUT));
    }
}
