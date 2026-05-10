package groupprojectexe;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
 
public class EditFoodSystem extends javax.swing.JFrame {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(EditFoodSystem.class.getName());
    
    private final int userId;
    private final int prodId;
    private final DashboardInventory parentDashboard;
    private boolean productFound = false; // FIX: Track if product was found
    private int currentStock;  // ADD this field
    
    public EditFoodSystem(int userId, int prodId, DashboardInventory parent) {
        this.userId = userId;
        this.prodId = prodId;
        this.parentDashboard = parent;
        
        initComponents();
        setLocationRelativeTo(null);
        
        loadCategories();
        loadProductData();
        
        // FIX: Show initialization message
        showInitializationMessage();
    }
    private void showInitializationMessage() {
        System.out.println("═══════════════════════════════════");
        System.out.println("    EDIT FOOD SYSTEM INITIALIZED    ");
        System.out.println("═══════════════════════════════════");
        System.out.println("  User ID: " + userId);
        System.out.println("  Product ID: " + prodId);
        System.out.println("  Mode: Editing Existing Product");
        System.out.println("═══════════════════════════════════");
    }
    private void loadCategories() {
    cmbSelectCategory.removeAllItems();
    String sql = "SELECT DISTINCT p.category FROM products p "
               + "WHERE p.user_id = ? AND p.category IS NOT NULL AND p.category != '' "
               + "AND p.category NOT IN ("
               + "  SELECT category_name FROM deleted_categories WHERE user_id = ?"
               + ") ORDER BY p.category";
 
    try (Connection conn = MySqlConnector.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {
 
        pst.setInt(1, userId);
        pst.setInt(2, userId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                cmbSelectCategory.addItem(rs.getString("category"));
            }
        }
    } catch (SQLException e) {
        logger.log(Level.SEVERE, "Error loading categories: ", e);
        JOptionPane.showMessageDialog(this, "Error loading categories.", "Database Error", JOptionPane.ERROR_MESSAGE);
    }
} 
    private void loadProductData() {
        String sql = "SELECT name, price, category, availability, stock FROM products WHERE prod_id = ? AND user_id = ?";
        
        try (Connection conn = MySqlConnector.getConnection();
             PreparedStatement pst = conn.prepareStatement(sql)) {
            
            pst.setInt(1, prodId);
            pst.setInt(2, userId);
            
            
            try (ResultSet rs = pst.executeQuery()) {
                // FIX: rs.next() MUST be called before reading any column
                if (rs.next()) {
                    currentStock = rs.getInt("stock");
                    productFound = true;
                    txtAddProduct.setText(rs.getString("name"));
                    txtEnterPrice.setText(String.valueOf(rs.getDouble("price")));
                    cmbSelectCategory.setSelectedItem(rs.getString("category"));
 
                    // Setup Availability Combo
                    cmbSelectStatus.removeAllItems();
                    cmbSelectStatus.addItem("Available");
                    cmbSelectStatus.addItem("Unavailable");
                    cmbSelectStatus.setSelectedItem(rs.getString("availability"));
                } else {
                    // FIX: Product not found - show error and close
                    JOptionPane.showMessageDialog(this, 
                        "Product not found or you don't have permission to edit it.", 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
                    this.dispose();
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error loading product data: ", e);
            JOptionPane.showMessageDialog(this, "Could not load product details.", "Error", JOptionPane.ERROR_MESSAGE);
            this.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        cmbSelectStatus = new javax.swing.JComboBox<>();
        jLabel9 = new javax.swing.JLabel();
        btnSave = new javax.swing.JButton();
        txtAddProduct = new javax.swing.JTextField();
        btnCancel = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        txtEnterPrice = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        cmbSelectCategory = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(20, 200, 130));

        jPanel2.setBackground(new java.awt.Color(15, 25, 35));

        jLabel12.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(255, 255, 255));
        jLabel12.setText("Status:");

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("▣");

        jLabel9.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(255, 255, 255));
        jLabel9.setText("Category Name:");

        btnSave.setBackground(new java.awt.Color(204, 204, 204));
        btnSave.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnSave.setText("Save");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        btnCancel.setBackground(new java.awt.Color(204, 204, 204));
        btnCancel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        btnCancel.setText("Cancel");
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(255, 255, 255));
        jLabel1.setText("Edit Food Product Sale");

        jLabel11.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(255, 255, 255));
        jLabel11.setText("Enter Selling Price:");

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(255, 255, 255));
        jLabel4.setText("------------------------------------------------------------------------------------------");

        jLabel8.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(255, 255, 255));
        jLabel8.setText("Product Name:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel4))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel1))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel8)
                            .addComponent(jLabel9)
                            .addComponent(jLabel11)
                            .addComponent(jLabel12)
                            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(jPanel2Layout.createSequentialGroup()
                                    .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
                                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel2Layout.createSequentialGroup()
                                    .addGap(10, 10, 10)
                                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(txtAddProduct, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                                        .addComponent(txtEnterPrice, javax.swing.GroupLayout.DEFAULT_SIZE, 304, Short.MAX_VALUE)
                                        .addComponent(cmbSelectCategory, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(cmbSelectStatus, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel4)
                .addGap(18, 18, 18)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtAddProduct, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel9)
                .addGap(8, 8, 8)
                .addComponent(cmbSelectCategory, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtEnterPrice, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cmbSelectStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(31, 31, 31)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
        // FIX: Check if product was found before saving
        if (!productFound) {
            JOptionPane.showMessageDialog(this, "Product data not loaded. Please try again.", "Error", JOptionPane.ERROR_MESSAGE);
            this.dispose();
            return;
        }
        
        // 1. Retrieve Input
        String name = txtAddProduct.getText().trim();
        String priceStr = txtEnterPrice.getText().trim();
        String category = (cmbSelectCategory.getSelectedItem() != null) ? cmbSelectCategory.getSelectedItem().toString() : "Uncategorized";
        String availability = (cmbSelectStatus.getSelectedItem() != null) ? cmbSelectStatus.getSelectedItem().toString() : "Unavailable";
 
        // 2. FIX: Enhanced Validation
        if (name.isEmpty() || priceStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Fields cannot be empty.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (name.length() < 2) {
            JOptionPane.showMessageDialog(this, "Product name must be at least 2 characters.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        double price;
        try {
            price = Double.parseDouble(priceStr);
            // FIX: Validate price
            if (price <= 0) {
                JOptionPane.showMessageDialog(this, "Price must be greater than 0.", "Input Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Price must be a valid number.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
 
        // 3. Database Transaction
        Connection conn = null;
        try {
            conn = MySqlConnector.getConnection();
            conn.setAutoCommit(false);
 
            // A. Update Product
            String updateSql = "UPDATE products SET name=?, category=?, price=?, availability=? WHERE prod_id=? AND user_id=?";
            try (PreparedStatement pstUpdate = conn.prepareStatement(updateSql)) {
                pstUpdate.setString(1, name);
                pstUpdate.setString(2, category);
                pstUpdate.setDouble(3, price);
                pstUpdate.setString(4, availability);
                pstUpdate.setInt(5, prodId);
                pstUpdate.setInt(6, userId);
                
                int rowsUpdated = pstUpdate.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new SQLException("No rows updated - product may have been deleted.");
                }
            }
 
            // B. Log Activity
            String logSql = "INSERT INTO activity_log (user_id, product_name, quantity, action_type, reason, log_time) VALUES (?, ?, ?, ?, ?, NOW())";
            try (PreparedStatement pstLog = conn.prepareStatement(logSql)) {
                pstLog.setInt(1, userId);
                pstLog.setString(2, name);
                pstLog.setInt(3, 0);
                pstLog.setString(4, "Edit");
                pstLog.setString(5, "Details updated - Price: P" + String.format("%.2f", price) + ", Availability: " + availability);
                pstLog.executeUpdate();
            }
 
            conn.commit();
 
            JOptionPane.showMessageDialog(this, 
                "Product Updated Successfully!\n\n" +
                "Name: " + name + "\n" +
                "Category: " + category + "\n" +
                "Price: P " + String.format("%.2f", price) + "\n" +
                "Status: " + availability,
                "Success", 
                JOptionPane.INFORMATION_MESSAGE);
 
            // Refresh Parent
            if (parentDashboard != null) {
                parentDashboard.refreshData();
            }
 
            this.dispose();
 
        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Rollback failed: ", ex);
            }
            logger.log(Level.SEVERE, "Database Error: ", e);
            JOptionPane.showMessageDialog(this, "Error updating product: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Connection close failed: ", e);
            }
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
        java.awt.EventQueue.invokeLater(() -> new EditFoodSystem(0, 0, null).setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnSave;
    private javax.swing.JComboBox<String> cmbSelectCategory;
    private javax.swing.JComboBox<String> cmbSelectStatus;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JTextField txtAddProduct;
    private javax.swing.JTextField txtEnterPrice;
    // End of variables declaration//GEN-END:variables
}
