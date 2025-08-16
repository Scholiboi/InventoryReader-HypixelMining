package inventoryreader.ir;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryReader implements ModInitializer{
    public static final String MOD_ID = "ir";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final AtomicBoolean serverRunning = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        LOGGER.info("Skyblock Resource Calculator Mod initializing...");
        cleanupLegacyFiles();
        FilePathManager.initializeDirectories();
    }

    private void cleanupLegacyFiles() {
        LOGGER.info("Cleaning up legacy files...");

        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File modDir = FilePathManager.MOD_DIR;
        
        String[] oldExeVersions = {
            "hypixel_dwarven_forge-v1.0.0.exe",
            "hypixel_dwarven_forge-v1.1.0.exe",
            "hypixel_dwarven_forge-v1.1.1.exe",
            "hypixel_dwarven_forge-v1.1.2.exe",
            "hypixel_dwarven_forge-v1.1.3.exe",
            "hypixel_dwarven_forge-v1.1.4.exe"
        };
    
        String[] oldJsonFiles = {
            "allcontainerData.json",
            "inventorydata.json"
        };

        for (String exeName : oldExeVersions) {
            cleanupFile(new File(gameDir, exeName));
        }
    
        for (String jsonName : oldJsonFiles) {
            cleanupFile(new File(gameDir, jsonName));
        }

        for (String exeName : oldExeVersions) {
            cleanupFile(new File(modDir, exeName));
        }
        
        LOGGER.info("Legacy file cleanup complete");
    }
    
    private void cleanupFile(File file) {
        if (file.exists()) {
            file.delete();
        }
    }
}