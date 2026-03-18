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

    public static UserRole getRole() {
        if (currentUser == null || isExpired()) {
            return null;
        }
        return currentUser.getRole();
    }

    public static boolean isAdmin() {
        return getRole() == UserRole.ADMIN;
    }

    public static boolean isFinanceUser() {
        return getRole() == UserRole.FINANCE_USER;
    }

    public static FUser getCurrentUser() {
        if (!isLoggedIn()) {
            throw new IllegalStateException("Session expired or not logged in");
        }
        return currentUser;
    }

    public static FUser getCurrentUserOrNull() {
        if (!isLoggedIn()) return null;
        return currentUser;
    }

    public static String getDisplayName() {
        FUser user = getCurrentUserOrNull();
        if (user == null) return "User";
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
        return user.getEmail() != null ? user.getEmail() : "User";
    }

    public static String getInitials() {
        String name = getDisplayName();
        if (name == null || name.isEmpty()) return "U";
        String[] parts = name.split("\\s+");
        String initials = parts[0].substring(0, 1);
        if (parts.length > 1) initials += parts[1].substring(0, 1);
        return initials.toUpperCase();
    }

    public static void logout() {
        currentUser = null;
        lastActivity = null;
    }

    private static boolean isExpired() {
        if (lastActivity == null) {
            return true;
        }
        return Instant.now().isAfter(lastActivity.plus(TIMEOUT));
    }

    public static long getRemainingSeconds() {
        if (currentUser == null || lastActivity == null) {
            return 0;
        }
        long secs = Duration.between(Instant.now(), lastActivity.plus(TIMEOUT)).getSeconds();
        return Math.max(0, secs);
    }

    public static void extendSession() {
        if (currentUser != null) {
            lastActivity = Instant.now();
        }
    }
}
