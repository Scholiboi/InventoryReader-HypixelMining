package inventoryreader.ir;

import net.fabricmc.api.ModInitializer;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InventoryReader implements ModInitializer{
    public static final String MOD_ID = "ir";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static Process serverProcess;
    private static final String SERVER_URL = "http://localhost:5000/api/mod/reset";

    @Override
    public void onInitialize() {
        LOGGER.info("IR Mod initializing...");

        FilePathManager.initializeDirectories();

        LOGGER.info("Initializing Inventory Reader");
        FilePathManager.initializeDirectories();
        LOGGER.info("File system initialized");
        
        registerShutdownHook();
		
        launchOrFetchExe();
        clearAllserverData();
        clearAlljsonData();
    }

    private void registerShutdownHook() {
        LOGGER.info("Registering JVM shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("JVM is shutting down - cleaning up server process");
            try {
                HttpUtil.shutdown();
                shutdownServer();
                LOGGER.info("Server shutdown completed successfully");
            } catch (Exception e) {
                LOGGER.error("Error during server shutdown", e);
            }
        }));
    }

    public static void launchOrFetchExe() {
        try {
            File exeFile = FilePathManager.getExecutablePath();
            if (exeFile.exists()) {
                ProcessBuilder builder = new ProcessBuilder(exeFile.getAbsolutePath());
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
                serverProcess = builder.start();
            } else {
                LOGGER.warn("Executable not found. The GUI will handle downloading it.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start server executable", e);
        }
    }

    public static void shutdownServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            ProcessHandle handle = serverProcess.toHandle();
            handle.descendants().forEach(child -> {
                child.destroy();
                try {
                    if (!child.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS).isAlive()) {
                    } else {
                        child.destroyForcibly();
                    }
                } catch (Exception e) {
                    child.destroyForcibly();
                }
            });
            serverProcess.destroy();
            try {
                if (!serverProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    serverProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                serverProcess.destroyForcibly();
            }
        }
    }

    public static void clearAlljsonData() {
        File file_generic = new File(FilePathManager.DATA_DIR, "allcontainerData.json");
        File file_inventory = new File(FilePathManager.DATA_DIR, "inventorydata.json");

        try {
            if (file_generic.exists()) {
                try (FileWriter writer = new FileWriter(file_generic)) {
                    writer.write("");
                }
            } else {
                file_generic.createNewFile();
            }

            if (file_inventory.exists()) {
                try (FileWriter writer = new FileWriter(file_inventory)) {
                    writer.write("");
                }
            } else {
                file_inventory.createNewFile();
            }
        } catch (Exception e) {
            LOGGER.error("Error while clearing json data: " + e);
        }
    }
    
    public static void clearAllserverData(){
        HttpUtil.HTTP_EXECUTOR.submit(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                LOGGER.info("Initialization POST Response Code :: " + response.statusCode());
            } catch (Exception e) {
                LOGGER.error("Failed to send initialization request to server", e);
            }
        });
    }
}