package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InventoryReaderClient implements ClientModInitializer {
    private final Map<String, Map<String, Integer>> allInventoryData = new HashMap<>();
    private final Map<String, Integer> changesData = new HashMap<>();
    private int tickCounter = 0;
    private static final File DATA_FILE = new File(FilePathManager.DATA_DIR, "inventorydata.json");
    private static final String SERVER_URL = "http://localhost:5000/api/mod/modify-resources";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            client.execute(() -> {
                client.setScreen(new ExeDownloadScreen());
            });
        });
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                if (tickCounter >= 1) {
                    checkInventory(client);
                    tickCounter = 0;
                } else {
                    tickCounter++;
                }
            }
        });

        IrCommandManager.registerCommands();
		
		ReminderManager.initialize();
		
		InventoryReader.LOGGER.info("Initialized Inventory Reader client components");
        
        scheduler.scheduleAtFixedRate(this::sendChangesToServer, 0, 5, TimeUnit.SECONDS);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HttpUtil.shutdown();
            InventoryReader.shutdownServer();
        });
    }

    private void checkInventory(MinecraftClient client) {
        assert client.player != null;
        PlayerInventory inventory = client.player.getInventory();
        if (inventory != null) {
            String title = "Player Inventory";
            saveInventoryContents(inventory, title);
        }
    }

    private void saveInventoryContents(PlayerInventory inventory, String title) {
        Map<String, Integer> containerData = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            String itemName = stack.getName().getString();
            int itemCount = stack.getCount();
            if (!stack.isEmpty()) {
                if (containerData.containsKey(itemName)) {
                    itemCount += containerData.get(itemName);
                }
                containerData.put(itemName, itemCount);
            }
        }

        if (!allInventoryData.containsKey(title)) {
            allInventoryData.put(title, containerData);
        } else {
            compareInventoryData(containerData, title);
        }
        saveDataToFile();
    }

    private void compareInventoryData(Map<String, Integer> newData, String title) {
        Type mapType = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
        Map<String, Map<String, Integer>> saveData = new HashMap<>();
        
        try (FileReader reader = new FileReader(DATA_FILE)) {
            saveData = gson.fromJson(reader, mapType);
            if (saveData == null) {
                saveData = new HashMap<>();
            }
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to read inventory data from file", e);
            saveData = new HashMap<>();
        }
        
        Map<String, Integer> previousData = saveData.getOrDefault(title, new HashMap<>());

        newData.forEach((itemName, newCount) -> {
            int previousCount = previousData.getOrDefault(itemName, 0);
            if (newCount != previousCount) {
                changesData.put(itemName, changesData.getOrDefault(itemName, 0) + newCount - previousCount);
                previousData.put(itemName, newCount);
            }
        });

        for (Map.Entry<String, Integer> entry : previousData.entrySet()) {
            if (!newData.containsKey(entry.getKey()) && entry.getValue() > 0) {
                changesData.put(entry.getKey(), changesData.getOrDefault(entry.getKey(), 0) - entry.getValue());
            }
        }
        previousData.entrySet().removeIf(entry -> !newData.containsKey(entry.getKey()) || entry.getValue() == 0);
        allInventoryData.put(title, previousData);
    }

    private void sendChangesToServer() {
        if (!InventoryReader.serverRunning.get()) {
            return;
        }
        
        if (changesData.isEmpty()) return;
//        HttpUtil.HTTP_EXECUTOR.submit(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String jsonInputString = gson.toJson(changesData);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonInputString, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                InventoryReader.LOGGER.info("POST Response Code :: " + response.statusCode());
                changesData.clear();
            } catch (Exception e) {
                InventoryReader.LOGGER.error("Failed to send data to server", e);
            }
//        });
    }

    private void saveDataToFile() {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            gson.toJson(allInventoryData, writer);
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to save inventory data to file", e);
        }
    }
}