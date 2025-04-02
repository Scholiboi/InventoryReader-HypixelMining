package inventoryreader.ir;

import java.io.File;

public class FilePathManager {
    private static final String DATA_FOLDER = "ir-data";
    
    private static final String INVENTORY_DATA_FILE = "inventorydata.json";
    private static final String ALL_CONTAINER_DATA_FILE = "allcontainerData.json";
    
    private static final String VERSION = "v1.1.2";
    private static final String LINUX_EXE = "hypixel_dwarven_forge-" + VERSION + "-linux";
    private static final String WINDOWS_EXE = "hypixel_dwarven_forge-" + VERSION + "-win.exe";
    private static final String MAC_EXE = "hypixel_dwarven_forge-" + VERSION + "-macos";
    
    private static final FilePathManager INSTANCE = new FilePathManager();
    private final File dataDirectory;
    
    private FilePathManager() {
        dataDirectory = new File(DATA_FOLDER);
        if (!dataDirectory.exists()) {
            if (dataDirectory.mkdir()) {
                InventoryReader.LOGGER.info("Created data directory: " + dataDirectory.getAbsolutePath());
            } else {
                InventoryReader.LOGGER.error("Failed to create data directory: " + dataDirectory.getAbsolutePath());
            }
        }
    }
    
    public static FilePathManager getInstance() {
        return INSTANCE;
    }
    
    public String getDataFolderPath() {
        return dataDirectory.getPath();
    }
    
    public File getInventoryDataFile() {
        return new File(dataDirectory, INVENTORY_DATA_FILE);
    }
    
    public File getAllContainerDataFile() {
        return new File(dataDirectory, ALL_CONTAINER_DATA_FILE);
    }
    
    public File getExecutableFile() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return new File(dataDirectory, WINDOWS_EXE);
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return new File(dataDirectory, MAC_EXE);
        } else {
            return new File(dataDirectory, LINUX_EXE);
        }
    }
    
    public String getExecutableFileName() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return WINDOWS_EXE;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return MAC_EXE;
        } else {
            return LINUX_EXE;
        }
    }
    
    public String getDownloadPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "win.exe";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            return "macos";
        } else {
            return "linux";
        }
    }
    
    public String getVersion() {
        return VERSION;
    }
}