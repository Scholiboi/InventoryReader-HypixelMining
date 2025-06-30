package inventoryreader.ir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

public class FilePathManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryReader.MOD_ID);

    public static final File MOD_DIR = new File(FabricLoader.getInstance().getGameDir().toFile(), ".ir-data");
    public static final File DATA_DIR = new File(MOD_DIR, "data");
    private static final File file_generic = new File(FilePathManager.DATA_DIR, "allcontainerData.json");
    private static final File file_inventory = new File(FilePathManager.DATA_DIR, "inventorydata.json");
    private static final File file_resources = new File(FilePathManager.DATA_DIR, "resources.json");
    private static final File SACK_NAMES_FILE = new File(FilePathManager.DATA_DIR, "sackNames.txt");
    public static final File file_widget_config = new File(FilePathManager.DATA_DIR, "widget_config.json");
    public static final File FORGING_JSON = new File(FilePathManager.DATA_DIR, "forging.json");
    public static final File GEMSTONE_RECIPES_JSON = new File(FilePathManager.DATA_DIR, "gemstone_recipes.json");

    static {
        initializeDirectories();
    }

    public static void initializeDirectories() {
        createDirectory(MOD_DIR);
        createDirectory(DATA_DIR);
        initializeFiles();
    }

    public static void reInitializeFiles() {
        reinitializeFiles();
    }

    private static void createDirectory(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
            LOGGER.info("Created directory: {}", directory.getAbsolutePath());
        }
    }

    private static void initializeFiles() {
        if (!file_generic.exists()) {
            try {
                file_generic.createNewFile();
                LOGGER.info("Created file: {}", file_generic.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.error("Failed to create file: {}", file_generic.getAbsolutePath(), e);
            }
        }

        if (!file_inventory.exists()) {
            try {
                file_inventory.createNewFile();
                // LOGGER.info("Created file: {}", file_inventory.getAbsolutePath());
            } catch (IOException e) {
                // LOGGER.error("Failed to create file: {}", file_inventory.getAbsolutePath(), e);
            }
        }
        if (!SACK_NAMES_FILE.exists()) {
            try {
                SACK_NAMES_FILE.createNewFile();
                // LOGGER.info("Created file: {}", SACK_NAMES_FILE.getAbsolutePath());
            } catch (IOException e) {
                // LOGGER.error("Failed to create file: {}", SACK_NAMES_FILE.getAbsolutePath(), e);
            }
        }

        if (!file_resources.exists()) {
            initializeResourcesData(file_resources);
        }
        
        if (!file_widget_config.exists()) {
            initializeWidgetConfigData(file_widget_config);
        }
        if (!FORGING_JSON.exists() || !GEMSTONE_RECIPES_JSON.exists()) 
        RecipeFileGenerator.initializeRecipeFiles();
    }

    private static void reinitializeFiles() {
        if (file_generic.exists()) {
            file_generic.delete();
        }
        if (file_inventory.exists()) {
            file_inventory.delete();
        }
        if (file_resources.exists()) {
            file_resources.delete();
        }
        if (file_widget_config.exists()) {
            file_widget_config.delete();
        }
        if (SACK_NAMES_FILE.exists()) {
            SACK_NAMES_FILE.delete();
        }
        initializeFiles();
    }

    private static void initializeResourcesData(File file) {
        Map<String, Integer> resources = new LinkedHashMap<>();
        
        String[] items = {
            "Rough Amber Gemstone",
            "Flawed Amber Gemstone",
            "Fine Amber Gemstone",
            "Flawless Amber Gemstone",
            "Perfect Amber Gemstone",
            "Rough Amethyst Gemstone",
            "Flawed Amethyst Gemstone",
            "Fine Amethyst Gemstone",
            "Flawless Amethyst Gemstone",
            "Perfect Amethyst Gemstone",
            "Rough Aquamarine Gemstone",
            "Flawed Aquamarine Gemstone",
            "Fine Aquamarine Gemstone",
            "Flawless Aquamarine Gemstone",
            "Perfect Aquamarine Gemstone",
            "Rough Citrine Gemstone",
            "Flawed Citrine Gemstone",
            "Fine Citrine Gemstone",
            "Flawless Citrine Gemstone",
            "Perfect Citrine Gemstone",
            "Rough Jade Gemstone",
            "Flawed Jade Gemstone",
            "Fine Jade Gemstone",
            "Flawless Jade Gemstone",
            "Perfect Jade Gemstone",
            "Rough Jasper Gemstone",
            "Flawed Jasper Gemstone",
            "Fine Jasper Gemstone",
            "Flawless Jasper Gemstone",
            "Perfect Jasper Gemstone",
            "Rough Onyx Gemstone",
            "Flawed Onyx Gemstone",
            "Fine Onyx Gemstone",
            "Flawless Onyx Gemstone",
            "Perfect Onyx Gemstone",
            "Rough Opal Gemstone",
            "Flawed Opal Gemstone",
            "Fine Opal Gemstone",
            "Flawless Opal Gemstone",
            "Perfect Opal Gemstone",
            "Rough Peridot Gemstone",
            "Flawed Peridot Gemstone",
            "Fine Peridot Gemstone",
            "Flawless Peridot Gemstone",
            "Perfect Peridot Gemstone",
            "Rough Ruby Gemstone",
            "Flawed Ruby Gemstone",
            "Fine Ruby Gemstone",
            "Flawless Ruby Gemstone",
            "Perfect Ruby Gemstone",
            "Rough Sapphire Gemstone",
            "Flawed Sapphire Gemstone",
            "Fine Sapphire Gemstone",
            "Flawless Sapphire Gemstone",
            "Perfect Sapphire Gemstone",
            "Rough Topaz Gemstone",
            "Flawed Topaz Gemstone",
            "Fine Topaz Gemstone",
            "Flawless Topaz Gemstone",
            "Perfect Topaz Gemstone",
            "Refined Diamond",
            "Enchanted Diamond Block",
            "Enchanted Diamond",
            "Diamond",
            "Refined Mithril",
            "Enchanted Mithril",
            "Mithril",
            "Refined Titanium",
            "Enchanted Titanium",
            "Titanium",
            "Refined Umber",
            "Enchanted Umber",
            "Umber",
            "Refined Tungsten",
            "Enchanted Tungsten",
            "Tungsten",
            "Glacite Jewel",
            "Bejeweled Handle",
            "Drill Motor",
            "Treasurite",
            "Enchanted Iron Block",
            "Enchanted Iron",
            "Iron Ingot",
            "Enchanted Redstone Block",
            "Enchanted Redstone",
            "Redstone",
            "Golden Plate",
            "Enchanted Gold Block",
            "Enchanted Gold",
            "Gold Ingot",
            "Fuel Canister",
            "Enchanted Coal Block",
            "Enchanted Coal",
            "Coal",
            "Gemstone Mixture",
            "Sludge Juice",
            "Glacite Amalgamation",
            "Enchanted Glacite",
            "Glacite",
            "Mithril Plate",
            "Tungsten Plate",
            "Umber Plate",
            "Perfect Plate",
            "Mithril Drill SX-R226",
            "Mithril Drill SX-R326",
            "Ruby Drill TX-15",
            "Gemstone Drill LT-522",
            "Topaz Drill KGR-12",
            "Magma Core",
            "Jasper Drill X",
            "Polished Topaz Rod",
            "Titanium Drill DR-X355",
            "Titanium Drill DR-X455",
            "Titanium Drill DR-X555",
            "Titanium Drill DR-X655",
            "Plasma",
            "Corleonite",
            "Chisel",
            "Reinforced Chisel",
            "Glacite-Plated Chisel",
            "Perfect Chisel",
            "Divan's Drill",
            "Divan's Alloy",
            "Mithril Necklace",
            "Mithril Cloak",
            "Mithril Belt",
            "Mithril Gauntlet",
            "Titanium Necklace",
            "Titanium Cloak",
            "Titanium Belt",
            "Titanium Gauntlet",
            "Refined Mineral",
            "Titanium Talisman",
            "Titanium Ring",
            "Titanium Artifact",
            "Titanium Relic",
            "Divan's Powder Coating",
            "Glossy Gemstone",
            "Divan Fragment",
            "Helmet Of Divan",
            "Chestplate Of Divan",
            "Leggings Of Divan",
            "Boots Of Divan",
            "Amber Necklace",
            "Sapphire Cloak",
            "Jade Belt",
            "Amethyst Gauntlet",
            "Gemstone Chamber",
            "Worm Membrane",
            "Dwarven Handwarmers",
            "Dwarven Metal Talisman",
            "Pendant Of Divan",
            "Shattered Locket",
            "Relic Of Power",
            "Artifact Of Power",
            "Ring Of Power",
            "Talisman Of Power",
            "Diamonite",
            "Pocket Iceberg",
            "Petrified Starfall",
            "Starfall",
            "Pure Mithril",
            "Dwarven Geode",
            "Enchanted Cobblestone",
            "Cobblestone",
            "Titanium Tesseract",
            "Enchanted Lapis Block",
            "Enchanted Lapis Lazuli",
            "Lapis Lazuli",
            "Gleaming Crystal",
            "Scorched Topaz",
            "Enchanted Hard Stone",
            "Hard Stone",
            "Amber Material",
            "Frigid Husk",
            "Starfall Seasoning",
            "Goblin Omelette",
            "Goblin Egg",
            "Spicy Goblin Omelette",
            "Red Goblin Egg",
            "Pesto Goblin Omelette",
            "Green Goblin Egg",
            "Sunny Side Goblin Omelette",
            "Yellow Goblin Egg",
            "Blue Cheese Goblin Omelette",
            "Blue Goblin Egg",
            "Tungsten Regulator",
            "Mithril-Plated Drill Engine",
            "Titanium-Plated Drill Engine",
            "Ruby-Polished Drill Engine",
            "Precursor Apparatus",
            "Control Switch",
            "Electron Transmitter",
            "FTX 3070",
            "Robotron Reflector",
            "Superlite Motor",
            "Synthetic Heart",
            "Sapphire-Polished Drill Engine",
            "Amber-Polished Drill Engine",
            "Mithril-Infused Fuel Tank",
            "Titanium-Infused Fuel Tank",
            "Gemstone Fuel Tank",
            "Perfectly-Cut Fuel Tank",
            "Bejeweled Collar",
            "Beacon I",
            "Beacon II",
            "Glass",
            "Beacon III",
            "Beacon IV",
            "Beacon V",
            "Travel Scroll To The Dwarven Forge",
            "Enchanted Ender Pearl",
            "Ender Pearl",
            "Travel Scroll To The Dwarven Base Camp",
            "Power Crystal",
            "Secret Railroad Pass",
            "Tungsten Key",
            "Umber Key",
            "Skeleton Key",
            "Portable Campfire",
            "Match-Sticks",
            "Sulphur",
            "Stick",
            "Gemstone Gauntlet"
        };

        for(String item:items){
            resources.put(item, 0);
        }

        try(FileWriter writer = new FileWriter(file)){
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(resources, writer);
            System.out.println("Resources intialized successfully in ");
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void initializeWidgetConfigData(File file) {
        LOGGER.info("Initializing widget configuration with default values");
        
        try (FileWriter writer = new FileWriter(file)) {
            Map<String, Object> widgetConfig = new LinkedHashMap<>();
            widgetConfig.put("enabled", false);
            widgetConfig.put("selectedRecipe", null);
            widgetConfig.put("widgetX", 10);
            widgetConfig.put("widgetY", 40);
            widgetConfig.put("expandedNodes", new HashMap<String, Boolean>());
            widgetConfig.put("craftAmount", 1);
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(widgetConfig, writer);
            
            LOGGER.info("Widget configuration initialized successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to initialize widget configuration file", e);
        }
    }
}