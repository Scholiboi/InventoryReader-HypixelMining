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

public class InventoryReader implements ModInitializer {
	public static final String MOD_ID = "ir";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String SERVER_URL = "http://localhost:5000/mod/reset";

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Inventory Reader");
		clearAllserverData();
		clearAlljsonData();
	}

	private void clearAlljsonData(){
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
	public void clearAllserverData(){
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
	}
}