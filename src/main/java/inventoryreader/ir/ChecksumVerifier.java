package inventoryreader.ir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ChecksumVerifier {
    private static final String HASH_ALGORITHM = "SHA-256";
    
    // Hardcoded values for the executable and its expected checksum
    public static final String EXE_FILENAME = "hypixel_dwarven_forge-v1.1.2.exe";
    public static final String EXPECTED_CHECKSUM = "623f5acf0d1811cb5e93d038ade4c50df13011551aa14ab5526ee64d04ab9c9e";

    //use paths from FilePathManager
    public static boolean verify(File file) {
        try {
            String filename = file.getName();
            String expectedChecksum = getStoredChecksum(filename);
            
            if (expectedChecksum == null) {
                if (filename.equals(EXE_FILENAME)) {
                    expectedChecksum = EXPECTED_CHECKSUM;
                    InventoryReader.LOGGER.info("Using hardcoded value for initial verification");
                } else {
                    InventoryReader.LOGGER.error("No checksum defined for: {}", filename);
                    return false;
                }
            }
            
            String calculatedChecksum = calculateChecksum(file);
            boolean match = expectedChecksum.equalsIgnoreCase(calculatedChecksum);
            
            if (!match) {
                InventoryReader.LOGGER.warn("Checksum mismatch: {} (expected: {}, actual: {})", 
                           filename, expectedChecksum, calculatedChecksum);
            }
            
            return match;
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Verification failed: {}", file.getName(), e);
            return false;
        }
    }

    public static boolean saveVerifiedHash(File file) {
        try {
            String filename = file.getName();
            String checksum = calculateChecksum(file);
            
            Map<String, String> checksums = loadChecksums();
            checksums.put(filename, checksum);
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(FilePathManager.CHECKSUMS_JSON)) {
                gson.toJson(checksums, writer);
            }
            
            InventoryReader.LOGGER.info("Saved hash for: {}", filename);
            return true;
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to save hash", e);
            return false;
        }
    }
    
    private static String getStoredChecksum(String filename) {
        Map<String, String> checksums = loadChecksums();
        return checksums.get(filename);
    }
    
    private static Map<String, String> loadChecksums() {
        try {
            Gson gson = new Gson();
            try (FileReader reader = new FileReader(FilePathManager.CHECKSUMS_JSON)) {
                return gson.fromJson(reader, new TypeToken<HashMap<String, String>>(){}.getType());
            }
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to load checksums", e);
            return new HashMap<>();
        }
    }
    
    public static String calculateChecksum(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] calculatedDigest = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : calculatedDigest) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
    
    public static boolean handleFailedVerification(File file) {
        InventoryReader.LOGGER.error("Security alert: File failed verification: {}", file.getName());
        
        try {
            File quarantinedFile = new File(FilePathManager.QUARANTINE_DIR, file.getName() + ".quarantined");
            Files.move(file.toPath(), quarantinedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            logSecurityIncident(file.getName());
            return true;
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to quarantine file", e);
            
            boolean deleted = file.delete();
            return deleted;
        }
    }

    private static void logSecurityIncident(String filename) {
        try {
            File securityLog = new File(FilePathManager.LOGS_DIR + File.separator + "security_incidents.log");
            StringBuilder logEntry = new StringBuilder();
            logEntry.append(new java.util.Date().toString())
                   .append(" - Verification failed: ")
                   .append(filename)
                   .append("\n");
            
            Files.write(
                securityLog.toPath(), 
                logEntry.toString().getBytes(), 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to log incident", e);
        }
    }
}