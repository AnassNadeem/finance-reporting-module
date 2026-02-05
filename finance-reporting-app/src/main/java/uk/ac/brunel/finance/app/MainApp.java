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

        // 🔐 TEMP: simulate logged-in user
        // (Later comes from AuthService)
        CurrentUser.login(Role.FINANCE_USER);

        // ✅ REAL database-backed authorization
        AuthorizationService authz = new DatabaseAuthorizationService();

        Button viewDashboardBtn = new Button("View Dashboard");
        Button manageDataBtn = new Button("Manage Finance Data");
        Button exportBtn = new Button("Export Reports");
        Button manageUsersBtn = new Button("Manage Users");

        // Authorization enforcement (DB-driven)
        viewDashboardBtn.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.VIEW_DASHBOARD)
        );

        manageDataBtn.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_FINANCE_DATA)
        );

        exportBtn.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.EXPORT_REPORTS)
        );

        manageUsersBtn.setDisable(
            !authz.isAllowed(CurrentUser.getRole(), Action.MANAGE_USERS)
        );

        VBox root = new VBox(10,
                viewDashboardBtn,
                manageDataBtn,
                exportBtn,
                manageUsersBtn
        );

        Scene scene = new Scene(root, 300, 200);

        stage.setTitle("Finance App - RBAC Demo");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
