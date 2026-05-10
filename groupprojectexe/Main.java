package groupprojectexe;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Main {

    // Check if admin exists - FULLY DEBUGGED VERSION
    private static boolean hasAdminAccount() {
    String sql = "SELECT id, username, role FROM users WHERE role = 'admin'";

    System.out.println("  [MAIN] Checking for admin account...");
    
    Connection conn = null;
    try {
        conn = MySqlConnector.getConnection();
        if (conn == null) {
            System.out.println("  [MAIN] ✗ Connection is null");
            return false;
        }
        
        try (PreparedStatement pst = conn.prepareStatement(sql); 
             ResultSet rs = pst.executeQuery()) {

            int count = 0;
            System.out.println("  [MAIN] Query results:");
            
            while (rs.next()) {
                count++;
                System.out.println("    Admin #" + count + ":");
                System.out.println("      - ID: " + rs.getInt("id"));
                System.out.println("      - Username: " + rs.getString("username"));
                System.out.println("      - Role: " + rs.getString("role"));
            }
            
            if (count == 0) {
                System.out.println("    No admins found");
            }
            
            System.out.println("  [MAIN] Total admins: " + count);
            return count > 0;
        }
    } catch (Exception e) {
        System.out.println("  [MAIN] ✗ Database Error: " + e.getMessage());
        e.printStackTrace();
        return false;
    } finally {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }
}

    // App Entry Point
    public static void main(String[] args) {

        System.out.println("\n═══════════════════════════════════");
        System.out.println("       STOCKPOS SYSTEM STARTING      ");
        System.out.println("═══════════════════════════════════\n");

        try {
            // Test connection first
            System.out.println("[STEP 1] Testing database connection...");
            if (!MySqlConnector.testConnection()) {
                System.out.println("\n✗ Cannot connect to database!");
                System.out.println("  Please ensure:");
                System.out.println("  1. MySQL server is running");
                System.out.println("  2. Database 'projectexe' exists");
                System.out.println("  3. Run the SQL setup script");
                return;
            }
            System.out.println();

            // Check for admin
            System.out.println("[STEP 2] Checking for admin account...");
            boolean hasAdmin = hasAdminAccount();
            System.out.println();

            // Decision
            if (hasAdmin) {
                System.out.println("[RESULT] Admin found → Opening Login Screen");
                System.out.println("═══════════════════════════════════\n");
                java.awt.EventQueue.invokeLater(() -> new LogInUser().setVisible(true));

            } else {
                System.out.println("[RESULT] No admin found → Opening Setup Wizard");
                System.out.println("═══════════════════════════════════\n");
                java.awt.EventQueue.invokeLater(() -> new CreateAccount().setVisible(true));
            }

        } catch (Exception e) {
            System.out.println("\n═══════════════════════════════════");
            System.out.println("       SYSTEM ERROR                  ");
            System.out.println("═══════════════════════════════════");
            System.out.println("  Error: " + e.getMessage());
            e.printStackTrace();
            System.out.println("═══════════════════════════════════");
        }
    }
}