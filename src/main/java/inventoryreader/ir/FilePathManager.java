package inventoryreader.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;

public class FilePathManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryReader.MOD_ID);

    public static final File MOD_DIR = new File(FabricLoader.getInstance().getGameDir().toFile(), "ir-data");
    
    public static final File LOGS_DIR = new File(MOD_DIR, "logs");
    public static final File QUARANTINE_DIR = new File(MOD_DIR, "quarantine");
    public static final File DATA_DIR = new File(MOD_DIR, "data");

    public static final File CHECKSUMS_JSON = new File(DATA_DIR, "checksums.json");
    public static final File SECURITY_LOG = new File(LOGS_DIR, "security_incidents.log");
    
    static {
        initializeDirectories();
    }
    
    public static void initializeDirectories() {
        createDirectory(MOD_DIR);
        createDirectory(LOGS_DIR);
        createDirectory(QUARANTINE_DIR);
        createDirectory(DATA_DIR);
        
        if (!CHECKSUMS_JSON.exists()) {
            try {
                CHECKSUMS_JSON.createNewFile();
                writeEmptyJson(CHECKSUMS_JSON);
                LOGGER.info("Created checksums file: {}", CHECKSUMS_JSON.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to create checksums file", e);
            }
        }
    }
    
    private static void createDirectory(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
            LOGGER.info("Created directory: {}", directory.getAbsolutePath());
        }
    }
    
    private static void writeEmptyJson(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{}");
        } catch (IOException e) {
            LOGGER.error("Failed to initialize JSON file: {}", file.getAbsolutePath(), e);
        }
    }

    public static File getExecutablePath() {
        return new File(MOD_DIR, ChecksumVerifier.EXE_FILENAME);
    }
    
    public static File getQuarantineFile(String filename) {
        return new File(QUARANTINE_DIR, filename + ".quarantined");
    }
}