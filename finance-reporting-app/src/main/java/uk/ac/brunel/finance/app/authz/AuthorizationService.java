package uk.ac.brunel.finance.app.authz;

public abstract class AuthorizationService {

    public final boolean isAllowed(Role role, Action action) {
        if (role == null || action == null) {
            return false;
        }
        return hasPermission(role, action);
    }

    protected abstract boolean hasPermission(Role role, Action action);
}
