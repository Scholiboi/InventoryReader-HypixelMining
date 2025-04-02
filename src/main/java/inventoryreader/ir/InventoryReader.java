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
	private static final String VERSION = FilePathManager.getInstance().getVersion();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static Process serverProcess;
	private static final String SERVER_URL = "http://localhost:5000/api/mod/reset";

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Inventory Reader");
		FilePathManager.getInstance();
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

	private void launchOrFetchExe() {
		FilePathManager pathManager = FilePathManager.getInstance();
		String osName = System.getProperty("os.name").toLowerCase();
		File exeFile = pathManager.getExecutableFile();
		String exeName = pathManager.getExecutableFileName();
		String downloadPath = pathManager.getDownloadPath();
		
		if (!exeFile.exists()) {
			LOGGER.info("Executable not found for " + osName + "; downloading...");
			try {
				String downloadUrl = "https://github.com/Scholiboi/hypixel-forge/releases/download/" + 
									VERSION + "/hypixel_dwarven_forge-" + VERSION + "-" + downloadPath;
				java.net.URI downloadUri = new java.net.URI(downloadUrl);
				java.net.URL downloadUrlObj = downloadUri.toURL();
				
				exeFile.getParentFile().mkdirs();
				
				try (java.io.InputStream in = downloadUrlObj.openStream();
					 java.io.FileOutputStream fos = new java.io.FileOutputStream(exeFile)) {
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = in.read(buffer)) != -1) {
						fos.write(buffer, 0, bytesRead);
					}
					}
				
				// Make the file executable on Unix-like systems
				if (!osName.contains("win")) {
					exeFile.setExecutable(true);
				}
				
				LOGGER.info("Download complete.");
			} catch (Exception e) {
				LOGGER.error("Failed to download executable", e);
				return;
			}
		}
	
		try {
			ProcessBuilder builder = new ProcessBuilder(exeFile.getAbsolutePath()).inheritIO();
			serverProcess = builder.start();
			LOGGER.info("External executable started successfully.");
		} catch (Exception e) {
			LOGGER.error("Failed to launch executable", e);
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
		FilePathManager pathManager = FilePathManager.getInstance();
		File file_generic = pathManager.getAllContainerDataFile();
		File file_inventory = pathManager.getInventoryDataFile();

		try {
			if(file_generic.exists()) {
				try(FileWriter writer = new FileWriter(file_generic)) {
					writer.write("");
				}
			} else {
				// Create parent directory if needed
				file_generic.getParentFile().mkdirs();
				file_generic.createNewFile();
			}

			if(file_inventory.exists()) {
				try(FileWriter writer = new FileWriter(file_inventory)) {
					writer.write("");
				}
			} else {
				// Create parent directory if needed
				file_inventory.getParentFile().mkdirs();
				file_inventory.createNewFile();
			}
		} catch(Exception e) {
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