package uk.ac.brunel.finance.app.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;

public class DatabaseConnectionManager {

    private static final String DB_URL =
        "jdbc:sqlite:D:/eclipse/finance-workspace/finance-reporting-module/finance-reporting-module/finance-reporting-app/src/main/resources/database/nc1605_finance.db";

    public static Connection getConnection() throws SQLException {

        // 🔍 PRINT ABSOLUTE FILE PATH
        String rawPath = DB_URL.replace("jdbc:sqlite:", "");
        File dbFile = new File(rawPath);

        System.out.println("Using DB URL: " + DB_URL);
        System.out.println("DB exists? " + dbFile.exists());
        System.out.println("DB absolute path: " + dbFile.getAbsolutePath());
        System.out.println("DB file size: " + dbFile.length() + " bytes");

        return DriverManager.getConnection(DB_URL);
    }
}
