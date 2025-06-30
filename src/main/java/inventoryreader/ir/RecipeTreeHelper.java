package inventoryreader.ir;

import java.awt.Rectangle;
import java.util.*;


public class RecipeTreeHelper {
    private List<Rectangle> nodeAreas = new ArrayList<>();
    private List<RecipeManager.RecipeNode> treeNodes = new ArrayList<>();
    
    public RecipeTreeHelper() {
    }
    
    public void addNodeArea(Rectangle area, RecipeManager.RecipeNode node) {
        nodeAreas.add(area);
        treeNodes.add(node);
    }
    
    public void clearNodeAreas() {
        nodeAreas.clear();
        treeNodes.clear();
    }
    
    public int getNodeIndex(int x, int y) {
        for (int i = 0; i < nodeAreas.size(); i++) {
            if (nodeAreas.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }
    
    public RecipeManager.RecipeNode getNodeByIndex(int index) {
        if (index >= 0 && index < treeNodes.size()) {
            return treeNodes.get(index);
        }
        return null;
    }
}
