package uk.ac.brunel.finance.app.authz;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthorizationServiceTest {

    private AuthorizationService authz;

    @Before
    public void setup() {
        authz = new AuthorizationService() {
            @Override
            protected boolean hasPermission(Role role, Action action) {

                if (role == Role.FINANCE_USER) {
                    return action == Action.VIEW_DASHBOARD ||
                           action == Action.MANAGE_FINANCE_DATA;
                }

                if (role == Role.VIEWER) {
                    return action == Action.VIEW_DASHBOARD;
                }

                return false;
            }
        };
    }

    @Test
    public void financeUserCanManageData() {
        assertTrue(authz.isAllowed(Role.FINANCE_USER, Action.MANAGE_FINANCE_DATA));
    }

    @Test
    public void financeUserCannotExport() {
        assertFalse(authz.isAllowed(Role.FINANCE_USER, Action.EXPORT_REPORTS));
    }

    @Test
    public void viewerCanOnlyView() {
        assertTrue(authz.isAllowed(Role.VIEWER, Action.VIEW_DASHBOARD));
        assertFalse(authz.isAllowed(Role.VIEWER, Action.MANAGE_FINANCE_DATA));
    }
}
