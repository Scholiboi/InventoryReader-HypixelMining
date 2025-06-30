package inventoryreader.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.stream.Collectors;

public class WidgetCustomizationMenu extends Screen {
    private enum Tab {
        RECIPE_SELECTION,
        POSITIONING
    }
    private final SandboxWidget widget;
    private final RecipeManager recipeManager;
    private final ResourcesManager resourcesManager;
    private TextFieldWidget searchField;
    private List<String> filteredRecipes;
    private int scrollOffset = 0;
    private final int MAX_RECIPES_SHOWN = 10;
    private String selectedRecipe = null;
    private RecipeManager.RecipeNode recipeTree = null;
    private int treeViewX = 300;
    private int treeViewY = 80;
    private int treeViewWidth = 400;
    private int treeViewHeight = 300;
    private int treeScrollOffset = 0;
    private static final int RECIPE_LEVEL_INDENT = 10;
    private static final int GOLD = 0xFFFFB728;
    private int widgetPositionX, widgetPositionY;
    private boolean isDraggingWidget = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private Tab currentTab = Tab.RECIPE_SELECTION;
    private ButtonWidget recipeTabButton;
    private ButtonWidget positioningTabButton;
    private int craftAmount;
    private TextFieldWidget craftAmountField;

    public WidgetCustomizationMenu() {
        super(Text.literal("Widget Customization"));
        this.widget = SandboxWidget.getInstance();
        this.recipeManager = RecipeManager.getInstance();
        this.resourcesManager = ResourcesManager.getInstance();
        this.filteredRecipes = new ArrayList<>(recipeManager.getRecipeNames());
        this.selectedRecipe = widget.getSelectedRecipe();
        this.widgetPositionX = widget.getWidgetX();
        this.widgetPositionY = widget.getWidgetY();
        this.craftAmount = widget.getCraftAmount();
        this.currentTab = Tab.RECIPE_SELECTION;
        if (selectedRecipe != null) {
            ResourcesManager.RemainingResponse response = resourcesManager.getRemainingIngredients(selectedRecipe, craftAmount);
            this.recipeTree = convertResourceNodeToRecipeNode(response.full_recipe);
        }
    }

    @Override
    protected void init() {
        super.init();
        recipeTabButton = ButtonWidget.builder(
            Text.literal("Recipe Selection"),
            button -> switchTab(Tab.RECIPE_SELECTION)
        )
        .dimensions(20, 20, 150, 20)
        .build();
        positioningTabButton = ButtonWidget.builder(
            Text.literal("Widget Positioning"),
            button -> switchTab(Tab.POSITIONING)
        )
        .dimensions(180, 20, 150, 20)
        .build();
        addDrawableChild(recipeTabButton);
        addDrawableChild(positioningTabButton);
        updateTabButtonStyles();
        if (currentTab == Tab.RECIPE_SELECTION) {
            initRecipeTab();
            
            // Add toggle button only in Recipe Selection tab
            ButtonWidget toggleButton = ButtonWidget.builder(
                widget.isEnabled() ? Text.literal("Disable Widget") : Text.literal("Enable Widget"),
                button -> {
                    boolean newState = !widget.isEnabled();
                    widget.setEnabled(newState);
                    button.setMessage(newState ? Text.literal("Disable Widget") : Text.literal("Enable Widget"));
                    
                    if (selectedRecipe != null && newState) {
                        widget.setSelectedRecipe(selectedRecipe);
                        widget.setCraftAmount(craftAmount);
                    }
                    widget.saveConfiguration();
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        Text message = Text.literal("Widget " + (newState ? "enabled!" : "disabled!"))
                            .setStyle(Style.EMPTY.withColor(newState ? Formatting.GREEN : Formatting.RED));
                        client.player.sendMessage(message, true);
                    }
                }
            )
            .dimensions(20, height - 30, 150, 20)
            .build();
            addDrawableChild(toggleButton);
        } else {
            initPositioningTab();
        }
        if (currentTab != Tab.POSITIONING) {
            ButtonWidget saveButton = ButtonWidget.builder(
                Text.literal("Save Configuration"),
                button -> saveWidgetConfiguration()
            )
            .dimensions(width - 170, height - 30, 150, 20)
            .build();
            addDrawableChild(saveButton);
        }
    }

    private void initRecipeTab() {
        searchField = new TextFieldWidget(textRenderer, 20, 55, 215, 20, Text.literal(""));
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search recipes..."));
        searchField.setChangedListener(this::updateFilteredRecipes);
        addDrawableChild(searchField);
        craftAmountField = new TextFieldWidget(textRenderer, 236, 55, 35, 20, Text.literal(""));
        craftAmountField.setText(String.valueOf(craftAmount));
        craftAmountField.setChangedListener(this::updateCraftAmount);
        addDrawableChild(craftAmountField);
        ButtonWidget applyButton = ButtonWidget.builder(
            Text.literal("Apply Recipe to Widget"),
            button -> applyConfiguration()
        )
        .dimensions(width / 2 - 90, height - 30, 180, 20)
        .build();
        addDrawableChild(applyButton);
    }

    private void initPositioningTab() {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int bottomMargin = 30;
        int y = height - bottomMargin;
        int spacing = 10;

        ButtonWidget applyButton = ButtonWidget.builder(
            Text.literal("Apply Configuration"),
            button -> applyConfiguration()
        )
        .dimensions(width / 2 - buttonWidth - spacing/2, y, buttonWidth, buttonHeight)
        .build();
        addDrawableChild(applyButton);

        ButtonWidget resetButton = ButtonWidget.builder(
            Text.literal("Reset Position"),
            button -> {
                widgetPositionX = 10;
                widgetPositionY = 40;
                widget.setWidgetPosition(widgetPositionX, widgetPositionY);
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    Text message = Text.literal("Widget position reset!")
                        .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
                    client.player.sendMessage(message, true);
                }
            }
        )
        .dimensions(width / 2 + spacing/2, y, buttonWidth, buttonHeight)
        .build();
        addDrawableChild(resetButton);
    }

    private void switchTab(Tab newTab) {
        currentTab = newTab;
        clearChildren();
        init();
    }

    private void updateTabButtonStyles() {
        if (recipeTabButton != null && positioningTabButton != null) {
            recipeTabButton.active = currentTab != Tab.RECIPE_SELECTION;
            positioningTabButton.active = currentTab != Tab.POSITIONING;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        //leave empty to avoid vanilla background rendering
    }

    private void updateFilteredRecipes(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            this.filteredRecipes = new ArrayList<>(recipeManager.getRecipeNames());
        } else {
            String lowerSearchTerm = searchTerm.toLowerCase();
            this.filteredRecipes = recipeManager.getRecipeNames().stream()
                .filter(name -> name.toLowerCase().contains(lowerSearchTerm))
                .collect(Collectors.toList());
        }
        scrollOffset = 0;
    }

    private void updateCraftAmount(String text) {
        try {
            int amount = Integer.parseInt(text);
            this.craftAmount = Math.max(1, amount); 
        }catch (NumberFormatException e) {
            this.craftAmount = 1;
        }
        if (selectedRecipe != null) {
            ResourcesManager.RemainingResponse response = resourcesManager.getRemainingIngredients(selectedRecipe, craftAmount);
            recipeTree = convertResourceNodeToRecipeNode(response.full_recipe);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Text mainHeading = Text.literal("Skyblock Mining Resource Reader Widget")
            .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true));
        context.drawCenteredTextWithShadow(textRenderer, mainHeading, width / 2, 6, 0xFFFFB728);
        
        if (currentTab == Tab.RECIPE_SELECTION) {
            renderRecipeTab(context, mouseX, mouseY);
        } else {
            renderPositioningTab(context, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderRecipeTab(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, 0xFC101010); 

        int borderColor = 0x88608C35;
        context.drawBorder(0, 0, width, height, borderColor);

        Text recipeSelectionTitle = Text.literal("Recipe Selection")
            .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true));
        context.drawTextWithShadow(textRenderer, recipeSelectionTitle, 20, 45, 0xE0E0E0);
        
        Text treeTitle = Text.literal("Recipe Tree Preview (" + craftAmount + "×)")
            .setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true));
        context.drawTextWithShadow(textRenderer, treeTitle, treeViewX, treeViewY - 15, 0xE0E0E0);

        context.drawTextWithShadow(textRenderer, Text.literal("Craft Amount:"), 236, 45, 0xE0E0E0);
        int yPos = 85; 
        int itemHeight = 20;
        int endIndex = Math.min(scrollOffset + MAX_RECIPES_SHOWN, filteredRecipes.size());

        context.fill(20, yPos - 5, 270, yPos + MAX_RECIPES_SHOWN * itemHeight + 15, 0xFC271910);

        int listBorderColor = 0xFFDAA520;
        for (int i = 0; i < 2; i++) { 
            context.drawBorder(
                20 - i, 
                yPos - 5 - i, 
                250 + i * 2, 
                MAX_RECIPES_SHOWN * itemHeight + 20 + i * 2, 
                listBorderColor
            );
        }
        if (filteredRecipes.isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("No recipes found"), 30, yPos + 10, 0xAAAAAA);
        } else {
            for (int i = scrollOffset; i < endIndex; i++) {
                String recipe = filteredRecipes.get(i);
                boolean isSelected = recipe.equals(selectedRecipe);
                if (isSelected) {
                    context.fill(20, yPos, 270, yPos + itemHeight, 0x99608C35);
                    context.drawText(textRenderer, Text.literal(recipe), 30, yPos + 5, 0xFFFFB728, false);
                } else {
                    boolean isHovered = mouseX >= 20 && mouseX <= 270 && mouseY >= yPos && mouseY <= yPos + itemHeight;
                    if (isHovered) {
                        context.fill(20, yPos, 270, yPos + itemHeight, 0x553E6428);
                    }
                    context.drawTextWithShadow(textRenderer, Text.literal(recipe), 30, yPos + 5, 0xFFFFFF);
                }
                yPos += itemHeight;
            }
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("▲"), 145, 75, GOLD);
            }
            if (endIndex < filteredRecipes.size()) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("▼"), 145, yPos + 5, GOLD);
            }
        }

        context.fill(treeViewX, treeViewY, treeViewX + treeViewWidth, treeViewY + treeViewHeight, 0xFC271910);
        
        int treeBorderColor = 0xFFDAA520;
        int borderThickness = 2;
        for (int i = 0; i < borderThickness; i++) {
            context.drawBorder(
                treeViewX - i, 
                treeViewY - i, 
                treeViewWidth + i * 2, 
                treeViewHeight + i * 2, 
                treeBorderColor
            );
        }
        
        context.enableScissor(
            treeViewX, 
            treeViewY, 
            treeViewX + treeViewWidth, 
            treeViewY + treeViewHeight
        );
        if (recipeTree != null) {
            renderRecipeTree(context, recipeTree, treeViewX + 10, treeViewY + 10 - treeScrollOffset, 0);
            int totalHeight = getExpandedNodeHeight(recipeTree);
            if (totalHeight > treeViewHeight) {
                if (treeScrollOffset > 0) {
                    context.drawCenteredTextWithShadow(textRenderer, 
                        Text.literal("▲"), 
                        treeViewX + treeViewWidth - 15, 
                        treeViewY + 15, 
                        GOLD);
                }
                if (treeScrollOffset < totalHeight - treeViewHeight + 20) {
                    context.drawCenteredTextWithShadow(textRenderer, 
                        Text.literal("▼"), 
                        treeViewX + treeViewWidth - 15, 
                        treeViewY + treeViewHeight - 15, 
                        GOLD);
                }
                int scrollbarWidth = 12;
                int scrollbarHeight = Math.max(40, treeViewHeight * treeViewHeight / totalHeight);
                int scrollbarY = treeViewY + (int)((treeViewHeight - scrollbarHeight) * ((float)treeScrollOffset / (totalHeight - treeViewHeight + 20)));
                context.fill(treeViewX + treeViewWidth - scrollbarWidth - 4, treeViewY, treeViewX + treeViewWidth - 4, treeViewY + treeViewHeight, 0x55FFFFFF);
                context.fill(treeViewX + treeViewWidth - scrollbarWidth - 4, scrollbarY, treeViewX + treeViewWidth - 4, scrollbarY + scrollbarHeight, 0xFFDAA520);
            }
        } else if (selectedRecipe != null) {
            context.drawTextWithShadow(textRenderer, 
                Text.literal("Loading recipe tree..."), 
                treeViewX + 20, 
                treeViewY + 20, 
                0xAAAAAA);
        } else {
            context.drawTextWithShadow(textRenderer, 
                Text.literal("Select a recipe to preview its tree"), 
                treeViewX + 20, 
                treeViewY + 20, 
                0xAAAAAA);
        }
        context.disableScissor();
    }

    private void renderPositioningTab(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("Widget Positioning"), 
            width / 2, 
            30, 
            0xE0E0E0);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("◆ Click and drag the widget preview to position it ◆"), 
            width / 2, 
            55, 
            GOLD);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("Current Position: X=" + widgetPositionX + ", Y=" + widgetPositionY), 
            width / 2, 
            75, 
            0xFFFFFF);
        int previewWidth = 250;
        int previewHeight = 150;
        for (int x = 0; x < width; x += 50) {
            context.fill(x, 0, x + 1, height, 0x22FFFFFF);
        }
        for (int y = 0; y < height; y += 50) {
            context.fill(0, y, width, y + 1, 0x22FFFFFF);
        }
        context.fill(widgetPositionX, widgetPositionY, widgetPositionX + previewWidth, widgetPositionY + previewHeight, 0xFC271910);
        context.fill(widgetPositionX, widgetPositionY, widgetPositionX + previewWidth, widgetPositionY + 20, 0xCC2C4A1B);
        int borderColor = isDraggingWidget ? 0xFFFFDD00 : 0xFFDAA520;
        int borderThickness = 2;
        for (int i = 0; i < borderThickness; i++) {
            context.drawBorder(
                widgetPositionX - i, 
                widgetPositionY - i, 
                previewWidth + i * 2, 
                previewHeight + i * 2, 
                borderColor
            );
        }
        String dragIcon = isDraggingWidget ? "✦" : "✥";
        context.drawTextWithShadow(textRenderer,
            Text.literal("Recipe Widget " + dragIcon), 
            widgetPositionX + 10, 
            widgetPositionY + 6, 
            GOLD);
        if (selectedRecipe != null) {
            context.drawTextWithShadow(textRenderer,
                Text.literal("Recipe: " + selectedRecipe), 
                widgetPositionX + 10, 
                widgetPositionY + 30, 
                GOLD);
            if (recipeTree != null) {
                int y = widgetPositionY + 50;
                int x = widgetPositionX + 15;
                renderPreviewRecipeTree(context, recipeTree, x, y, 0);
            }
        } else {
            context.drawTextWithShadow(textRenderer,
                Text.literal("No recipe selected"), 
                widgetPositionX + 10, 
                widgetPositionY + 60, 
                0xFFFFFF);
        }
        if (isDraggingWidget) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal("Release to place widget"), 
                widgetPositionX + previewWidth / 2, 
                widgetPositionY + previewHeight - 15, 
                0xFFAAAAFF);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal("⇦ Drag to reposition ⇨"), 
                widgetPositionX + previewWidth / 2, 
                widgetPositionY + previewHeight - 15, 
                0xFFAAAAAA);
        }
    }

    private int renderPreviewRecipeTree(DrawContext context, RecipeManager.RecipeNode node, int x, int y, int level) {
        if (node == null || level > 2) return y;
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Integer> resources = ResourcesManager.getInstance().getAllResources();
        int indent = level * RECIPE_LEVEL_INDENT;
        int available = resources.getOrDefault(node.name, 0);
        boolean hasEnough = available >= node.amount;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int bgColor = 0x99271910;
        int nodeWidth = 220 - indent;
        context.fill(x + indent, y, x + indent + nodeWidth, y + 16, bgColor);
        if (hasChildren) {
            String expandIcon = level == 0 ? "▼" : "▶";
            context.drawText(
                client.textRenderer,
                expandIcon,
                x + indent + 5,
                y + 4,
                0xFFFFFFFF,
                true
            );
        }
        context.drawText(
            client.textRenderer,
            node.amount + "×",
            x + indent + (hasChildren ? 25 : 5),
            y + 4,
            GOLD,
            true
        );
        int amountWidth = client.textRenderer.getWidth(node.amount + "×");
        Text itemName = Text.literal(node.name)
            .setStyle(Style.EMPTY.withColor(level == 0 ? Formatting.GOLD : (hasEnough ? Formatting.GREEN : Formatting.WHITE))
            .withBold(level == 0));
        context.drawText(
            client.textRenderer,
            itemName,
            x + indent + amountWidth + (hasChildren ? 30 : 10),
            y + 4,
            0xFFFFFFFF,
            false
        );
        y += 18;
        if (level == 0 && hasChildren && node.ingredients.size() > 0) {
            int childLimit = Math.min(node.ingredients.size(), 2);
            for (int i = 0; i < childLimit; i++) {
                RecipeManager.RecipeNode child = node.ingredients.get(i);
                y = renderPreviewRecipeTree(context, child, x, y, level + 1);
            }
            if (node.ingredients.size() > childLimit) {
                context.drawText(
                    client.textRenderer,
                    "... and " + (node.ingredients.size() - childLimit) + " more items",
                    x + indent + 25,
                    y + 4,
                    0xFFAAAAAA,
                    true
                );
                y += 18;
            }
        }
        return y;
    }

    private int renderRecipeTree(DrawContext context, RecipeManager.RecipeNode node, int x, int y, int level) {
        if (node == null) return y;
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, Integer> resources = ResourcesManager.getInstance().getAllResources();
        int indent = level * RECIPE_LEVEL_INDENT;
        int available = resources.getOrDefault(node.name, 0);
        boolean hasEnough = available >= node.amount;
        String nodeKey = SandboxWidget.getNodeKey(node);
        boolean isExpanded = widget.isNodeExpanded(nodeKey);
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int bgColor = 0x99271910;
        int hoverEffect = isMouseOverNode(client.mouse.getX(), client.mouse.getY(), x + indent, y, treeViewWidth - 20 - indent, 16) ? 0x22FFFFFF : 0;
        int nodeWidth = treeViewWidth - 20 - indent;
        context.fill(x + indent, y, x + indent + nodeWidth, y + 16, bgColor + hoverEffect);
        int borderColor = hasEnough ? 0x88608C35 : 0x88FF5555;
        context.drawBorder(x + indent, y, nodeWidth, 16, borderColor);
        if (hasChildren) {
            String expandIcon = isExpanded ? "▼" : "▶";
            context.drawText(
                client.textRenderer,
                expandIcon,
                x + indent + 5,
                y + 4,
                0xFFFFFFFF,
                true
            );
        }
        context.drawText(
            client.textRenderer,
            node.amount + "×",
            x + indent + (hasChildren ? 25 : 10),
            y + 4,
            GOLD,
            true
        );
        int amountWidth = client.textRenderer.getWidth(node.amount + "×");
        Formatting nameColor;
        if (level == 0) {
            nameColor = Formatting.GOLD;
        } else {
            nameColor = hasEnough ? Formatting.GREEN : Formatting.WHITE;
        }
        Text itemName = Text.literal(node.name)
            .setStyle(Style.EMPTY.withColor(nameColor)
            .withBold(level == 0));
        context.drawText(
            client.textRenderer,
            itemName,
            x + indent + amountWidth + (hasChildren ? 30 : 15),
            y + 4,
            0xFFFFFFFF,
            false
        );
        if (node.ingredients == null || node.ingredients.isEmpty()) {
            String statusText = available + "/" + node.amount;
            Formatting statusColor = hasEnough ? Formatting.GREEN : Formatting.RED;
            Text statusDisplay = Text.literal(statusText)
                .setStyle(Style.EMPTY.withColor(statusColor));
            int statusWidth = client.textRenderer.getWidth(statusText);
            context.drawText(
                client.textRenderer,
                statusDisplay,
                x + indent + nodeWidth - statusWidth - 5,
                y + 4,
                0xFFFFFFFF,
                false
            );
        }
        y += 18;
        if (hasChildren && isExpanded && node.ingredients.size() > 0) {
            int childrenHeight = getExpandedNodeHeight(node) - 18;
            if (node.ingredients.size() > 1) {
                context.fill(x + indent + 10, y, x + indent + 11, y + childrenHeight - 9, 0xAABBBBBB);
            }
            for (int i = 0; i < node.ingredients.size(); i++) {
                RecipeManager.RecipeNode child = node.ingredients.get(i);
                boolean isLastChild = (i == node.ingredients.size() - 1);
                context.fill(x + indent + 11, y + 8, x + indent + RECIPE_LEVEL_INDENT, y + 9, 0xAABBBBBB);
                if (!isLastChild) {
                    context.fill(x + indent + 10, y + 8, x + indent + 11, y + 9, 0xAABBBBBB);
                } else {
                    context.fill(x + indent + 10, y, x + indent + 11, y + 9, 0xAABBBBBB);
                }
                y = renderRecipeTree(context, child, x, y, level + 1);
            }
        }
        return y;
    }

    private boolean isMouseOverNode(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (currentTab == Tab.RECIPE_SELECTION && mouseX >= 20 && mouseX <= 270 && mouseY >= 80) {
            if (verticalAmount < 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (verticalAmount > 0 && scrollOffset + MAX_RECIPES_SHOWN < filteredRecipes.size()) {
                scrollOffset++;
                return true;
            }
        }
        if (currentTab == Tab.RECIPE_SELECTION && 
            mouseX >= treeViewX && mouseX <= treeViewX + treeViewWidth && 
            mouseY >= treeViewY && mouseY <= treeViewY + treeViewHeight) {
            if (verticalAmount != 0 && recipeTree != null) {
                int totalTreeHeight = getExpandedNodeHeight(recipeTree);
                int visibleHeight = treeViewHeight - 20;
                int scrollAmount = (int)(verticalAmount * -12);
                treeScrollOffset += scrollAmount;
                int maxScroll = Math.max(0, totalTreeHeight - visibleHeight);
                treeScrollOffset = Math.max(0, Math.min(treeScrollOffset, maxScroll));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (currentTab == Tab.POSITIONING) {
            int previewWidth = 250;
            int previewHeight = 150;
            if (mouseX >= widgetPositionX && mouseX <= widgetPositionX + previewWidth &&
                mouseY >= widgetPositionY && mouseY <= widgetPositionY + previewHeight) {
                isDraggingWidget = true;
                dragOffsetX = (int) mouseX - widgetPositionX;
                dragOffsetY = (int) mouseY - widgetPositionY;
                return true;
            }
        }
        else if (currentTab == Tab.RECIPE_SELECTION) {
            if (mouseX >= 20 && mouseX <= 270 && mouseY >= 80 && mouseY <= 85 + MAX_RECIPES_SHOWN * 20) {
                int recipeIndex = (int) ((mouseY - 80) / 20);
                int actualIndex = scrollOffset + recipeIndex;
                if (actualIndex >= 0 && actualIndex < filteredRecipes.size() && recipeIndex < MAX_RECIPES_SHOWN) {
                    selectedRecipe = filteredRecipes.get(actualIndex);
                    ResourcesManager.RemainingResponse response = resourcesManager.getRemainingIngredients(selectedRecipe, craftAmount);
                    recipeTree = convertResourceNodeToRecipeNode(response.full_recipe);
                    if (recipeTree != null) {
                        widget.setNodeExpansion(SandboxWidget.getNodeKey(recipeTree), true);
                    }
                    return true;
                }
            }
            if (recipeTree != null && mouseX >= treeViewX && mouseX <= treeViewX + treeViewWidth && 
                mouseY >= treeViewY && mouseY <= treeViewY + treeViewHeight) {
                return handleTreeNodeClick(mouseX, mouseY);
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (isDraggingWidget && currentTab == Tab.POSITIONING) {
            widgetPositionX = (int) mouseX - dragOffsetX;
            widgetPositionY = (int) mouseY - dragOffsetY;
            widgetPositionX = Math.max(0, Math.min(width - 100, widgetPositionX));
            widgetPositionY = Math.max(0, Math.min(height - 100, widgetPositionY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingWidget) {
            isDraggingWidget = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleTreeNodeClick(double mouseX, double mouseY) {
        int x = treeViewX + 10;
        int y = treeViewY + 10 - treeScrollOffset;
        return checkNodeClick(recipeTree, mouseX, mouseY, x, y, 0);
    }

    private boolean checkNodeClick(RecipeManager.RecipeNode node, double mouseX, double mouseY, int x, int y, int level) {
        if (node == null) return false;
        
        if (y + 18 < treeViewY || y > treeViewY + treeViewHeight) {
            y += 18;
            if (node.ingredients != null && !node.ingredients.isEmpty() && 
                widget.isNodeExpanded(SandboxWidget.getNodeKey(node))) {
                for (RecipeManager.RecipeNode child : node.ingredients) {
                    boolean childResult = checkNodeClick(child, mouseX, mouseY, x, y, level + 1);
                    if (childResult) return true;
                    y += getExpandedNodeHeight(child);
                }
            }
            return false;
        }
        
        int indent = level * RECIPE_LEVEL_INDENT;
        int nodeHeight = 18;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int nodeWidth = treeViewWidth - 20 - indent;
        
        if (mouseY >= y && mouseY <= y + nodeHeight && 
            mouseY >= treeViewY && mouseY <= treeViewY + treeViewHeight) {
            if (mouseX >= x + indent && mouseX <= x + indent + nodeWidth) {
                String nodeKey = SandboxWidget.getNodeKey(node);
                if (hasChildren && mouseX <= x + indent + 25) {
                    widget.toggleNodeExpansion(nodeKey);
                    return true;
                } 
                else if (hasChildren) {
                    widget.toggleNodeExpansion(nodeKey);
                    return true;
                }
                else {
                    MinecraftClient client = MinecraftClient.getInstance();
                    Map<String, Integer> resources = ResourcesManager.getInstance().getAllResources();
                    int available = resources.getOrDefault(node.name, 0);
                    boolean hasEnough = available >= node.amount;
                    Text message = Text.literal("You have " + available + "/" + node.amount + " of " + node.name)
                        .setStyle(Style.EMPTY.withColor(hasEnough ? Formatting.GREEN : Formatting.RED));
                    if (client.player != null) {
                        client.player.sendMessage(message, true);
                    }
                    return true;
                }
            }
        }
        
        y += nodeHeight;
        
        if (hasChildren && widget.isNodeExpanded(SandboxWidget.getNodeKey(node))) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                if (checkNodeClick(child, mouseX, mouseY, x, y, level + 1)) {
                    return true;
                }
                y += getExpandedNodeHeight(child);
            }
        }
        return false;
    }

    private int getExpandedNodeHeight(RecipeManager.RecipeNode node) {
        if (node == null) return 0;
        int height = 18;
        if (node.ingredients != null && !node.ingredients.isEmpty() && 
            widget.isNodeExpanded(SandboxWidget.getNodeKey(node))) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                height += getExpandedNodeHeight(child);
            }
        }
        return height;
    }

    private void applyConfiguration() {
        if (currentTab == Tab.RECIPE_SELECTION && selectedRecipe != null) {
            widget.setSelectedRecipe(selectedRecipe);
            widget.setCraftAmount(craftAmount);
        }
        widget.setWidgetPosition(widgetPositionX, widgetPositionY);
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            Text message = Text.literal("Widget configuration applied!")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
            client.player.sendMessage(message, true);
        }
    }

    private void saveWidgetConfiguration() {
        widget.saveConfiguration();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            Text message = Text.literal("Widget configuration saved!")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
            client.player.sendMessage(message, true);
        }
    }

    @Override
    public void close() {
        if (widget.getWidgetX() != widgetPositionX || widget.getWidgetY() != widgetPositionY) {
            widget.setWidgetPosition(widgetPositionX, widgetPositionY);
        }
        if (widget.getCraftAmount() != craftAmount) {
            widget.setCraftAmount(craftAmount);
        }
        widget.saveConfiguration();
        super.close();
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
}
