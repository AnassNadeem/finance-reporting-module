package uk.ac.brunel.finance.app.security;


public class PasswordHasherTest {


public static void main(String[] args) {
String raw = "TestPassword123";
String hash = PasswordHasher.hashPassword(raw);


System.out.println("Hash: " + hash);
System.out.println("Matches: " + PasswordHasher.verifyPassword(raw, hash));
}
}