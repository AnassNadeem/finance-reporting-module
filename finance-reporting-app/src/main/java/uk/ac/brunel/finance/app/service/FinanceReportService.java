package uk.ac.brunel.finance.app.service;

import uk.ac.brunel.finance.app.auth.SessionManager;
import uk.ac.brunel.finance.app.authz.Action;
import uk.ac.brunel.finance.app.authz.AuthorizationService;
import uk.ac.brunel.finance.app.authz.DatabaseAuthorizationService;
import uk.ac.brunel.finance.app.authz.Role;

public class FinanceReportService {

    private final AuthorizationService authz =
        new DatabaseAuthorizationService();

    public void viewDashboard() {

        Role role = SessionManager.getCurrentUser().getRole();

        if (!authz.isAllowed(role, Action.VIEW_DASHBOARD)) {
            throw new SecurityException("Access denied: dashboard");
        }

        System.out.println("Dashboard loaded");
    }

    public void exportReports() {

        Role role = SessionManager.getCurrentUser().getRole();

        if (!authz.isAllowed(role, Action.EXPORT_REPORTS)) {
            throw new SecurityException("Access denied: export reports");
        }

        System.out.println("Reports exported");
    }
}
