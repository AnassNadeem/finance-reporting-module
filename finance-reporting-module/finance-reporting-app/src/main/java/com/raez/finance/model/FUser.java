package com.raez.finance.model;

import java.time.LocalDateTime;

public class FUser {

    private final int id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final String firstName;
    private final String lastName;
    private final boolean active;
    private final LocalDateTime lastLogin;

    public FUser(
            int id,
            String email,
            String username,
            String passwordHash,
            UserRole role,
            String firstName,
            String lastName,
            boolean active,
            LocalDateTime lastLogin
    ) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.firstName = firstName;
        this.lastName = lastName;
        this.active = active;
        this.lastLogin = lastLogin;
    }

    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public boolean isFirstLogin() {
        return lastLogin == null;
    }
}
