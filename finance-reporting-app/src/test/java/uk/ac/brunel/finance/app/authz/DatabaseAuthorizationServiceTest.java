package uk.ac.brunel.finance.app.authz;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatabaseAuthorizationServiceTest {

    private AuthorizationService authz;

    @Before
    public void setup() {
        authz = new DatabaseAuthorizationService();
    }

    @Test
    public void financeUserCanViewDashboard() {
        assertTrue(
            authz.isAllowed(
                Role.FINANCE_USER,
                Action.VIEW_DASHBOARD
            )
        );
    }

    @Test
    public void financeUserCannotExportReports() {
        assertFalse(
            authz.isAllowed(
                Role.FINANCE_USER,
                Action.EXPORT_REPORTS
            )
        );
    }

    @Test
    public void viewerHasReadOnlyAccess() {
        assertTrue(
            authz.isAllowed(
                Role.VIEWER,
                Action.VIEW_DASHBOARD
            )
        );

        assertFalse(
            authz.isAllowed(
                Role.VIEWER,
                Action.MANAGE_FINANCE_DATA
            )
        );
    }
}
