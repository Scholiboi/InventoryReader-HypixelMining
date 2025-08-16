package inventoryreader.ir.recipes;

public final class Recipe {
    public final String output;
    public final String[] ing;
    public final short[] cnt;
    public final byte category;
    public final byte sourcePriority;

    public Recipe(String output, String[] ing, short[] cnt, byte category, byte sourcePriority) {
        this.output = output;
        this.ing = ing;
        this.cnt = cnt;
        this.category = category;
        this.sourcePriority = sourcePriority;
    }
}
