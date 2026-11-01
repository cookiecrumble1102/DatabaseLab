import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Database
{
    // .db file -> 73 bytes for record (int, bool, char(60), double)
    // .idx file -> 12 bytes for record ("index for search" int, "offset" long)
    private final int NAMESIZE = 60; 
    private final int DBRECORDSIZE = NAMESIZE + 13;
    private final int INDEXRECORDSIZE = 12;

    // Variables
    private String dbFileName;
    private String indexFileName;
    private String backupFolderPath;
    private boolean backupEnabled;
    private boolean nonKeySearchEnabled;

    // Maps & HashSets for optimization
    // Map for index file (db_index: offset)
    Map<Integer, Long> indexMap;
    Map<String, HashSet<Integer>> nameMap;
    TreeMap<Double, HashSet<Integer>> priceMap;

    public Database(String dbname, String dbpath)
    {
        File dir = new File(dbpath);
        if (!dir.exists())
        {
            dir.mkdirs();
        }

        this.dbFileName = dbpath + File.separator + dbname + ".db";
        this.indexFileName = dbpath + File.separator + dbname + ".idx";

        this.indexMap = new HashMap<>();
        this.nameMap = new HashMap<>();
        this.priceMap = new TreeMap<>();

        checkFiles();
        loadIndex();
    }

    // Enables backup feature on certain database
    public boolean enableBackup(String backupFolderPath)
    {
        if (this.backupEnabled)
        {
            return true;
        }
        else
        {
            File backupDir = new File(backupFolderPath);
            if (!backupDir.exists())
            {
                boolean isCreated = backupDir.mkdirs();
                if (isCreated)
                {
                    this.backupFolderPath = backupFolderPath;
                    this.backupEnabled = true;
                    return true;
                }
                else
                {
                    System.err.println("Error while creating backup folder.");
                    return false;
                }
            }
            else
            {
                this.backupFolderPath = backupFolderPath;
                this.backupEnabled = true;
                return true;
            }
        }
    }

    // File operations

    // Creates backup of current file
    public boolean createBackup()
    {
        if (this.backupEnabled)
        {
            File backupFolder = new File(this.backupFolderPath);
            if (!backupFolder.exists())
            {
                System.err.println("Backup folder does not exist.");
                return false;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

            Path sourceDB = Path.of(this.dbFileName);
            String nameDB = sourceDB.getFileName().toString();
            String baseName = nameDB.substring(0, nameDB.length() - 3);
            Path sourceIndex = Path.of(this.indexFileName);

            Path targetDB = Path.of(this.backupFolderPath + File.separator + "shop_" + timestamp + "_" + baseName + ".db");
            Path targetIndex = Path.of(this.backupFolderPath + File.separator + "shop_idx_" + timestamp + "_" + baseName + ".idx");

            try
            {
                Files.copy(sourceDB, targetDB, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(sourceIndex, targetIndex, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            catch (IOException e)
            {
                System.err.println("Error while copying into backup.");
                return false;
            }
        }
        else
        {
            System.err.println("An attempt to perform backup when it is not enabled.");
            return false;
        }
    }

    public boolean loadFromFile(String filePath, String indexPath)
    {
        File backupFile = new File(filePath);
        File indexFile = new File(indexPath);
        if (backupFile.exists() && indexFile.exists())
        {
            Path sourceDB = Path.of(filePath);
            Path sourceIndex = Path.of(indexPath);

            Path targetDB = Path.of(this.dbFileName);
            Path targetIndex = Path.of(this.indexFileName);
            try
            {
                Files.copy(sourceDB, targetDB, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(sourceIndex, targetIndex, StandardCopyOption.REPLACE_EXISTING);
                loadIndex();
                return true;
            }
            catch (IOException e)
            {
                System.err.println("Error while loading from file.");
                return false;
            }
        }
        else
        {
            System.err.println("No file found");
            return false;
        }
    }

    public boolean saveDatabaseAs(String filePath, String indexPath) {
        try 
        {
            // Check if they exist
            File currentDbFile = new File(this.dbFileName);
            File currentIdxFile = new File(this.indexFileName);

            if (currentDbFile.exists()) {
                java.nio.file.Files.copy(
                    currentDbFile.toPath(), 
                    new File(filePath).toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            if (currentIdxFile.exists()) {
                java.nio.file.Files.copy(
                    currentIdxFile.toPath(), 
                    new File(indexPath).toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            // Change paths
            this.dbFileName = filePath;
            this.indexFileName = indexPath;

            // Перезагружаем хэши/индексы для нового файла
            this.loadHashes();

            return true;
        } 
        catch (Exception e) 
        {
            System.err.println("Error while saving DB: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<Record> loadDatabase()
    {
        ArrayList<Record> dbArray = new ArrayList<Record>();
        try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "r"))
        {
            long len = filerf.length();
            for (long i = 0; i < len; i += DBRECORDSIZE)
            {
                filerf.seek(i);
                int id = filerf.readInt();
                boolean isAvailable = filerf.readBoolean();
                if (!isAvailable)
                {
                    continue;
                }

                byte[] nameBuffer = new byte[NAMESIZE];
                filerf.readFully(nameBuffer);
                String name = new String(nameBuffer, StandardCharsets.UTF_8).trim();
                double price = filerf.readDouble();

                dbArray.add(new Record(id, isAvailable, name, price));
            }
            return dbArray;
        }
        catch (IOException e)
        {
            System.err.println("Error while reading database file.");
            return new ArrayList<Record>();
        }
    }

    public boolean exportToCSV(String csvFilePath)
    {
        ArrayList<Record> records = loadDatabase();
        File csvFile = new File(csvFilePath);
        if (!csvFile.exists())
        {
            try
            {
                csvFile.createNewFile();
            }
            catch (IOException e)
            {
                System.err.println("Error while creating CSV file. " + e.getMessage());
                return false;
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile)))
        {
            writer.write('\uFEFF');

            writer.println("ID;IsAvailable;Name;Price");
            
            for (Record item: records)
            {
                // Intended to prevent users from crash if they are dumb enough to use ; inside their name
                String nameSafe = item.getName().replace(";", " ");

                // Formatting line for .csv
                String line = String.format("%d;%b;%s;%.2f", item.getID(), item.isAvailable(), nameSafe, item.getPrice());
                writer.println(line);
            }

            return true;
            
        }
        catch (Exception e) 
        {
           System.err.println("Error with importing data into CSV. " + e.getMessage());
           return false;
        }


    }

    // Checks files integrity and creates them if neccessary
    public void checkFiles()
    {
        try
        {
            File dataFile = new File(this.dbFileName);
            File indexFile = new File(this.indexFileName);

            if (!dataFile.exists())
            {
                dataFile.createNewFile();
            }

            if (!indexFile.exists())
            {
                indexFile.createNewFile();
            }
        }
        catch (IOException e)
        {
            System.err.println("Error while creating files. " + e.getMessage());
        }
    }

    // Updates database and removes previously softly-deleted lines
    public boolean updateDatabase()
    {
        // Creates backup before updating
        if (this.createBackup())
        {
            try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "rw"))
            {
                long readpt = 0;
                long writept = 0;
                long len = filerf.length();

                while (readpt < len)
                {
                    filerf.seek(readpt);
                    filerf.skipBytes(4);
                    boolean isAvailable = filerf.readBoolean();
                    if (isAvailable)
                    {
                        // Scans data into buffer
                        filerf.seek(readpt);
                        byte[] bytesBuffer = new byte[DBRECORDSIZE];
                        filerf.readFully(bytesBuffer);

                        // Rewrites data
                        filerf.seek(writept);
                        filerf.write(bytesBuffer);
                        writept += DBRECORDSIZE;
                    }
                    readpt += DBRECORDSIZE;
                }
                
                filerf.setLength(writept);
                buildIndexFile();
                return true;
            }
            catch (IOException e)
            {
                System.err.println("Error while updating database.");
                return false;
            }
        }
        else
        {
            System.err.println("Update is not secure. Prevented data loss.");
            return false;
        }
    }

    // Clears database from data
    public boolean clearDatabase()
    {
        try
        {
            File dataFile = new File(this.dbFileName);
            File indexFile = new File(this.indexFileName);

            // Deleting files if they do exist at the moment
            if (dataFile.exists()) dataFile.delete();
            if (indexFile.exists()) indexFile.delete();

            // Creating blank database files in their location
            dataFile.createNewFile();
            indexFile.createNewFile();

            this.indexMap.clear();
            this.nameMap.clear();
            this.priceMap.clear();

            return true;
        }
        catch (IOException e)
        {
            System.err.println("Error while clearing database. " + e.getMessage());
            return false;
        }
    }

    // Removes database-related files
    // Returns true if all went successfully, otherwise returns false
    public boolean removeDatabase()
    {
        boolean isFine = true;
        File dataFile = new File(this.dbFileName);
        File indexFile = new File(this.indexFileName);

        if (dataFile.exists())
        {
            if (!dataFile.delete())
            {
                System.err.println("Error while deleting database-file");
                isFine = false;
            }
        }
            
        if (indexFile.exists())
        {
            if (!indexFile.delete())
            {
                System.err.println("Error while deleting index-file");
                isFine = false;
            }
        }
        return isFine;
    }

    // Creates hashtables in active memory to help optimize search using non-key attributes
    // For name it uses common Map, while for values it was decided to use TreeMap
    // Works in O(N) time complexity, but executes once
    public boolean loadHashes()
    {
        this.nameMap.clear();
        this.priceMap.clear();
        try (RandomAccessFile indexrf = new RandomAccessFile(this.indexFileName, "r"))
        {
            for (long i = 0; i < indexrf.length(); i += INDEXRECORDSIZE)
            {
                // Reading offset
                indexrf.seek(i);
                int id = indexrf.readInt();
                
                // Look for the values in main file and inserting them into
                Record item = searchByID(id);

                // If there is no item found
                if (item == null || item.getID() == Record.DEFAULTID)
                {
                    continue;
                }

                String name = item.getName().trim();
                double price = item.getPrice();

                // Adds to nameMap
                if (!this.nameMap.containsKey(name)) 
                {
                    this.nameMap.put(name, new HashSet<Integer>());
                }
                this.nameMap.get(name).add(id);

                // Adds to priceMap
                if (!this.priceMap.containsKey(price)) 
                {
                    this.priceMap.put(price, new HashSet<Integer>());
                }
                this.priceMap.get(price).add(id);
            }
            this.nonKeySearchEnabled = true;
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Error while loading additional hash-tables.");
            return false;
        }
    }

    // Implements hashing for O(1) search
    // Loads data from .idx file into Map structure to perform search operations in constant time
    public void loadIndex()
    {
        indexMap.clear();
        try (RandomAccessFile indexrf = new RandomAccessFile(this.indexFileName, "r"))
        {
            long len = indexrf.length();
            for (long i = 0; i < len; i += INDEXRECORDSIZE)
            {
                indexrf.seek(i);
                int id = indexrf.readInt();
                long offset = indexrf.readLong();
                this.indexMap.put(id, offset);
            }
        }
        catch (IOException e)
        {
            System.err.println("Error while loading indexes");
        }
    }
    
    // Creates index file for future hashing (time complexity O(n))
    // It is used in the start of working process when we don't have .idx file for some reason
    // Here it tries to prevent situations when an user due to his retardness has deleted our precious .idx file :D
    public void buildIndexFile()
    {
        this.indexMap.clear();
        try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "r"); 
             RandomAccessFile indexrf = new RandomAccessFile(this.indexFileName, "rw"))
        {
            indexrf.setLength(0);

            long fileLen = filerf.length();
            for (long offset = 0; offset < fileLen; offset += DBRECORDSIZE)
            {
                filerf.seek(offset);
                int id = filerf.readInt();
                boolean isAvailable = filerf.readBoolean();
                if (isAvailable)
                {
                    insertIntoIndexFile(id, offset);
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Error while rebuilding index file.");
        }
    }

    // Inserts data into index file
    public boolean insertIntoIndexFile(int id, long offset)
    {
        try (RandomAccessFile indexrf = new RandomAccessFile(this.indexFileName, "rw"))
        {
            indexrf.seek(indexrf.length());
            indexrf.writeInt(id);
            indexrf.writeLong(offset);
            indexMap.put(id, offset);
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Index insert failed... " + e.getMessage());
            return false;
        }
    }

    // Database Functionality
    public boolean insertLine(Record item)
    {
        // Check that line we're trying to insert is valid
        if (!item.isValid())
        {
            System.err.println("An attempt to insert invalid line was committed.");
            return false;
        }

        if (this.indexMap.get(item.getID()) != null)
        {
            System.err.println("An attempt to insert duplicate line was committed.");
            return false;
        }
        
        try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "rw"))
        {
            long len = filerf.length();
            filerf.seek(len);

            if (item.isAvailable())
            {
                this.insertIntoIndexFile(item.getID(), len);
                this.indexMap.put(item.getID(), len);
            }

            // Data insertion
            filerf.writeInt(item.getID());
            filerf.writeBoolean(item.isAvailable());

            // For string it gets little bit complicated (convert to bytes)
            byte[] nameBytes = item.getName().getBytes(StandardCharsets.UTF_8);
            byte[] nameBuffer = new byte[NAMESIZE];
            System.arraycopy(nameBytes, 0, nameBuffer, 0, Math.min(nameBytes.length, NAMESIZE));
            filerf.write(nameBuffer);

            filerf.writeDouble(item.getPrice());
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Error while inserting a line. " + e.getMessage());
            return false;
        }
    }

    // Edits certain line by using offset and new record to replace original
    public boolean editLine(Record newRecord)
    {
        Long offset = this.indexMap.get(newRecord.getID());
        if (offset == null)
        {
            System.err.println("No data to edit was found.");
            return false;
        }
        try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "rw"))
        {
            filerf.seek(offset);

            // Replace ID
            if (newRecord.getID() != Record.DEFAULTID)
            {
                filerf.writeInt(newRecord.getID());
            }

            // isAvailable is excluded due to it's inner logic
            filerf.skipBytes(1);

            // Replace name
            if (!Record.DEFAULTNAME.equals(newRecord.getName()))
            {
                byte[] nameBytes = newRecord.getName().getBytes(StandardCharsets.UTF_8);
                byte[] nameBuffer = new byte[NAMESIZE];
                System.arraycopy(nameBytes, 0, nameBuffer, 0, Math.min(nameBytes.length, NAMESIZE));
                filerf.write(nameBuffer);
            }

            // Replace price
            if (newRecord.getPrice() != Record.DEFAULTPRICE)
            {
                filerf.writeDouble(newRecord.getPrice());
            }

            return true;
        }
        catch (IOException e)
        {
            System.err.println("Error while editing data.");
            return false;
        }
    }

    public boolean removeLine(Record item)
    {
        Long offset = this.indexMap.get(item.getID());
        if (offset == null)
        {
            System.err.println("No data to delete.");
            return false;
        }
        try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "rw"))
        {
            filerf.seek(offset);
            filerf.skipBytes(4);
            filerf.writeBoolean(false);
            this.indexMap.remove(item.getID());
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Error while deleting data.");
            return false;
        }
    }

    public Record searchByID(int searchID)
    {
        Long offset = this.indexMap.get(searchID);
        if (offset == null)
        {
            return new Record();
        }
        else
        {
            try (RandomAccessFile filerf = new RandomAccessFile(this.dbFileName, "r"))
            {
                filerf.seek(offset);

                int id = filerf.readInt();
                boolean isAvailable = filerf.readBoolean();
                
                byte[] nameBuffer = new byte[NAMESIZE];
                filerf.readFully(nameBuffer);
                String name = new String(nameBuffer, StandardCharsets.UTF_8).trim();
                
                if (!isAvailable)
                {
                    return new Record();
                }

                double price = filerf.readDouble();

                return new Record(id, isAvailable, name, price);
            }
            catch (IOException e)
            {
                System.err.println("Error while searching for a line. " + e.getMessage());
                return new Record();
            }
        }
    }

    public ArrayList<Record> searchMultiple(String name, double price_l, double price_h)
    {
        if (!this.nonKeySearchEnabled && !this.loadHashes())
        {
            System.err.println("Multiple key search is disabled.");
            return new ArrayList<Record>();   
        }

        if (price_l != Record.DEFAULTPRICE && price_h != Record.DEFAULTPRICE && price_l > price_h)
        {
                System.err.println("Wrong borders inserted.");
                return new ArrayList<Record>(); 
        }

        HashSet<Integer> nameSet = new HashSet<>();
        HashSet<Integer> priceSet = new HashSet<>();

        // Get name
        if (!Record.DEFAULTNAME.equals(name))
        {
            nameSet = this.nameMap.getOrDefault(name, new HashSet<Integer>());
        }

        // Get price
        if (price_l != Record.DEFAULTPRICE || price_h != Record.DEFAULTPRICE)
        {
            
            if (Double.compare(price_l, price_h) == 0)
            {
                priceSet = this.priceMap.getOrDefault(price_l, new HashSet<Integer>());
            }
            else
            {
                for (HashSet<Integer> ids : priceMap.subMap(price_l, true, price_h, true).values())
                {
                    priceSet.addAll(ids);
                }
            }
        }

        // Get union/intersection and convert to list
        if (!nameSet.isEmpty() || !priceSet.isEmpty())
        {
            HashSet<Integer> resultSet;
            if (!nameSet.isEmpty() && !priceSet.isEmpty())
            {
                resultSet = new HashSet<>(nameSet);
                resultSet.retainAll(priceSet);
                if (resultSet.isEmpty())
                {
                    return new ArrayList<Record>(); 
                }
            }
            else
            {
                resultSet = nameSet.isEmpty() ? priceSet : nameSet;
            }

            ArrayList<Record> resultArray = new ArrayList<Record>();
            for (Integer itemID : resultSet)
            {
                Record record = searchByID(itemID);
                if (record.isValid())
                {
                    resultArray.add(record);
                }
            }
            return resultArray;
        }
        else
        {
            return new ArrayList<Record>(); 
        }
    }

    public boolean removeMultiple(String name, double price_l, double price_h)
    {
        ArrayList<Record> resultArray = searchMultiple(name, price_l, price_h);
        if (!resultArray.isEmpty())
        {
            boolean removed = false;
            for (Record item : resultArray)
            {
                if (item.isValid() && item.isAvailable())
                {
                    removed = removeLine(item)? true : removed;
                }
            }
    
            return removed;
        }
        else
        {
            return false;
        }
    }

}