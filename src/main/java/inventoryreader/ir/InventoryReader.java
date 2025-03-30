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

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static Process serverProcess;
	private static final String SERVER_URL = "http://localhost:5000/api/mod/reset";

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Inventory Reader");
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
		File exeFile = new File("hypixel_dwarven_forge-v1.1.1.exe");
		if (!exeFile.exists()) {
			LOGGER.info("the .exe not found; downloading...");
			try {
				java.net.URI downloadUri = new java.net.URI("https://github.com/Scholiboi/hypixel-forge/releases/download/v1.1.1/hypixel_dwarven_forge-v1.1.1.exe");
				java.net.URL downloadUrl = downloadUri.toURL();
				try (java.io.InputStream in = downloadUrl.openStream();
					 java.io.FileOutputStream fos = new java.io.FileOutputStream(exeFile)) {
					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = in.read(buffer)) != -1) {
						fos.write(buffer, 0, bytesRead);
					}
				}
				LOGGER.info("Download complete.");
			} catch (Exception e) {
				LOGGER.error("Failed to download exe", e);
				return;
			}
		}
	
		try {
			ProcessBuilder builder = new ProcessBuilder("hypixel_dwarven_forge-v1.1.1.exe").inheritIO();
			serverProcess = builder.start();
			LOGGER.info("External exe started successfully.");
		} catch (Exception e) {
			LOGGER.error("Failed to launch exe", e);
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

	public static void clearAlljsonData(){
		String DATA_FILE_generic = "allcontainerData.json";
		String DATA_FILE_inventory = "inventorydata.json";

		File file_generic = new File(DATA_FILE_generic);
		File file_inventory = new File(DATA_FILE_inventory);

		try{
			if(file_generic.exists()){
				try(FileWriter writer = new FileWriter(DATA_FILE_generic)){
					writer.write("");
				}
			}else{
				file_generic.createNewFile();
			}

			if (file_inventory.exists()){
				try(FileWriter writer = new FileWriter(DATA_FILE_inventory)){
					writer.write("");
				}
			}else{
				file_inventory.createNewFile();
			}
		}catch(Exception e){
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