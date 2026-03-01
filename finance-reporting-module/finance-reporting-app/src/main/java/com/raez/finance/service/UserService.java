package com.raez.finance.service;

import com.raez.finance.dao.FUserDao;
import com.raez.finance.model.UserRole;
import org.mindrot.jbcrypt.BCrypt;

public class UserService {

    private final FUserDao fUserDao = new FUserDao();

    public void createUser(
            String email,
            String username,
            String plainPassword,
            UserRole role,
            String firstName,
            String lastName,
            String phone,
            boolean active
    ) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }

        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));

        try {
            fUserDao.insertUser(
                    email.trim(),
                    username.trim(),
                    hash,
                    role.name(),
                    emptyToNull(firstName),
                    emptyToNull(lastName),
                    emptyToNull(phone),
                    active
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
        }
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
