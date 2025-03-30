package inventoryreader.ir;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SackReader {
    Map<String, Integer> sackData = new HashMap<>();

    private static final String SERVER_URL = "http://localhost:5000/api/mod/modify-resources"; // localhost server url

    private static final Set<String> GEMSTONE_RARITIES = new HashSet<>(Arrays.asList("Rough:", "Flawed:", "Fine:", "Flawless:", "Perfect:"));

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    List<String> sackNames = new ArrayList<>();

    private static SackReader instance;

    private static boolean needsReminder = false;

    public static SackReader getInstance() {
        if (instance == null) {
            instance = new SackReader();
        }
        return instance;
    }
    
    public void clearSacks() {
        InventoryReader.LOGGER.info("Clearing all sack data");
        sackNames.clear();
        sackData.clear();
        
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"reset\": true}", StandardCharsets.UTF_8))
                    .build();
                    
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            InventoryReader.LOGGER.info("Sack reset notification sent to server, status: " + response.statusCode());
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Failed to send sack reset notification", e);
        }
    }

    public static void setNeedsReminder(boolean state) {
        needsReminder = state;
    }
    
    public static boolean getNeedsReminder() {
        return needsReminder;
    }

    public void saveLoreComponents(ScreenHandler handler, String title) {
        if (sackNames.contains(title)) {
            return;
        } else {
            sackNames.add(title);
            setNeedsReminder(false);
            
            SendingManager.unblockDataSend();
        }

        if (title.contains("Gemstone")) {
            saveGemstoneSackData(handler);
            return;
        }
        List<Slot> slots = handler.slots;
        InventoryReader.LOGGER.info("Number of slots: " + slots.size());
        int slotsToIterate = slots.size() - 36;
        for (int i = 0; i < slotsToIterate; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String itemName = stack.getName().getString();
                LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
                if (loreComponent != null) {
                    List<Text> loreLines = loreComponent.lines();
                    for (Text line : loreLines) {
                        String l = line.getString();
                        if (l.contains("Stored:")) {
                            String[] parts = l.split("/");
                            String[] itemCountStr = parts[0].split("Stored: ");
                            String itemCountCleaned = itemCountStr[1].trim().replace(",", "");
                            int itemCount = Integer.parseInt(itemCountCleaned);
                            sackData.put(itemName, itemCount);
                        }
                    }
                } else {
                    InventoryReader.LOGGER.info("No lore component found for item.");
                }
            }
        }
        sendDataToServer();
    }

    private void saveGemstoneSackData(ScreenHandler handler){
        List<Slot> slots = handler.slots;
        InventoryReader.LOGGER.info("Number of slots: " + slots.size());
        int slotsToIterate = slots.size() - 36; // this excludes player inventory, excludes the last 36 slots
        for (int i = 0; i < slotsToIterate; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                String itemName = stack.getName().getString();
                if (!itemName.contains("Gemstone")) {
                    continue;
                }
                LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
                if (loreComponent != null) {
                    List<Text> loreLines = loreComponent.lines();
                    for (Text line : loreLines) {
                        String l = line.getString();
                        String[] parts = l.split(" ");
//                        InventoryReader.LOGGER.info("Parts: " + Arrays.toString(parts));
                        if (parts.length < 4) {
                            continue;
                        }
                        String rarity = parts[1];
                        if (GEMSTONE_RARITIES.contains(rarity)) {
                            String itemGemstone = rarity.substring(0, rarity.length()-1)  + " " + itemName.substring(0,itemName.length()-1);
                            Integer itemCount = Integer.parseInt(parts[2].trim().replace(",", ""));
//                            InventoryReader.LOGGER.info("Item: " + itemGemstone + ", Count: " + itemCount);
                            sackData.put(itemGemstone, itemCount);
                        }
                    }
                } else {
                    InventoryReader.LOGGER.info("No lore component found for item.");
                }
            }
        }
        sendDataToServer();
    }

    public void sendDataToServer() {
        HttpUtil.HTTP_EXECUTOR.submit(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String jsonInputString = gson.toJson(sackData);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonInputString, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                InventoryReader.LOGGER.info("POST Response Code :: " + response.statusCode());
//            InventoryReader.LOGGER.info("Response Body :: " + response.body());

                sackData.clear();
            } catch (Exception e) {
                InventoryReader.LOGGER.error("Failed to send data to server", e);
            }
        });
    }
}