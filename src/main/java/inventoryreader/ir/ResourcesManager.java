package inventoryreader.ir;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.io.File;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class ResourcesManager {

    private static final ResourcesManager INSTANCE = new ResourcesManager();
    private static final File resourcesFile = new File(FilePathManager.DATA_DIR, "resources.json");

    private ResourcesManager() {}

    public static ResourcesManager getInstance() {
        return INSTANCE;
    }

    public void saveData(Map<String, Integer> data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Map<String, Integer> resources = new java.util.LinkedHashMap<>();
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        try (FileReader reader = new FileReader(resourcesFile)) {
            Map<String, Integer> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                resources.putAll(loaded);
            }
        } catch (IOException e) {}
        for (String item : data.keySet()) {
            if (item == null || item.isEmpty()) continue;
            Integer value = data.get(item);
            if (resources.containsKey(item)) {
                resources.put(item, resources.get(item) + value);
            } else {
                String[] itemSplit = item.split(" ");
                if (itemSplit.length > 1) {
                    String itemRefined = String.join(" ", java.util.Arrays.copyOfRange(itemSplit, 1, itemSplit.length));
                    if (resources.containsKey(itemRefined)) {
                        resources.put(itemRefined, resources.get(itemRefined) + value);
                    }
                }
            }
        }
        try (FileWriter writer = new FileWriter(resourcesFile)) {
            gson.toJson(resources, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Integer> getAllResources() {
        Gson gson = new Gson();
        if (!resourcesFile.exists() || resourcesFile.length() == 0) {
            return new LinkedHashMap<>();
        }
        try (FileReader reader = new FileReader(resourcesFile)) {
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            Map<String, Integer> resources = gson.fromJson(reader, type);
            if (resources != null) {
                return new LinkedHashMap<>(resources);
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {}
        return new LinkedHashMap<>();
    }

    public Integer getResourceByName(String name) {
        Map<String, Integer> resources = getAllResources();
        return resources.getOrDefault(name, 0);
    }

    public void setResourceAmount(String name, int amount) {
        Map<String, Integer> resources = getAllResources();
        resources.put(name, amount);
        saveData(resources);
    }

    public void craft(String name, int amt) {
        RecipeManager rm = RecipeManager.getInstance();
        Map<String, Map<String, Integer>> forging = rm.getAllRecipes();
        Map<String, Integer> myResources = getAllResources();
        myResources.put(name, myResources.getOrDefault(name, 0) + amt);
        for (Map.Entry<String, Integer> entry : forging.get(name).entrySet()) {
            craftItem(entry.getKey(), entry.getValue() * amt, forging, myResources);
        }
        saveData(myResources);
    }
    
    private void craftItem(String currentItem, int multiplier, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources) {
        if (forging.containsKey(currentItem)) {
            int available = myResources.getOrDefault(currentItem, 0);
            if (available < multiplier) {
                int remaining = multiplier - available;
                myResources.put(currentItem, 0);
                for (Map.Entry<String, Integer> entry : forging.get(currentItem).entrySet()) {
                    craftItem(entry.getKey(), entry.getValue() * remaining, forging, myResources);
                }
            } else {
                myResources.put(currentItem, available - multiplier);
            }
        } else {
            int current = myResources.getOrDefault(currentItem, 0);
            myResources.put(currentItem, Math.max(0, current - multiplier));
        }
    }
    
    public void updateResourcesAfterCraft(String name, int amt) {
        craft(name, amt);
    }

    public Map<String, Integer> getSimpleRemainingIngredients(String name, int amt) {
        Map<String, Integer> resources = getAllResources();
        Map<String, Integer> needed = new LinkedHashMap<>();
        calculateNeeded(name, amt, resources, needed);
        return needed;
    }

    private void calculateNeeded(String name, int amt, Map<String, Integer> resources, Map<String, Integer> needed) {
        RecipeManager rm = RecipeManager.getInstance();
        if (!rm.getAllRecipes().containsKey(name)) {
            int have = resources.getOrDefault(name, 0);
            if (have < amt) {
                needed.put(name, amt - have);
            }
            return;
        }
        Map<String, Integer> recipe = rm.getAllRecipes().get(name);
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            String ingredient = entry.getKey();
            int required = entry.getValue() * amt;
            int have = resources.getOrDefault(ingredient, 0);
            if (rm.getAllRecipes().containsKey(ingredient)) {
                calculateNeeded(ingredient, required, resources, needed);
            } else {
                if (have < required) {
                    needed.put(ingredient, needed.getOrDefault(ingredient, 0) + (required - have));
                }
            }
        }
    }

    public List<ResourceEntry> getAllResourceEntries() {
        Map<String, Integer> map = getAllResources();
        List<ResourceEntry> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() > 0) {
                list.add(new ResourceEntry(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }
    
    public List<ResourceEntry> getAllResourceEntriesIncludingZero() {
        Map<String, Integer> map = getAllResources();
        List<ResourceEntry> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            list.add(new ResourceEntry(entry.getKey(), entry.getValue()));
        }
        // Sort resources by name for better readability in the UI
        Collections.sort(list, (a, b) -> a.name.compareTo(b.name));
        return list;
    }

    public RemainingResponse getRemainingIngredients(String name, int amt) {
        RecipeManager rm = RecipeManager.getInstance();
        Map<String, Map<String, Integer>> forging = rm.getAllRecipes();
        Map<String, Integer> myResources = getAllResources();
        Map<String, Integer> messages = new LinkedHashMap<>();
        int old = myResources.getOrDefault(name, 0);
        Map<String, Integer> simResources = new LinkedHashMap<>(myResources);
        buildRecipe(name, amt, forging, simResources, messages);
        int updated = simResources.getOrDefault(name, 0);
        RecipeNode fullRecipe;
        if (updated - old >= amt) {
            fullRecipe = expandRequiredRecipe(name, (updated-old)-amt, forging, simResources);
        } else {
            fullRecipe = expandRequiredRecipe(name, amt-(updated-old), forging, simResources);
        }
        return new RemainingResponse(name, fullRecipe, messages);
    }

    private void buildRecipe(String currentItem, int multiplier, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources, Map<String, Integer> messages) {
        Map<String, Integer> recipe = forging.get(currentItem);
        if (recipe == null) return;
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            String item = entry.getKey();
            int quantity = entry.getValue();
            if (forging.containsKey(item)) {
                compositeMaterial(item, Math.max(0, (quantity * multiplier) - myResources.getOrDefault(item, 0)), forging, myResources, messages);
            }
        }
        check(currentItem, multiplier, forging, myResources, messages);
    }

    private void check(String currentItem, int multiplier, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources, Map<String, Integer> messages) {
        Map<String, Integer> recipe = forging.get(currentItem);
        if (recipe == null) return;
        List<Integer> count = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            String baseItem = entry.getKey();
            int quantityOfBaseItem = entry.getValue();
            int possibleItems = myResources.getOrDefault(baseItem, 0) / quantityOfBaseItem;
            count.add(multiplier - possibleItems);
        }
        int maxcount = count.stream().mapToInt(i -> i).max().orElse(0);
        maxcount = Math.max(maxcount, 0);
        myResources.put(currentItem, myResources.getOrDefault(currentItem, 0) + (multiplier - maxcount));
        if (multiplier - maxcount > 0) {
            messages.put(currentItem, messages.getOrDefault(currentItem, 0) + (multiplier - maxcount));
        }
        allocate(currentItem, multiplier-maxcount, forging, myResources);
    }

    private void allocate(String currentItem, int a, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources) {
        Map<String, Integer> recipe = forging.get(currentItem);
        if (recipe == null) return;
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            String baseItem = entry.getKey();
            int quantityOfBaseItem = entry.getValue();
            myResources.put(baseItem, myResources.getOrDefault(baseItem, 0) - quantityOfBaseItem * a);
        }
    }

    private void compositeMaterial(String currentItem, int multiplier, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources, Map<String, Integer> messages) {
        Map<String, Integer> recipe = forging.get(currentItem);
        if (recipe == null) return;
        for (Map.Entry<String, Integer> entry : recipe.entrySet()) {
            String item = entry.getKey();
            int quantity = entry.getValue();
            if (forging.containsKey(item)) {
                compositeMaterial(item, Math.max(0, (quantity * multiplier) - myResources.getOrDefault(item, 0)), forging, myResources, messages);
            }
        }
        check(currentItem, multiplier, forging, myResources, messages);
    }

    private RecipeNode expandRequiredRecipe(String currentName, int multiplier, Map<String, Map<String, Integer>> forging, Map<String, Integer> myResources) {
        if (!forging.containsKey(currentName)) {
            int have = myResources.getOrDefault(currentName, 0);
            if (have < multiplier) {
                int temp = have;
                myResources.put(currentName, 0);
                return new RecipeNode(currentName, multiplier - temp, Collections.emptyList());
            } else {
                myResources.put(currentName, have - multiplier);
                return new RecipeNode(currentName, 0, Collections.emptyList());
            }
        } else {
            List<RecipeNode> ingredients = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : forging.get(currentName).entrySet()) {
                String item = entry.getKey();
                int qty = entry.getValue();
                if (forging.containsKey(item)) {
                    if (myResources.getOrDefault(item, 0) < qty * multiplier) {
                        RecipeNode expanded = expandRequiredRecipe(item, (qty * multiplier) - myResources.getOrDefault(item, 0), forging, myResources);
                        ingredients.add(expanded);
                        myResources.put(item, 0);
                    } else {
                        myResources.put(item, myResources.getOrDefault(item, 0) - qty * multiplier);
                        RecipeNode expanded = expandRequiredRecipe(item, 0, forging, myResources);
                        ingredients.add(expanded);
                    }
                } else {
                    if (myResources.getOrDefault(item, 0) < qty * multiplier) {
                        RecipeNode expanded = expandRequiredRecipe(item, qty * multiplier, forging, myResources);
                        ingredients.add(expanded);
                    } else {
                        myResources.put(item, myResources.getOrDefault(item, 0) - qty * multiplier);
                        RecipeNode expanded = expandRequiredRecipe(item, 0, forging, myResources);
                        ingredients.add(expanded);
                    }
                }
            }
            return new RecipeNode(currentName, multiplier, ingredients);
        }
    }

    public static class ResourceEntry {
        public String name;
        public int amount;
        public ResourceEntry(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }
    }

    public static class RemainingResponse {
        public String name;
        public RecipeNode full_recipe;
        public Map<String, Integer> messages;
        public RemainingResponse(String name, RecipeNode fullRecipe, Map<String, Integer> messages) {
            this.name = name;
            this.full_recipe = fullRecipe;
            this.messages = messages;
        }
    }

    public static class RecipeNode {
        public String name;
        public int amount;
        public java.util.List<RecipeNode> ingredients;
        
        public RecipeNode(String name, int amount, java.util.List<RecipeNode> ingredients) {
            this.name = name;
            this.amount = amount;
            this.ingredients = ingredients;
        }
    }
}
