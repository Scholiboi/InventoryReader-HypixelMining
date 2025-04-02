package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    private static final File CONFIG_FILE = new File(FilePathManager.MOD_DIR, "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ConfigData config;
    
    static {
        loadConfig();
    }
    
    public static class ConfigData {
        public boolean alwaysConfirmDownloads = true;
        public boolean showVerificationReports = true;
    }
    
    public static boolean shouldAlwaysConfirmDownloads() {
        return config.alwaysConfirmDownloads;
    }
    
    public static boolean shouldShowVerificationReports() {
        return config.showVerificationReports;
    }
    
    public static void setAlwaysConfirmDownloads(boolean value) {
        config.alwaysConfirmDownloads = value;
        saveConfig();
    }
    
    public static void setShowVerificationReports(boolean value) {
        config.showVerificationReports = value;
        saveConfig();
    }
    
    public static void loadConfig() {
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    config = GSON.fromJson(reader, ConfigData.class);
                }
            }
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to load config", e);
        }
        
        if (config == null) {
            config = new ConfigData();
            saveConfig();
        }
    }
    
    public static void saveConfig() {
        try {
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to save config", e);
        }
    }
}