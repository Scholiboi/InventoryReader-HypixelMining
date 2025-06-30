package inventoryreader.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SandboxWidget {
    private static final SandboxWidget INSTANCE = new SandboxWidget();
    private static final int DARK_PANEL_BG = 0x99271910;
    private static final int HEADER_BG = 0xCC2C4A1B;
    private static final int GOLD = 0xFFFFB728;
    private static final int RECIPE_LEVEL_INDENT = 10;
    private static final Identifier SANDBOX_WIDGET_LAYER = Identifier.of(InventoryReader.MOD_ID, "sandbox_widget");
    private boolean enabled = false;
    private String selectedRecipe = null;
    private RecipeManager.RecipeNode recipeTree = null;
    private int widgetX = 10;
    private int widgetY = 40;
    private boolean isRepositioning = false;
    private Map<String, Boolean> expandedNodes = new HashMap<>();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private long messageDisplayTime = 0;
    private static final long MESSAGE_DURATION = 5000;
    private int craftAmount = 1;
    private final ResourcesManager resourcesManager;
    private final ScheduledExecutorService scheduler;

    private SandboxWidget() {
        this.resourcesManager = ResourcesManager.getInstance();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> {
            layeredDrawer.attachLayerBefore(IdentifiedLayer.CHAT, SANDBOX_WIDGET_LAYER, (context, tickCounter) -> {
                if (enabled && selectedRecipe != null && recipeTree != null) {
                    render(context);
                }
            });
        });
        scheduler.scheduleAtFixedRate(this::updateRecipeData, 0, 1, TimeUnit.SECONDS);
        loadConfiguration();
    }

    public static SandboxWidget getInstance() {
        return INSTANCE;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.selectedRecipe = null;
            this.recipeTree = null;
        }
        saveConfiguration();
    }
    public boolean isEnabled() {
        return this.enabled;
    }
    public void setSelectedRecipe(String recipeName) {
        this.selectedRecipe = recipeName;
        if (recipeName != null) {
            expandedNodes.put(recipeName, true);
        }
        updateRecipeData();
        saveConfiguration();
    }
    public String getSelectedRecipe() {
        return this.selectedRecipe;
    }
    public int getWidgetX() {
        return widgetX;
    }
    public int getWidgetY() {
        return widgetY;
    }
    public void setWidgetPosition(int x, int y) {
        this.widgetX = x;
        this.widgetY = y;
        saveConfiguration();
    }
    public static String getNodeKey(RecipeManager.RecipeNode node) {
        return node.name + "_" + node.amount;
    }
    public boolean isNodeExpanded(String nodeKey) {
        if (expandedNodes == null) {
            expandedNodes = new HashMap<>();
            return false;
        }
        
        Boolean result = expandedNodes.getOrDefault(nodeKey, false);
        return result != null ? result : false;
    }
    public void setNodeExpansion(String nodeKey, boolean expanded) {
        expandedNodes.put(nodeKey, expanded);
    }
    public void toggleNodeExpansion(String nodeKey) {
        if (expandedNodes == null) {
            expandedNodes = new HashMap<>();
        }
        
        Boolean currentState = expandedNodes.getOrDefault(nodeKey, false);
        if (currentState == null) {
            currentState = false;
        }
        
        boolean newState = !currentState;
        expandedNodes.put(nodeKey, newState);
        saveConfiguration();
    }
    public void saveConfiguration() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            WidgetConfig config = new WidgetConfig(
                enabled,
                selectedRecipe,
                widgetX,
                widgetY,
                expandedNodes,
                craftAmount
            );
            FilePathManager.file_widget_config.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FilePathManager.file_widget_config)) {
                gson.toJson(config, writer);
            }
            InventoryReader.LOGGER.info("Widget configuration saved");
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to save widget configuration", e);
        }
    }
    private void loadConfiguration() {
        try {
            if (!FilePathManager.file_widget_config.exists()) {
                InventoryReader.LOGGER.info("No widget configuration file found, using defaults");
                return;
            }
            Gson gson = new Gson();
            try (FileReader reader = new FileReader(FilePathManager.file_widget_config)) {
                WidgetConfig config = gson.fromJson(reader, WidgetConfig.class);
                if (config != null) {
                    this.enabled = config.enabled;
                    this.selectedRecipe = config.selectedRecipe;
                    this.widgetX = config.widgetX;
                    this.widgetY = config.widgetY;
                    if (config.expandedNodes != null) {
                        this.expandedNodes = config.expandedNodes;
                    }
                    if (config.craftAmount > 0) {
                        this.craftAmount = config.craftAmount;
                    }
                    InventoryReader.LOGGER.info("Widget configuration loaded");
                    if (selectedRecipe != null) {
                        updateRecipeData();
                    }
                }
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            InventoryReader.LOGGER.error("Failed to load widget configuration", e);
        }
    }
    private static class WidgetConfig {
        boolean enabled;
        String selectedRecipe;
        int widgetX;
        int widgetY;
        Map<String, Boolean> expandedNodes;
        int craftAmount;
        public WidgetConfig(boolean enabled, String selectedRecipe, int widgetX, int widgetY, 
                            Map<String, Boolean> expandedNodes, int craftAmount) {
            this.enabled = enabled;
            this.selectedRecipe = selectedRecipe;
            this.widgetX = widgetX;
            this.widgetY = widgetY;
            this.expandedNodes = expandedNodes;
            this.craftAmount = craftAmount;
        }
    }
    public void startRepositioning() {
        if (enabled) {
            isRepositioning = true;
            MinecraftClient client = MinecraftClient.getInstance();
            client.mouse.unlockCursor();
            if (client.player != null) {
                client.player.sendMessage(
                    Text.literal("Mouse cursor unlocked. Click anywhere to position the widget.")
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)), 
                    false
                );
            }
            InventoryReader.LOGGER.info("Widget repositioning mode activated");
        } else {
            InventoryReader.LOGGER.warn("Cannot reposition disabled widget");
        }
    }
    public void stopRepositioning() {
        if (isRepositioning) {
            isRepositioning = false;
            MinecraftClient.getInstance().mouse.lockCursor();
            InventoryReader.LOGGER.info("Widget repositioning mode deactivated");
            saveConfiguration();
        }
    }
    public boolean isRepositioning() {
        return isRepositioning;
    }
    public boolean handleMouseClick(double mouseX, double mouseY) {
        if (isRepositioning) {
            widgetX = (int)mouseX;
            widgetY = (int)mouseY;
            isRepositioning = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                Text message = Text.literal("Widget position set: X:" + widgetX + ", Y:" + widgetY)
                    .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
                client.player.sendMessage(message, true);
                client.mouse.lockCursor();
            }
            return true;
        } else if (enabled && selectedRecipe != null && recipeTree != null) {
            return handleTreeNodeClick(mouseX, mouseY);
        }
        return false;
    }
    private boolean handleTreeNodeClick(double mouseX, double mouseY) {
        int panelWidth = 250;
        int panelX = widgetX;
        int panelY = widgetY;
        if (mouseX < panelX || mouseX > panelX + panelWidth) {
            return false;
        }
        int y = panelY + 20;
        return checkNodeClick(recipeTree, mouseX, mouseY, panelX, y, 0);
    }
    private boolean checkNodeClick(RecipeManager.RecipeNode node, double mouseX, double mouseY, int x, int y, int level) {
        if (node == null) return false;
        int indent = level * RECIPE_LEVEL_INDENT;
        int nodeHeight = 16;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int nodeWidth = 230 - indent;
        if (mouseY >= y && mouseY <= y + nodeHeight) {
            if (mouseX >= x + indent && mouseX <= x + indent + nodeWidth) {
                String nodeKey = getNodeKey(node);
                if (hasChildren) {
                    boolean expanded = expandedNodes.getOrDefault(nodeKey, false);
                    expandedNodes.put(nodeKey, !expanded);
                    return true;
                } else {
                    MinecraftClient client = MinecraftClient.getInstance();
                    Map<String, Integer> resources = resourcesManager.getAllResources();
                    int available = resources.getOrDefault(node.name, 0);
                    int remainingNeeded = node.amount;
                    boolean hasEnough = remainingNeeded == 0;
                    Text message = Text.literal("You need " + remainingNeeded + " more " + node.name + " (Have: " + available + ")")
                        .setStyle(Style.EMPTY.withColor(hasEnough ? Formatting.GREEN : Formatting.RED));
                    if (client.player != null) {
                        client.player.sendMessage(message, true);
                    }
                    return true;
                }
            }
        }
        y += nodeHeight;
        if (hasChildren && expandedNodes.getOrDefault(getNodeKey(node), false)) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                boolean childClicked = checkNodeClick(child, mouseX, mouseY, x, y, level + 1);
                if (childClicked) return true;
                y += getExpandedNodeHeight(child);
            }
        }
        return false;
    }
    private int getExpandedNodeHeight(RecipeManager.RecipeNode node) {
        if (node == null) return 0;
        int height = 16;
        if (node.ingredients != null && !node.ingredients.isEmpty() && 
            expandedNodes.getOrDefault(getNodeKey(node), false)) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                height += getExpandedNodeHeight(child);
            }
        }
        return height;
    }

    private void updateRecipeData() {
        if (!enabled || selectedRecipe == null) {
            return;
        }
        
        // Safety check to ensure expandedNodes is initialized
        if (expandedNodes == null) {
            expandedNodes = new HashMap<>();
        }
        
        Map<String, Boolean> prevExpandedState = new HashMap<>(expandedNodes);
        ResourcesManager.RemainingResponse response = resourcesManager.getRemainingIngredients(selectedRecipe, craftAmount);
        
        messages.clear();
        
        if (response.messages != null && !response.messages.isEmpty()) {
            messages.add("Craftable -");
            
            for (Map.Entry<String, Integer> entry : response.messages.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    messages.add("   " + entry.getValue() + "× " + entry.getKey());
                }
            }
        }
        
        messageDisplayTime = Util.getMeasuringTimeMs();
        
        recipeTree = convertResourceNodeToRecipeNode(response.full_recipe);
        boolean craftable = false;
        if (recipeTree != null && recipeTree.ingredients != null) {
            craftable = recipeTree.ingredients.stream().allMatch(child -> child.amount <= 0);
            if (craftable && messages.isEmpty()) {
                addMessage("Craftable -");
            } else if (!craftable && messages.isEmpty()) {
                addMessage("Craftable -");
            }
        }
        if (recipeTree != null) {
            expandedNodes.put(getNodeKey(recipeTree), true);
            preserveNodeExpansionStates(recipeTree, prevExpandedState);
        }
    }

    private RecipeManager.RecipeNode convertResourceNodeToRecipeNode(ResourcesManager.RecipeNode resourceNode) {
        if (resourceNode == null) return null;
        List<RecipeManager.RecipeNode> ingredients = new ArrayList<>();
        if (resourceNode.ingredients != null) {
            for (ResourcesManager.RecipeNode child : resourceNode.ingredients) {
                ingredients.add(convertResourceNodeToRecipeNode(child));
            }
        }
        return new RecipeManager.RecipeNode(resourceNode.name, resourceNode.amount, ingredients);
    }
    private void render(DrawContext context) {
        if (!enabled || selectedRecipe == null || recipeTree == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int height = client.getWindow().getScaledHeight();
        int panelWidth = 250;
        int visibleLines = countVisibleRecipeTreeLines(recipeTree);
        
        int messageLines = countMessageLines(client, panelWidth);
        int messageSectionHeight = messageLines > 0 ? messageLines * 16 + 20 : 0; 
        
        int panelHeight = Math.min(height - 40, 20 + (visibleLines * 16) + messageSectionHeight + 20);
        int panelX = widgetX;
        int panelY = widgetY;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DARK_PANEL_BG);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 20, HEADER_BG);
        int borderColor = 0xFFDAA520;
        int borderThickness = 2;
        for (int i = 0; i < borderThickness; i++) {
            context.drawBorder(
                panelX - i, 
                panelY - i, 
                panelWidth + i * 2, 
                panelHeight + i * 2, 
                borderColor
            );
        }
        Text title = Text.literal("Recipe: " + selectedRecipe)
            .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true));
        int titleWidth = client.textRenderer.getWidth(title);
        context.drawText(
            client.textRenderer,
            title,
            panelX + (panelWidth - titleWidth) / 2,
            panelY + 5,
            0xFFFFFFFF,
            false
        );
        if (isRepositioning) {
            String repoText = "◆ Click to place widget ◆";
            context.drawText(
                client.textRenderer,
                repoText,
                panelX + panelWidth - client.textRenderer.getWidth(repoText) - 5,
                panelY + panelHeight - 12,
                GOLD,
                true
            );
        }
        context.fill(panelX, panelY + 19, panelX + panelWidth, panelY + 20, 0x99608C35);
        int y = panelY + 22;
        
        int messagesDisplayHeight = 0;
        if (!messages.isEmpty()) {
            messagesDisplayHeight = countMessageLines(client, panelWidth) * 16 + 20; // 20px for header and spacing
        }

        int maxRecipeTreeY = panelY + panelHeight - messagesDisplayHeight - 10;
        
        int afterTreeY = renderRecipeTree(context, recipeTree, panelX, y, 0, maxRecipeTreeY);
        
        int messageY = Math.min(afterTreeY + 5, maxRecipeTreeY);
        drawMessages(context, panelX, messageY, panelWidth);
    }
    private int countVisibleRecipeTreeLines(RecipeManager.RecipeNode node) {
        if (node == null) return 0;
        int count = 1;
        
        if (expandedNodes == null) {
            expandedNodes = new HashMap<>();
        }
        
        String nodeKey = getNodeKey(node);
        Boolean isExpanded = expandedNodes.getOrDefault(nodeKey, false);
        
        if (isExpanded == null) {
            isExpanded = false;
            expandedNodes.put(nodeKey, false);
        }
        
        if (node.ingredients != null && !node.ingredients.isEmpty() && isExpanded) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                count += countVisibleRecipeTreeLines(child);
            }
        }
        return count;
    }
    private int renderRecipeTree(DrawContext context, RecipeManager.RecipeNode node, int x, int y, int level, int maxY) {
        if (node == null) return y;
        MinecraftClient client = MinecraftClient.getInstance();
        int indent = level * RECIPE_LEVEL_INDENT;
        boolean hasEnough = (node.amount == 0);
        String nodeKey = getNodeKey(node);
        boolean isExpanded = expandedNodes.getOrDefault(nodeKey, false);
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int bgColor = 0x99271910;
        int mouseX = (int)(client.mouse.getX() / client.getWindow().getScaleFactor());
        int mouseY = (int)(client.mouse.getY() / client.getWindow().getScaleFactor());
        boolean isHovered = mouseX >= x + indent && mouseX <= x + indent + 230 - indent && 
                            mouseY >= y && mouseY <= y + 16;
        int hoverEffect = isHovered ? 0x22FFFFFF : 0;
        int nodeWidth = 230 - indent;
        context.fill(x + indent, y, x + indent + nodeWidth, y + 16, bgColor + hoverEffect);
        int borderColor = hasEnough ? 0x88608C35 : 0x88FF5555;
        context.drawBorder(x + indent, y, nodeWidth, 16, borderColor);
        if (hasChildren) {
            String expandIcon = isExpanded ? "▼" : "►";
            context.drawText(
                client.textRenderer,
                expandIcon,
                x + indent + 5,
                y + 4,
                0xFFFFFFFF,
                false
            );
        }
        int nameX = x + indent + (hasChildren ? 25 : 10);
        int textColor;
        boolean isBold = (level == 0);
        if (level == 0) {
            textColor = GOLD; 
        } else {
            textColor = hasEnough ? 0xFFFFFFFF : 0xFFFF6B6B;
        }
        String prefix = "";
        String amountText = node.amount + "×";
        int amountColor = hasEnough ? 0xFF6EFF6E : 0xFFFF6B6B;
        context.drawText(
            client.textRenderer,
            prefix,
            nameX,
            y + 4,
            0xFFFFFFFF,
            false
        );
        context.drawText(
            client.textRenderer,
            amountText,
            nameX + client.textRenderer.getWidth(prefix),
            y + 4,
            amountColor,
            false
        );
        Text itemName = Text.literal(node.name)
            .setStyle(Style.EMPTY.withColor(textColor)
            .withBold(isBold));
        context.drawText(
            client.textRenderer,
            itemName,
            nameX + client.textRenderer.getWidth(prefix + amountText + " "),
            y + 4,
            0xFFFFFFFF,
            false
        );
        y += 16;
        if (hasChildren && isExpanded && node.ingredients.size() > 0) {
            int lineColor = 0xFF777777;
            for (int i = 0; i < node.ingredients.size(); i++) {
                RecipeManager.RecipeNode child = node.ingredients.get(i);
                int lineStartX = x + indent + 6;
                int vertLineY = y;
                int childIndentX = x + indent + RECIPE_LEVEL_INDENT;
                context.fill(lineStartX, vertLineY, lineStartX + 1, vertLineY + 8, lineColor);
                context.fill(lineStartX, vertLineY + 8, childIndentX, vertLineY + 9, lineColor);
                int nextY = renderRecipeTree(context, child, x, y, level + 1, maxY);
                if (nextY > maxY) {
                    return nextY;
                }
                y = nextY;
            }
        }
        return y;
    }
    private void preserveNodeExpansionStates(RecipeManager.RecipeNode node, Map<String, Boolean> prevStates) {
        if (node == null) return;
        
        if (expandedNodes == null) {
            expandedNodes = new HashMap<>();
        }
        
        String nodeKey = getNodeKey(node);
        if (prevStates != null && prevStates.containsKey(nodeKey)) {
            Boolean value = prevStates.get(nodeKey);
            expandedNodes.put(nodeKey, value != null ? value : false);
        } else if (prevStates != null) {
            for (Map.Entry<String, Boolean> entry : prevStates.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith(node.name + "_")) {
                    Boolean value = entry.getValue();
                    expandedNodes.put(nodeKey, value != null ? value : false);
                    break;
                }
            }
        }
        if (node.ingredients != null) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                preserveNodeExpansionStates(child, prevStates);
            }
        }
    }
    private void drawMessages(DrawContext context, int x, int y, int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        long currentTime = Util.getMeasuringTimeMs();
        if (currentTime - messageDisplayTime <= MESSAGE_DURATION && !messages.isEmpty()) {
            context.fill(x, y - 5, x + width - 10, y - 4, 0x99608C35);
            
            Text messagesHeader = Text.literal("Craftable -")
                .setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true));
            context.drawText(
                client.textRenderer,
                messagesHeader,
                x + 5,
                y - 3,
                0xFFFFFFFF,
                false
            );
            
            if (messages.size() == 1 && messages.get(0).equals("Craftable -")) {
                return;
            }
            
            y += 12;
            
            List<String> messagesCopy = new ArrayList<>(messages);
            for (int i = 0; i < messagesCopy.size(); i++) {
                String message = messagesCopy.get(i);

                if (i == 0 && message.equals("Craftable -")) {
                    continue;
                }

                int textColor = message.startsWith("   ") ? 0xFFFF9D00 : 0xFFFFFFFF;

                String[] words = message.split(" ");
                StringBuilder line = new StringBuilder();
                for (String word : words) {
                    if (client.textRenderer.getWidth(line.toString() + word) > width - 15) {
                        context.drawText(
                            client.textRenderer,
                            line.toString(),
                            x + 5,
                            y,
                            textColor,
                            false
                        );
                        y += 10;
                        line = new StringBuilder(message.startsWith("   ") ? "      " : "   ").append(word).append(" ");
                    } else {
                        line.append(word).append(" ");
                    }
                }
                if (line.length() > 0) {
                    context.drawText(
                        client.textRenderer,
                        line.toString(),
                        x + 5,
                        y,
                        textColor,
                        false
                    );
                    y += 9;
                }
            }
        }
    }
    public void addMessage(String message) {
        this.messages.add(message);
        this.messageDisplayTime = Util.getMeasuringTimeMs();
    }
    public int getCraftAmount() {
        return craftAmount;
    }
    public void setCraftAmount(int craftAmount) {
        if (craftAmount < 1) {
            craftAmount = 1;
        }
        this.craftAmount = craftAmount;
        updateRecipeData();
        saveConfiguration();
    }
    private int countMessageLines(MinecraftClient client, int width) {
        int lineCount = 0;
        long currentTime = Util.getMeasuringTimeMs();
        if (currentTime - messageDisplayTime <= MESSAGE_DURATION && !messages.isEmpty()) {
            if (messages.size() == 1 && messages.get(0).equals("Craftable -")) {
                return 1;
            }
            
            List<String> messagesCopy = new ArrayList<>(messages);
            for (int i = 0; i < messagesCopy.size(); i++) {
                String message = messagesCopy.get(i);
                
                if (i == 0 && message.equals("Craftable -")) {
                    continue;
                }
                
                String[] words = message.split(" ");
                StringBuilder line = new StringBuilder();
                int linesInMessage = 1;
                
                for (String word : words) {
                    if (client.textRenderer.getWidth(line.toString() + word) > width - 15) {
                        linesInMessage++;
                        line = new StringBuilder(message.startsWith("   ") ? "      " : "   ").append(word).append(" ");
                    } else {
                        line.append(word).append(" ");
                    }
                }
                lineCount += linesInMessage;
            }
        }
        return lineCount;
    }
}