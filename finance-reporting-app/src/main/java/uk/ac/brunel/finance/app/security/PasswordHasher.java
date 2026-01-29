package uk.ac.brunel.finance.app.security;


import org.mindrot.jbcrypt.BCrypt;


public final class PasswordHasher {


private static final int WORK_FACTOR = 12;


private PasswordHasher() {}


public static String hashPassword(String plainPassword) {
return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
}


public static boolean verifyPassword(String plainPassword, String hashedPassword) {
return BCrypt.checkpw(plainPassword, hashedPassword);
}
}