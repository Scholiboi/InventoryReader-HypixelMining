package inventoryreader.ir;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
// import com.google.gson.Gson;
// import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.*;

public class SackReader {
    private static final Set<String> GEMSTONE_RARITIES = new HashSet<>(Arrays.asList("Rough:", "Flawed:", "Fine:", "Flawless:", "Perfect:"));
    private static final File SACK_NAMES_FILE = new File(FilePathManager.DATA_DIR, "sackNames.txt");
    private static SackReader instance;
    private static boolean needsReminder = false;
    private static final ResourcesManager RESOURCES_MANAGER = ResourcesManager.getInstance();

    public static SackReader getInstance() {
        if (instance == null) {
            instance = new SackReader();
        }
        return instance;
    }
    
    public List<String> loadSackNames() {
        List<String> sackNames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(SACK_NAMES_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    sackNames.add(line.trim());
                }
            }
            InventoryReader.LOGGER.info("Loaded {} sack names from file", sackNames.size());
            
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to load sack names from file", e);
        }
        return sackNames;
    }

    public void saveSackNames(List<String> sackNames) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SACK_NAMES_FILE))) {
            for (String sackName : sackNames) {
                writer.write(sackName);
                writer.newLine();
            }
            InventoryReader.LOGGER.info("Saved {} sack names to file", sackNames.size());
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to save sack names to file", e);
        }
    }

    public static void setNeedsReminder(boolean state) {
        needsReminder = state;
    }
    
    public static boolean getNeedsReminder() {
        return needsReminder;
    }

    public void saveLoreComponents(ScreenHandler handler, String title) {
        List<String> sackNames = loadSackNames();
        if (sackNames.contains(title)) {
            return;
        } else {
            sackNames.add(title);
            setNeedsReminder(false);
            
            saveSackNames(sackNames);
            
            SendingManager.unblockDataSend();
        }

        Map<String, Integer> sackData = new HashMap<>();
        if (title.contains("Gemstone")) {
            saveGemstoneSackData(handler, sackData);
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
        RESOURCES_MANAGER.saveData(sackData);
    }

    private void saveGemstoneSackData(ScreenHandler handler, Map<String, Integer> sackData){
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
                        if (parts.length < 4) {
                            continue;
                        }
                        String rarity = parts[1];
                        if (GEMSTONE_RARITIES.contains(rarity)) {
                            String itemGemstone = rarity.substring(0, rarity.length()-1)  + " " + itemName.substring(0,itemName.length()-1);
                            Integer itemCount = Integer.parseInt(parts[2].trim().replace(",", ""));
                            sackData.put(itemGemstone, itemCount);
                        }
                    }
                } else {
                    InventoryReader.LOGGER.info("No lore component found for item.");
                }
            }
        }
        RESOURCES_MANAGER.saveData(sackData);
    }
}