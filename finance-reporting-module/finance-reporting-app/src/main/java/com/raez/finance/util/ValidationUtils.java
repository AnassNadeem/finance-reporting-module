package com.raez.finance.util;

/**
 * Shared validation for email domain and password strength.
 */
public final class ValidationUtils {

    private static final String RAEZ_EMAIL_SUFFIX = "@raez.org.uk";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private ValidationUtils() {
    }

    /**
     * Returns true if the email is non-null, trimmed, and ends with @raez.org.uk (case-insensitive).
     */
    public static boolean isRaezEmail(String email) {
        if (email == null) return false;
        String e = email.trim();
        return e.toLowerCase().endsWith(RAEZ_EMAIL_SUFFIX.toLowerCase());
    }

    /**
     * Returns an error message if the password does not meet policy; null if valid.
     * Policy: min 8 chars, at least one uppercase, one lowercase, one digit, one symbol.
     */
    public static String validateNewPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSymbol = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isLetterOrDigit(c)) hasSymbol = true;
        }
        if (!hasUpper) return "Password must contain at least one uppercase letter.";
        if (!hasLower) return "Password must contain at least one lowercase letter.";
        if (!hasDigit) return "Password must contain at least one digit.";
        if (!hasSymbol) return "Password must contain at least one symbol.";
        return null;
    }
}
