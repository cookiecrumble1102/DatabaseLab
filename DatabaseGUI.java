import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

public class DatabaseGUI extends JFrame {
    private static int WIDTH = 1600;
    private static int HEIGHT = 800;
    
    private Database db;
    private JTable table;
    private DefaultTableModel tableModel;

    // Basic Fields
    private JTextField idField = new JTextField(4);
    private JTextField nameField = new JTextField(12);
    private JTextField priceField = new JTextField(5);

    // Multiple Search Fields
    private JTextField searchNameField = new JTextField(10);
    private JTextField priceLowField = new JTextField(5);
    private JTextField priceHighField = new JTextField(5);
    private JCheckBox exactPriceCheckbox = new JCheckBox("Exact price");

    public DatabaseGUI(Database db) {
        this.db = db;

        // Main Window
        setTitle("Database Manager");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Align center
        setLocationRelativeTo(null); 
        setLayout(new BorderLayout(10, 10));

        // Upper line with names (shapka :D)
        String[] columns = {"ID", "State", "Name", "Price"};
        
        // Override method to prevent editing right at place
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };
        
        table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // Input data panel (North)
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Data insertion"));
        
        inputPanel.add(new JLabel("ID:"));
        inputPanel.add(idField);
        inputPanel.add(new JLabel("Name:"));
        inputPanel.add(nameField);
        inputPanel.add(new JLabel("Price:"));
        inputPanel.add(priceField);

        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton searchButton = new JButton("Search by ID");
        JButton deleteButton = new JButton("Remove");

        inputPanel.add(addButton);
        inputPanel.add(editButton);
        inputPanel.add(searchButton);
        inputPanel.add(deleteButton);
        
        add(inputPanel, BorderLayout.NORTH);

        // Service buttons & Multiple Operations (South)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Multiple Search, Delete and Service"));

        // Items for searchMultiple|removeMultiple
        bottomPanel.add(new JLabel("Search Name:"));
        bottomPanel.add(searchNameField);
        bottomPanel.add(new JLabel("Price / From:"));
        bottomPanel.add(priceLowField);
        bottomPanel.add(new JLabel("To:"));
        bottomPanel.add(priceHighField);
        bottomPanel.add(exactPriceCheckbox);


        JButton saveAsButton = new JButton("Save As");
        JButton multiSearchButton = new JButton("Multiple Search");
        JButton multiRemoveButton = new JButton("Remove Multiple");
        JButton updateButton = new JButton("Update");
        JButton refreshButton = new JButton("Refresh");
        JButton csvButton = new JButton("Export to CSV");
        JButton backupButton = new JButton("Backup");
        JButton loadFileButton = new JButton("Open/Load");
        JButton clearDbButton = new JButton("Clear"); 

        bottomPanel.add(multiSearchButton);
        bottomPanel.add(multiRemoveButton);
        bottomPanel.add(updateButton);
        bottomPanel.add(refreshButton);
        bottomPanel.add(csvButton);
        bottomPanel.add(backupButton);
        bottomPanel.add(loadFileButton);
        bottomPanel.add(clearDbButton);
        bottomPanel.add(saveAsButton);
        
        add(bottomPanel, BorderLayout.SOUTH);

        // Button function description
        // -----------------------------

        // Refresh Button
        updateButton.addActionListener(e -> 
        {
            if (db.updateDatabase()) {
                refreshTable();
                JOptionPane.showMessageDialog(this, "Database successfully updated [Backup stored]!");
            } else {
                JOptionPane.showMessageDialog(this, "Database update was stopped. Data loss prevented!", "Update Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Refresh table without backup
        refreshButton.addActionListener(e ->
        {
            refreshTable();
        });

        // Add Button
        addButton.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                String name = nameField.getText().trim();
                double price = Double.parseDouble(priceField.getText().trim());

                Record r = new Record(id, true, name, price);
                
                if (db.insertLine(r)) {
                    JOptionPane.showMessageDialog(this, "Successfully added data!");
                    refreshTable();
                    clearFields();
                } else {
                    JOptionPane.showMessageDialog(this, "Error while inserting data!", "Insert Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Incorrect format spotted!", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Edit Button
        editButton.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                String name = nameField.getText().trim();
                double price = Double.parseDouble(priceField.getText().trim());

                Record existing = db.searchByID(id);
                if (!existing.isValid()) {
                    JOptionPane.showMessageDialog(this, "Record with ID " + id + " not found for update!", "Update Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                Record updatedRecord = new Record(id, existing.isAvailable(), name, price);
                
                if (db.editLine(updatedRecord)) { 
                    JOptionPane.showMessageDialog(this, "Record successfully updated!");
                    refreshTable();
                    clearFields();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to update record.", "Update Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Incorrect format for ID or Price!", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Search by ID
        searchButton.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Record item = db.searchByID(id);
                
                if (item.isValid() && item.isAvailable()) {
                    tableModel.setRowCount(0); 
                    tableModel.addRow(new Object[]{item.getID(), item.isAvailable(), item.getName(), item.getPrice()});
                } else {
                    JOptionPane.showMessageDialog(this, "Record with id " + id + " was not found", "Search failed", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "ID Format Error", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Multiple Search (searchMultiple)
        multiSearchButton.addActionListener(e -> {
            try {
                String nameQuery = searchNameField.getText().trim();
                if (nameQuery.isEmpty()) {
                    nameQuery = Record.DEFAULTNAME;
                }

                double pLow = Record.DEFAULTPRICE;
                double pHigh = Record.DEFAULTPRICE;

                if (!priceLowField.getText().trim().isEmpty()) {
                    pLow = Double.parseDouble(priceLowField.getText().trim());
                }

                if (exactPriceCheckbox.isSelected()) {
                    pHigh = pLow;
                } else {
                    if (!priceHighField.getText().trim().isEmpty()) {
                        pHigh = Double.parseDouble(priceHighField.getText().trim());
                    } else {
                        pHigh = Double.MAX_VALUE;
                    }
                }

                ArrayList<Record> results = db.searchMultiple(nameQuery, pLow, pHigh);
                
                tableModel.setRowCount(0);
                for (Record r : results) {
                    if (r.isAvailable()) {
                        tableModel.addRow(new Object[]{r.getID(), r.isAvailable(), r.getName(), r.getPrice()});
                    }
                }
                JOptionPane.showMessageDialog(this, "Found records: " + tableModel.getRowCount());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Incorrect price format for search!", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Multiple Remove Button
        multiRemoveButton.addActionListener(e -> {
            try {
                String nameQuery = searchNameField.getText().trim();
                if (nameQuery.isEmpty()) {
                    nameQuery = Record.DEFAULTNAME;
                }

                double pL = Record.DEFAULTPRICE;
                double pH = Record.DEFAULTPRICE;

                if (!priceLowField.getText().trim().isEmpty()) {
                    pL = Double.parseDouble(priceLowField.getText().trim());
                }

                if (exactPriceCheckbox.isSelected()) {
                    pH = pL;
                } else {
                    if (!priceHighField.getText().trim().isEmpty()) 
                    {
                        pH = Double.parseDouble(priceHighField.getText().trim());
                    } 
                }

                int confirm = JOptionPane.showConfirmDialog(this, 
                    "Are you sure want to proceed and delete all these lines?", 
                    "Confirm", JOptionPane.YES_NO_OPTION);
                
                if (confirm == JOptionPane.YES_OPTION) {
                    boolean success = db.removeMultiple(nameQuery, pL, pH);
                    if (success) {
                        JOptionPane.showMessageDialog(this, "Lines were successfully deleted!");
                        refreshTable();
                    } else {
                        JOptionPane.showMessageDialog(this, "No data to delete or error occured", "Info", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Incorrect price format for removal!", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // Delete Button (*using ID)
        deleteButton.addActionListener(e -> {
            try {
                int id = Integer.parseInt(idField.getText().trim());
                Record item = db.searchByID(id);
                
                if (item.isValid() && db.removeLine(item)) {
                    JOptionPane.showMessageDialog(this, "Record was removed!");
                    refreshTable();
                    clearFields();
                } else {
                    JOptionPane.showMessageDialog(this, "Record delete failed (Check ID).", "Delete Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Wrong format of ID detected", "Format Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        // CSV Export
        csvButton.addActionListener(e -> {
            String path = "database_export.csv";
            if (db.exportToCSV(path)) {
                JOptionPane.showMessageDialog(this, "Exported successfully into:\n" + path);
            } else {
                JOptionPane.showMessageDialog(this, "Error while exporting in CSV!", "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Manual Backup Button
        backupButton.addActionListener(e -> {
            if (db.createBackup()) {
                JOptionPane.showMessageDialog(this, "Backup successfully created!");
            } else {
                JOptionPane.showMessageDialog(this, "Backup process was unfinished.", "Backup Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Load From File
        loadFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Choose database file (.db)");
            int userSelection = fileChooser.showOpenDialog(this);
            
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File dbFile = fileChooser.getSelectedFile();
                String dbPath = dbFile.getAbsolutePath();

                // File name
                String fileName = dbFile.getName();

                String parentDir = dbFile.getParent();
                if (parentDir == null) {
                    parentDir = ".";
                }

                String baseName = fileName.endsWith(".db") ? fileName.substring(0, fileName.length() - 3) : fileName;
                String indexPath = parentDir + java.io.File.separator + baseName + ".idx";

                if (db.loadFromFile(dbPath, indexPath)) {
                    JOptionPane.showMessageDialog(this, "Database successfully loaded from:\n" + dbPath);
                    refreshTable();
                } else {
                    JOptionPane.showMessageDialog(this, "Error while loading database from file", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        saveAsButton.addActionListener(e -> {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Сохранить базу данных как...");
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String dbPath = fileToSave.getAbsolutePath();
            String indexPath = dbPath + ".idx";
            
            if (db.saveDatabaseAs(dbPath, indexPath)) {
                JOptionPane.showMessageDialog(this, "Database successfully stored into:\n" + dbPath);
            } else {
                JOptionPane.showMessageDialog(this, "Error while saving database.", "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        });
        // Clear DB Button
        clearDbButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Are you sure, that you want to clear all database?", 
                "Attention", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    db.clearDatabase(); 
                    JOptionPane.showMessageDialog(this, "DB was cleared.");
                    refreshTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error while clearing db: " + ex.getMessage(), "Clear Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        refreshTable();
    }

    // Loads database from file to GUI
    private void refreshTable() {
        tableModel.setRowCount(0);
        ArrayList<Record> records = db.loadDatabase();
        
        if (records != null) {
            for (Record r : records) {
                tableModel.addRow(new Object[]{
                    r.getID(), 
                    r.isAvailable(), 
                    r.getName(), 
                    r.getPrice()
                });
            }
        }
    }

    // It does clear :3
    private void clearFields() {
        idField.setText("");
        nameField.setText("");
        priceField.setText("");
        searchNameField.setText("");
        priceLowField.setText("");
        priceHighField.setText("");
        exactPriceCheckbox.setSelected(false);
    }
}