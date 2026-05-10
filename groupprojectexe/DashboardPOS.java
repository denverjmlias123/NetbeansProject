package groupprojectexe;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
 
public class DashboardPOS extends javax.swing.JFrame {  
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(DashboardPOS.class.getName());
 
    private static final double TAX_RATE = 0.12;
    private static final int RECEIPT_WIDTH = 40;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm");
 
    private int userId;
    private String userName;
    private int adminId;
    private String currentCashInput = "";
    private final Map<Integer, Object[]> cart = new HashMap<>();
    private String userRole;
    
    // ENHANCED: Now supports multiple held transactions
    private final List<HeldTransaction> heldTransactions = new ArrayList<>();
    private int holdCounter = 1;
    
    // Inner class to store held transaction data
    private static class HeldTransaction {
        String name;
        Map<Integer, Object[]> cart;
        String cashInput;
        String time;
        
        HeldTransaction(String name, Map<Integer, Object[]> cart, String cashInput) {
            this.name = name;
            this.cart = new HashMap<>(cart); // Copy the cart
            this.cashInput = cashInput; // FIX: Store cashInput BEFORE clearing cart
            this.time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        }
        
        double getTotal() {
            double total = 0;
            for (Object[] item : cart.values()) {
                total += (double) item[3];
            }
            return total + (total * 0.12);
        }
    }
    
    public DashboardPOS(int userId, String userName) {
        initComponents();
        txtDisplayOrder.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        
        this.userId = userId;
        this.userName = userName;
 
        setLocationRelativeTo(null);
        
        // Fetch admin ID and Role
        determineAdminId();
        
        if (lblTitle != null) {
            lblTitle.setText("User: " + userName + " | Role: " + (userRole != null ? userRole : "Unknown"));
        }
        
        setupAccessButton();
        
        // FIX: Add WindowListener to handle cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.out.println("✓ POS Dashboard closing. Held transactions: " + heldTransactions.size());
            }
        });
        
        loadCategories();
        loadProducts();
        setupListeners();
        setupReceiptClickListener();   // Feature: click receipt to remove items
        updateReceiptDisplay();
        
        // FIX: Show initialization message
        showInitializationMessage();
    }
    // NEW: Initialization message
    private void showInitializationMessage() {
        String initMsg = 
            "═══════════════════════════════════\n" +
            "       POS DASHBOARD INITIALIZED     \n" +
            "═══════════════════════════════════\n\n" +
            "✓ User: " + userName + "\n" +
            "✓ Role: " + (userRole != null ? userRole : "Unknown") + "\n" +
            "✓ Tax Rate: 12%\n" +
            "✓ Payment Methods: Cash, GCash, Credit\n" +
            "✓ Held Transactions: Ready\n\n" +
            "System ready for POS operations.\n" +
            "═══════════════════════════════════";
        System.out.println(initMsg);
    }
    private void setupAccessButton() {
        if (btnAccessInventory != null) {
            boolean isAdmin = "Admin".equalsIgnoreCase(userRole);
            btnAccessInventory.setVisible(isAdmin);
            
            for (java.awt.event.ActionListener al : btnAccessInventory.getActionListeners()) {
                btnAccessInventory.removeActionListener(al);
            }
            
            if (isAdmin) {
                btnAccessInventory.addActionListener(e -> {
                    new DashboardInventory(adminId, userName).setVisible(true);
                    this.dispose();
                });
            }
        }
    }
    private void determineAdminId() {
        String sql = "SELECT parent_id, role FROM users WHERE id = ?";
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, userId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int parentId = rs.getInt("parent_id");
                    this.adminId = (parentId > 0) ? parentId : userId;
                    this.userRole = rs.getString("role");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error determining admin ID", e);
            this.adminId = userId;
            this.userRole = "Unknown";
        }
    }
    private void loadCategories() {
        cmbSelectCategory.removeAllItems();
        cmbSelectCategory.addItem("All");
        String sql = "SELECT DISTINCT category FROM products WHERE user_id = ? AND availability = 'Available' AND category IS NOT NULL";
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, adminId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    cmbSelectCategory.addItem(rs.getString("category"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading categories", e);
        }
    }
    private void loadProducts() {
        DefaultTableModel model = (DefaultTableModel) tblShowSellProduct.getModel();
        model.setRowCount(0);
        // Column 3 = current stock count (was misleadingly named "Quantity" before)
        String sql = "SELECT prod_id, name, category, stock, price, low_stock_alert FROM products "
                   + "WHERE user_id = ? AND availability = 'Available' "
                   + "AND (expiration_date IS NULL OR expiration_date >= CURDATE()) "
                   + "ORDER BY (stock = 0) ASC, name ASC";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setInt(1, adminId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    int stock = rs.getInt("stock");
                    int lowAlert = rs.getInt("low_stock_alert");
                    String status = (stock == 0) ? "Out of Stock" : (stock <= lowAlert ? "Low Stock" : "Full Stock");
                    model.addRow(new Object[]{
                        rs.getInt("prod_id"), rs.getString("name"), rs.getString("category"),
                        stock, rs.getDouble("price"), status
                    });
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading products", e);
        }
    }
    private void setupListeners() {
        txtSearchProduct.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                filterTable();
            }
        });
 
        cmbSelectCategory.addActionListener(e -> {
            String selected = cmbSelectCategory.getSelectedItem().toString();
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) tblShowSellProduct.getModel());
            tblShowSellProduct.setRowSorter(sorter);
            sorter.setRowFilter("All".equals(selected) ? null : RowFilter.regexFilter("^" + selected + "$", 2));
        });
 
        tblShowSellProduct.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = tblShowSellProduct.getSelectedRow();
                if (row == -1) return;
 
                int modelRow = tblShowSellProduct.convertRowIndexToModel(row);
                String status = tblShowSellProduct.getModel().getValueAt(modelRow, 5).toString();
 
                if ("Out of Stock".equalsIgnoreCase(status)) {
                    JOptionPane.showMessageDialog(DashboardPOS.this,
                        "This product is Out of Stock.\nPlease contact inventory to restock.",
                        "Product Unavailable",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
 
                try {
                    int    prodId = Integer.parseInt(tblShowSellProduct.getModel().getValueAt(modelRow, 0).toString());
                    String name   = tblShowSellProduct.getModel().getValueAt(modelRow, 1).toString();
                    int    stock  = Integer.parseInt(tblShowSellProduct.getModel().getValueAt(modelRow, 3).toString());
                    double price  = Double.parseDouble(tblShowSellProduct.getModel().getValueAt(modelRow, 4).toString());
 
                    // Already in cart? show current qty as default
                    int defaultQty = 1;
                    if (cart.containsKey(prodId)) {
                        defaultQty = (int) cart.get(prodId)[2];
                    }
 
                    // Ask quantity via input dialog
                    String input = JOptionPane.showInputDialog(
                        DashboardPOS.this,
                        String.format("Product: %s\nPrice: ₱%.2f  |  Stock: %d\n\nEnter quantity to add:", name, price, stock),
                        String.valueOf(defaultQty)
                    );
 
                    if (input == null || input.trim().isEmpty()) return; // cancelled
 
                    int qty;
                    try {
                        qty = Integer.parseInt(input.trim());
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(DashboardPOS.this,
                            "Please enter a valid whole number.", "Invalid Quantity", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
 
                    if (qty <= 0) {
                        JOptionPane.showMessageDialog(DashboardPOS.this,
                            "Quantity must be greater than 0.", "Invalid Quantity", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
 
                    // If already in cart, replace qty instead of adding on top
                    if (cart.containsKey(prodId)) {
                        if (qty > stock) {
                            JOptionPane.showMessageDialog(DashboardPOS.this,
                                "Not enough stock! Available: " + stock, "Stock Error", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        Object[] item = cart.get(prodId);
                        item[2] = qty;
                        item[3] = qty * price;
                        updateReceiptDisplay();
                    } else {
                        addToCart(prodId, name, price, stock, qty);
                    }
 
                } catch (NumberFormatException ex) {
                    logger.log(Level.SEVERE, "Error parsing product data: ", ex);
                    JOptionPane.showMessageDialog(DashboardPOS.this, "Error reading product data.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
    private void addToCart(int prodId, String name, double price, int stock, int qty) {
        if (cart.containsKey(prodId)) {
            Object[] item = cart.get(prodId);
            int newQty = (int) item[2] + qty;
            if (newQty > stock) {
                JOptionPane.showMessageDialog(this, "Not enough stock! Available: " + stock, "Stock Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            item[2] = newQty;
            item[3] = newQty * price;
        } else {
            if (qty > stock) {
                JOptionPane.showMessageDialog(this, "Not enough stock!", "Stock Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            cart.put(prodId, new Object[]{name, price, qty, qty * price});
        }
        updateReceiptDisplay();
    }
    private void updateReceiptDisplay() {
        StringBuilder sb = new StringBuilder();
        int width = RECEIPT_WIDTH;
        
        sb.append(repeatChar('=', width)).append("\n");
        sb.append(center("CRAZY CRUNCH", width)).append("\n");
        sb.append(center("INVENTORY & POS SYSTEM", width)).append("\n");
        sb.append(repeatChar('=', width)).append("\n");
        sb.append(String.format("Date: %s\n", DATE_FORMAT.format(new Date())));
        sb.append(String.format("Cashier: %s\n", userName));
        
        if (!heldTransactions.isEmpty()) {
            sb.append(String.format("Held Transactions: %d\n", heldTransactions.size()));
        }
        
        sb.append(repeatChar('-', width)).append("\n\n");
        
        sb.append(String.format("%-15s %3s %10s\n", "ITEM", "QTY", "AMOUNT"));
        sb.append(repeatChar('-', width)).append("\n");
        
        double subTotal = 0;
        if (cart.isEmpty()) {
            sb.append(center("[ NO ITEMS IN CART ]", width)).append("\n");
        } else {
            for (Object[] item : cart.values()) {
                String name = (String) item[0];
                int qty = (int) item[2];
                double total = (double) item[3];
                subTotal += total;
                String displayName = name.length() > 14 ? name.substring(0, 14) : name;
                sb.append(String.format("%-15s %3d %10.2f\n", displayName, qty, total));
            }
        }
        
        sb.append("\n").append(repeatChar('-', width)).append("\n");
        
        double tax = subTotal * TAX_RATE;
        double grandTotal = subTotal + tax;
        
        sb.append(String.format("%22s %10.2f\n", "Subtotal:", subTotal));
        sb.append(String.format("%22s %10.2f\n", "Tax (12%):", tax));
        sb.append(repeatChar('=', width)).append("\n");
        sb.append(String.format("%22s %10.2f\n", "TOTAL:", grandTotal));
        sb.append(repeatChar('=', width)).append("\n\n");
 
        if (!currentCashInput.isEmpty()) {
            try {
                double cash = Double.parseDouble(currentCashInput);
                sb.append(String.format("%22s %10.2f\n", "Cash Tendered:", cash));
                if (cash >= grandTotal) {
                    sb.append(String.format("%22s %10.2f\n", "CHANGE:", cash - grandTotal));
                } else {
                    sb.append(String.format("%22s %10.2f\n", "Balance Due:", grandTotal - cash));
                }
            } catch (NumberFormatException e) {
                sb.append(center("[ INVALID INPUT ]", width)).append("\n");
            }
        }
        
        if (!cart.isEmpty()) {
            sb.append("\n").append(center("Thank you for shopping!", width)).append("\n");
        }
        
        txtDisplayOrder.setText(sb.toString());
    } 
    private String repeatChar(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(c);
        return sb.toString();
    }  
    private String center(String text, int width) {
        if (text == null || text.length() >= width) return text;
        int leftPad = (width - text.length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leftPad; i++) sb.append(" ");
        sb.append(text);
        return sb.toString();
    }
    private void clearCart() {
        cart.clear();
        currentCashInput = "";
        updateReceiptDisplay();
    }
    // Feature: Click on the receipt display to remove an item from the cart
    private void setupReceiptClickListener() {
        txtDisplayOrder.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (cart.isEmpty()) return;
 
                // Build ordered list of cart entries for the popup
                List<String> itemLabels = new ArrayList<>();
                List<Integer> prodIds   = new ArrayList<>();
                for (Map.Entry<Integer, Object[]> entry : cart.entrySet()) {
                    Object[] item = entry.getValue();
                    String name   = (String) item[0];
                    int    qty    = (int)    item[2];
                    double total  = (double) item[3];
                    itemLabels.add(String.format("%s  x%d  (₱%.2f)", name, qty, total));
                    prodIds.add(entry.getKey());
                }
 
                // Show selection dialog — user picks which item to remove
                String[] labelArr = itemLabels.toArray(new String[0]);
                String chosen = (String) JOptionPane.showInputDialog(
                    DashboardPOS.this,
                    "Select item to remove from cart:",
                    "Remove Item",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    labelArr,
                    labelArr[0]
                );
 
                if (chosen == null) return; // cancelled
 
                int idx = itemLabels.indexOf(chosen);
                if (idx < 0) return;
 
                int prodId = prodIds.get(idx);
                Object[] item = cart.get(prodId);
                int currentQty = (int) item[2];
 
                if (currentQty > 1) {
                    // Reduce by 1
                    item[2] = currentQty - 1;
                    item[3] = (currentQty - 1) * (double) item[1];
                    JOptionPane.showMessageDialog(DashboardPOS.this,
                        "Reduced quantity of \"" + item[0] + "\" to " + (currentQty - 1) + ".",
                        "Item Updated", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    // Remove entirely
                    cart.remove(prodId);
                    JOptionPane.showMessageDialog(DashboardPOS.this,
                        "\"" + item[0] + "\" removed from cart.",
                        "Item Removed", JOptionPane.INFORMATION_MESSAGE);
                }
                updateReceiptDisplay();
            }
        });
    }
    private void appendCash(String num) {
        currentCashInput += num;
        updateReceiptDisplay();
    }
    private void filterTable() {
        String text = txtSearchProduct.getText();
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) tblShowSellProduct.getModel());
        tblShowSellProduct.setRowSorter(sorter);
        sorter.setRowFilter(text.trim().isEmpty() ? null : RowFilter.regexFilter("(?i)" + text, 1));
    }
    private void showHeldTransactionsDialog() {
        if (heldTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No held transactions.", "Hold", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String[] options = new String[heldTransactions.size()];
        for (int i = 0; i < heldTransactions.size(); i++) {
            HeldTransaction ht = heldTransactions.get(i);
            options[i] = String.format("%s - P%.2f (%s)", ht.name, ht.getTotal(), ht.time);
        }
        
        String selected = (String) JOptionPane.showInputDialog(this, 
            "Select transaction to restore:", 
            "Held Transactions (" + heldTransactions.size() + ")", 
            JOptionPane.QUESTION_MESSAGE, 
            null, 
            options, 
            options[0]);
        
        if (selected != null) {
            for (int i = 0; i < heldTransactions.size(); i++) {
                if (selected.startsWith(heldTransactions.get(i).name)) {
                    HeldTransaction ht = heldTransactions.remove(i);
                    // FIX: Restore cart AND cashInput before clearing
                    cart.putAll(ht.cart);
                    currentCashInput = ht.cashInput;
                    updateReceiptDisplay();
                    JOptionPane.showMessageDialog(this, "Restored: " + ht.name, "Hold Retrieved", JOptionPane.INFORMATION_MESSAGE);
                    break;
                }
            }
        }
    }
    private boolean processTransaction(String paymentMethod, double cashReceived, double grandTotal) {
        Connection conn = null;
        try {
            conn = MySqlConnector.getConnection();
            conn.setAutoCommit(false);
 
            // Insert into transactions (payment_method, cash_tendered, change_given added via SQL ALTER)
            String transSql = "INSERT INTO transactions (user_id, total_amount, payment_method, cash_tendered, change_given, transaction_date) "
                            + "VALUES (?, ?, ?, ?, ?, NOW())";
            int transId;
            try (PreparedStatement pstTrans = conn.prepareStatement(transSql, Statement.RETURN_GENERATED_KEYS)) {
                pstTrans.setInt(1, userId);
                pstTrans.setDouble(2, grandTotal);
                pstTrans.setString(3, paymentMethod);
                pstTrans.setDouble(4, cashReceived);
                pstTrans.setDouble(5, Math.max(0, cashReceived - grandTotal));
                pstTrans.executeUpdate();
 
                try (ResultSet rs = pstTrans.getGeneratedKeys()) {
                    if (rs.next()) transId = rs.getInt(1);
                    else throw new SQLException("Failed to create transaction ID.");
                }
            }
 
            // Insert each cart item into transaction_items
            String itemSql = "INSERT INTO transaction_items "
                           + "(transaction_id, product_id, product_name, quantity, unit_price, subtotal) "
                           + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstItem = conn.prepareStatement(itemSql)) {
                for (Map.Entry<Integer, Object[]> entry : cart.entrySet()) {
                    int    prodId   = entry.getKey();
                    Object[] item  = entry.getValue();
                    String name    = (String) item[0];
                    double price   = (double) item[1];
                    int    qty     = (int)    item[2];
                    double subAmt  = (double) item[3];  // qty * price (pre-tax line total)
 
                    pstItem.setInt(1, transId);
                    pstItem.setInt(2, prodId);
                    pstItem.setString(3, name);
                    pstItem.setInt(4, qty);
                    pstItem.setDouble(5, price);
                    pstItem.setDouble(6, subAmt);
                    pstItem.addBatch();
                }
                pstItem.executeBatch();
            }
 
            // Deduct stock and log activity per item
            String updateStockSql = "UPDATE products SET stock = stock - ? WHERE prod_id = ? AND user_id = ?";
            String logSql = "INSERT INTO activity_log (user_id, product_name, quantity, action_type, reason, log_time) "
                          + "VALUES (?, ?, ?, 'Stock Out', ?, NOW())";
 
            try (PreparedStatement pstStock = conn.prepareStatement(updateStockSql);
                 PreparedStatement pstLog   = conn.prepareStatement(logSql)) {
 
                for (Map.Entry<Integer, Object[]> entry : cart.entrySet()) {
                    int    prodId = entry.getKey();
                    Object[] item = entry.getValue();
                    String name   = (String) item[0];
                    int    qty    = (int)    item[2];
 
                    pstStock.setInt(1, qty);
                    pstStock.setInt(2, prodId);
                    pstStock.setInt(3, adminId);   // products belong to admin
                    pstStock.addBatch();
 
                    pstLog.setInt(1, userId);
                    pstLog.setString(2, name);
                    pstLog.setInt(3, qty);
                    pstLog.setString(4, "Sold via POS | Payment: " + paymentMethod + " | Txn #" + transId);
                    pstLog.addBatch();
                }
                pstStock.executeBatch();
                pstLog.executeBatch();
            }
 
            conn.commit();
            return true;
 
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed", ex); }
            logger.log(Level.SEVERE, "Transaction failed", e);
            JOptionPane.showMessageDialog(this, "Transaction Failed: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException e) { logger.log(Level.SEVERE, "Connection close failed", e); }
        }
    }   
    private void loadTodaySalesReport() {
        if (adminId <= 0) {
            JOptionPane.showMessageDialog(this, "User session error.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
 
        String sql = "SELECT COUNT(*) as transaction_count, SUM(total_amount) as total_sales " +
                     "FROM transactions " +
                     "WHERE (user_id = ? OR user_id IN (SELECT id FROM users WHERE parent_id = ?)) " +
                     "AND DATE(transaction_date) = CURDATE()";
 
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, adminId);
            pst.setInt(2, adminId);
            
            try (ResultSet rs = pst.executeQuery()) {
                int transactionCount = 0;
                double totalSales = 0.0;
                
                if (rs.next()) {
                    transactionCount = rs.getInt("transaction_count");
                    totalSales = rs.getDouble("total_sales");
                    if (rs.wasNull()) totalSales = 0.0;
                }
                
                displaySalesReport(transactionCount, totalSales);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading sales report", e);
            JOptionPane.showMessageDialog(this, "Unable to load sales report.", "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void displaySalesReport(int transactionCount, double totalSales) {
        StringBuilder message = new StringBuilder();
        message.append("Today's Sales Summary\n");
        message.append("═══════════════════════════════════\n\n");
        message.append(String.format("Transactions: %d\n", transactionCount));
        message.append(String.format("Total Sales:  P %,.2f", totalSales));
        
        JOptionPane.showMessageDialog(this, message.toString(), "Daily Sales Report", JOptionPane.INFORMATION_MESSAGE);
    }
      
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel6 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        txtDisplayOrder = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblShowSellProduct = new javax.swing.JTable();
        jPanel2 = new javax.swing.JPanel();
        btnLogOutAssignUser = new javax.swing.JButton();
        btnRecordToday = new javax.swing.JButton();
        jPanel10 = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        txtSearchProduct = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        cmbSelectCategory = new javax.swing.JComboBox<>();
        jLabel9 = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        btn0 = new javax.swing.JButton();
        btnPay = new javax.swing.JButton();
        btn3 = new javax.swing.JButton();
        btn2 = new javax.swing.JButton();
        btn1 = new javax.swing.JButton();
        btn4 = new javax.swing.JButton();
        btn7 = new javax.swing.JButton();
        btn8 = new javax.swing.JButton();
        btn9 = new javax.swing.JButton();
        btn6 = new javax.swing.JButton();
        btn5 = new javax.swing.JButton();
        btnAC = new javax.swing.JButton();
        btnErase = new javax.swing.JButton();
        btnInquiry = new javax.swing.JButton();
        btnHelp = new javax.swing.JButton();
        btnHold = new javax.swing.JButton();
        btn00 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        lblTitle = new javax.swing.JLabel();
        btnAccessInventory = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel6.setBackground(new java.awt.Color(0, 0, 0));
        jPanel6.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jPanel7.setBackground(new java.awt.Color(241, 144, 53));

        txtDisplayOrder.setColumns(20);
        txtDisplayOrder.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        txtDisplayOrder.setRows(5);
        jScrollPane1.setViewportView(txtDisplayOrder);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 305, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(13, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 514, Short.MAX_VALUE)
                .addContainerGap())
        );

        jPanel6.add(jPanel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 40, 330, 540));

        tblShowSellProduct.setBackground(new java.awt.Color(255, 255, 0));
        tblShowSellProduct.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        tblShowSellProduct.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "ID", "Name", "Category", "Quantity", "Price", "Stock"
            }
        ));
        jScrollPane2.setViewportView(tblShowSellProduct);

        jPanel6.add(jScrollPane2, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 40, 610, 225));

        jPanel2.setBackground(new java.awt.Color(241, 144, 53));

        btnLogOutAssignUser.setBackground(new java.awt.Color(255, 102, 102));
        btnLogOutAssignUser.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnLogOutAssignUser.setText("LogOut");
        btnLogOutAssignUser.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLogOutAssignUserActionPerformed(evt);
            }
        });

        btnRecordToday.setBackground(new java.awt.Color(204, 255, 255));
        btnRecordToday.setText("Record Today");
        btnRecordToday.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRecordTodayActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnLogOutAssignUser, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnRecordToday, javax.swing.GroupLayout.DEFAULT_SIZE, 149, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRecordToday, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnLogOutAssignUser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        jPanel6.add(jPanel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 590, 330, 50));

        jPanel10.setBackground(new java.awt.Color(241, 144, 53));

        jPanel9.setBackground(new java.awt.Color(26, 92, 58));

        jPanel3.setBackground(new java.awt.Color(26, 92, 58));

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(255, 255, 255));
        jLabel8.setText("Search");

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(255, 255, 255));
        jLabel9.setText("Category");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(txtSearchProduct, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 53, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(32, 32, 32)
                .addComponent(cmbSelectCategory, javax.swing.GroupLayout.PREFERRED_SIZE, 149, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel9)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel8)
                            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel9)
                                .addComponent(cmbSelectCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(txtSearchProduct, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );

        jPanel4.setBackground(new java.awt.Color(26, 92, 58));

        btn0.setText("0");
        btn0.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn0ActionPerformed(evt);
            }
        });

        btnPay.setBackground(new java.awt.Color(255, 208, 0));
        btnPay.setText("Pay");
        btnPay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPayActionPerformed(evt);
            }
        });

        btn3.setText("3");
        btn3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn3ActionPerformed(evt);
            }
        });

        btn2.setText("2");
        btn2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn2ActionPerformed(evt);
            }
        });

        btn1.setText("1");
        btn1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn1ActionPerformed(evt);
            }
        });

        btn4.setText("4");
        btn4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn4ActionPerformed(evt);
            }
        });

        btn7.setText("7");
        btn7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn7ActionPerformed(evt);
            }
        });

        btn8.setText("8");
        btn8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn8ActionPerformed(evt);
            }
        });

        btn9.setText("9");
        btn9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn9ActionPerformed(evt);
            }
        });

        btn6.setText("6");
        btn6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn6ActionPerformed(evt);
            }
        });

        btn5.setText("5");
        btn5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn5ActionPerformed(evt);
            }
        });

        btnAC.setBackground(new java.awt.Color(204, 0, 0));
        btnAC.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnAC.setText("AC");
        btnAC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnACActionPerformed(evt);
            }
        });

        btnErase.setBackground(new java.awt.Color(204, 204, 204));
        btnErase.setText("Erase");
        btnErase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEraseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addComponent(btnPay, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(btn4, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(btn9, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn8, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(btn3, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(btnAC, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btn2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btn0, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btn7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btn6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btn1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnErase, javax.swing.GroupLayout.DEFAULT_SIZE, 80, Short.MAX_VALUE)))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnPay, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 261, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn7, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn8, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn9, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btn5, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
                    .addComponent(btn4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btn6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn3, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn2, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn1, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn0, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnAC, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnErase, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        btnInquiry.setText("Inquiry");
        btnInquiry.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInquiryActionPerformed(evt);
            }
        });

        btnHelp.setText("Help");
        btnHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHelpActionPerformed(evt);
            }
        });

        btnHold.setText("Hold");
        btnHold.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHoldActionPerformed(evt);
            }
        });

        btn00.setText("00");
        btn00.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn00ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btn00, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnHold, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnHelp, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnInquiry, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(btnInquiry, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnHelp, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnHold, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btn00, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel1.setText("DashboardPOS] — Crazy Krunch");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap(215, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addGap(215, 215, 215))
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel6.add(jPanel10, new org.netbeans.lib.awtextra.AbsoluteConstraints(350, 270, 610, 370));

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("Dashboard POS System");
        jPanel6.add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 10, -1, 20));

        lblTitle.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(255, 255, 255));
        lblTitle.setText("Appear whos user and title is. lbTitle");
        jPanel6.add(lblTitle, new org.netbeans.lib.awtextra.AbsoluteConstraints(730, 10, 210, 20));

        btnAccessInventory.setText("Access Inventory");
        jPanel6.add(btnAccessInventory, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 10, -1, 20));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 973, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 652, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnRecordTodayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRecordTodayActionPerformed
        loadTodaySalesReport();
    }//GEN-LAST:event_btnRecordTodayActionPerformed

    private void btnLogOutAssignUserActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogOutAssignUserActionPerformed
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to log out?",
            "Confirm Logout",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
 
        if (confirm == JOptionPane.YES_OPTION) {
            // FIX: Better error handling for logout logging
            boolean logSuccess = false;
            try (Connection conn = MySqlConnector.getConnection()) {
                String sql = "INSERT INTO time_log (user_id, username, action, module, log_time) VALUES (?, ?, 'Logout', 'POS', NOW())";
                try (PreparedStatement pst = conn.prepareStatement(sql)) {
                    pst.setInt(1, userId);
                    pst.setString(2, userName);
                    int affected = pst.executeUpdate();
                    logSuccess = (affected > 0);
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Logout logging failed", e);
            }
 
            // FIX: Show warning if logging failed but still allow logout
            if (!logSuccess) {
                int proceed = JOptionPane.showConfirmDialog(this,
                    "Warning: Failed to record logout time.\nDo you want to proceed with logout anyway?",
                    "Logging Error",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
                if (proceed != JOptionPane.YES_OPTION) return;
            }
 
            JOptionPane.showMessageDialog(this,
                "You have been logged out successfully!",
                "Logout",
                JOptionPane.INFORMATION_MESSAGE);
 
            new LogInUser().setVisible(true);
            this.dispose();
        }
    }//GEN-LAST:event_btnLogOutAssignUserActionPerformed

    private void btn0ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn0ActionPerformed
        appendCash("0");
    }//GEN-LAST:event_btn0ActionPerformed

    private void btn1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn1ActionPerformed
        appendCash("1");
    }//GEN-LAST:event_btn1ActionPerformed

    private void btn2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn2ActionPerformed
        appendCash("2");
    }//GEN-LAST:event_btn2ActionPerformed

    private void btn3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn3ActionPerformed
        appendCash("3");
    }//GEN-LAST:event_btn3ActionPerformed

    private void btn4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn4ActionPerformed
        appendCash("4");
    }//GEN-LAST:event_btn4ActionPerformed

    private void btn5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn5ActionPerformed
        appendCash("5");
    }//GEN-LAST:event_btn5ActionPerformed

    private void btn6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn6ActionPerformed
        appendCash("6");
    }//GEN-LAST:event_btn6ActionPerformed

    private void btn7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn7ActionPerformed
        appendCash("7");
    }//GEN-LAST:event_btn7ActionPerformed

    private void btn8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn8ActionPerformed
        appendCash("8");
    }//GEN-LAST:event_btn8ActionPerformed

    private void btn9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn9ActionPerformed
        appendCash("9");
    }//GEN-LAST:event_btn9ActionPerformed

    private void btnEraseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEraseActionPerformed
        if (!currentCashInput.isEmpty()) {
            currentCashInput = currentCashInput.substring(0, currentCashInput.length() - 1);
            updateReceiptDisplay();
        }
    }//GEN-LAST:event_btnEraseActionPerformed

    private void btnACActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnACActionPerformed
        clearCart();        // Clears the cart AND cash input
        txtSearchProduct.setText(""); // Clears search box
        filterTable();      // Resets table filter
        // Note: clearCart() already calls updateReceiptDisplay()
    }//GEN-LAST:event_btnACActionPerformed

    private void btnPayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPayActionPerformed
        if (cart.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Cart is empty!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
 
        double subTotal = 0;
        for (Object[] item : cart.values()) subTotal += (double) item[3];
        double grandTotal = subTotal + (subTotal * TAX_RATE);
 
        // Step 1: Choose payment method
        String[] methods = {"Cash"};
        int choice = JOptionPane.showOptionDialog(
            this,
            String.format("Total Amount: ₱%.2f\nSelect Pay in Cash:", grandTotal),
            "Payment",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, methods, methods[0]);
 
        if (choice == -1) return; // cancelled
 
        String paymentMethod = methods[choice];
        double cashReceived;
 
        if (choice == 0) {
            // ── CASH: must use numpad to enter amount ──────────────────────
            if (currentCashInput.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Please enter the cash amount using the number pad first.",
                    "Cash Amount Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                cashReceived = Double.parseDouble(currentCashInput);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid Cash Amount.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (cashReceived < grandTotal) {
                JOptionPane.showMessageDialog(this,
                    String.format("Insufficient Cash!\nEntered: ₱%.2f\nNeeded:  ₱%.2f", cashReceived, grandTotal),
                    "Insufficient Cash", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Confirm cash payment with change
            double change = cashReceived - grandTotal;
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Cash Received: ₱%.2f\nTotal:         ₱%.2f\nChange:        ₱%.2f\n\nConfirm payment?",
                    cashReceived, grandTotal, change),
                "Confirm Cash Payment", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
 
        } else if (choice == 1) {
            // ── GCASH: exact amount, no change ────────────────────────────
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("GCash Payment\nAmount to charge: ₱%.2f\n\nConfirm payment?", grandTotal),
                "Confirm GCash Payment", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            cashReceived = grandTotal;
 
        } else {
            // ── CREDIT CARD: exact amount, no change ──────────────────────
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Credit Card Payment\nAmount to charge: ₱%.2f\n\nConfirm payment?", grandTotal),
                "Confirm Credit Card Payment", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            cashReceived = grandTotal;
        }
 
        boolean success = processTransaction(paymentMethod, cashReceived, grandTotal);
        if (success) {
            // Build receipt summary
            StringBuilder receipt = new StringBuilder();
            receipt.append("═══════════════════════════════════\n");
            receipt.append("          PAYMENT SUCCESSFUL          \n");
            receipt.append("═══════════════════════════════════\n");
            receipt.append(String.format("Method:  %s\n", paymentMethod));
            receipt.append(String.format("Total:   ₱%.2f\n", grandTotal));
            if (choice == 0) {
                receipt.append(String.format("Cash:    ₱%.2f\n", cashReceived));
                receipt.append(String.format("Change:  ₱%.2f\n", cashReceived - grandTotal));
            }
            receipt.append("═══════════════════════════════════");
            JOptionPane.showMessageDialog(this, receipt.toString(), "Payment Successful", JOptionPane.INFORMATION_MESSAGE);
            clearCart();
            loadProducts(); // Refresh stock after sale
        }
    }//GEN-LAST:event_btnPayActionPerformed

    private void btn00ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn00ActionPerformed
        appendCash("00");
    }//GEN-LAST:event_btn00ActionPerformed

    private void btnHoldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHoldActionPerformed
        // Case 1: If cart is empty but we have held transactions, show selection dialog
        if (cart.isEmpty() && !heldTransactions.isEmpty()) {
            showHeldTransactionsDialog();
            return;
        }
        
        // Case 2: If cart is empty and no held transactions
        if (cart.isEmpty() && heldTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No active transaction or held transactions.", "Hold", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Case 3: Hold current transaction
        if (!cart.isEmpty()) {
            String holdName = "Hold #" + holdCounter++;
            // FIX: Store cashInput in constructor BEFORE clearing
            heldTransactions.add(new HeldTransaction(holdName, cart, currentCashInput));
            clearCart();
            updateReceiptDisplay();
            JOptionPane.showMessageDialog(this, 
                "Transaction saved as: " + holdName + "\nTotal held transactions: " + heldTransactions.size(), 
                "Transaction Held", 
                JOptionPane.INFORMATION_MESSAGE);
        }
    }//GEN-LAST:event_btnHoldActionPerformed

    private void btnHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHelpActionPerformed
        String helpText = "POS SYSTEM HELP\n" +
                         "═══════════════════════════════════\n\n" +
                         "1. Click product in table to add to cart\n" +
                         "2. Use number pad to enter cash amount\n" +
                         "3. Click PAY to process transaction\n" +
                         "4. Click AC to clear cart\n" +
                         "5. Click HOLD to save transaction for later\n" +
                         "   - Can hold multiple transactions\n" +
                         "   - Click Hold when cart empty to see list\n" +
                         "6. Click Inquiry to check product details\n" +
                         "7. Use Search and Category to filter products\n\n" +
                         "Held Transactions: " + heldTransactions.size() + "\n" +
                         "═══════════════════════════════════";
        JOptionPane.showMessageDialog(this, helpText, "Help", JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_btnHelpActionPerformed

    private void btnInquiryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInquiryActionPerformed
        int selectedRow = tblShowSellProduct.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product from the table.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        int modelRow = tblShowSellProduct.convertRowIndexToModel(selectedRow);
        try {
            String name = tblShowSellProduct.getModel().getValueAt(modelRow, 1).toString();
            int stock = Integer.parseInt(tblShowSellProduct.getModel().getValueAt(modelRow, 3).toString());
            double price = Double.parseDouble(tblShowSellProduct.getModel().getValueAt(modelRow, 4).toString());
            String status = tblShowSellProduct.getModel().getValueAt(modelRow, 5).toString();
            
            StringBuilder info = new StringBuilder();
            info.append("PRODUCT INQUIRY\n");
            info.append("═══════════════════════════════════\n\n");
            info.append(String.format("Name:    %s\n", name));
            info.append(String.format("Price:   P %.2f\n", price));
            info.append(String.format("Stock:   %d units\n", stock));
            info.append(String.format("Status:  %s\n", status));
            if (!heldTransactions.isEmpty()) {
                info.append(String.format("\nHeld Transactions: %d", heldTransactions.size()));
            }
            info.append("\n═══════════════════════════════════");
            
            JOptionPane.showMessageDialog(this, info.toString(), "Product Inquiry", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error retrieving product info.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_btnInquiryActionPerformed


    public static void main(String args[]) {
        
        
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btn0;
    private javax.swing.JButton btn00;
    private javax.swing.JButton btn1;
    private javax.swing.JButton btn2;
    private javax.swing.JButton btn3;
    private javax.swing.JButton btn4;
    private javax.swing.JButton btn5;
    private javax.swing.JButton btn6;
    private javax.swing.JButton btn7;
    private javax.swing.JButton btn8;
    private javax.swing.JButton btn9;
    private javax.swing.JButton btnAC;
    private javax.swing.JButton btnAccessInventory;
    private javax.swing.JButton btnErase;
    private javax.swing.JButton btnHelp;
    private javax.swing.JButton btnHold;
    private javax.swing.JButton btnInquiry;
    private javax.swing.JButton btnLogOutAssignUser;
    private javax.swing.JButton btnPay;
    private javax.swing.JButton btnRecordToday;
    private javax.swing.JComboBox<String> cmbSelectCategory;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JTable tblShowSellProduct;
    private javax.swing.JTextArea txtDisplayOrder;
    private javax.swing.JTextField txtSearchProduct;
    // End of variables declaration//GEN-END:variables
}
