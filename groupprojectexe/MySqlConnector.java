package groupprojectexe;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySqlConnector {

    // UPDATE: Make database name a constant for easy changes
    private static final String DATABASE = "projectexe";
    private static final String URL = "jdbc:mysql://localhost:3306/" + DATABASE 
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static Connection getConnection() {
        System.out.println("  [DB] Attempting to connect to: " + DATABASE);
        
        try {
            // Load driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("  [DB] Driver loaded successfully");
            
            // Connect
            Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("  [DB] ✓ Connected to MySQL successfully!");
            con.setAutoCommit(true);
            
            return con;
            
        } catch (ClassNotFoundException e) {
            System.err.println("  [DB] ✗ ERROR: MySQL Driver not found!");
            System.err.println("  [DB] Solution: Add mysql-connector-j.jar to your project libraries");
            throw new RuntimeException("MySQL Driver not found", e);
            
        } catch (SQLException e) {
            System.err.println("  [DB] ✗ ERROR: Database connection failed!");
            System.err.println("  [DB] Error message: " + e.getMessage());
            System.err.println("  [DB] Error code: " + e.getErrorCode());
            
            // Provide helpful error messages based on error code
            if (e.getErrorCode() == 1049) {
                System.err.println("  [DB] Solution: Database '" + DATABASE + "' does not exist!");
                System.err.println("  [DB] Run: CREATE DATABASE " + DATABASE);
            } else if (e.getErrorCode() == 1045) {
                System.err.println("  [DB] Solution: Check username/password");
            } else if (e.getErrorCode() == 0) {
                System.err.println("  [DB] Solution: Make sure MySQL server is running!");
            }
            
            throw new RuntimeException("Database connection failed", e);
        }
    }
    
    // NEW: Method to test connection
    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            if (conn != null && !conn.isClosed()) {
                System.out.println("  [DB] ✓ Connection test PASSED");
                conn.close();
                return true;
            }
        } catch (Exception e) {
            System.out.println("  [DB] ✗ Connection test FAILED: " + e.getMessage());
        }
        return false;
    }
}
