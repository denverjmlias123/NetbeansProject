package groupprojectexe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.RowFilter;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
 
public class DashboardInventory extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(DashboardInventory.class.getName());
 
    // User Session Data
    private final int userId;
    private final String userName;
    private String userRole; // NEW: Store user role
    
    // Table sorter for filtering
    private TableRowSorter<DefaultTableModel> sorter;
    private JComboBox<String> availabilityComboBox;
 
    public DashboardInventory(int userId, String username) {
    initComponents();
    this.userId = userId;
    this.userName = username;

    loadUserRole();
    lblTitle.setText("User: " + userName + " | Role: " + userRole);

    // Load initial data FIRST
    loadCategories();
    loadProductsTable();
    loadAssignUsers();
    loadTimeLog();
    loadDashboardStats();
    loadAvailableDates(); // NEW: populate the previous-date combo box

    // Add action listener AFTER initComponents()
    cmbSelection.addActionListener(e -> {
        if (cmbSelection.getSelectedIndex() >= 0) {
            loadDashboardSelection();
        }
    });

    // Set default selection LAST
    cmbSelection.setSelectedItem("Recent Activity");

    // Wire up the Reports tab combo box
    cmbSelectedReport.addActionListener(e -> {
        if (cmbSelectedReport.getSelectedIndex() >= 0) {
            loadSelectedReport();
        }
    });

    // NEW: Wire up the previous date combo box — reload report on change
    cmbSelectPreviousDate.addActionListener(e -> {
        if (cmbSelectedReport.getSelectedIndex() >= 0) {
            loadSelectedReport();
        }
    });

    // Trigger first report load
    loadSelectedReport();

    showInitializationMessage();
}
    /** Populates cmbSelectPreviousDate with "Today" + all distinct past transaction dates. */
    private void loadAvailableDates() {
    cmbSelectPreviousDate.removeAllItems();
    String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
    cmbSelectPreviousDate.addItem("Today (" + today + ")");

    String sql = "SELECT DISTINCT DATE(transaction_date) AS txn_date "
               + "FROM transactions "
               + "WHERE (user_id = ? OR user_id IN (SELECT id FROM users WHERE parent_id = ?)) "
               + "  AND DATE(transaction_date) < CURDATE() "
               + "ORDER BY txn_date DESC";

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setInt(1, userId);
        pst.setInt(2, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                cmbSelectPreviousDate.addItem(rs.getString("txn_date"));
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading available dates: ", e);
    }
}
    // NEW: Handle selection from cmbSelection
    private void loadDashboardSelection() {
    // Check if table is initialized
    if (tblSelectTableAction == null) return;
    
    String selection = cmbSelection.getSelectedItem().toString();
    
    switch(selection) {
        case "Recent Activity":
            loadRecentActivity();
            break;
        case "Stock Alert":
            loadStockAlert();
            break;
        case "Stocks Date Good/Expired":
            loadStocksDateGoodExpired();
            break;
    }
}
 
    // ═══════════════════════════════════════════════════════════════
    //  REPORTS TAB — cmbSelectedReport dispatcher + 5 loaders
    // ═══════════════════════════════════════════════════════════════
    /** Called whenever cmbSelectedReport changes. Routes to the right loader. */
    private void loadSelectedReport() {
        if (tblSelectReport == null || cmbSelectedReport == null) return;
        String selected = (String) cmbSelectedReport.getSelectedItem();
        if (selected == null) return;
 
        switch (selected) {
            case "All Inventory History Product":
                loadReportAllInventoryHistory();
                break;
            case "All Product List Sales Today":
                loadReportProductSalesToday();
                break;
            case "All Transcation List Today":
                loadReportTransactionListToday();
                break;
            case "Total Sales List Today":
                loadReportTotalSalesToday();
                break;
            case "Total Monthly Sales":
                loadReportTotalMonthlySales();
                break;
            default:
                break;
        }
    }
    /**
     * All Inventory History Product
     * Shows every activity_log entry (stock in, stock out, adds, edits, deletes)
     * for this admin and all cashiers under them.
     * Columns: Date/Time | Product Name | Qty | Action | Done By | Reason
     */
    private void loadReportAllInventoryHistory() {
        DefaultTableModel model = (DefaultTableModel) tblSelectReport.getModel();
        model.setRowCount(0);
        model.setColumnIdentifiers(new Object[]{
            "Date/Time", "Product Name", "Qty", "Action", "Done By", "Reason"
        });
 
        if (!tableExists("activity_log")) return;
 
        String sql = "SELECT al.log_time, al.product_name, al.quantity, al.action_type, "
                   + "       u.username, al.reason "
                   + "FROM activity_log al "
                   + "JOIN users u ON al.user_id = u.id "
                   + "WHERE al.user_id = ? "
                   + "   OR al.user_id IN (SELECT id FROM users WHERE parent_id = ?) "
                   + "ORDER BY al.log_time DESC";
 
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    String reason = rs.getString("reason");
                    if (reason != null) {
                        int pipe = reason.indexOf(" | ");
                        if (pipe != -1) reason = reason.substring(0, pipe).trim();
                    }
                    model.addRow(new Object[]{
                        sdf.format(rs.getTimestamp("log_time")),
                        rs.getString("product_name"),
                        rs.getInt("quantity"),
                        rs.getString("action_type"),
                        rs.getString("username"),
                        reason != null ? reason : ""
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading inventory history report: ", e);
        }
    }
    /**
     * All Product List Sales Today
     * Shows each distinct product sold today with total qty sold and total revenue.
     * Columns: Product Name | Qty Sold | Unit Price | Total Revenue | Cashier
     */
    private void loadReportProductSalesToday() {
    DefaultTableModel model = (DefaultTableModel) tblSelectReport.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{
        "Product Name", "Qty Sold", "Unit Price (₱)", "Total Revenue (₱)", "Sold By"
    });

    if (!tableExists("transaction_items") || !tableExists("transactions")) return;

    String reportDate = getSelectedReportDate(); // CHANGED: was promptReportDate()

    String sql = "SELECT ti.product_name, SUM(ti.quantity) AS total_qty, "
               + "       ti.unit_price, SUM(ti.subtotal) AS total_rev, u.username "
               + "FROM transaction_items ti "
               + "JOIN transactions t  ON ti.transaction_id = t.id "
               + "JOIN users u         ON t.user_id = u.id "
               + "WHERE DATE(t.transaction_date) = ? "
               + "  AND (t.user_id = ? OR t.user_id IN (SELECT id FROM users WHERE parent_id = ?)) "
               + "GROUP BY ti.product_name, ti.unit_price, u.username "
               + "ORDER BY total_rev DESC";

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setString(1, reportDate);
        pst.setInt(2, userId);
        pst.setInt(3, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("product_name"),
                    rs.getInt("total_qty"),
                    String.format("%.2f", rs.getDouble("unit_price")),
                    String.format("%.2f", rs.getDouble("total_rev")),
                    rs.getString("username")
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading product sales today report: ", e);
    }
}
    // NEW: Prompts the user to pick a report date. Returns today's date string if cancelled.
    /** Reads the selected date from cmbSelectPreviousDate. Returns today's date string if "Today" is selected. */
    private String getSelectedReportDate() {
    String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
    if (cmbSelectPreviousDate == null || cmbSelectPreviousDate.getSelectedItem() == null) return today;
    String selected = cmbSelectPreviousDate.getSelectedItem().toString();
    // "Today (yyyy-MM-dd)" item — extract the date
    if (selected.startsWith("Today")) return today;
    return selected.trim();
}
    /**
     * All Transaction List Today
     * One row per completed transaction today.
     * Columns: Txn # | Date/Time | Cashier | Payment Method | Subtotal (₱) | Tax (₱) | Grand Total (₱) | Cash Tendered | Change
     */
    private void loadReportTransactionListToday() {
    DefaultTableModel model = (DefaultTableModel) tblSelectReport.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{
        "Txn #", "Date/Time", "Cashier", "Payment", "Subtotal (₱)", "Tax (₱)", "Grand Total (₱)", "Cash Tendered (₱)", "Change (₱)"
    });

    if (!tableExists("transactions")) return;

    String reportDate = getSelectedReportDate(); // CHANGED: was promptReportDate()

    String sql = "SELECT t.id, t.transaction_date, u.username, t.payment_method, "
               + "       t.total_amount, t.cash_tendered, t.change_given "
               + "FROM transactions t "
               + "JOIN users u ON t.user_id = u.id "
               + "WHERE DATE(t.transaction_date) = ? "
               + "  AND (t.user_id = ? OR t.user_id IN (SELECT id FROM users WHERE parent_id = ?)) "
               + "ORDER BY t.transaction_date DESC";

    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setString(1, reportDate);
        pst.setInt(2, userId);
        pst.setInt(3, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                double grand    = rs.getDouble("total_amount");
                double subtotal = grand / 1.12;
                double tax      = grand - subtotal;
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    sdf.format(rs.getTimestamp("transaction_date")),
                    rs.getString("username"),
                    rs.getString("payment_method"),
                    String.format("%.2f", subtotal),
                    String.format("%.2f", tax),
                    String.format("%.2f", grand),
                    String.format("%.2f", rs.getDouble("cash_tendered")),
                    String.format("%.2f", rs.getDouble("change_given"))
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading transaction list today report: ", e);
    }
}
    /**
     * Total Sales List Today
     * Summary row per cashier (or admin) for today's totals.
     * Columns: Cashier | Role | # Transactions | Total Items Sold | Grand Total Sales (₱)
     */
    private void loadReportTotalSalesToday() {
    DefaultTableModel model = (DefaultTableModel) tblSelectReport.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{
        "Cashier", "Role", "# Transactions", "Total Items Sold", "Grand Total Sales (₱)"
    });

    if (!tableExists("transactions") || !tableExists("transaction_items")) return;

    String reportDate = getSelectedReportDate(); // CHANGED: was promptReportDate()

    String sql = "SELECT u.username, u.role, "
               + "       COUNT(t.id)           AS txn_count, "
               + "       COALESCE(SUM(ti.quantity), 0) AS items_sold, "
               + "       SUM(t.total_amount)   AS total_sales "
               + "FROM transactions t "
               + "JOIN users u ON t.user_id = u.id "
               + "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id "
               + "WHERE DATE(t.transaction_date) = ? "
               + "  AND (t.user_id = ? OR t.user_id IN (SELECT id FROM users WHERE parent_id = ?)) "
               + "GROUP BY u.username, u.role "
               + "ORDER BY total_sales DESC";

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setString(1, reportDate);
        pst.setInt(2, userId);
        pst.setInt(3, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getInt("txn_count"),
                    rs.getInt("items_sold"),
                    String.format("%.2f", rs.getDouble("total_sales"))
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading total sales today report: ", e);
    }
}
    /**
     * Total Monthly Sales
     * One summary row per calendar month.
     * Columns: Month | # Transactions | Total Items Sold | Grand Total Sales (₱) | Avg per Transaction (₱)
     */
    private void loadReportTotalMonthlySales() {
        DefaultTableModel model = (DefaultTableModel) tblSelectReport.getModel();
        model.setRowCount(0);
        model.setColumnIdentifiers(new Object[]{
            "Month", "# Transactions", "Total Items Sold", "Grand Total Sales (₱)", "Avg per Transaction (₱)"
        });
 
       if (!tableExists("transactions") || !tableExists("transaction_items")) return;
 
        String sql = "SELECT DATE_FORMAT(t.transaction_date, '%Y-%m') AS month, "
                   + "       COUNT(t.id)                              AS txn_count, "
                   + "       COALESCE(SUM(ti.quantity), 0)           AS items_sold, "
                   + "       SUM(t.total_amount)                     AS total_sales, "
                   + "       AVG(t.total_amount)                     AS avg_sale "
                   + "FROM transactions t "
                   + "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id "
                   + "WHERE t.user_id = ? OR t.user_id IN (SELECT id FROM users WHERE parent_id = ?) "
                   + "GROUP BY DATE_FORMAT(t.transaction_date, '%Y-%m') "
                   + "ORDER BY month DESC";
 
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("month"),
                        rs.getInt("txn_count"),
                        rs.getInt("items_sold"),
                        String.format("%.2f", rs.getDouble("total_sales")),
                        String.format("%.2f", rs.getDouble("avg_sale"))
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading monthly sales report: ", e);
        }
    }
    // NEW: Load Recent Activity
    // Shows ALL actions but only: Product Name, Quantity, Action, Reason
    private void loadRecentActivity() {
    DefaultTableModel model = (DefaultTableModel) tblSelectTableAction.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{"Product Name", "Quantity", "Action", "Reason"});

    if (!tableExists("activity_log")) {
        System.out.println("Table 'activity_log' does not exist. Skipping...");
        return;
    }

    String sql = "SELECT product_name, quantity, action_type, reason "
               + "FROM activity_log "
               + "WHERE (user_id = ? OR user_id IN (SELECT id FROM users WHERE parent_id = ?)) "
               + "AND action_type IN ('Product Added', 'Stock In', 'Stock Out', 'Edit', 'Delete') "
               + "ORDER BY log_time DESC LIMIT 50";

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {

        pst.setInt(1, userId);
        pst.setInt(2, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                String actionType = rs.getString("action_type");
                String reason     = rs.getString("reason");

                if (reason != null) {
                    int pipeIdx = reason.indexOf(" | ");
                    if (pipeIdx != -1) {
                        reason = reason.substring(0, pipeIdx).trim();
                    }
                }

                String actionDisplay;
                switch (actionType) {
                    case "Product Added": actionDisplay = "Added";     break;
                    case "Stock In":      actionDisplay = "Stock In";  break;
                    case "Stock Out":     actionDisplay = "Stock Out"; break;
                    case "Edit":          actionDisplay = "Edited";    break;
                    case "Delete":        actionDisplay = "Deleted";   break;
                    default:              actionDisplay = actionType;  break;
                }

                model.addRow(new Object[]{
                    rs.getString("product_name"),
                    rs.getInt("quantity"),
                    actionDisplay,
                    reason
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading recent activity: ", e);
    }
}
    // NEW: Load Stock Alert (Low Stock + Out of Stock)
    private void loadStockAlert() {
        DefaultTableModel model = (DefaultTableModel) tblSelectTableAction.getModel();
        model.setRowCount(0);
        model.setColumnIdentifiers(new Object[]{"Product Name", "Current Stock", "Low Stock Alert", "Status", "Category"});
 
        // First: Load Low Stock products
        String sqlLowStock = "SELECT name, stock, low_stock_alert, category FROM products "
                           + "WHERE user_id = ? AND stock > 0 AND stock <= low_stock_alert "
                           + "ORDER BY stock ASC";
 
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sqlLowStock)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getInt("stock"),
                        rs.getInt("low_stock_alert"),
                        "LOW STOCK",
                        rs.getString("category")
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading low stock: ", e);
        }
 
        // Second: Load Out of Stock products
        String sqlOutOfStock = "SELECT name, stock, low_stock_alert, category FROM products "
                              + "WHERE user_id = ? AND stock = 0 ORDER BY name ASC";
 
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sqlOutOfStock)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("name"),
                        rs.getInt("stock"),
                        rs.getInt("low_stock_alert"),
                        "OUT OF STOCK",
                        rs.getString("category")
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading out of stock: ", e);
        }
    }
    // NEW: Load Stocks Date Good/Expired
    private void loadStocksDateGoodExpired() {
    DefaultTableModel model = (DefaultTableModel) tblSelectTableAction.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{
        "Product Name", "Stock", "Expiration Date", "Days Left", "Status", "Type"
    });

    if (!tableExists("products")) return;

    // FIX: only exclude unavailable/incoming products, keep both current + next batch for available ones
    String sql = "SELECT name, stock, next_batch_stock, expiration_date, next_expiration_date "
               + "FROM products "
               + "WHERE user_id = ? "
               + "AND availability = 'Available' "
               + "AND (expiration_date IS NOT NULL OR next_expiration_date IS NOT NULL) "
               + "ORDER BY COALESCE(expiration_date, next_expiration_date) ASC";

    java.util.Calendar calToday = java.util.Calendar.getInstance();
    calToday.set(java.util.Calendar.HOUR_OF_DAY, 0);
    calToday.set(java.util.Calendar.MINUTE, 0);
    calToday.set(java.util.Calendar.SECOND, 0);
    calToday.set(java.util.Calendar.MILLISECOND, 0);

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {

        pst.setInt(1, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                String        name           = rs.getString("name");
                int           currentStock   = rs.getInt("stock");
                int           nextBatchStock = rs.getInt("next_batch_stock");
                java.sql.Date expDate        = rs.getDate("expiration_date");
                java.sql.Date nextDate       = rs.getDate("next_expiration_date");

                if (expDate != null) {
                    model.addRow(buildDateRow(name, currentStock, expDate, calToday, "Current"));
                }
                if (nextDate != null) {
                    model.addRow(buildDateRow(name, nextBatchStock, nextDate, calToday, "Incoming Batch"));
                }
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading stocks date: ", e);
    }
}
    // Helper — builds a single date row for the table
    private Object[] buildDateRow(String name, int stock, java.sql.Date date,
                               java.util.Calendar calToday, String type) {
    java.util.Calendar calExp = java.util.Calendar.getInstance();
    calExp.setTime(date);
    calExp.set(java.util.Calendar.HOUR_OF_DAY, 0);
    calExp.set(java.util.Calendar.MINUTE, 0);
    calExp.set(java.util.Calendar.SECOND, 0);
    calExp.set(java.util.Calendar.MILLISECOND, 0);
 
    long diffMs   = calExp.getTimeInMillis() - calToday.getTimeInMillis();
    int  daysLeft = (int)(diffMs / (1000 * 60 * 60 * 24));
 
    String daysDisplay;
    String status;
 
    if (daysLeft < 0) {
        daysDisplay = "Expired";
        status      = "Expired";
    } else if (daysLeft == 0) {
        daysDisplay = "Today";
        status      = "Expires Today";
    } else if (daysLeft <= 7) {
        daysDisplay = daysLeft + " days left";
        status      = "Expiring Soon";
    } else {
        daysDisplay = daysLeft + " days left";
        status      = "Good";
    }
 
    return new Object[]{ name, stock, date.toString(), daysDisplay, status, type };
}
    // NEW: Load user role from database
    private void loadUserRole() {
        String sql = "SELECT role FROM users WHERE id = ?";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    userRole = rs.getString("role");
                } else {
                    userRole = "Unknown"; // Default if not found
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading user role: ", e);
            userRole = "Unknown";
        }
    }
    // NEW: Load all dashboard statistics
    private void loadDashboardStats() {
        loadTodaySales();
        loadLowStockCount();
        loadOutOfStockCount();
        loadProductCount();
        loadAttentionProducts();
    }
    // NEW: Get today's sales
    private void loadTodaySales() {
    if (!tableExists("transactions")) {
        System.out.println("Table 'transactions' does not exist. Setting sales to ₱0.00");
        lblTodaySales.setText("₱0.00");
        return;
    }
 
    // FIX: Include admin's own sales + all cashiers under this admin
    String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM transactions "
               + "WHERE DATE(transaction_date) = CURDATE() "
               + "AND (user_id = ? OR user_id IN (SELECT id FROM users WHERE parent_id = ?))";
 
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
 
        pst.setInt(1, userId);
        pst.setInt(2, userId);  // FIX: second parameter for parent_id check
        try (ResultSet rs = pst.executeQuery()) {
            if (rs.next()) {
                double totalSales = rs.getDouble(1);
                lblTodaySales.setText(String.format("₱%.2f", totalSales));
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading today's sales: ", e);
        lblTodaySales.setText("₱0.00");
    }
}
    // NEW: Count low stock products
    private void loadLowStockCount() {
        String sql = "SELECT COUNT(*) FROM products WHERE user_id = ? AND stock > 0 AND stock <= low_stock_alert";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    lblLowStock.setText(String.valueOf(count));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading low stock count: ", e);
            lblLowStock.setText("0");
        }
    }
    // NEW: Count out of stock products
    private void loadOutOfStockCount() {
        String sql = "SELECT COUNT(*) FROM products WHERE user_id = ? AND stock = 0";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    lblOutOfStock.setText(String.valueOf(count));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading out of stock count: ", e);
            lblOutOfStock.setText("0");
        }
    } 
    // NEW: Count all products
    private void loadProductCount() {
        String sql = "SELECT COUNT(*) FROM products WHERE user_id = ?";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    lblCountProduct.setText(String.valueOf(count));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading product count: ", e);
            lblCountProduct.setText("0");
        }
    } 
    // NEW: Get products that need attention (low stock or out of stock)
    private void loadAttentionProducts() {
        StringBuilder attentionText = new StringBuilder();
        String sql = "SELECT name, stock FROM products WHERE user_id = ? AND (stock = 0 OR stock <= low_stock_alert) ORDER BY stock ASC LIMIT 5";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                boolean hasAttention = false;
                while (rs.next()) {
                    hasAttention = true;
                    if (attentionText.length() > 0) {
                        attentionText.append(", ");
                    }
                    String productName = rs.getString("name");
                    int stock = rs.getInt("stock");
                    if (stock == 0) {
                        attentionText.append(productName).append(" (Out of Stock)");
                    } else {
                        attentionText.append(productName).append(" (Stock: ").append(stock).append(")");
                    }
                }
                
                if (hasAttention) {
                    lblAttention.setText(attentionText.toString());
                } else {
                    lblAttention.setText("All products are well stocked!");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading attention products: ", e);
            lblAttention.setText("Unable to load alerts");
        }
    }
    private void showInitializationMessage() {
        System.out.println("═══════════════════════════════════");
        System.out.println("   INVENTORY DASHBOARD INITIALIZED   ");
        System.out.println("═══════════════════════════════════");
        System.out.println("  User: " + userName);
        System.out.println("  User ID: " + userId);
        System.out.println("  Role: " + userRole);
        System.out.println("  Status: Ready");
        System.out.println("═══════════════════════════════════");
    }
    // NEW: Load Categories for Filter
    private void loadCategories() {
    cmbCategory.removeAllItems();
    cmbCategory.addItem("All Categories"); // Default option

    String sql = "SELECT DISTINCT category FROM products "
               + "WHERE user_id = ? AND category IS NOT NULL AND category != '' "
               + "AND category NOT IN (SELECT category_name FROM deleted_categories WHERE user_id = ?) "
               + "ORDER BY category";

    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {

        pst.setInt(1, userId);
        pst.setInt(2, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                String category = rs.getString("category");
                if (category != null && !category.trim().isEmpty()) {
                    cmbCategory.addItem(category);
                }
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading categories: ", e);
    }
}
    // Data Loading Methods
    private void loadProductsTable() {
    DefaultTableModel model = (DefaultTableModel) tblShowAddedProduct.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{
        "ID", "Name", "Category", "Stock", "Price", "Availability", "Status", "Date"
    });
 
    String sql = "SELECT prod_id, name, category, stock, price, availability, low_stock_alert, expiration_date "
               + "FROM products WHERE user_id = ? "
               + "ORDER BY "
               + "  CASE WHEN stock = 0 AND expiration_date IS NOT NULL AND expiration_date > CURDATE() THEN 1 ELSE 0 END ASC, "
               + "  prod_id ASC";
 
    java.util.Calendar calToday = java.util.Calendar.getInstance();
    calToday.set(java.util.Calendar.HOUR_OF_DAY, 0);
    calToday.set(java.util.Calendar.MINUTE, 0);
    calToday.set(java.util.Calendar.SECOND, 0);
    calToday.set(java.util.Calendar.MILLISECOND, 0);
 
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setInt(1, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                int stock = rs.getInt("stock");
                int lowStockAlert = rs.getInt("low_stock_alert");
                String status = calculateStatus(stock, lowStockAlert);
                java.sql.Date expDate = rs.getDate("expiration_date");
 
                String dateDisplay = "";
                if (expDate != null) {
                    java.util.Calendar calExp = java.util.Calendar.getInstance();
                    calExp.setTime(expDate);
                    calExp.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calExp.set(java.util.Calendar.MINUTE, 0);
                    calExp.set(java.util.Calendar.SECOND, 0);
                    calExp.set(java.util.Calendar.MILLISECOND, 0);
 
                    if (calExp.before(calToday)) {
                        dateDisplay = "⚠️ Expired";
                    } else {
                        dateDisplay = expDate.toString(); // future or today: show the date
                    }
                }
 
                model.addRow(new Object[]{
                    rs.getInt("prod_id"),
                    rs.getString("name"),
                    rs.getString("category"),
                    stock,
                    rs.getDouble("price"),
                    rs.getString("availability"),
                    status,
                    dateDisplay
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading products: ", e);
        JOptionPane.showMessageDialog(this, "Error loading products: " + e.getMessage());
    }
 
    setupAvailabilityColumn();
    setupRealTimeSearch();
    applyProductTableRenderer(); // grey out future rows
}
    // FIXED: Now accepts lowStockAlert parameter
    private String calculateStatus(int stock, int lowStockAlert) {
        if (stock == 0) {
            return "Out of Stock";
        } else if (stock <= lowStockAlert) {  // FIX: Use dynamic lowStockAlert
            return "Low Stock";
        } else {
            return "Full Stock";
        }
    }
    // NEW: Setup Editable Availability Column
    private void setupAvailabilityColumn() {
        // Create combo box for availability
        availabilityComboBox = new JComboBox<>(new String[]{"Available", "Unavailable"});
        
        // Get the availability column (column 5)
        TableColumn availabilityColumn = tblShowAddedProduct.getColumnModel().getColumn(5);
        availabilityColumn.setCellEditor(new DefaultCellEditor(availabilityComboBox));
        
        // Add listener to detect changes
        DefaultTableModel model = (DefaultTableModel) tblShowAddedProduct.getModel();
        model.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 5) {
                    int row = e.getFirstRow();
                    int prodId = (int) model.getValueAt(row, 0);
                    String newAvailability = (String) model.getValueAt(row, 5);
                    updateProductAvailability(prodId, newAvailability);
                }
            }
        });
    }
    // NEW: Update Availability in Database
    private void updateProductAvailability(int prodId, String availability) {
        String sql = "UPDATE products SET availability = ? WHERE prod_id = ? AND user_id = ?";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setString(1, availability);
            pst.setInt(2, prodId);
            pst.setInt(3, userId);
            
            int updated = pst.executeUpdate();
            
            if (updated > 0) {
                System.out.println("✓ Product " + prodId + " availability updated to: " + availability);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating availability: ", e);
            JOptionPane.showMessageDialog(this, "Error updating availability: " + e.getMessage());
        }
    }
    // NEW: Setup Real-Time Search
    private void setupRealTimeSearch() {
        DefaultTableModel model = (DefaultTableModel) tblShowAddedProduct.getModel();
        sorter = new TableRowSorter<>(model);
        tblShowAddedProduct.setRowSorter(sorter);
    }
    // NEW: Apply Filters (Search + Category)
    private void applyFilters() {
        if (sorter == null) {
            sorter = new TableRowSorter<>((DefaultTableModel) tblShowAddedProduct.getModel());
            tblShowAddedProduct.setRowSorter(sorter);
        }
        
        String searchText = txtSearch.getText().trim();
        String selectedCategory = (cmbCategory.getSelectedItem() != null) 
            ? cmbCategory.getSelectedItem().toString() 
            : "All Categories";
        
        RowFilter<DefaultTableModel, Object> searchFilter = null;
        RowFilter<DefaultTableModel, Object> categoryFilter = null;
        
        // Search filter on Name column (column 1)
        if (!searchText.isEmpty()) {
            searchFilter = RowFilter.regexFilter("(?i)" + searchText, 1); // Column 1 = Name
        }
        
        // Category filter (column 2)
        if (!selectedCategory.equals("All Categories")) {
            categoryFilter = new RowFilter<DefaultTableModel, Object>() {
                @Override
                public boolean include(RowFilter.Entry<? extends DefaultTableModel, ? extends Object> entry) {
                    String category = (String) entry.getValue(2);
                    return selectedCategory.equals(category);
                }
            };
        }
        
        // Combine filters
        if (searchFilter != null && categoryFilter != null) {
            sorter.setRowFilter(RowFilter.andFilter(java.util.Arrays.asList(searchFilter, categoryFilter)));
        } else if (searchFilter != null) {
            sorter.setRowFilter(searchFilter);
        } else if (categoryFilter != null) {
            sorter.setRowFilter(categoryFilter);
        } else {
            sorter.setRowFilter(null); // No filter
        }
    }
    private void loadAssignUsers() {
    DefaultTableModel model = (DefaultTableModel) tblShowAssignUser.getModel();
    model.setRowCount(0);
    model.setColumnIdentifiers(new Object[]{"Username", "Role", "Assigned By", "Status"});
 
    String sql = "SELECT username, role, is_active FROM users WHERE parent_id = ?";
 
    try (Connection conn = MySqlConnector.getConnection(); PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setInt(1, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getString("username"),
                    rs.getString("role"),
                    this.userName,
                    rs.getInt("is_active") == 1 ? "Active" : "Deactivated"
                });
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading users: ", e);
    }
}
    private void loadTimeLog() {
        DefaultTableModel model = (DefaultTableModel) tblTimeLogUser.getModel();
        model.setRowCount(0);
        // FIX: Show each entry as its own row — don't try to pair Login/Logout
        model.setColumnIdentifiers(new Object[]{"Username", "Action", "Time", "Module", "Role"});
 
        String sql = "SELECT t.username, t.action, t.log_time, t.module, u.role "
                   + "FROM time_log t "
                   + "JOIN users u ON t.user_id = u.id "
                   + "WHERE t.user_id = ? OR u.parent_id = ? "  // FIX: also show cashier logs
                   + "ORDER BY t.log_time DESC LIMIT 20";
 
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
 
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, userId);
            pst.setInt(2, userId);  // FIX: second param for parent_id
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getString("username"),
                        rs.getString("action"),                          // FIX: show action directly
                        sdf.format(rs.getTimestamp("log_time")),         // FIX: always show the time
                        rs.getString("module"),
                        rs.getString("role")
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading time log: ", e);
        }
    }
    // UPDATED: Refresh Data - Now also refreshes dashboard stats
    public void refreshData() {
        loadCategories();       // Reload categories
        loadProductsTable();   // Reload products
        loadAssignUsers();
        loadTimeLog();
        loadDashboardStats();  // Reload dashboard statistics
        loadDashboardSelection(); // Reload current selection
        loadSelectedReport();  // Reload current report view
 
        System.out.println("✓ Dashboard data refreshed");
    } 
    private String generateSecurePassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
        StringBuilder password = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }
    
    // Helper method to check if table exists
    private boolean tableExists(String tableName) {
    String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'projectexe' AND table_name = ?";
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
        pst.setString(1, tableName);
        try (ResultSet rs = pst.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error checking if table exists: ", e);
    }
    return false;
}
    private void deleteByCategory() {
    java.util.List<String> categories = new java.util.ArrayList<>();
    String sqlCats = "SELECT DISTINCT category FROM products "
                   + "WHERE user_id = ? AND category IS NOT NULL AND category != '' "
                   + "AND category NOT IN (SELECT category_name FROM deleted_categories WHERE user_id = ?) "
                   + "ORDER BY category";
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sqlCats)) {
        pst.setInt(1, userId);
        pst.setInt(2, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        }
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error loading categories: " + e.getMessage());
        logger.log(Level.SEVERE, "Error loading categories for delete: ", e);
        return;
    }

    if (categories.isEmpty()) {
        JOptionPane.showMessageDialog(this, "No categories found.");
        return;
    }

    String[] catArray = categories.toArray(new String[0]);
    String selected = (String) JOptionPane.showInputDialog(
        this,
        "Select a category to remove from the Add Product list.\n"
        + "Note: Existing products in this category will NOT be deleted.\n"
        + "The category will just no longer be available when adding new products.",
        "Remove Category",
        JOptionPane.PLAIN_MESSAGE,
        null,
        catArray,
        catArray[0]
    );

    if (selected == null) return;

    int confirm = JOptionPane.showConfirmDialog(
        this,
        "Remove category \"" + selected + "\" from the Add Product options?\n"
        + "Existing products with this category will remain.\n"
        + "You will no longer be able to assign this category to new products.",
        "Confirm Remove Category",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE
    );

    if (confirm != JOptionPane.YES_OPTION) return;

    String sqlMark = "INSERT INTO deleted_categories (user_id, category_name) VALUES (?, ?) "
                   + "ON DUPLICATE KEY UPDATE deleted_at = NOW()";
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sqlMark)) {
        pst.setInt(1, userId);
        pst.setString(2, selected);
        pst.executeUpdate();
        JOptionPane.showMessageDialog(
            this,
            "Category \"" + selected + "\" removed from Add Product options.\n"
            + "Existing products in this category are untouched."
        );
        loadCategories();
        loadDashboardStats();
    } catch (SQLException e) {
        JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        logger.log(Level.SEVERE, "Error removing category: ", e);
    }
}
    private void applyProductTableRenderer() {
    java.util.Calendar calToday = java.util.Calendar.getInstance();
    calToday.set(java.util.Calendar.HOUR_OF_DAY, 0);
    calToday.set(java.util.Calendar.MINUTE, 0);
    calToday.set(java.util.Calendar.SECOND, 0);
    calToday.set(java.util.Calendar.MILLISECOND, 0);
 
    tblShowAddedProduct.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
        @Override
        public java.awt.Component getTableCellRendererComponent(
                javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
 
            java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
 
            int modelRow = table.convertRowIndexToModel(row);
            String dateVal = table.getModel().getValueAt(modelRow, 7) != null
                           ? table.getModel().getValueAt(modelRow, 7).toString() : "";
 
            // Only grey out if stock = 0 AND date is future (arrival date, not yet here)
            int stockVal = 0;
            Object stockObj = table.getModel().getValueAt(modelRow, 3);
            if (stockObj != null) {
                try { stockVal = Integer.parseInt(stockObj.toString()); } catch (NumberFormatException ignored) {}
            }
 
            boolean isFuture = false;
            if (stockVal == 0 && !dateVal.isEmpty() && !dateVal.equals("⚠️ Expired")) {
                try {
                    java.sql.Date expDate = java.sql.Date.valueOf(dateVal);
                    java.util.Calendar calExp = java.util.Calendar.getInstance();
                    calExp.setTime(expDate);
                    calExp.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    calExp.set(java.util.Calendar.MINUTE, 0);
                    calExp.set(java.util.Calendar.SECOND, 0);
                    calExp.set(java.util.Calendar.MILLISECOND, 0);
                    isFuture = calExp.after(calToday);
                } catch (IllegalArgumentException ignored) {}
            }
 
            if (isFuture) {
                c.setForeground(java.awt.Color.GRAY);
                c.setBackground(isSelected ? new java.awt.Color(200, 200, 200) : new java.awt.Color(240, 240, 240));
            } else {
                c.setForeground(java.awt.Color.BLACK);
                c.setBackground(isSelected ? table.getSelectionBackground() : java.awt.Color.WHITE);
            }
 
            return c;
        }
    });
}
    private boolean isSelectedRowFuture() {
    int selectedRow = tblShowAddedProduct.getSelectedRow();
    if (selectedRow == -1) return false;
    int modelRow = tblShowAddedProduct.convertRowIndexToModel(selectedRow);
 
    // Only block if stock = 0 (arrival date) AND date is future
    Object stockObj = tblShowAddedProduct.getModel().getValueAt(modelRow, 3);
    int stockVal = 0;
    if (stockObj != null) {
        try { stockVal = Integer.parseInt(stockObj.toString()); } catch (NumberFormatException ignored) {}
    }
    if (stockVal > 0) return false; // stock > 0 means expiration date, never block
 
    String dateVal = tblShowAddedProduct.getModel().getValueAt(modelRow, 7) != null
                   ? tblShowAddedProduct.getModel().getValueAt(modelRow, 7).toString() : "";
    if (dateVal.isEmpty() || dateVal.equals("⚠️ Expired")) return false;
    try {
        java.sql.Date expDate = java.sql.Date.valueOf(dateVal);
        java.util.Calendar calExp = java.util.Calendar.getInstance();
        calExp.setTime(expDate);
        calExp.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calExp.set(java.util.Calendar.MINUTE, 0);
        calExp.set(java.util.Calendar.SECOND, 0);
        calExp.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar calToday = java.util.Calendar.getInstance();
        calToday.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calToday.set(java.util.Calendar.MINUTE, 0);
        calToday.set(java.util.Calendar.SECOND, 0);
        calToday.set(java.util.Calendar.MILLISECOND, 0);
        return calExp.after(calToday);
    } catch (IllegalArgumentException ignored) {}
    return false;
}
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        btnDashboard = new javax.swing.JButton();
        btnProducts = new javax.swing.JButton();
        btnUser = new javax.swing.JButton();
        btnReport = new javax.swing.JButton();
        btnAccessPOS = new javax.swing.JButton();
        btnLogOut = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel4 = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        jPanel13 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        lblLowStock = new javax.swing.JLabel();
        jPanel21 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        lblAttention = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        jPanel20 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        lblCountProduct = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblSelectTableAction = new javax.swing.JTable();
        jPanel11 = new javax.swing.JPanel();
        jPanel19 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        lblOutOfStock = new javax.swing.JLabel();
        lblTitle = new javax.swing.JLabel();
        jLabel = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jPanel12 = new javax.swing.JPanel();
        jPanel14 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        lblTodaySales = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        cmbSelection = new javax.swing.JComboBox<>();
        jPanel5 = new javax.swing.JPanel();
        jPanel15 = new javax.swing.JPanel();
        txtSearch = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        cmbCategory = new javax.swing.JComboBox<>();
        jLabel3 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblShowAddedProduct = new javax.swing.JTable();
        jLabel5 = new javax.swing.JLabel();
        btnADD = new javax.swing.JButton();
        btnEDIT = new javax.swing.JButton();
        btnStockAction = new javax.swing.JButton();
        btnDelete = new javax.swing.JButton();
        btnDetails = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        jPanel16 = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        tblShowAssignUser = new javax.swing.JTable();
        jScrollPane5 = new javax.swing.JScrollPane();
        tblTimeLogUser = new javax.swing.JTable();
        jPanel27 = new javax.swing.JPanel();
        txtAddAssignUserFromAdmin = new javax.swing.JTextField();
        cmbAssignUserRole = new javax.swing.JComboBox<>();
        btnSaveUser = new javax.swing.JButton();
        jLabel16 = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        btnEditUser = new javax.swing.JButton();
        jLabel9 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        jPanel17 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        cmbSelectedReport = new javax.swing.JComboBox<>();
        jScrollPane6 = new javax.swing.JScrollPane();
        tblSelectReport = new javax.swing.JTable();
        jLabel11 = new javax.swing.JLabel();
        cmbSelectPreviousDate = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(255, 51, 0));
        jPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel2.setBackground(new java.awt.Color(28, 43, 58));

        jPanel3.setBackground(new java.awt.Color(38, 66, 95));
        jPanel3.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel18.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/Gemini_Generated_Image_45mrif45mrif45mr-removebg-preview.png"))); // NOI18N
        jPanel3.add(jLabel18, new org.netbeans.lib.awtextra.AbsoluteConstraints(-110, -100, 490, 310));

        btnDashboard.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnDashboard.setText("Dashboard");
        btnDashboard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDashboardActionPerformed(evt);
            }
        });
        jPanel3.add(btnDashboard, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 220, 201, 40));

        btnProducts.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnProducts.setText("Products");
        btnProducts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnProductsActionPerformed(evt);
            }
        });
        jPanel3.add(btnProducts, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 270, 201, 40));

        btnUser.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnUser.setText("User's");
        btnUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUserActionPerformed(evt);
            }
        });
        jPanel3.add(btnUser, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 320, 201, 40));

        btnReport.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnReport.setText("Reports");
        btnReport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnReportActionPerformed(evt);
            }
        });
        jPanel3.add(btnReport, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 370, 201, 40));

        btnAccessPOS.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnAccessPOS.setText("Access POS");
        btnAccessPOS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAccessPOSActionPerformed(evt);
            }
        });
        jPanel3.add(btnAccessPOS, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 420, 200, 40));

        btnLogOut.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnLogOut.setText("Log Out");
        btnLogOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLogOutActionPerformed(evt);
            }
        });
        jPanel3.add(btnLogOut, new org.netbeans.lib.awtextra.AbsoluteConstraints(90, 510, 201, 35));

        jPanel4.setBackground(new java.awt.Color(27, 47, 67));
        jPanel4.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel9.setBackground(new java.awt.Color(255, 153, 51));

        jPanel13.setBackground(new java.awt.Color(38, 66, 95));

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(255, 255, 255));
        jLabel8.setText("Low Stock");

        lblLowStock.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblLowStock.setForeground(new java.awt.Color(255, 255, 255));
        lblLowStock.setText("lblLowStock");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel8)
                .addGap(21, 21, 21)
                .addComponent(lblLowStock, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblLowStock, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.add(jPanel9, new org.netbeans.lib.awtextra.AbsoluteConstraints(250, 80, -1, -1));

        jPanel21.setBackground(new java.awt.Color(244, 164, 94));

        jLabel6.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel6.setText("ALERT NOTIFACATION:");

        lblAttention.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        lblAttention.setText("Appear what product is need lblAttention");

        javax.swing.GroupLayout jPanel21Layout = new javax.swing.GroupLayout(jPanel21);
        jPanel21.setLayout(jPanel21Layout);
        jPanel21Layout.setHorizontalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel6)
                .addGap(61, 61, 61)
                .addComponent(lblAttention)
                .addContainerGap(363, Short.MAX_VALUE))
        );
        jPanel21Layout.setVerticalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                .addComponent(jLabel6, javax.swing.GroupLayout.DEFAULT_SIZE, 45, Short.MAX_VALUE)
                .addComponent(lblAttention))
        );

        jPanel4.add(jPanel21, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 150, 880, -1));

        jPanel10.setBackground(new java.awt.Color(51, 51, 255));

        jPanel20.setBackground(new java.awt.Color(38, 66, 95));

        jLabel15.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel15.setForeground(new java.awt.Color(255, 255, 255));
        jLabel15.setText("Product Count");

        lblCountProduct.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblCountProduct.setForeground(new java.awt.Color(255, 255, 255));
        lblCountProduct.setText("lblCountP");

        javax.swing.GroupLayout jPanel20Layout = new javax.swing.GroupLayout(jPanel20);
        jPanel20.setLayout(jPanel20Layout);
        jPanel20Layout.setHorizontalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel15)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblCountProduct, javax.swing.GroupLayout.DEFAULT_SIZE, 78, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel20Layout.setVerticalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblCountProduct, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel20, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.add(jPanel10, new org.netbeans.lib.awtextra.AbsoluteConstraints(710, 80, -1, -1));

        tblSelectTableAction.setBorder(new javax.swing.border.MatteBorder(null));
        tblSelectTableAction.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblSelectTableAction.setFocusable(false);
        tblSelectTableAction.setGridColor(new java.awt.Color(204, 204, 204));
        tblSelectTableAction.setIntercellSpacing(new java.awt.Dimension(0, 0));
        tblSelectTableAction.setRowHeight(25);
        tblSelectTableAction.setShowVerticalLines(false);
        jScrollPane3.setViewportView(tblSelectTableAction);

        jPanel4.add(jScrollPane3, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 250, 880, 303));

        jPanel11.setBackground(new java.awt.Color(255, 51, 51));

        jPanel19.setBackground(new java.awt.Color(38, 66, 95));

        jLabel14.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(255, 255, 255));
        jLabel14.setText("OutOfStock");

        lblOutOfStock.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblOutOfStock.setForeground(new java.awt.Color(255, 255, 255));
        lblOutOfStock.setText("lblOutS");

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel14)
                .addGap(13, 13, 13)
                .addComponent(lblOutOfStock, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblOutOfStock, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel19, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel19, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.add(jPanel11, new org.netbeans.lib.awtextra.AbsoluteConstraints(480, 80, -1, -1));

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(255, 255, 255));
        lblTitle.setText("Appear whos user and title is lbTitle");
        jPanel4.add(lblTitle, new org.netbeans.lib.awtextra.AbsoluteConstraints(650, 30, 250, -1));

        jLabel.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel.setForeground(new java.awt.Color(255, 255, 255));
        jLabel.setText("CRAZY CRUNCH INVENTORY");
        jPanel4.add(jLabel, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 20, -1, -1));

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(255, 255, 255));
        jLabel4.setText("----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
        jPanel4.add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 60, 880, -1));

        jPanel12.setBackground(new java.awt.Color(20, 200, 130));

        jPanel14.setBackground(new java.awt.Color(38, 66, 95));

        jLabel7.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(255, 255, 255));
        jLabel7.setText("Today Sales");

        lblTodaySales.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTodaySales.setForeground(new java.awt.Color(255, 255, 255));
        lblTodaySales.setText("lblTodaySales");

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblTodaySales, javax.swing.GroupLayout.DEFAULT_SIZE, 88, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblTodaySales, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.add(jPanel12, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 80, -1, -1));

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Select Notification Dropdown:");
        jPanel4.add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 210, -1, 30));

        cmbSelection.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Recent Activity", "Stock Alert", "Stocks Date Good/Expired" }));
        jPanel4.add(cmbSelection, new org.netbeans.lib.awtextra.AbsoluteConstraints(250, 210, 255, -1));

        jTabbedPane1.addTab("DASHBOARD", jPanel4);

        jPanel15.setBackground(new java.awt.Color(27, 47, 67));

        txtSearch.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                txtSearchKeyReleased(evt);
            }
        });

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("Category");

        cmbCategory.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbCategory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbCategoryActionPerformed(evt);
            }
        });

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Search");

        tblShowAddedProduct.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Name", "Category", "Stock", "Price", "Availability", "Status", "Expiration Date"
            }
        ));
        tblShowAddedProduct.setGridColor(new java.awt.Color(204, 204, 204));
        tblShowAddedProduct.setIntercellSpacing(new java.awt.Dimension(0, 0));
        tblShowAddedProduct.setRowHeight(25);
        tblShowAddedProduct.setShowVerticalLines(false);
        jScrollPane1.setViewportView(tblShowAddedProduct);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(255, 255, 255));
        jLabel5.setText("Product Management");

        btnADD.setBackground(new java.awt.Color(102, 255, 102));
        btnADD.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnADD.setText("ADD");
        btnADD.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnADDActionPerformed(evt);
            }
        });

        btnEDIT.setBackground(new java.awt.Color(51, 102, 255));
        btnEDIT.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnEDIT.setText("EDIT");
        btnEDIT.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEDITActionPerformed(evt);
            }
        });

        btnStockAction.setBackground(new java.awt.Color(255, 153, 51));
        btnStockAction.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnStockAction.setText("STOCK ACTION");
        btnStockAction.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStockActionActionPerformed(evt);
            }
        });

        btnDelete.setBackground(new java.awt.Color(255, 51, 51));
        btnDelete.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnDelete.setText("DELETE");
        btnDelete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteActionPerformed(evt);
            }
        });

        btnDetails.setBackground(new java.awt.Color(255, 0, 255));
        btnDetails.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnDetails.setText("DETAILS");
        btnDetails.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDetailsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel15Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addGap(111, 111, 111)
                        .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 131, Short.MAX_VALUE)
                        .addComponent(cmbCategory, javax.swing.GroupLayout.PREFERRED_SIZE, 155, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2))
                    .addGroup(jPanel15Layout.createSequentialGroup()
                        .addComponent(btnADD, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnEDIT, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(btnStockAction, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(btnDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(btnDetails, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(30, 30, 30))
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 31, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(cmbCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 383, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btnADD, javax.swing.GroupLayout.DEFAULT_SIZE, 85, Short.MAX_VALUE)
                        .addComponent(btnEDIT, javax.swing.GroupLayout.DEFAULT_SIZE, 85, Short.MAX_VALUE))
                    .addComponent(btnDetails, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnDelete, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnStockAction, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel15, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("PRODUCT", jPanel5);

        jPanel16.setBackground(new java.awt.Color(27, 47, 67));

        tblShowAssignUser.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Name", "Role", "Assign By"
            }
        ));
        jScrollPane4.setViewportView(tblShowAssignUser);

        tblTimeLogUser.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Name", "Log In", "Log Out", "Time Log Module", "Role"
            }
        ));
        jScrollPane5.setViewportView(tblTimeLogUser);

        jPanel27.setBackground(new java.awt.Color(38, 66, 95));

        cmbAssignUserRole.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "cashier" }));

        btnSaveUser.setText("Save");
        btnSaveUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveUserActionPerformed(evt);
            }
        });

        jLabel16.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(255, 255, 255));
        jLabel16.setText("Select Role ");

        jLabel17.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(255, 255, 255));
        jLabel17.setText("AssignUser by Admin");

        btnEditUser.setText("Edit");
        btnEditUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEditUserActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel27Layout = new javax.swing.GroupLayout(jPanel27);
        jPanel27.setLayout(jPanel27Layout);
        jPanel27Layout.setHorizontalGroup(
            jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel27Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtAddAssignUserFromAdmin)
                    .addComponent(cmbAssignUserRole, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel27Layout.createSequentialGroup()
                        .addComponent(btnSaveUser, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 61, Short.MAX_VALUE)
                        .addComponent(btnEditUser, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel27Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel17)
                .addGap(76, 76, 76))
            .addGroup(jPanel27Layout.createSequentialGroup()
                .addGap(104, 104, 104)
                .addComponent(jLabel16)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel27Layout.setVerticalGroup(
            jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel27Layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addComponent(jLabel17)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtAddAssignUserFromAdmin, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(26, 26, 26)
                .addComponent(jLabel16)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cmbAssignUserRole, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnEditUser, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSaveUser, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(32, 32, 32))
        );

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(255, 255, 255));
        jLabel9.setText("Time Logs");

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addGap(84, 84, 84)
                .addComponent(jLabel9)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel16Layout.createSequentialGroup()
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 871, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(22, Short.MAX_VALUE))
                    .addGroup(jPanel16Layout.createSequentialGroup()
                        .addComponent(jPanel27, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 524, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(54, 54, 54))))
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 294, Short.MAX_VALUE)
                    .addComponent(jPanel27, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 43, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("USER'S", jPanel6);

        jPanel17.setBackground(new java.awt.Color(27, 47, 67));

        jLabel10.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText("SELECT REPORT'S WANT TO SEE:");

        cmbSelectedReport.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        cmbSelectedReport.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Inventory History Product", "All Product List Sales Today", "All Transcation List Today", "Total Sales List Today", "Total Monthly Sales" }));

        tblSelectReport.setBorder(new javax.swing.border.MatteBorder(null));
        tblSelectReport.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        tblSelectReport.setFocusable(false);
        tblSelectReport.setGridColor(new java.awt.Color(204, 204, 204));
        tblSelectReport.setIntercellSpacing(new java.awt.Dimension(0, 0));
        tblSelectReport.setRowHeight(25);
        tblSelectReport.setShowVerticalLines(false);
        jScrollPane6.setViewportView(tblSelectReport);

        jLabel11.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(255, 255, 255));
        jLabel11.setText("SELECT Previous Date:");

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addGap(36, 36, 36)
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 850, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel17Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addGap(18, 18, 18)
                        .addComponent(cmbSelectedReport, javax.swing.GroupLayout.PREFERRED_SIZE, 255, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel11)
                        .addGap(18, 18, 18)
                        .addComponent(cmbSelectPreviousDate, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(32, Short.MAX_VALUE))
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmbSelectedReport, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11)
                    .addComponent(cmbSelectPreviousDate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 441, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(38, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("REPORTS", jPanel7);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 392, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 923, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(jTabbedPane1)
        );

        jPanel1.add(jPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 10, 1340, 590));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, 608, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnDashboardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDashboardActionPerformed
        jTabbedPane1.setSelectedIndex(0);
    }//GEN-LAST:event_btnDashboardActionPerformed

    private void btnProductsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnProductsActionPerformed
        jTabbedPane1.setSelectedIndex(1);
    }//GEN-LAST:event_btnProductsActionPerformed

    private void btnUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUserActionPerformed
        jTabbedPane1.setSelectedIndex(2);
    }//GEN-LAST:event_btnUserActionPerformed

    private void btnReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnReportActionPerformed
        jTabbedPane1.setSelectedIndex(3);
    }//GEN-LAST:event_btnReportActionPerformed

    private void btnLogOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogOutActionPerformed
         int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to log out?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
 
        if (confirm == JOptionPane.YES_OPTION) {
            // Record logout
            try (Connection conn = MySqlConnector.getConnection(); PreparedStatement pst = conn.prepareStatement(
                    "INSERT INTO time_log (user_id, username, action, module, log_time) VALUES (?, ?, 'Logout', 'Inventory', NOW())")) {
                pst.setInt(1, userId);
                pst.setString(2, userName);
                pst.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error recording logout: ", e);
            }
 
            new LogInUser().setVisible(true);
            this.dispose();
        }
    }//GEN-LAST:event_btnLogOutActionPerformed

    private void btnEditUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditUserActionPerformed
        int selectedRow = tblShowAssignUser.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a user.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        String username = tblShowAssignUser.getValueAt(selectedRow, 0).toString();
        String currentStatus = tblShowAssignUser.getValueAt(selectedRow, 3).toString();
        boolean isActive = currentStatus.equals("Active");
 
        String[] options = {isActive ? "Deactivate User" : "Reactivate User", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "User: " + username + "\nCurrent Status: " + currentStatus,
                "Manage User",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
 
        if (choice == 0) {
            int newStatus = isActive ? 0 : 1;
            String action = isActive ? "Deactivate" : "Reactivate";
            int confirm = JOptionPane.showConfirmDialog(this,
                    action + " user: " + username + "?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
 
            if (confirm == JOptionPane.YES_OPTION) {
                try (Connection conn = MySqlConnector.getConnection();
                     PreparedStatement pst = conn.prepareStatement(
                             "UPDATE users SET is_active = ? WHERE username = ? AND parent_id = ?")) {
                    pst.setInt(1, newStatus);
                    pst.setString(2, username);
                    pst.setInt(3, userId);
                    pst.executeUpdate();
                    JOptionPane.showMessageDialog(this, "User " + action.toLowerCase() + "d successfully.");
                    loadAssignUsers();
                } catch (SQLException e) {
                    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
                }
            }
        }
    }//GEN-LAST:event_btnEditUserActionPerformed

    private void btnSaveUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveUserActionPerformed
        String username = txtAddAssignUserFromAdmin.getText().trim();
 
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a username.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        if (username.length() < 3) {
            JOptionPane.showMessageDialog(this, "Username must be at least 3 characters.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            JOptionPane.showMessageDialog(this, "Username can only contain letters, numbers, and underscores.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        String role = cmbAssignUserRole.getSelectedItem() != null ? cmbAssignUserRole.getSelectedItem().toString() : "";
        if (role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a role.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        String generatedPassword = generateSecurePassword(8);
 
        String sql = "INSERT INTO users (full_name, username, password, role, parent_id) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = MySqlConnector.getConnection(); PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, username);   // full_name — no separate field in UI, use username
            pst.setString(2, username);
            pst.setString(3, PasswordUtil.hashPassword(generatedPassword));
            pst.setString(4, role);
            pst.setInt(5, userId);
            pst.executeUpdate();
 
            JOptionPane.showMessageDialog(this,
                    "User Created Successfully!\n\n"
                    + "Username: " + username + "\n"
                    + "Password: " + generatedPassword + "\n\n"
                    + "Share this password with the user.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
 
            txtAddAssignUserFromAdmin.setText("");
            loadAssignUsers();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                JOptionPane.showMessageDialog(this, "Username already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_btnSaveUserActionPerformed

    private void btnDetailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDetailsActionPerformed
        int selectedRow = tblShowAddedProduct.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product.");
            return;
        }

        int viewRow = tblShowAddedProduct.convertRowIndexToModel(selectedRow);
        DefaultTableModel model = (DefaultTableModel) tblShowAddedProduct.getModel();
        int prodId = Integer.parseInt(model.getValueAt(viewRow, 0).toString());

        // ── Fetch full product details from DB ───────────────────────────────
        String sql = "SELECT name, category, stock, price, availability, low_stock_alert, expiration_date "
                   + "FROM products WHERE prod_id = ? AND user_id = ?";

        String name = "", category = "", availability = "", status = "";
        double price = 0;
        int stock = 0, lowStockAlert = 0;
        String expirationDate = "Not Set";

        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {

            pst.setInt(1, prodId);
            pst.setInt(2, userId);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    name          = rs.getString("name");
                    category      = rs.getString("category");
                    stock         = rs.getInt("stock");
                    price         = rs.getDouble("price");
                    availability  = rs.getString("availability");
                    lowStockAlert = rs.getInt("low_stock_alert");
                    // Compute status dynamically — no status column in DB
                    status        = calculateStatus(stock, lowStockAlert);

                    java.sql.Date expDate = rs.getDate("expiration_date");
                    if (expDate != null) {
                        // Check if expired
                        java.util.Calendar calToday = java.util.Calendar.getInstance();
                        calToday.set(java.util.Calendar.HOUR_OF_DAY, 0);
                        calToday.set(java.util.Calendar.MINUTE, 0);
                        calToday.set(java.util.Calendar.SECOND, 0);
                        calToday.set(java.util.Calendar.MILLISECOND, 0);

                        java.util.Calendar calExp = java.util.Calendar.getInstance();
                        calExp.setTime(expDate);
                        calExp.set(java.util.Calendar.HOUR_OF_DAY, 0);
                        calExp.set(java.util.Calendar.MINUTE, 0);
                        calExp.set(java.util.Calendar.SECOND, 0);
                        calExp.set(java.util.Calendar.MILLISECOND, 0);

                        if (calExp.before(calToday)) {
                            expirationDate = expDate.toString() + "  ⚠️ EXPIRED";
                        } else {
                            long diffMs   = calExp.getTimeInMillis() - calToday.getTimeInMillis();
                            int  daysLeft = (int)(diffMs / (1000 * 60 * 60 * 24));
                            if (daysLeft == 0) {
                                expirationDate = expDate.toString() + "  (Expires Today!)";
                            } else if (daysLeft <= 7) {
                                expirationDate = expDate.toString() + "  (" + daysLeft + " days left ⚠️)";
                            } else {
                                expirationDate = expDate.toString() + "  (" + daysLeft + " days left)";
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading product details: ", e);
            JOptionPane.showMessageDialog(this, "Error loading product details: " + e.getMessage());
            return;
        }

        // ── Build custom styled dialog ───────────────────────────────────────
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Product Details", true);
        dialog.setSize(420, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(false);
        dialog.setLayout(new java.awt.BorderLayout());

        // Header panel
        javax.swing.JPanel headerPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        headerPanel.setBackground(new java.awt.Color(15, 25, 35));
        headerPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));

        javax.swing.JLabel titleLabel = new javax.swing.JLabel("▣  Product Details");
        titleLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 18));
        titleLabel.setForeground(new java.awt.Color(20, 200, 130));
        headerPanel.add(titleLabel, java.awt.BorderLayout.WEST);

        javax.swing.JLabel idLabel = new javax.swing.JLabel("ID #" + prodId);
        idLabel.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        idLabel.setForeground(new java.awt.Color(160, 160, 160));
        headerPanel.add(idLabel, java.awt.BorderLayout.EAST);

        // Body panel
        javax.swing.JPanel bodyPanel = new javax.swing.JPanel();
        bodyPanel.setBackground(new java.awt.Color(24, 36, 48));
        bodyPanel.setLayout(new java.awt.GridBagLayout());
        bodyPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 24, 16, 24));

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.insets  = new java.awt.Insets(6, 4, 6, 4);
        gbc.anchor  = java.awt.GridBagConstraints.WEST;
        gbc.fill    = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Determine status color
        java.awt.Color statusColor;
        switch (status) {
            case "Full Stock":  statusColor = new java.awt.Color(20, 200, 130); break;
            case "Low Stock":   statusColor = new java.awt.Color(255, 193, 7);  break;
            default:            statusColor = new java.awt.Color(220, 53, 69);  break; // Out of Stock
        }

        // Availability color
        java.awt.Color availColor = "Available".equals(availability)
            ? new java.awt.Color(20, 200, 130)
            : new java.awt.Color(220, 53, 69);

        // Helper to add a label row
        // col 0 = field label, col 1 = value
        Object[][] rows = {
            { "Product Name",      name },
            { "Category",          category },
            { "Price",             String.format("₱ %.2f", price) },
            { "Stock",             String.valueOf(stock) },
            { "Low Stock Alert",   "≤ " + lowStockAlert + " units" },
            { "Status",            status },
            { "Availability",      availability },
            { "Expiration Date",   expirationDate }
        };

        for (int i = 0; i < rows.length; i++) {
            String fieldName  = (String) rows[i][0];
            String fieldValue = (String) rows[i][1];

            // Field label (left)
            javax.swing.JLabel lbl = new javax.swing.JLabel(fieldName);
            lbl.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            lbl.setForeground(new java.awt.Color(160, 160, 160));
            lbl.setPreferredSize(new java.awt.Dimension(130, 24));

            gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0;
            bodyPanel.add(lbl, gbc);

            // Value label (right)
            javax.swing.JLabel val = new javax.swing.JLabel(fieldValue);
            val.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));

            // Special coloring for status/availability/expiration
            if (fieldName.equals("Status")) {
                val.setForeground(statusColor);
                val.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            } else if (fieldName.equals("Availability")) {
                val.setForeground(availColor);
                val.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
            } else if (fieldName.equals("Expiration Date") && fieldValue.contains("⚠️")) {
                val.setForeground(new java.awt.Color(255, 193, 7));
            } else if (fieldName.equals("Expiration Date") && fieldValue.contains("EXPIRED")) {
                val.setForeground(new java.awt.Color(220, 53, 69));
            } else {
                val.setForeground(java.awt.Color.WHITE);
            }

            gbc.gridx = 1; gbc.gridy = i; gbc.weightx = 1;
            bodyPanel.add(val, gbc);

            // Divider line between rows
            if (i < rows.length - 1) {
                javax.swing.JSeparator sep = new javax.swing.JSeparator();
                sep.setForeground(new java.awt.Color(40, 60, 75));
                sep.setBackground(new java.awt.Color(40, 60, 75));
                gbc.gridx = 0; gbc.gridy = rows.length + i;
                gbc.gridwidth = 2; gbc.weightx = 1;
                bodyPanel.add(sep, gbc);
                gbc.gridwidth = 1;
            }
        }

        // Footer / Close button
        javax.swing.JPanel footerPanel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 16, 10));
        footerPanel.setBackground(new java.awt.Color(15, 25, 35));

        javax.swing.JButton closeBtn = new javax.swing.JButton("Close");
        closeBtn.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        closeBtn.setBackground(new java.awt.Color(20, 200, 130));
        closeBtn.setForeground(java.awt.Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new java.awt.Dimension(90, 32));
        closeBtn.addActionListener(e -> dialog.dispose());

        footerPanel.add(closeBtn);

        dialog.add(headerPanel, java.awt.BorderLayout.NORTH);
        dialog.add(new javax.swing.JScrollPane(bodyPanel), java.awt.BorderLayout.CENTER);
        dialog.add(footerPanel, java.awt.BorderLayout.SOUTH);
        dialog.setVisible(true);
    }//GEN-LAST:event_btnDetailsActionPerformed

    private void btnDeleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteActionPerformed
        int selectedRow = tblShowAddedProduct.getSelectedRow();
 
    if (selectedRow != -1 && isSelectedRowFuture()) {
        JOptionPane.showMessageDialog(this, "This product is not yet available. It will unlock on its set date.", "Not Yet Available", JOptionPane.WARNING_MESSAGE);
        return;
    }
 
        if (selectedRow == -1) {
            // ── No row selected: show delete-by options ──────────────────────
            String[] options = {"Delete by Category", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                this,
                "No product selected.\nWhat would you like to delete?",
                "Delete Options",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
            );
 
            if (choice == 0) {
                deleteByCategory();
            }
            return;
        }
 
        // ── Row IS selected: delete that single product ───────────────────────
        int modelRow = tblShowAddedProduct.convertRowIndexToModel(selectedRow);
        int prodId = Integer.parseInt(
            tblShowAddedProduct.getModel().getValueAt(modelRow, 0).toString()
        );
        String prodName = tblShowAddedProduct.getModel().getValueAt(modelRow, 1).toString();
 
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Delete product: \"" + prodName + "\"?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            String sql = "DELETE FROM products WHERE prod_id = ? AND user_id = ?";
            try (Connection conn = MySqlConnector.getConnection();
                 PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setInt(1, prodId);
                pst.setInt(2, userId);
                if (pst.executeUpdate() > 0) {
                    // Log the deletion so Recent Activity reflects it
                    String logSql = "INSERT INTO activity_log (user_id, product_name, quantity, action_type, reason, log_time) "
                                  + "VALUES (?, ?, 0, 'Delete', 'Product permanently removed', NOW())";
                    try (Connection logConn = MySqlConnector.getConnection();
                         PreparedStatement logPst = logConn.prepareStatement(logSql)) {
                        logPst.setInt(1, userId);
                        logPst.setString(2, prodName);
                        logPst.executeUpdate();
                    }
                    JOptionPane.showMessageDialog(this, "Product deleted successfully!");
                    loadProductsTable();
                    loadCategories();
                    loadDashboardStats();
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
                logger.log(Level.SEVERE, "Error deleting product: ", e);
            }
        }
    }//GEN-LAST:event_btnDeleteActionPerformed

    private void btnStockActionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStockActionActionPerformed
        int selectedRow = tblShowAddedProduct.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product.");
            return;
        }
        if (isSelectedRowFuture()) {
            JOptionPane.showMessageDialog(this, "This product is not yet available. It will unlock on its set date.", "Not Yet Available", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tblShowAddedProduct.convertRowIndexToModel(selectedRow);
        int prodId = Integer.parseInt(tblShowAddedProduct.getModel().getValueAt(modelRow, 0).toString());
        new StockAction(userId, prodId, this, false).setVisible(true);
    }//GEN-LAST:event_btnStockActionActionPerformed

    private void btnEDITActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEDITActionPerformed
        int selectedRow = tblShowAddedProduct.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product.");
            return;
        }
        if (isSelectedRowFuture()) {
            JOptionPane.showMessageDialog(this, "This product is not yet available. It will unlock on its set date.", "Not Yet Available", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = tblShowAddedProduct.convertRowIndexToModel(selectedRow);
        int prodId = Integer.parseInt(tblShowAddedProduct.getModel().getValueAt(modelRow, 0).toString());
        new EditFoodSystem(userId, prodId, this).setVisible(true);
    }//GEN-LAST:event_btnEDITActionPerformed

    private void btnADDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnADDActionPerformed
        new AddFoodSystem(userId, this).setVisible(true);
    }//GEN-LAST:event_btnADDActionPerformed

    private void cmbCategoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbCategoryActionPerformed
        applyFilters();
    }//GEN-LAST:event_cmbCategoryActionPerformed

    private void txtSearchKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSearchKeyReleased
        applyFilters();  // was: creating new sorter here
    }//GEN-LAST:event_txtSearchKeyReleased

    private void btnAccessPOSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAccessPOSActionPerformed
        System.out.println("Opening POS from Inventory Dashboard...");
        new DashboardPOS(userId, userName).setVisible(true);
        this.dispose();
    }//GEN-LAST:event_btnAccessPOSActionPerformed

    public static void main(String args[]) {

    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnADD;
    private javax.swing.JButton btnAccessPOS;
    private javax.swing.JButton btnDashboard;
    private javax.swing.JButton btnDelete;
    private javax.swing.JButton btnDetails;
    private javax.swing.JButton btnEDIT;
    private javax.swing.JButton btnEditUser;
    private javax.swing.JButton btnLogOut;
    private javax.swing.JButton btnProducts;
    private javax.swing.JButton btnReport;
    private javax.swing.JButton btnSaveUser;
    private javax.swing.JButton btnStockAction;
    private javax.swing.JButton btnUser;
    private javax.swing.JComboBox<String> cmbAssignUserRole;
    private javax.swing.JComboBox<String> cmbCategory;
    private javax.swing.JComboBox<String> cmbSelectPreviousDate;
    private javax.swing.JComboBox<String> cmbSelectedReport;
    private javax.swing.JComboBox<String> cmbSelection;
    private javax.swing.JLabel jLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel21;
    private javax.swing.JPanel jPanel27;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel lblAttention;
    private javax.swing.JLabel lblCountProduct;
    private javax.swing.JLabel lblLowStock;
    private javax.swing.JLabel lblOutOfStock;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblTodaySales;
    private javax.swing.JTable tblSelectReport;
    private javax.swing.JTable tblSelectTableAction;
    private javax.swing.JTable tblShowAddedProduct;
    private javax.swing.JTable tblShowAssignUser;
    private javax.swing.JTable tblTimeLogUser;
    private javax.swing.JTextField txtAddAssignUserFromAdmin;
    private javax.swing.JTextField txtSearch;
    // End of variables declaration//GEN-END:variables
}
