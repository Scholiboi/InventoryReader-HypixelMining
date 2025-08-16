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
    private boolean resizing = false;
    private int resizeStartX, resizeStartY;
    private int initialWidth, initialHeight;
    private int initialWidgetX, initialWidgetY;
    private int previewWidthOverride = -1;
    private int previewHeightOverride = -1;
    private enum ResizeCorner { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private ResizeCorner activeCorner = ResizeCorner.NONE;
    private static final int RESIZE_HANDLE_SIZE = 10;
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

    public WidgetCustomizationMenu(boolean openPositioningTab) {
        this();
        if (openPositioningTab) {
            this.currentTab = Tab.POSITIONING;
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
        Text mainHeading = Text.literal("Skyblock Resource Calculator Widget")
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
            renderRecipeTree(context, recipeTree, treeViewX + 10, treeViewY + 10 - treeScrollOffset, 0, selectedRecipe);
            int totalHeight = getExpandedNodeHeight(recipeTree, selectedRecipe);
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
    int previewWidth = getPreviewWidth();
    int previewHeight = getPreviewHeight();
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
        drawFittedTextWithShadow(context,
            Text.literal("Recipe Widget " + dragIcon),
            widgetPositionX + 10,
            widgetPositionY + 6,
            GOLD,
            Math.max(10, previewWidth - 20)
        );
    if (selectedRecipe != null) {
            drawFittedTextWithShadow(context,
                Text.literal("Recipe: " + selectedRecipe),
                widgetPositionX + 10,
                widgetPositionY + 30,
                GOLD,
                Math.max(10, previewWidth - 20)
            );
            if (recipeTree != null) {
                int contentX = widgetPositionX + 10;
                int contentY = widgetPositionY + 50;
                int contentWidth = Math.max(20, previewWidth - 20);
                int totalLines = getExpandedNodeHeight(recipeTree, selectedRecipe) / 16; 
                totalLines = Math.max(totalLines, 1);
                int availableHeight = Math.max(10, previewHeight - (contentY - widgetPositionY) - 10);
                int lineHeight = Math.min(16, Math.max(6, availableHeight / totalLines));

                int maxDepth = getExpandedMaxDepth(recipeTree, selectedRecipe, 0);
                int baseIndentUnit = Math.max(4, Math.round(RECIPE_LEVEL_INDENT * Math.max(0.3f, lineHeight / 16.0f)));
                int minNodeWidth = 60;
                int maxAllowedIndent = Math.max(2, (contentWidth - minNodeWidth) / Math.max(1, maxDepth));
                int indentUnit = Math.max(2, Math.min(baseIndentUnit, maxAllowedIndent));

                int treeEndY = renderStaticWidgetStyleTreeScaled(context, recipeTree, contentX, contentY, 0, contentWidth, selectedRecipe, lineHeight, indentUnit);

                int dividerY = treeEndY + 4;
                if (dividerY < widgetPositionY + previewHeight - 5) {
                    context.fill(widgetPositionX, dividerY, widgetPositionX + previewWidth, dividerY + 1, 0x99608C35);
                    int messageY = dividerY + 6;
                    int maxMessageY = widgetPositionY + previewHeight - 5;
                    drawCraftablePreview(context, widgetPositionX, messageY, previewWidth, maxMessageY);
                }
            }
        } else {
            drawFittedTextWithShadow(context,
                Text.literal("No recipe selected"),
                widgetPositionX + 10,
                widgetPositionY + 60,
                0xFFFFFF,
                Math.max(10, previewWidth - 20)
            );
        }
        if (isDraggingWidget || resizing) {
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal(resizing ? "Release to resize" : "Release to place widget"), 
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

    drawHandle(context, widgetPositionX - RESIZE_HANDLE_SIZE/2, widgetPositionY - RESIZE_HANDLE_SIZE/2);
    drawHandle(context, widgetPositionX + previewWidth - RESIZE_HANDLE_SIZE/2, widgetPositionY - RESIZE_HANDLE_SIZE/2);
    drawHandle(context, widgetPositionX - RESIZE_HANDLE_SIZE/2, widgetPositionY + previewHeight - RESIZE_HANDLE_SIZE/2);
    drawHandle(context, widgetPositionX + previewWidth - RESIZE_HANDLE_SIZE/2, widgetPositionY + previewHeight - RESIZE_HANDLE_SIZE/2);
    }

    

    private int renderStaticWidgetStyleTreeScaled(DrawContext context, RecipeManager.RecipeNode node, int x, int y, int level, int availableWidth, String pathKey, int lineHeight, int indentUnit) {
        if (node == null) return y;
        int indent = level * indentUnit;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        boolean hasEnough = (node.amount == 0);
        int nodeWidth = Math.max(40, availableWidth - indent);

        int bgColor = 0x99271910;
        context.fill(x + indent, y, x + indent + nodeWidth, y + lineHeight, bgColor);
        int borderColor = hasEnough ? 0x88608C35 : 0x88FF5555;
        context.drawBorder(x + indent, y, nodeWidth, lineHeight, borderColor);

        String nextKey = SandboxWidget.makePathKey(pathKey, node.name);
        boolean isExpanded = widget.isNodeExpanded(nextKey);
        if (hasChildren) {
            context.drawText(
                textRenderer,
                isExpanded ? "▼" : "▶",
                x + indent + Math.max(3, Math.round(5 * (lineHeight / 16.0f))),
                y + Math.max(1, Math.round(4 * (lineHeight / 16.0f))),
                0xFFFFFFFF,
                false
            );
        }

        int nameX = x + indent + (hasChildren ? Math.max(14, Math.round(25 * (lineHeight / 16.0f))) : Math.max(6, Math.round(10 * (lineHeight / 16.0f))));
        int textColor = (level == 0) ? GOLD : (hasEnough ? 0xFFFFFFFF : 0xFFFF6B6B);
        boolean isBold = (level == 0);

        String amountText = node.amount + "× ";
        int amountColor = hasEnough ? 0xFF6EFF6E : 0xFFFF6B6B;
        int amountWidth = textRenderer.getWidth(amountText);
        Text itemName = Text.literal(node.name).setStyle(Style.EMPTY.withColor(textColor).withBold(isBold));
        int itemWidth = textRenderer.getWidth(itemName);
        int totalWidth = amountWidth + itemWidth;
        int maxTextWidth = Math.max(10, nodeWidth - (hasChildren ? Math.max(14, Math.round(25 * (lineHeight / 16.0f))) : Math.max(6, Math.round(10 * (lineHeight / 16.0f)))));
        int adjustedMax = (int)Math.floor(maxTextWidth / Math.max(0.01f, (lineHeight / 16.0f)));
        float textScaleLocal = totalWidth > adjustedMax ? (float)adjustedMax / (float)totalWidth : 1.0f;
        float textScale = Math.min(1.0f, textScaleLocal) * (lineHeight / 16.0f);
        context.getMatrices().push();
        context.getMatrices().translate(nameX, y + Math.max(1, Math.round(4 * (lineHeight / 16.0f))), 0);
        context.getMatrices().scale(textScale, textScale, 1);
        context.drawText(textRenderer, Text.literal(amountText), 0, 0, amountColor, false);
        context.drawText(textRenderer, itemName, amountWidth, 0, 0xFFFFFFFF, false);
        context.getMatrices().pop();

        y += lineHeight;

        if (hasChildren && isExpanded) {
            int lineColor = 0xFF777777;
            for (int i = 0; i < node.ingredients.size(); i++) {
                RecipeManager.RecipeNode child = node.ingredients.get(i);
                int lineStartX = x + indent + Math.max(4, Math.round(6 * (lineHeight / 16.0f)));
                int vertLineY = y;
                int childIndentX = x + indent + indentUnit;
                int vertLen = Math.max(3, Math.round(8 * (lineHeight / 16.0f)));
                context.fill(lineStartX, vertLineY, lineStartX + 1, vertLineY + vertLen, lineColor);
                context.fill(lineStartX, vertLineY + vertLen, childIndentX, vertLineY + vertLen + 1, lineColor);
                y = renderStaticWidgetStyleTreeScaled(context, child, x, y, level + 1, availableWidth, nextKey, lineHeight, indentUnit);
            }
        }
        return y;
    }

    private void drawCraftablePreview(DrawContext context, int x, int y, int width, int maxY) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<String> msgs = SandboxWidget.getInstance().getMessagesSnapshot();
        if (msgs == null || msgs.isEmpty()) {
            Text header = Text.literal("Craftable -").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true));
            if (y + 10 <= maxY) context.drawText(client.textRenderer, header, x + 5, y, 0xFFFFFFFF, false);
            return;
        }
        int baseHeader = 13;
        int baseLine = 10;
        int availableHeight = Math.max(0, maxY - y);
        int lines = 1;
        for (String m : msgs) {
            if (!"Craftable -".equals(m)) lines++;
        }
        int desiredHeight = baseHeader + Math.max(0, (lines - 1) * baseLine);
        float scale = desiredHeight > 0 ? Math.min(1.0f, Math.max(0.4f, (float)availableHeight / (float)desiredHeight)) : 1.0f;

        if (y + Math.round(baseLine * scale) <= maxY) {
            Text header = Text.literal("Craftable -").setStyle(Style.EMPTY.withColor(Formatting.YELLOW).withBold(true));
            context.getMatrices().push();
            context.getMatrices().translate(x + 5, y, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawText(client.textRenderer, header, 0, 0, 0xFFFFFFFF, false);
            context.getMatrices().pop();
        } else {
            return;
        }
        if (msgs.size() == 1 && msgs.get(0).equals("Craftable -")) return;
        y += Math.round(baseHeader * scale);

        List<String> messagesCopy = new ArrayList<>(msgs);
        messagesCopy.sort((a, b) -> {
            if (a.equals("Craftable -")) return -1;
            if (b.equals("Craftable -")) return 1;
            try {
                int amountA = extractAmount(a);
                int amountB = extractAmount(b);
                return Integer.compare(amountB, amountA);
            } catch (Exception e) { return 0; }
        });
        int unscaledWrapWidth = Math.max(10, (int)Math.floor((width - 15) / Math.max(0.01f, scale)));
        for (String message : messagesCopy) {
            if (message.equals("Craftable -")) continue;
            int textColor = message.startsWith("   ") ? 0xFFFF9D00 : 0xFFFFFFFF;
            String[] words = message.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (client.textRenderer.getWidth(line.toString() + word) > unscaledWrapWidth) {
                    if (y + Math.round(baseLine * scale) > maxY) return;
                    context.getMatrices().push();
                    context.getMatrices().translate(x + 5, y, 0);
                    context.getMatrices().scale(scale, scale, 1);
                    context.drawText(client.textRenderer, line.toString(), 0, 0, textColor, false);
                    context.getMatrices().pop();
                    y += Math.round(baseLine * scale);
                    line = new StringBuilder(message.startsWith("   ") ? "      " : "   ").append(word).append(" ");
                } else {
                    line.append(word).append(" ");
                }
            }
            if (line.length() > 0) {
                if (y + Math.round((baseLine - 1) * scale) > maxY) return;
                context.getMatrices().push();
                context.getMatrices().translate(x + 5, y, 0);
                context.getMatrices().scale(scale, scale, 1);
                context.drawText(client.textRenderer, line.toString(), 0, 0, textColor, false);
                context.getMatrices().pop();
                y += Math.round((baseLine - 1) * scale);
            }
        }
    }

    private int extractAmount(String message) {
        try {
            int idx = message.indexOf('×');
            if (idx > 0) {
                String amt = message.substring(0, idx).trim();
                return Integer.parseInt(amt);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int getExpandedMaxDepth(RecipeManager.RecipeNode node, String pathKey, int level) {
        if (node == null) return level;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        String nextKey = SandboxWidget.makePathKey(pathKey, node.name);
        boolean isExpanded = widget.isNodeExpanded(nextKey);
        int max = level;
        if (hasChildren && isExpanded) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
                max = Math.max(max, getExpandedMaxDepth(child, nextKey, level + 1));
            }
        }
        return max;
    }

    private void drawHandle(DrawContext context, int x, int y) {
        context.fill(x, y, x + RESIZE_HANDLE_SIZE, y + RESIZE_HANDLE_SIZE, 0xFFFFB728);
    }

    private void drawFittedTextWithShadow(DrawContext context, Text text, int x, int y, int color, int maxWidth) {
        int width = textRenderer.getWidth(text);
        float scale = width > maxWidth ? (float)maxWidth / (float)width : 1.0f;
        scale = Math.min(scale, getMaxTextScale());
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawTextWithShadow(textRenderer, text, 0, 0, color);
        context.getMatrices().pop();
    }

    private float getMaxTextScale() {
        return 1.0f;
    }

    

    private int renderRecipeTree(DrawContext context, RecipeManager.RecipeNode node, int x, int y, int level, String pathKey) {
        if (node == null) return y;
        MinecraftClient client = MinecraftClient.getInstance();
        int indent = level * RECIPE_LEVEL_INDENT;
        boolean hasEnough = (node.amount == 0);
        String nodeKey = SandboxWidget.makePathKey(pathKey, node.name);
        boolean isExpanded = widget.isNodeExpanded(nodeKey);
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int bgColor = 0x99271910;
        
        int mouseX = (int)(client.mouse.getX() / client.getWindow().getScaleFactor());
        int mouseY = (int)(client.mouse.getY() / client.getWindow().getScaleFactor());
        boolean isHovered = mouseX >= x + indent && mouseX <= x + indent + (treeViewWidth - 20 - indent) && 
                           mouseY >= y && mouseY <= y + 16;
        int hoverEffect = isHovered ? 0x22FFFFFF : 0;
        
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
                
                y = renderRecipeTree(context, child, x, y, level + 1, nodeKey);
            }
        }
        return y;
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
                int totalTreeHeight = getExpandedNodeHeight(recipeTree, selectedRecipe);
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
        if (currentTab == Tab.POSITIONING) {
            int previewWidth = getPreviewWidth();
            int previewHeight = getPreviewHeight();
            ResizeCorner corner = getCornerHandle(mouseX, mouseY, previewWidth, previewHeight);
            if (corner != ResizeCorner.NONE) {
                resizing = true;
                activeCorner = corner;
                resizeStartX = (int) mouseX;
                resizeStartY = (int) mouseY;
                initialWidth = previewWidth;
                initialHeight = previewHeight;
                initialWidgetX = widgetPositionX;
                initialWidgetY = widgetPositionY;
                return true;
            }
            if (mouseX >= widgetPositionX && mouseX <= widgetPositionX + previewWidth &&
                mouseY >= widgetPositionY && mouseY <= widgetPositionY + previewHeight) {
                isDraggingWidget = true;
                dragOffsetX = (int) mouseX - widgetPositionX;
                dragOffsetY = (int) mouseY - widgetPositionY;
                return true;
            }
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
        if (currentTab == Tab.RECIPE_SELECTION) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
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
    return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (currentTab == Tab.POSITIONING) {
            if (isDraggingWidget) {
                widgetPositionX = (int) mouseX - dragOffsetX;
                widgetPositionY = (int) mouseY - dragOffsetY;
                widgetPositionX = Math.max(0, Math.min(width - 50, widgetPositionX));
                widgetPositionY = Math.max(0, Math.min(height - 50, widgetPositionY));
                return true;
            } else if (resizing) {
                int dx = (int) mouseX - resizeStartX;
                int dy = (int) mouseY - resizeStartY;
                int newWidth = initialWidth;
                int newHeight = initialHeight;
                int newX = initialWidgetX;
                int newY = initialWidgetY;
                int minW = 180;
                int minH = 120;
                switch (activeCorner) {
                    case TOP_LEFT -> {
                        newWidth = initialWidth - dx;
                        newHeight = initialHeight - dy;
                        newX = initialWidgetX + dx;
                        newY = initialWidgetY + dy;
                        if (newWidth < minW) {
                            newX = initialWidgetX + (initialWidth - minW);
                            newWidth = minW;
                        }
                        if (newHeight < minH) {
                            newY = initialWidgetY + (initialHeight - minH);
                            newHeight = minH;
                        }
                    }
                    case TOP_RIGHT -> {
                        newWidth = initialWidth + dx;
                        newHeight = initialHeight - dy;
                        newY = initialWidgetY + dy;
                        if (newWidth < minW) {
                            newWidth = minW;
                        }
                        if (newHeight < minH) {
                            newY = initialWidgetY + (initialHeight - minH);
                            newHeight = minH;
                        }
                    }
                    case BOTTOM_LEFT -> {
                        newWidth = initialWidth - dx;
                        newHeight = initialHeight + dy;
                        newX = initialWidgetX + dx;
                        if (newWidth < minW) {
                            newX = initialWidgetX + (initialWidth - minW);
                            newWidth = minW;
                        }
                        if (newHeight < minH) {
                            newHeight = minH;
                        }
                    }
                    case BOTTOM_RIGHT -> {
                        newWidth = initialWidth + dx;
                        newHeight = initialHeight + dy;
                        if (newWidth < minW) newWidth = minW;
                        if (newHeight < minH) newHeight = minH;
                    }
                    case NONE -> {
                        
                    }
                }
                widgetPositionX = Math.max(0, newX);
                widgetPositionY = Math.max(0, newY);
                previewWidthOverride = newWidth;
                previewHeightOverride = newHeight;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (currentTab == Tab.POSITIONING) {
            if (isDraggingWidget || resizing) {
                isDraggingWidget = false;
                resizing = false;
                activeCorner = ResizeCorner.NONE;
                int commitW = getPreviewWidth();
                int commitH = getPreviewHeight();
                widget.setWidgetSize(commitW, commitH);
                widget.setWidgetPosition(widgetPositionX, widgetPositionY);
                widget.saveConfiguration();
                previewWidthOverride = -1;
                previewHeightOverride = -1;
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getPreviewWidth() {
        return previewWidthOverride > 0 ? previewWidthOverride : widget.getWidgetWidth();
    }
    private int getPreviewHeight() {
        return previewHeightOverride > 0 ? previewHeightOverride : widget.getWidgetHeight();
    }

    private ResizeCorner getCornerHandle(double mouseX, double mouseY, int previewWidth, int previewHeight) {
        if (isInHandle(mouseX, mouseY, widgetPositionX, widgetPositionY)) return ResizeCorner.TOP_LEFT;
        if (isInHandle(mouseX, mouseY, widgetPositionX + previewWidth, widgetPositionY)) return ResizeCorner.TOP_RIGHT;
        if (isInHandle(mouseX, mouseY, widgetPositionX, widgetPositionY + previewHeight)) return ResizeCorner.BOTTOM_LEFT;
        if (isInHandle(mouseX, mouseY, widgetPositionX + previewWidth, widgetPositionY + previewHeight)) return ResizeCorner.BOTTOM_RIGHT;
        return ResizeCorner.NONE;
    }
    private boolean isInHandle(double mouseX, double mouseY, int cx, int cy) {
        int x = cx - RESIZE_HANDLE_SIZE/2;
        int y = cy - RESIZE_HANDLE_SIZE/2;
        return mouseX >= x && mouseX <= x + RESIZE_HANDLE_SIZE && mouseY >= y && mouseY <= y + RESIZE_HANDLE_SIZE;
    }

    private boolean handleTreeNodeClick(double mouseX, double mouseY) {
        int x = treeViewX + 10;
        int y = treeViewY + 10 - treeScrollOffset;
        return checkNodeClick(recipeTree, mouseX, mouseY, x, y, 0, selectedRecipe);
    }

    private boolean checkNodeClick(RecipeManager.RecipeNode node, double mouseX, double mouseY, int x, int y, int level, String pathKey) {
        if (node == null) return false;
        
        if (y + 16 < treeViewY || y > treeViewY + treeViewHeight) {
            y += 16;
            if (node.ingredients != null && !node.ingredients.isEmpty() && 
                widget.isNodeExpanded(SandboxWidget.makePathKey(pathKey, node.name))) {
                for (RecipeManager.RecipeNode child : node.ingredients) {
                    boolean childResult = checkNodeClick(child, mouseX, mouseY, x, y, level + 1, SandboxWidget.makePathKey(pathKey, node.name));
                    if (childResult) return true;
                    y += getExpandedNodeHeight(child, SandboxWidget.makePathKey(pathKey, node.name));
                }
            }
            return false;
        }
        
        int indent = level * RECIPE_LEVEL_INDENT;
        int nodeHeight = 16;
        boolean hasChildren = node.ingredients != null && !node.ingredients.isEmpty();
        int nodeWidth = treeViewWidth - 20 - indent;
        
    if (mouseY >= y && mouseY <= y + nodeHeight && 
            mouseY >= treeViewY && mouseY <= treeViewY + treeViewHeight) {
            if (mouseX >= x + indent && mouseX <= x + indent + nodeWidth) {
        String nodeKey = SandboxWidget.makePathKey(pathKey, node.name);
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
        
    if (hasChildren && widget.isNodeExpanded(SandboxWidget.makePathKey(pathKey, node.name))) {
            for (RecipeManager.RecipeNode child : node.ingredients) {
        if (checkNodeClick(child, mouseX, mouseY, x, y, level + 1, SandboxWidget.makePathKey(pathKey, node.name))) {
                    return true;
                }
        y += getExpandedNodeHeight(child, SandboxWidget.makePathKey(pathKey, node.name));
            }
        }
        return false;
    }

    private int getExpandedNodeHeight(RecipeManager.RecipeNode node, String pathKey) {
        if (node == null) return 0;
        int height = 16;
        if (node.ingredients != null && !node.ingredients.isEmpty() && 
        widget.isNodeExpanded(SandboxWidget.makePathKey(pathKey, node.name))) {
        for (RecipeManager.RecipeNode child : node.ingredients) {
        height += getExpandedNodeHeight(child, SandboxWidget.makePathKey(pathKey, node.name));
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
