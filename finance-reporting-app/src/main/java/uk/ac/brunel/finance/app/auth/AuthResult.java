package uk.ac.brunel.finance.app.auth;

public class AuthResult {

    private final boolean success;
    private final String message;
    private final String role;

    private AuthResult(boolean success, String message, String role) {
        this.success = success;
        this.message = message;
        this.role = role;
    }

    public static AuthResult success(String message, String role) {
        return new AuthResult(true, message, role);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getRole() {
        return role;
    }
}
