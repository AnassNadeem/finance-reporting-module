package uk.ac.brunel.finance.app.database;


import java.sql.Connection;
import java.sql.SQLException;


public class DatabaseConnectionTest {


public static void main(String[] args) {
try {
Connection connection = DatabaseConnectionManager.getConnection();
System.out.println("✅ Database connected successfully");
} catch (SQLException e) {
e.printStackTrace();
}
}
}