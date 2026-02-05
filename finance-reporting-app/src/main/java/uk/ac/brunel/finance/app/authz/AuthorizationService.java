package uk.ac.brunel.finance.app.authz;

/**
 * Base authorization service.
 * This class defines the public API (isAllowed)
 * and delegates permission checks to hasPermission().
 *
 * Subclasses or anonymous classes override hasPermission().
 */
public class AuthorizationService {

    /**
     * Hook method for permission checks.
     * Default implementation denies everything.
     */
    protected boolean hasPermission(Role role, Action action) {
        return false;
    }

    /**
     * Public method used by UI and controllers.
     */
    public boolean isAllowed(Role role, Action action) {
        return hasPermission(role, action);
    }
}
