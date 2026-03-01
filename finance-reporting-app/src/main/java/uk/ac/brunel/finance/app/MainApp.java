package uk.ac.brunel.finance.app;
import  uk.ac.brunel.finance.app.finance.OrderService;
import  uk.ac.brunel.finance.app.finance.PaymentService;
import  uk.ac.brunel.finance.app.finance.RefundService;


import uk.ac.brunel.finance.app.auth.SessionManager;
import uk.ac.brunel.finance.app.auth.User;
import uk.ac.brunel.finance.app.authz.Role;

public class MainApp {

    public static void main(String[] args) {

		new PaymentService().processPayment(15000);
		new RefundService().processRefund(500, 200);
		new OrderService().placeOrder(75000);
        if (!SessionManager.isLoggedIn()) {
            System.out.println("No active session. Please log in.");
            return;
        }

        User user = SessionManager.getCurrentUser();
        Role role = user.getRole();

        System.out.println("Logged in as: " + user.getEmail());
        System.out.println("Role: " + role);

        switch (role) {
            case SUPER_ADMIN:
                System.out.println("Super Admin dashboard");
                break;
            case FINANCE_ADMIN:
                System.out.println("Finance Admin dashboard");
                break;
            case FINANCE_USER:
                System.out.println("Finance User dashboard");
                break;
            case VIEWER:
                System.out.println("Read-only dashboard");
                break;
                
                
        }

        SessionManager.logout();
    }
}
