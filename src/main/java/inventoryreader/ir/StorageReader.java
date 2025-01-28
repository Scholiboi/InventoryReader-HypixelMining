package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageReader {
    private static final String DATA_FILE = "allcontainerData.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final StorageReader INSTANCE = new StorageReader();

    private StorageReader() {}

    public static StorageReader getInstance() {
        return INSTANCE;
    }

    private final Map<String, Integer> changesData = new HashMap<>();

    Map<String, Map<String, Integer>> allcontainerData = new HashMap<>();

    private static final String SERVER_URL = "http://localhost:5000/mod/modify-resources";

    public void saveContainerContents(ScreenHandler handler, String title) {
        readDataFromFile();

        if (!title.contains("Backpack") && !title.contains("Ender Chest") && !title.contains("The Forge")) {
            return;
        }

        if (allcontainerData.containsKey(title)) {
            compareContainerData(handler, title);
            return;
        }

        List<Slot> slots = handler.slots;
        int slotsToIterate = slots.size() - 36;
        Map<String, Integer> newData = new HashMap<>();

        for (int i = 0; i < slotsToIterate; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String itemName = stack.getName().getString();
                int itemCount = stack.getCount();
//                InventoryReader.LOGGER.info("Item: {}, Count: 0 -> {}", itemName, itemCount);
                newData.put(itemName, newData.getOrDefault(itemName, 0) + itemCount);
            }
        }
        allcontainerData.put(title, newData);
        saveDataToFile();
        sendChangesToServer(newData);
    }

    public void compareContainerData(ScreenHandler handler, String title) {
        List<Slot> slots = handler.slots;
        int slotsToIterate = slots.size() - 36;

        Map<String, Integer> previousData = allcontainerData.get(title);

        Map<String, Integer> newData = new HashMap<>();

        for (int i = 0; i < slotsToIterate; i++) {

            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String itemName = stack.getName().getString();
                int itemCount = stack.getCount();
                newData.put(itemName, newData.getOrDefault(itemName, 0) + itemCount);
            }
        }

        for (Map.Entry<String, Integer> entry : newData.entrySet()) {
            String itemName = entry.getKey();
            int newCount = entry.getValue();
            int previousCount = previousData.getOrDefault(itemName, 0);

            if (newCount != previousCount) {
//                InventoryReader.LOGGER.info("Item: {}, Count: {} -> {}", itemName, previousCount, newCount);
                changesData.put(itemName, newCount-previousCount);
                previousData.put(itemName, newCount);
            }
        }

        for (Map.Entry<String, Integer> entry : previousData.entrySet()) {
            if (!newData.containsKey(entry.getKey()) && entry.getValue() > 0) {
//                InventoryReader.LOGGER.info("Item: {}, Count: {} -> 0", entry.getKey(), entry.getValue());
                changesData.put(entry.getKey(), -entry.getValue());
            }
        }

        // Remove items not present or with zero count
        previousData.entrySet().removeIf(entry -> !newData.containsKey(entry.getKey()) || entry.getValue() == 0);

        allcontainerData.put(title, previousData);
        saveDataToFile();
        sendChangesToServer(changesData);
        changesData.clear();
    }

    public void readDataFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
            Map<String, Map<String, Integer>> data = gson.fromJson(reader, type);
            if (data != null) {
                allcontainerData = data;
            }
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to read data from file", e);
        }
    }

    public void saveDataToFile() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            gson.toJson(allcontainerData, writer);
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to save data to file", e);
        }
    }

    private void sendChangesToServer(Map<String, Integer> data){
        if (data.isEmpty()) {
            return;
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonInputString = gson.toJson(data);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonInputString, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            InventoryReader.LOGGER.info("POST Response Code :: " + response.statusCode());
//            InventoryReader.LOGGER.info("Response Body :: " + response.body());

        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to send data to server", e);
        }
    }
}