package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
        try (FileReader f1 = new FileReader(FilePathManager.DATA_DIR + "/forging.json");
             FileReader f2 = new FileReader(FilePathManager.DATA_DIR + "/gemstone_recipes.json")) {
            Type type = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
            Map<String, Map<String, Integer>> forging = gson.fromJson(f1, type);
            Map<String, Map<String, Integer>> gemstone = gson.fromJson(f2, type);
            if (forging != null) recipes.putAll(forging);
            if (gemstone != null) recipes.putAll(gemstone);
            recipeNames.addAll(recipes.keySet());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getRecipeNames() {
        return new ArrayList<>(recipeNames);
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
