package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ScheduledExecutorService;
// import java.util.concurrent.TimeUnit;

public class InventoryReaderClient implements ClientModInitializer {
    private final Map<String, Integer> changesData = new HashMap<>();
    private int tickCounter = 0;
    private static final File DATA_FILE = new File(FilePathManager.DATA_DIR, "inventorydata.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static KeyBinding openSandboxViewerKey;
    private static KeyBinding openWidgetCustomizationKey;
    public static boolean shouldOpenSandboxViewer = false;
    public static boolean shouldOpenWidgetCustomization = false;

    @Override
    public void onInitializeClient() {
        openSandboxViewerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ir.opensandboxviewer",
            GLFW.GLFW_KEY_V,
            "Skyblock Mining Resource Reader"
        ));
        
        openWidgetCustomizationKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ir.openwidgetcustomization",
            GLFW.GLFW_KEY_B,
            "Skyblock Mining Resource Reader"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openSandboxViewerKey.wasPressed() || shouldOpenSandboxViewer) {
                client.execute(() -> client.setScreen(new SandboxViewer()));
                shouldOpenSandboxViewer = false;
            }
            
            if (openWidgetCustomizationKey.wasPressed() || shouldOpenWidgetCustomization) {
                client.execute(() -> client.setScreen(new WidgetCustomizationMenu()));
                shouldOpenWidgetCustomization = false;
            }
            
            if (client.player != null && client.world != null) {
                if (tickCounter >= 1) {
                    checkInventory(client);
                    tickCounter = 0;
                } else {
                    tickCounter++;
                }
            }
        });

		ReminderManager.initialize();
        WelcomeManager.initialize();
		
		InventoryReader.LOGGER.info("Initialized Inventory Reader client components");
        
        // scheduler.scheduleAtFixedRate(this::saveData, 0, 5, TimeUnit.SECONDS);
        SandboxWidget.getInstance();
    }

    private Map<String, Map<String, Integer>> loadAllInventoryDataFromFile() {
        Type mapType = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
        try (FileReader reader = new FileReader(DATA_FILE)) {
            Map<String, Map<String, Integer>> data = gson.fromJson(reader, mapType);
            return data != null ? data : new HashMap<>();
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to read inventory data from file", e);
            return new HashMap<>();
        }
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
        Map<String, Integer> currentInventoryData = new HashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            String itemName = stack.getName().getString();
            int itemCount = stack.getCount();
            if (!stack.isEmpty()) {
                if (currentInventoryData.containsKey(itemName)) {
                    itemCount += currentInventoryData.get(itemName);
                }
                currentInventoryData.put(itemName, itemCount);
            }
        }

        Map<String, Map<String, Integer>> fileSaveInventoryData = loadAllInventoryDataFromFile();
        if (!fileSaveInventoryData.containsKey(title)) {
            fileSaveInventoryData.put(title, currentInventoryData);
            changesData.putAll(currentInventoryData);
        } else {
            fileSaveInventoryData = compareInventoryData(currentInventoryData, title, fileSaveInventoryData);
        }
        saveDataToFile(fileSaveInventoryData);
    }

    private Map<String, Map<String, Integer>> compareInventoryData(Map<String, Integer> newData, String title, Map<String, Map<String, Integer>> fileSaveInventoryData) {
        Map<String, Integer> previousData = fileSaveInventoryData.getOrDefault(title, new HashMap<>());

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
        fileSaveInventoryData.put(title, previousData);
        return fileSaveInventoryData;
    }

    private void saveDataToFile(Map<String, Map<String, Integer>> allInventoryData) {
        try (FileWriter writer = new FileWriter(DATA_FILE)) {
            gson.toJson(allInventoryData, writer);
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to save inventory data to file", e);
        }
        ResourcesManager RESOURCES_MANAGER = ResourcesManager.getInstance();
        RESOURCES_MANAGER.saveData(changesData);
        changesData.clear();
    }
}
