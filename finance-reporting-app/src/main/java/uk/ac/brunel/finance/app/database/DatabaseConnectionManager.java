package uk.ac.brunel.finance.app.database;


import uk.ac.brunel.finance.app.config.DatabaseConfig;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DatabaseConnectionManager {


private static Connection connection;


public static Connection getConnection() throws SQLException {


if (connection == null || connection.isClosed()) {
connection = DriverManager.getConnection(
DatabaseConfig.DB_URL,
DatabaseConfig.DB_USER,
DatabaseConfig.DB_PASSWORD
);
}


return connection;
}
}