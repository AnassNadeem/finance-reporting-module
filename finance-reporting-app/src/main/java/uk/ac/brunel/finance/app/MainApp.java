package uk.ac.brunel.finance.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import uk.ac.brunel.finance.app.auth.CurrentUser;
import uk.ac.brunel.finance.app.authz.Action;
import uk.ac.brunel.finance.app.authz.AuthorizationService;
import uk.ac.brunel.finance.app.authz.DatabaseAuthorizationService;
import uk.ac.brunel.finance.app.authz.Role;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        // TEMP login (Phase 3.4)
        CurrentUser.login(Role.FINANCE_USER);

        AuthorizationService authz = new DatabaseAuthorizationService();

        Button viewDashboard = new Button("View Dashboard");
        Button manageData = new Button("Manage Finance Data");
        Button exportReports = new Button("Export Reports");
        Button manageUsers = new Button("Manage Users");

        // Initial RBAC enforcement
        manageData.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_FINANCE_DATA)
        );

        exportReports.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.EXPORT_REPORTS)
        );

        manageUsers.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_USERS)
        );

        // -----------------------------
        // CLICK HANDLERS (CRITICAL)
        // -----------------------------

        viewDashboard.setOnAction(e -> {
            System.out.println("CLICK: View Dashboard");

            if (!authz.isAllowed(CurrentUser.getRole(), Action.VIEW_DASHBOARD)) {
                System.out.println("DENIED: Session expired or no permission");
                return;
            }

            System.out.println("ALLOWED: Dashboard opened");
        });

        manageData.setOnAction(e -> {
            System.out.println("CLICK: Manage Finance Data");

            if (!authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_FINANCE_DATA)) {
                System.out.println("DENIED: Session expired or no permission");
                return;
            }

            System.out.println("ALLOWED: Managing finance data");
        });

        exportReports.setOnAction(e -> {
            System.out.println("CLICK: Export Reports");

            if (!authz.isAllowed(CurrentUser.getRole(), Action.EXPORT_REPORTS)) {
                System.out.println("DENIED: Session expired or no permission");
                return;
            }

            System.out.println("ALLOWED: Exporting reports");
        });

        manageUsers.setOnAction(e -> {
            System.out.println("CLICK: Manage Users");

            if (!authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_USERS)) {
                System.out.println("DENIED: Session expired or no permission");
                return;
            }

            System.out.println("ALLOWED: Managing users");
        });

        VBox root = new VBox(10,
                viewDashboard,
                manageData,
                exportReports,
                manageUsers
        );

        stage.setScene(new Scene(root, 320, 220));
        stage.setTitle("Finance App – Phase 3.4");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
