package inventoryreader.ir.recipes;

import java.util.Map;

public interface RecipeProvider {
    String id();
    int priority();
    Map<String, Recipe> load() throws Exception;
}
