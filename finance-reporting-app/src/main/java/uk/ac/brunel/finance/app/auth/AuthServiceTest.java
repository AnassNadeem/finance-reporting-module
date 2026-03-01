package uk.ac.brunel.finance.app.auth;

public class AuthServiceTest {

    public static void main(String[] args) {

        AuthService authService = new AuthService();

        AuthResult result = authService.authenticate(
                "finance.auditor@sys.com",
                "auditor123"
        );

        System.out.println(result.getMessage());

        if (result.isSuccess()) {
            System.out.println("Role: " + result.getRole());
        }
    }
}
