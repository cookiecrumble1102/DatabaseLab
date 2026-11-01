import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        
        // Default file to load
        String dbName = "shop";
        String dataDirectory = "./Data";
        String backupDirectory = "./Backups";

        // Creating database
        Database db = new Database(dbName, dataDirectory);
        
        // Enables backup
        db.enableBackup(backupDirectory);

        // Loads GUI
        SwingUtilities.invokeLater(() -> {
            DatabaseGUI gui = new DatabaseGUI(db);
            gui.setVisible(true);
        });

        System.out.println("GUI is working fine!");
    }
}