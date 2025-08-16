package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class RecipeManager {
    private static final RecipeManager INSTANCE = new RecipeManager();
    private final Map<String, Map<String, Integer>> recipes = new LinkedHashMap<>();
    private final List<String> recipeNames = new ArrayList<>();

    private RecipeManager() {
        loadRecipes();
    }

    public static RecipeManager getInstance() {
        return INSTANCE;
    }

    private void loadRecipes() {
        recipes.clear();
        recipeNames.clear();
        Gson gson = new Gson();
        try {
            Map<String, Map<String, Integer>> forging = readRecipeMap(gson, FilePathManager.FORGING_JSON);
            Map<String, Map<String, Integer>> gemstone = readRecipeMap(gson, FilePathManager.GEMSTONE_RECIPES_JSON);
            if (forging != null) recipes.putAll(forging);
            if (gemstone != null) recipes.putAll(gemstone);

            File remote = FilePathManager.REMOTE_RECIPES_JSON;
            if (remote.exists() && remote.length() > 0) {
                try (FileReader fr = new FileReader(remote)) {
                    Type type = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
                    Map<String, Map<String, Integer>> remoteMap = gson.fromJson(fr, type);
                    if (remoteMap != null) {
                        for (Map.Entry<String, Map<String, Integer>> e : remoteMap.entrySet()) {
                            recipes.put(e.getKey(), e.getValue());
                        }
                    }
                } catch (IOException ignore) {}
            }

            Map<String, Map<String, Integer>> sanitized = sanitizeRecipes(recipes);
            recipes.clear();
            recipes.putAll(sanitized);

            recipeNames.addAll(recipes.keySet());
            Set<String> allNames = new LinkedHashSet<>();
            allNames.addAll(recipes.keySet());
            for (Map<String, Integer> m : recipes.values()) allNames.addAll(m.keySet());
            FilePathManager.ensureResourceNames(allNames);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Map<String, Map<String, Integer>> readRecipeMap(Gson gson, File file) throws IOException {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (FileReader fr = new FileReader(file)) {
            java.lang.reflect.Type any = new com.google.gson.reflect.TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> root = gson.fromJson(fr, any);
            if (root == null || root.isEmpty()) return null;
            Object recipesNode = root.get("recipes");
            Gson gg = new Gson();
            java.lang.reflect.Type t = new com.google.gson.reflect.TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
            if (recipesNode instanceof Map<?, ?> m) {
                String json = gg.toJson(m);
                return gg.fromJson(json, t);
            }
            String json = gg.toJson(root);
            return gg.fromJson(json, t);
        }
    }

    public List<String> getRecipeNames() {
        List<String> list = new ArrayList<>(recipeNames);
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    public Map<String, Integer> getSimpleRecipe(String name, int amt) {
        Map<String, Integer> base = recipes.getOrDefault(name, Collections.emptyMap());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : base.entrySet()) {
            result.put(entry.getKey(), entry.getValue() * amt);
        }
        return result;
    }

    public RecipeNode expandRecipe(String currentName, int multiplier) {
        if (!recipes.containsKey(currentName)) {
            return new RecipeNode(currentName, multiplier, Collections.emptyList());
        }
        List<RecipeNode> ingredients = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipes.get(currentName).entrySet()) {
            String item = entry.getKey();
            int qty = entry.getValue();
            RecipeNode expanded = expandRecipe(item, qty * multiplier);
            ingredients.add(expanded);
        }
        return new RecipeNode(currentName, multiplier, ingredients);
    }

    public Map<String, Map<String, Integer>> getAllRecipes() {
        return new LinkedHashMap<>(recipes);
    }

    public RecipeResponse getRecipe(String name, int amt) {
        if (!recipes.containsKey(name)) {
            return null; 
        }
        
        Map<String, Integer> simpleRecipe = getSimpleRecipe(name, amt);
        RecipeNode fullRecipe = expandRecipe(name, amt);
        
        return new RecipeResponse(name, simpleRecipe, fullRecipe);
    }

    private Map<String, Map<String, Integer>> sanitizeRecipes(Map<String, Map<String, Integer>> input) {
        if (input == null || input.isEmpty()) return Collections.emptyMap();

        Set<String> baseMaterials = new HashSet<>(Arrays.asList(
            "Diamond", "Iron Ingot", "Coal", "Gold Ingot", "Lapis Lazuli", "Emerald", "Redstone", "Quartz", "White Wool"
        ));

        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : input.entrySet()) {
            String output = entry.getKey();
            Map<String, Integer> ing = entry.getValue();
            if (ing == null || ing.isEmpty()) { out.put(output, ing); continue; }

            Map<String, Integer> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> ie : ing.entrySet()) {
                String name = ie.getKey();
                if (name == null || name.isEmpty()) continue;
                if (name.matches("\\d+")) continue;
                cleaned.put(name, ie.getValue());
            }

            if (baseMaterials.contains(output)) {
                if (containsBlockIngredientForBase(output, cleaned.keySet())) {
                    continue;
                }
            }

            boolean selfRef = false;
            for (String k : cleaned.keySet()) {
                if (k != null && k.equalsIgnoreCase(output)) { selfRef = true; break; }
            }
            if (selfRef) continue;

            out.put(output, cleaned);
        }
        return out;
    }

    private boolean containsBlockIngredientForBase(String base, Collection<String> ingredientNames) {
        String core = base;
        if (base.endsWith(" Ingot")) {
            core = base.substring(0, base.length() - " Ingot".length());
        }
        String a = "Block of " + core;
        String b = core + " Block";
        List<String> variants = new ArrayList<>();
        variants.add(a);
        variants.add(b);
        if ("Redstone".equals(core)) variants.add("Redstone Block");
        if ("Emerald".equals(core)) variants.add("Emerald Block");
        if ("Coal".equals(core)) variants.add("Block of Coal");
        if ("Diamond".equals(core)) variants.add("Block of Diamond");
        if ("Gold".equals(core)) variants.add("Block of Gold");
        if ("Iron".equals(core)) variants.add("Block of Iron");
        if ("Quartz".equals(core)) variants.add("Block of Quartz");
        if ("Lapis Lazuli".equals(core)) variants.add("Lapis Lazuli Block");

        for (String n : ingredientNames) {
            for (String v : variants) {
                if (v.equalsIgnoreCase(n)) return true;
            }
        }
        return false;
    }

    public static class RecipeResponse {
        public String name;
        public Map<String, Integer> simple_recipe;
        public RecipeNode full_recipe;
        public Map<String, Integer> messages;
        
        public RecipeResponse(String name, Map<String, Integer> simpleRecipe, RecipeNode fullRecipe) {
            this.name = name;
            this.simple_recipe = simpleRecipe;
            this.full_recipe = fullRecipe;
            this.messages = new LinkedHashMap<>();
        }
        
        public RecipeResponse(String name, Map<String, Integer> simpleRecipe, RecipeNode fullRecipe, Map<String, Integer> messages) {
            this.name = name;
            this.simple_recipe = simpleRecipe;
            this.full_recipe = fullRecipe;
            this.messages = messages;
        }
    }

    public static class RecipeNode {
        public String name;
        public int amount;
        public List<RecipeNode> ingredients;
        
        public RecipeNode(String name, int amount, List<RecipeNode> ingredients) {
            this.name = name;
            this.amount = amount;
            this.ingredients = ingredients;
        }
    }
}
