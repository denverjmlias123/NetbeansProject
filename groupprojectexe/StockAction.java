package groupprojectexe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
 
public class StockAction extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(StockAction.class.getName());
    
    private final int userId;
    private final int prodId;
    private final DashboardInventory parentDashboard;
    private final boolean isNewProduct;
    
    private int currentStock;
    private String productName;
    private String currentAvailability;
    private java.sql.Date currentExpirationDate;
    
    // Constructor for existing products (default behavior)
    public StockAction(int userId, int prodId, DashboardInventory parent) {
        this(userId, prodId, parent, false);
    }  
    // Constructor with New Product flag
    public StockAction(int userId, int prodId, DashboardInventory parent, boolean isNewProduct) {
        this.userId = userId;
        this.prodId = prodId;
        this.parentDashboard = parent;
        this.isNewProduct = isNewProduct;
        
        initComponents();
        setLocationRelativeTo(null);
        
        setupUI();
        loadProductInfo();
        updateReasons();
        
        showInitializationMessage();
    }
    private void showInitializationMessage() {
        System.out.println("═══════════════════════════════════");
        System.out.println("     STOCK ACTION INITIALIZED       ");
        System.out.println("═══════════════════════════════════");
        System.out.println("  User ID: " + userId);
        System.out.println("  Product ID: " + prodId);
        System.out.println("  Mode: " + (isNewProduct ? "New Product" : "Existing Product"));
        System.out.println("═══════════════════════════════════");
    } 
    private void setupUI() {
    txtPname.setEditable(false);
    txtCstock.setEditable(false);
    txtLowAlert.setEditable(true);
 
    cmbAction.removeAllItems();
    cmbAction.addItem("Stock In");
    cmbAction.addItem("Stock Out");
 
    cmbAction.addActionListener(e -> {
        updateReasons();
        updateDatePickerVisibility();
    });
 
    // Start with today for Stock In
    dcExpectedDate.setDate(null);
    updateDatePickerVisibility();
}
    private void updateReasons() {
        cmbReason.removeAllItems();
        String selectedAction = cmbAction.getSelectedItem().toString();
        
        if (selectedAction.equals("Stock In")) {
            if (isNewProduct) {
                cmbReason.addItem("New Stock");
                cmbReason.setEnabled(false);
            } else if (currentStock == 0) {
                cmbReason.addItem("New Stock");
                cmbReason.addItem("Restock");
            } else {
                cmbReason.addItem("Additional Stock");
                cmbReason.addItem("Restock");
                cmbReason.addItem("Return from Customer");
                cmbReason.addItem("Inventory Adjustment");
                cmbReason.setSelectedItem("Additional Stock");
            }
        } else if (selectedAction.equals("Stock Out")) {
            cmbReason.addItem("Damaged");
            cmbReason.addItem("Expired");
            cmbReason.addItem("Sold");
            cmbReason.addItem("Lost/Stolen");
            cmbReason.setEnabled(true);
        }
    }
    private void loadProductInfo() {
    String sql = "SELECT name, stock, low_stock_alert, availability, expiration_date, next_expiration_date "
               + "FROM products WHERE prod_id = ? AND user_id = ?";
 
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
 
        pst.setInt(1, prodId);
        pst.setInt(2, userId);
 
        try (ResultSet rs = pst.executeQuery()) {
            if (rs.next()) {
                productName          = rs.getString("name");
                currentStock         = rs.getInt("stock");
                currentAvailability  = rs.getString("availability");
                currentExpirationDate = rs.getDate("expiration_date");
 
                txtPname.setText(productName);
                txtCstock.setText(String.valueOf(currentStock));
                txtLowAlert.setText(String.valueOf(rs.getInt("low_stock_alert")));
 
                // Always start date picker empty — user sets new batch date manually
                dcExpectedDate.setDate(null);
 
                updateReasons();
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading product info: ", e);
        JOptionPane.showMessageDialog(this, "Error loading product details.", "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}
    private String calculateStatus(int newStock, int lowAlert) {
        if (newStock == 0) return "Out of Stock";
        if (newStock <= lowAlert) return "Low Stock";
        return "Full Stock";
    }
    private String calculateAvailability(int newStock, String action) {
        if (newStock == 0) return "Unavailable";
        if (action.equals("Stock In")) return "Available";
        return currentAvailability;
    }
    private void updateDatePickerVisibility() {
    boolean isStockIn = "Stock In".equals(cmbAction.getSelectedItem());
    dcExpectedDate.setEnabled(isStockIn);
    dcExpectedDate.setVisible(isStockIn);
    jLabel14.setVisible(isStockIn); // the "Expected Date" label
}

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        txtPname = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        txtCstock = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        txtQuantity = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        cmbAction = new javax.swing.JComboBox<>();
        jLabel12 = new javax.swing.JLabel();
        txtLowAlert = new javax.swing.JTextField();
        jLabel13 = new javax.swing.JLabel();
        cmbReason = new javax.swing.JComboBox<>();
        btnSave = new javax.swing.JButton();
        btnCancel = new javax.swing.JButton();
        jLabel14 = new javax.swing.JLabel();
        dcExpectedDate = new com.toedter.calendar.JDateChooser();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(20, 200, 130));

        jPanel2.setBackground(new java.awt.Color(15, 25, 35));

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Stock-In/Out Products");

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(255, 255, 255));
        jLabel4.setText("------------------------------------------------------------------------------------------");

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("▣");

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(255, 255, 255));
        jLabel8.setText("Product Name:");

        txtPname.setBackground(new java.awt.Color(204, 204, 204));

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(255, 255, 255));
        jLabel9.setText("Current Stock:");

        txtCstock.setBackground(new java.awt.Color(204, 204, 204));

        jLabel10.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText("Set Quantity:");

        jLabel11.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(255, 255, 255));
        jLabel11.setText("Select Action:");

        jLabel12.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(255, 255, 255));
        jLabel12.setText("Set Low Stocks Alert:");

        txtLowAlert.setBackground(new java.awt.Color(204, 204, 204));

        jLabel13.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(255, 255, 255));
        jLabel13.setText("Reason Of Action:");

        btnSave.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnSave.setText("SAVE");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        btnCancel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        btnCancel.setText("CANCEL");
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        jLabel14.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(255, 255, 255));
        jLabel14.setText("Product Date Expected:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14)
                    .addComponent(jLabel13)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addGap(109, 109, 109)
                        .addComponent(jLabel11))
                    .addComponent(jLabel9)
                    .addComponent(jLabel8)
                    .addComponent(jLabel4)
                    .addComponent(jLabel12)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel1)))
                .addGap(29, 29, 29))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(cmbReason, javax.swing.GroupLayout.PREFERRED_SIZE, 314, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                                .addComponent(txtQuantity, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(cmbAction, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(txtCstock, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 314, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtPname, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 314, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(txtLowAlert, javax.swing.GroupLayout.PREFERRED_SIZE, 314, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(dcExpectedDate, javax.swing.GroupLayout.PREFERRED_SIZE, 315, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(49, 49, 49))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPname, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtCstock, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cmbAction)
                    .addComponent(txtQuantity, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel14)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dcExpectedDate, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtLowAlert, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel13)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cmbReason, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(38, 38, 38)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(28, 28, 28))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        int qty;
        int lowAlert;
 
        try {
            qty      = Integer.parseInt(txtQuantity.getText().trim());
            lowAlert = Integer.parseInt(txtLowAlert.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
 
        if (qty <= 0) {
            JOptionPane.showMessageDialog(this, "Quantity must be greater than 0.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (lowAlert < 1) {
            JOptionPane.showMessageDialog(this, "Low Stock Alert must be at least 1.", "Input Error", JOptionPane.WARNING_MESSAGE);
            txtLowAlert.requestFocus();
            return;
        }
 
        String action   = cmbAction.getSelectedItem().toString();
        int    newStock = currentStock;
 
        if (action.equals("Stock In")) {
            newStock += qty;
        } else {
            if (qty > currentStock) {
                JOptionPane.showMessageDialog(this, "Cannot remove more than current stock.", "Stock Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            newStock -= qty;
        }
 
        String newStatus       = calculateStatus(newStock, lowAlert);
        String newAvailability = calculateAvailability(newStock, action);
 
        // ── Stock In: read new batch date → save to next_expiration_date only ──
        // ── Stock Out: completely ignore the date picker ─────────────────────
        java.sql.Date newBatchDate = null;
        if (action.equals("Stock In")) {
            Date picked = dcExpectedDate.getDate();
            if (picked != null) {
                newBatchDate = new java.sql.Date(picked.getTime());
            }
        }
 
        Connection conn = null;
        try {
            conn = MySqlConnector.getConnection();
            conn.setAutoCommit(false);
 
            if (action.equals("Stock In")) {
                // Update stock/status/availability + next_expiration_date + next_batch_stock
                // next_batch_stock stores the exact qty entered for this incoming batch.
                // expiration_date is NEVER touched here.
                String updateSql = "UPDATE products SET stock=?, low_stock_alert=?, availability=?, "
                                 + "next_expiration_date=?, next_batch_stock=? WHERE prod_id=?";
                try (PreparedStatement pst = conn.prepareStatement(updateSql)) {
                    pst.setInt(1, newStock);
                    pst.setInt(2, lowAlert);
                    pst.setString(3, newAvailability);
                    pst.setDate(4, newBatchDate);  // null if user left it empty
                    pst.setInt(5, qty);            // batch input qty — shown in monitor table
                    pst.setInt(6, prodId);
                    pst.executeUpdate();
                }
            } else {
                // Stock Out — only update stock/status/availability
                // expiration_date and next_expiration_date are NEVER touched
                String updateSql = "UPDATE products SET stock=?, low_stock_alert=?, availability=? "
                                 + "WHERE prod_id=?";
                try (PreparedStatement pst = conn.prepareStatement(updateSql)) {
                    pst.setInt(1, newStock);
                    pst.setInt(2, lowAlert);
                    pst.setString(3, newAvailability);
                    pst.setInt(4, prodId);
                    pst.executeUpdate();
                }
            }
 
            // Activity log
            String logSql = "INSERT INTO activity_log (user_id, product_name, quantity, action_type, reason, log_time) "
                          + "VALUES (?, ?, ?, ?, ?, NOW())";
            try (PreparedStatement pstLog = conn.prepareStatement(logSql)) {
                String reason     = (cmbReason.getSelectedItem() != null) ? cmbReason.getSelectedItem().toString() : "Not specified";
                String fullReason = reason;
                if (action.equals("Stock In") && newBatchDate != null) {
                    fullReason = reason + " | Next Expires: " + newBatchDate.toString();
                }
                pstLog.setInt(1, userId);
                pstLog.setString(2, productName);
                pstLog.setInt(3, qty);
                pstLog.setString(4, action);
                pstLog.setString(5, fullReason);
                pstLog.executeUpdate();
            }
 
            conn.commit();
 
            String successMsg =
                "Stock Updated Successfully!\n\n" +
                "Product: "        + productName + "\n" +
                "Action: "         + action + "\n" +
                "Quantity: "       + qty + "\n" +
                "Previous Stock: " + currentStock + "\n" +
                "New Stock: "      + newStock + "\n" +
                "Status: "         + newStatus +
                (action.equals("Stock In") && newBatchDate != null
                    ? "\nNext Expiration Date: " + newBatchDate.toString()
                    : "") + "\n" +
                "Reason: "         + (cmbReason.getSelectedItem() != null ? cmbReason.getSelectedItem().toString() : "N/A");
 
            JOptionPane.showMessageDialog(this, successMsg, "Success", JOptionPane.INFORMATION_MESSAGE);
 
            if (parentDashboard != null) parentDashboard.refreshData();
            this.dispose();
 
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed: ", ex); }
            logger.log(Level.SEVERE, "Database Error: ", e);
            JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException e) { logger.log(Level.WARNING, "Connection close failed: ", e); }
        }
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to cancel?",
            "Confirm Cancel",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            this.dispose();
        }
    }//GEN-LAST:event_btnCancelActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new StockAction(0, 0, null).setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnSave;
    private javax.swing.JComboBox<String> cmbAction;
    private javax.swing.JComboBox<String> cmbReason;
    private com.toedter.calendar.JDateChooser dcExpectedDate;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTextField txtCstock;
    private javax.swing.JTextField txtLowAlert;
    private javax.swing.JTextField txtPname;
    private javax.swing.JTextField txtQuantity;
    // End of variables declaration//GEN-END:variables
}
