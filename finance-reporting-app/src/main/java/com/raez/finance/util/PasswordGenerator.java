package com.raez.finance.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates random passwords suitable for temporary first-time login.
 * Ensures at least one character from each required set (upper, lower, digit, symbol).
 */
public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /**
     * Generates a random password of the given length (minimum 8).
     * Contains at least one uppercase, one lowercase, one digit, and one symbol.
     */
    public static String generate(int length) {
        if (length < 8) {
            length = 12;
        }
        List<Character> chars = new ArrayList<>();
        chars.add(pickOne(UPPER));
        chars.add(pickOne(LOWER));
        chars.add(pickOne(DIGITS));
        chars.add(pickOne(SYMBOLS));

        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        for (int i = chars.size(); i < length; i++) {
            chars.add(all.charAt(RANDOM.nextInt(all.length())));
        }

        // Shuffle so required chars are not always at the start
        for (int i = chars.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Character t = chars.get(i);
            chars.set(i, chars.get(j));
            chars.set(j, t);
        }

        StringBuilder sb = new StringBuilder(chars.size());
        for (Character c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** 12-character default. */
    public static String generate() {
        return generate(12);
    }

    private static char pickOne(String s) {
        return s.charAt(RANDOM.nextInt(s.length()));
    }
}
