package inventoryreader.ir;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.client.MinecraftClient;

import java.util.*;
import java.util.stream.Collectors;

public class SandboxViewer extends Screen {

    private static final int PANEL_BG = 0xFF232323;
    private static final int ITEM_BG = 0xFF303030;
    private static final int ITEM_BG_ALT = 0xFF383838;
    private static final int SELECTED_BG = 0xFF5F7FA0;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TITLE_BG = 0xFF2A4153;
    private static final int GOLD = 0xFFFFB728;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFDDDDDD;
    private static final int SUCCESS_GREEN = 0xFF6EFF6E;
    private static final int ERROR_RED = 0xFFFF6B6B;

    public enum Mode {
        RESOURCE_VIEWER,
        RECIPE_VIEWER,
        FORGE_MODE,
        MODIFY_RESOURCES
    }

    private Mode mode = Mode.RESOURCE_VIEWER;

    private final RecipeManager recipeManager = RecipeManager.getInstance();
    private final ResourcesManager resourcesManager = ResourcesManager.getInstance();

    private List<ResourcesManager.ResourceEntry> resources = new ArrayList<>();
    private List<ResourcesManager.ResourceEntry> filteredResources = new ArrayList<>();
    private String resourceSearchTerm = "";

    private List<String> recipeNames = new ArrayList<>();
    private List<String> filteredRecipeNames = new ArrayList<>();
    private String recipeSearchTerm = "";
    private String selectedRecipe = null;
    private RecipeManager.RecipeNode expandedRecipeTree = null;
    private Map<String, Integer> simpleRecipe = null;

    private ResourcesManager.RemainingResponse remainingResult = null;
    private int craftAmount = 1;
    private boolean craftable = false;
    private final List<String> messages = new ArrayList<>();

    private TextFieldWidget searchBox;
    private TextFieldWidget amountField;
    private final Map<String, TextFieldWidget> resourceAmountFields = new HashMap<>();
    private String activeTextField = null;
    private int scrollOffset = 0;

    private final Map<String, Boolean> expandedNodes = new HashMap<>();

    private record ClickableElement(int x, int y, int width, int height, Runnable action) {}
    private final List<ClickableElement> clickableElements = new ArrayList<>();

    private int recipeTreeScrollOffset = 0;
    private int recipeTreeMaxScroll = 0;
    private int forgeTreeScrollOffset = 0;
    private int forgeTreeMaxScroll = 0;

    private Map<String, Integer> modifiedResources = new LinkedHashMap<>();
    private List<ResourcesManager.ResourceEntry> selectedResources = new ArrayList<>();

    public SandboxViewer() {
        super(Text.literal("Hypixel Forge"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        int centerX = this.width / 2;
        int buttonHeight = 20;

        switch (mode) {
            case RESOURCE_VIEWER -> initResourceViewer(buttonHeight);
            case RECIPE_VIEWER -> initRecipeViewer(centerX, buttonHeight);
            case FORGE_MODE -> initForgeMode(centerX, buttonHeight);
            case MODIFY_RESOURCES -> initModifyResources(buttonHeight);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    // Do nothing: disables vanilla haze/dimming
    }

    private void initResourceViewer(int buttonHeight) {
        searchBox = new TextFieldWidget(this.textRenderer, 30, 45, 210, buttonHeight, Text.literal("Search Resources"));
        searchBox.setPlaceholder(Text.literal("Search resources..."));
        searchBox.setChangedListener(this::onResourceSearchChanged);
        this.addDrawableChild(searchBox);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("🔄 Refresh"), button -> loadResources())
            .dimensions(this.width - 120, 45, 90, buttonHeight).build());

        loadResources();
    }

    private void initRecipeViewer(int centerX, int buttonHeight) {
        searchBox = new TextFieldWidget(this.textRenderer, 30, 45, 210, buttonHeight, Text.literal(""));
        searchBox.setPlaceholder(Text.literal("Search recipes..."));
        searchBox.setChangedListener(this::onRecipeSearchChanged);
        this.addDrawableChild(searchBox);

        loadRecipes();
    }

    private void initForgeMode(int centerX, int buttonHeight) {
        // Load resources and recipes first
        loadResources();
        loadRecipes();
        
        searchBox = new TextFieldWidget(this.textRenderer, 30, 45, 190, buttonHeight, Text.literal(""));
        searchBox.setPlaceholder(Text.literal("Search recipes..."));
        searchBox.setChangedListener(this::onRecipeSearchChanged);
        this.addDrawableChild(searchBox);

        // Add HUD overlay button only in Forge Mode
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal(SandboxWidget.getInstance().isEnabled() ? "❌ Disable HUD Overlay" : "✓ Enable HUD Overlay"), 
            button -> {
                boolean isCurrentlyEnabled = SandboxWidget.getInstance().isEnabled();
                SandboxWidget.getInstance().setEnabled(!isCurrentlyEnabled);
                if (!isCurrentlyEnabled && selectedRecipe != null) {
                    SandboxWidget.getInstance().setSelectedRecipe(selectedRecipe);
                }
                button.setMessage(Text.literal(SandboxWidget.getInstance().isEnabled() ? "❌ Disable HUD Overlay" : "✓ Enable HUD Overlay"));
            }
        ).dimensions(this.width - 160, 45, 150, buttonHeight).build());

        if (selectedRecipe != null) {
            amountField = new TextFieldWidget(this.textRenderer, 221, 45, 19, buttonHeight, Text.literal("1"));
            amountField.setText(String.valueOf(craftAmount));
            amountField.setChangedListener(this::onAmountChanged);
            this.addDrawableChild(amountField);

            // if (craftable) {
            //     ButtonWidget craftButton = ButtonWidget.builder(Text.literal("✓ Craft"), button -> craftSelectedRecipe())
            //             .dimensions(rightColumnX + 70, 45, 100, buttonHeight).build();
            //     this.addDrawableChild(craftButton);
            // }
        }
    }

    private void initModifyResources(int buttonHeight) {
        resourceAmountFields.clear();
        activeTextField = null;
        
        searchBox = new TextFieldWidget(this.textRenderer, 30, 45, 210, buttonHeight, Text.literal("Search Resources"));
        searchBox.setPlaceholder(Text.literal("Search resources..."));
        searchBox.setChangedListener(this::onResourceSearchChanged);
        this.addDrawableChild(searchBox);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("💾 Save Changes"), button -> saveResourceChanges())
            .dimensions(this.width - 350, 45, 120, buttonHeight).build());

        loadResources();
    }

    private void saveResourceChanges() {
        ResourcesManager rm = ResourcesManager.getInstance();
        for (ResourcesManager.ResourceEntry entry : selectedResources) {
            rm.setResourceAmount(entry.name, entry.amount);
        }
        selectedResources.clear();
        modifiedResources.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("Resources saved successfully!"), true);
        }
    }

    private void loadResources() {
        if (mode == Mode.MODIFY_RESOURCES) {
            resources = resourcesManager.getAllResourceEntriesIncludingZero();
        } else {
            resources = resourcesManager.getAllResourceEntries();
        }
        filterResources();
    }

    private void loadRecipes() {
        recipeNames = recipeManager.getRecipeNames();
        filterRecipes();

        if (selectedRecipe != null) {
            expandedRecipeTree = recipeManager.expandRecipe(selectedRecipe, craftAmount);
            simpleRecipe = recipeManager.getSimpleRecipe(selectedRecipe, craftAmount);
        }
    }

    private void filterResources() {
        if (mode == Mode.MODIFY_RESOURCES) {
            filteredResources = resources.stream()
                .filter(resource -> resourceSearchTerm.isEmpty() || resource.name.toLowerCase().contains(resourceSearchTerm.toLowerCase()))
                .collect(Collectors.toList());
        } else {
            filteredResources = resources.stream()
                .filter(resource -> resource.amount > 0)
                .filter(resource -> resourceSearchTerm.isEmpty() || resource.name.toLowerCase().contains(resourceSearchTerm.toLowerCase()))
                .collect(Collectors.toList());
        }
    }

    private void filterRecipes() {
        if (recipeSearchTerm.isEmpty()) {
            filteredRecipeNames = new ArrayList<>(recipeNames);
        } else {
            filteredRecipeNames = recipeNames.stream()
                .filter(name -> name.toLowerCase().contains(recipeSearchTerm.toLowerCase()))
                .collect(Collectors.toList());
        }
    }

    private void onResourceSearchChanged(String text) {
        resourceSearchTerm = text;
        filterResources();
    }

    private void onRecipeSearchChanged(String text) {
        recipeSearchTerm = text;
        filterRecipes();
    }

    private void onAmountChanged(String text) {
        try {
            craftAmount = Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            craftAmount = 1;
        }

        if (selectedRecipe != null) {
            expandedRecipeTree = recipeManager.expandRecipe(selectedRecipe, craftAmount);
            simpleRecipe = recipeManager.getSimpleRecipe(selectedRecipe, craftAmount);
            if (mode == Mode.FORGE_MODE) {
                checkRecipeRequirements();
                // Removed this.init() to prevent text field from losing focus and becoming unresponsive
            }
        }
    }

    private void checkRecipeRequirements() {
        if (selectedRecipe == null) return;

        remainingResult = resourcesManager.getRemainingIngredients(selectedRecipe, craftAmount);
        
        InventoryReader.LOGGER.info("Before clearing, messages size: " + messages.size());
        
        messages.clear();
        if (remainingResult.messages != null && !remainingResult.messages.isEmpty()) {
            for (Map.Entry<String, Integer> entry : remainingResult.messages.entrySet()) {
                String message = "You need to craft x" + entry.getValue() + " " + entry.getKey();
                messages.add(message);
                InventoryReader.LOGGER.info("Adding message: " + message);
            }
        }

        craftable = remainingResult.full_recipe.ingredients.stream().allMatch(child -> child.amount <= 0);

        if (messages.isEmpty() && !craftable) {
            String message = "Missing resources to craft " + selectedRecipe;
            messages.add(message);
            InventoryReader.LOGGER.info("Adding default message: " + message);
        }
        
        InventoryReader.LOGGER.info("After populating, messages size: " + messages.size());
    }

    private void selectRecipe(String name) {
        selectedRecipe = name;
        expandedRecipeTree = recipeManager.expandRecipe(name, craftAmount);
        simpleRecipe = recipeManager.getSimpleRecipe(name, craftAmount);
        messages.clear(); 

        if (SandboxWidget.getInstance().isEnabled()) {
            SandboxWidget.getInstance().setSelectedRecipe(name);
        }

        this.init();
        if (mode == Mode.FORGE_MODE) {
            checkRecipeRequirements();
        }
    }

    private void toggleNodeExpanded(String nodePath) {
        expandedNodes.put(nodePath, !expandedNodes.getOrDefault(nodePath, false));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SandboxWidget widget = SandboxWidget.getInstance();
        if (widget.isEnabled() && widget.isRepositioning()) {
            widget.handleMouseClick(mouseX, mouseY);
            return true;
        }

        if (mode == Mode.MODIFY_RESOURCES) {
            String previousActiveField = activeTextField;
            activeTextField = null;

            boolean textFieldClicked = false;
            for (Map.Entry<String, TextFieldWidget> entry : resourceAmountFields.entrySet()) {
                TextFieldWidget field = entry.getValue();
                if (field.isMouseOver(mouseX, mouseY)) {
                    field.setFocused(true);
                    field.mouseClicked(mouseX, mouseY, button);
                    textFieldClicked = true;
                    activeTextField = entry.getKey();
                    
                    if (!entry.getKey().equals(previousActiveField)) {
                        field.setSelectionStart(0);
                        field.setSelectionEnd(field.getText().length());
                    }
                } else {
                    field.setFocused(false);
                }
            }
            
            if (textFieldClicked) {
                return true;
            }
        }
        
        for (ClickableElement element : clickableElements) {
            if (mouseX >= element.x && mouseX < element.x + element.width &&
                mouseY >= element.y && mouseY < element.y + element.height) {
                element.action.run();
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == Mode.MODIFY_RESOURCES && activeTextField != null) {
            TextFieldWidget field = resourceAmountFields.get(activeTextField);
            if (field != null && field.isFocused()) {
                if (keyCode == 258) {
                    boolean shiftPressed = (modifiers & 1) != 0;
                    moveToNextTextField(shiftPressed);
                    return true;
                }
                
                if (keyCode == 257 || keyCode == 335) {
                    field.setFocused(false);
                    moveToNextTextField(false);
                    return true;
                }
                
                return field.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private void moveToNextTextField(boolean reverse) {
        if (filteredResources.isEmpty() || activeTextField == null) return;

        int maxVisibleItems = getResourceMaxVisibleItems();
        int startIndex = Math.min(scrollOffset, Math.max(0, filteredResources.size() - maxVisibleItems));
        int endIndex = Math.min(startIndex + maxVisibleItems, filteredResources.size());
        
        List<String> visibleResources = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            visibleResources.add(filteredResources.get(i).name);
        }
        
        if (visibleResources.isEmpty()) return;

        int currentIndex = visibleResources.indexOf(activeTextField);
        if (currentIndex == -1) {
            currentIndex = 0;
        } else {
            if (reverse) {
                currentIndex = (currentIndex - 1 + visibleResources.size()) % visibleResources.size();
            } else {
                currentIndex = (currentIndex + 1) % visibleResources.size();
            }
        }
        
        if (resourceAmountFields.containsKey(activeTextField)) {
            resourceAmountFields.get(activeTextField).setFocused(false);
        }
        
        String newActiveField = visibleResources.get(currentIndex);
        activeTextField = newActiveField;
        
        if (resourceAmountFields.containsKey(newActiveField)) {
            TextFieldWidget field = resourceAmountFields.get(newActiveField);
            field.setFocused(true);
            field.setSelectionStart(0);
            field.setSelectionEnd(field.getText().length());
        }
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (mode == Mode.MODIFY_RESOURCES && activeTextField != null) {
            TextFieldWidget field = resourceAmountFields.get(activeTextField);
            if (field != null && field.isFocused()) {
                return field.charTyped(chr, modifiers);
            }
        }
        
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(chr, modifiers)) {
            return true;
        }
        
        return super.charTyped(chr, modifiers);
    }

    private int getResourceMaxVisibleItems() {
        int contentY = 35 + 5;
        int contentHeight = this.height - contentY - 20;
        int gridStartY = contentY + 35;
        int headerHeight = 24;
        int lineHeight = 30;
        int listStartY = gridStartY + headerHeight;
        int listHeight = contentHeight - (listStartY - contentY);
        
        if (mode == Mode.MODIFY_RESOURCES) {
            int maxVisibleRows = listHeight / lineHeight;
            return maxVisibleRows;
        } else {
            int columnsCount = Math.max(1, (this.width - 40) / 220);
            int maxVisibleRows = listHeight / lineHeight;
            return maxVisibleRows * columnsCount;
        }
    }

    private int getRecipeMaxVisibleItems() {
        int contentY = 35 + 5;
        int contentHeight = this.height - contentY - 20;
        int lineHeight = 24;
        int listStartY = contentY + 70;
        int listHeight = contentHeight - (listStartY - contentY);
        return listHeight / lineHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxVisibleItems;
        int maxItems;
        if (mode == Mode.RECIPE_VIEWER) {
            int x = 250 + 15; 
            int y = 35 + 5 + 10 + 50 + 30 + 30 + 35 + 10;
            int width = this.width - 40 - 220 - 30 - 30;
            int height = this.height - y - 20 - 10;
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                recipeTreeScrollOffset -= (int)verticalAmount * 24; 
                recipeTreeScrollOffset = Math.max(0, Math.min(recipeTreeScrollOffset, recipeTreeMaxScroll));
                return true;
            }
        }

        if (mode == Mode.FORGE_MODE) {
            int x = 250 + 15;
            int y = 35 + 5 + 10 + 50 + 60 + 70 + 35 + 10;
            int width = this.width - 40 - 220 - 40 - 30;
            int height = Math.min(300, this.height - y - 20 - 10);
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                forgeTreeScrollOffset -= (int)verticalAmount * 24;
                forgeTreeScrollOffset = Math.max(0, Math.min(forgeTreeScrollOffset, forgeTreeMaxScroll));
                return true;
            }
        }

        switch (mode) {
            case RESOURCE_VIEWER:
            case MODIFY_RESOURCES:
                maxVisibleItems = getResourceMaxVisibleItems();
                maxItems = filteredResources.size();
                break;
            case RECIPE_VIEWER:
            case FORGE_MODE:
                maxVisibleItems = getRecipeMaxVisibleItems();
                maxItems = filteredRecipeNames.size();
                break;
            default:
                return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (maxItems > maxVisibleItems) {
            scrollOffset -= (int)verticalAmount;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxItems - maxVisibleItems));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        clickableElements.clear();
        try {
            context.fill(0, 0, this.width, this.height, 0xFF101010);

            int headerHeight = 35;
            context.fill(0, 0, this.width, headerHeight, TITLE_BG);
            context.drawBorder(0, 0, this.width, headerHeight, BORDER_COLOR);
            
            String headingText = "Skyblock Mining Resource Reader";
            context.drawCenteredTextWithShadow(textRenderer, 
                Text.literal(headingText).setStyle(Style.EMPTY.withColor(Formatting.GOLD).withBold(true)),
                this.width / 2, 10, WHITE);

            int tabWidth = 100;
            int tabHeight = 25;
            int startX = 20;
            int tabY = headerHeight - tabHeight;

            renderTab(context, "Resources", startX, tabY, tabWidth, tabHeight, mode == Mode.RESOURCE_VIEWER, () -> {
                mode = Mode.RESOURCE_VIEWER; this.init();
            });
            renderTab(context, "Recipes", startX + tabWidth, tabY, tabWidth, tabHeight, mode == Mode.RECIPE_VIEWER, () -> {
                mode = Mode.RECIPE_VIEWER; this.init();
            });
            renderTab(context, "Forge", startX + 2 * tabWidth, tabY, tabWidth, tabHeight, mode == Mode.FORGE_MODE, () -> {
                mode = Mode.FORGE_MODE; this.init();
            });
            renderTab(context, "Modify", startX + 3 * tabWidth, tabY, tabWidth, tabHeight, mode == Mode.MODIFY_RESOURCES, () -> {
                mode = Mode.MODIFY_RESOURCES; this.init();
            });

            int contentX = 20;
            int contentY = headerHeight + 5;
            int contentWidth = this.width - 40;
            int contentHeight = this.height - contentY - 20;
            context.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, PANEL_BG);
            context.drawBorder(contentX, contentY, contentWidth, contentHeight, BORDER_COLOR);

            switch (mode) {
                case RESOURCE_VIEWER -> renderResourceViewer(context, contentX, contentY, contentWidth, contentHeight);
                case RECIPE_VIEWER -> renderRecipeViewer(context, contentX, contentY, contentWidth, contentHeight);
                case FORGE_MODE -> renderForgeMode(context, contentX, contentY, contentWidth, contentHeight);
                case MODIFY_RESOURCES -> renderModifyResources(context, contentX, contentY, contentWidth, contentHeight);
            }

            super.render(context, mouseX, mouseY, delta);
        } catch (Exception e) {
            InventoryReader.LOGGER.error("Error in render method", e);
        }
    }

    private void renderTab(DrawContext context, String text, int x, int y, int width, int height, boolean selected, Runnable action) {
        context.fill(x, y, x + width, y + height, selected ? SELECTED_BG : PANEL_BG);
        context.drawBorder(x, y, width, height, BORDER_COLOR);
        drawCenteredText(context, text, x + width / 2, y + (height - textRenderer.fontHeight) / 2, WHITE);
        clickableElements.add(new ClickableElement(x, y, width, height, action));
    }

    private void renderResourceViewer(DrawContext context, int contentX, int contentY, int contentWidth, int contentHeight) {
        int lineHeight = 30;
        int columnsCount = Math.max(1, contentWidth / 220);
        int columnWidth = contentWidth / columnsCount;
        int gridStartY = contentY + 35;
        int totalItems = filteredResources.size();
        int maxVisibleItems = getResourceMaxVisibleItems();
        int startIndex = Math.min(scrollOffset, Math.max(0, totalItems - maxVisibleItems));

        int headerHeight = 24;
        context.fill(contentX, gridStartY, contentX + contentWidth, gridStartY + headerHeight, ITEM_BG_ALT);
        context.drawBorder(contentX, gridStartY, contentWidth, headerHeight, BORDER_COLOR);

        context.drawText(textRenderer, "Resource Name", contentX + 12, gridStartY + 8, GOLD, false);
        context.drawText(textRenderer, "Amount", contentX + contentWidth - columnWidth/4 - 40, gridStartY + 8, GOLD, false);

        int listStartY = gridStartY + headerHeight;
        int listHeight = contentY + contentHeight - listStartY;
        context.fill(contentX, listStartY, contentX + contentWidth, contentY + contentHeight, PANEL_BG);

        if (totalItems == 0) {
            drawCenteredText(context, "No resources found", contentX + contentWidth / 2, listStartY + 30, TEXT_SECONDARY);
        }

        int index = 0;
        for (int i = startIndex; i < Math.min(startIndex + maxVisibleItems, totalItems); i++) {
            ResourcesManager.ResourceEntry resource = filteredResources.get(i);
            int row = index / columnsCount;
            int col = index % columnsCount;
            int itemX = contentX + col * columnWidth + 5;
            int itemY = listStartY + row * lineHeight + 3;
            int itemWidth = columnWidth - 10;

            boolean isAlternate = row % 2 == 1;
            context.fill(itemX, itemY, itemX + itemWidth, itemY + lineHeight - 4, isAlternate ? ITEM_BG_ALT : ITEM_BG);

            if (isMouseOver(itemX, itemY, itemWidth, lineHeight - 4)) {
                context.fill(itemX, itemY, itemX + itemWidth, itemY + lineHeight - 4, SELECTED_BG);
            }

            context.drawText(textRenderer, resource.name, itemX + 8, itemY + (lineHeight - textRenderer.fontHeight) / 2, WHITE, false);
            String amountText = resource.amount + "×";
            int amountWidth = textRenderer.getWidth(amountText);
            context.drawText(textRenderer, amountText, itemX + itemWidth - amountWidth - 8, itemY + (lineHeight - textRenderer.fontHeight) / 2, GOLD, false);
            index++;
        }

        if (totalItems > maxVisibleItems) {
            int scrollbarWidth = 6;
            int scrollbarX = contentX + contentWidth - scrollbarWidth - 2;
            context.fill(scrollbarX, listStartY, scrollbarX + scrollbarWidth, listStartY + listHeight, ITEM_BG_ALT);

            int thumbHeight = Math.max(10, listHeight * maxVisibleItems / totalItems);
            int thumbY = listStartY;
            if (totalItems > maxVisibleItems) {
                thumbY += (scrollOffset * (listHeight - thumbHeight) / (totalItems - maxVisibleItems));
            }
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, BORDER_COLOR);
        }
    }

    private boolean isMouseOver(int x, int y, int width, int height) {
        double mouseX = this.client.mouse.getX() * (double)this.client.getWindow().getScaledWidth() / (double)this.client.getWindow().getWidth();
        double mouseY = this.client.mouse.getY() * (double)this.client.getWindow().getScaledHeight() / (double)this.client.getWindow().getHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void renderRecipeViewer(DrawContext context, int contentX, int contentY, int contentWidth, int contentHeight) {
        int leftPanelWidth = 220;
        int leftPanelX = contentX + 10;
        int rightPanelX = contentX + leftPanelWidth + 20;

        context.fill(contentX + leftPanelWidth + 10, contentY, contentX + leftPanelWidth + 11, contentY + contentHeight, BORDER_COLOR);

        renderRecipeList(context, leftPanelX, contentY, leftPanelWidth, contentHeight);

        if (selectedRecipe != null && expandedRecipeTree != null) {
            renderRecipeDetails(context, rightPanelX, contentY, contentWidth - leftPanelWidth - 30, contentHeight);
        } else {
            drawCenteredText(context, "Select a recipe from the list", rightPanelX + (contentWidth - leftPanelWidth - 30) / 2, contentY + contentHeight / 2, TEXT_SECONDARY);
        }
    }
    
    private void renderForgeMode(DrawContext context, int contentX, int contentY, int contentWidth, int contentHeight) {
        int leftPanelWidth = 220;
        int leftPanelX = contentX + 10;
        int rightPanelX = contentX + leftPanelWidth + 20;

        context.fill(contentX + leftPanelWidth + 10, contentY, contentX + leftPanelWidth + 11, contentY + contentHeight, BORDER_COLOR);

        renderRecipeList(context, leftPanelX, contentY, leftPanelWidth, contentHeight);

        if (selectedRecipe == null || remainingResult == null) {
            drawCenteredText(context, "Select a recipe to forge", rightPanelX + (contentWidth - leftPanelWidth - 40) / 2, contentY + contentHeight / 2, TEXT_SECONDARY);
            return;
        }
        
        renderForgeDetails(context, rightPanelX, contentY, contentWidth - leftPanelWidth - 40, contentHeight);
    }

    private void renderRecipeList(DrawContext context, int x, int y, int width, int height) {
        int recipeListY = y + 40;
        int lineHeight = 24;

        context.fill(x, recipeListY, x + width - 10, recipeListY + 24, TITLE_BG);
        context.drawBorder(x, recipeListY, width - 10, 24, BORDER_COLOR);
        context.drawText(textRenderer, "Available Recipes", x + 10, recipeListY + 8, GOLD, false);

        recipeListY += 30;

        int maxVisibleItems = getRecipeMaxVisibleItems();
        int totalItems = filteredRecipeNames.size();

        int visibleIndex = 0;
        for (String name : filteredRecipeNames) {
            if (visibleIndex >= scrollOffset && visibleIndex < scrollOffset + maxVisibleItems) {
                int itemY = recipeListY + (visibleIndex - scrollOffset) * lineHeight;
                boolean isSelected = name.equals(selectedRecipe);
                int itemBgColor = (visibleIndex % 2 == 0) ? ITEM_BG : ITEM_BG_ALT;
                context.fill(x, itemY, x + width - 16, itemY + lineHeight - 2, itemBgColor);

                if (isSelected) {
                    context.fill(x, itemY, x + width - 16, itemY + lineHeight - 2, SELECTED_BG);
                }
                if (isMouseOver(x, itemY, width - 16, lineHeight - 2) && !isSelected) {
                    context.fill(x, itemY, x + width - 16, itemY + lineHeight - 2, 0x32FFFFFF);
                }
                
                final String currentName = name;
                clickableElements.add(new ClickableElement(x, itemY, width - 16, lineHeight - 2, () -> selectRecipe(currentName)));

                String displayName = name;
                int maxWidth = width - 30;
                if (textRenderer.getWidth(displayName) > maxWidth) {
                    displayName = textRenderer.trimToWidth(displayName, maxWidth - textRenderer.getWidth("...")) + "...";
                }
                context.drawText(textRenderer, displayName, x + 8, itemY + (lineHeight - textRenderer.fontHeight) / 2, isSelected ? WHITE : TEXT_SECONDARY, false);
            }
            visibleIndex++;
        }

        if (totalItems > maxVisibleItems) {
            int scrollbarWidth = 6;
            int scrollbarX = x + width - 16;
            int listStartY = recipeListY;
            int listHeight = y + height - listStartY;

            context.fill(scrollbarX, listStartY, scrollbarX + scrollbarWidth, listStartY + listHeight, ITEM_BG_ALT);

            int thumbHeight = Math.max(10, listHeight * maxVisibleItems / totalItems);
            int thumbY = listStartY;
            if (totalItems > maxVisibleItems) {
                thumbY += (scrollOffset * (listHeight - thumbHeight) / (totalItems - maxVisibleItems));
            }
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, BORDER_COLOR);
        }
    }

    private void renderRecipeDetails(DrawContext context, int x, int y, int width, int height) {
        int rightColumnY = y + 10;

        context.fill(x, rightColumnY, x + width, rightColumnY + 40, TITLE_BG);
        context.drawBorder(x, rightColumnY, width, 40, BORDER_COLOR);
        drawCenteredText(context, selectedRecipe, x + width / 2, rightColumnY + 15, GOLD);
        rightColumnY += 50;

        context.fill(x, rightColumnY, x + width, rightColumnY + 25, ITEM_BG_ALT);
        context.drawText(textRenderer, "Required Materials", x + 10, rightColumnY + 8, GOLD, false);
        rightColumnY += 30;

        if (simpleRecipe != null && !simpleRecipe.isEmpty()) {
            int cardWidth = 180;
            int cardsPerRow = Math.max(1, (width - 20) / cardWidth);
            int cardSpacing = 10;
            int materialIndex = 0;
            for (Map.Entry<String, Integer> entry : simpleRecipe.entrySet()) {
                int col = materialIndex % cardsPerRow;
                int row = materialIndex / cardsPerRow;
                int itemX = x + col * (cardWidth + cardSpacing);
                int itemY = rightColumnY + row * 34;
                context.fill(itemX, itemY, itemX + cardWidth, itemY + 28, ITEM_BG);
                context.drawBorder(itemX, itemY, cardWidth, 28, BORDER_COLOR);
                context.drawText(textRenderer, entry.getKey(), itemX + 8, itemY + 10, WHITE, false);
                String qtyText = entry.getValue() + "×";
                context.drawText(textRenderer, qtyText, itemX + cardWidth - textRenderer.getWidth(qtyText) - 8, itemY + 10, GOLD, false);
                materialIndex++;
            }
            rightColumnY += ((materialIndex + cardsPerRow - 1) / cardsPerRow) * 34 + 20;
        } else {
            context.drawText(textRenderer, "No materials required", x + 10, rightColumnY + 5, TEXT_SECONDARY, false);
            rightColumnY += 30;
        }

        context.fill(x, rightColumnY, x + width, rightColumnY + 25, ITEM_BG_ALT);
        context.drawText(textRenderer, "Crafting Tree (Click to expand)", x + 10, rightColumnY + 8, GOLD, false);
        rightColumnY += 35;

        int treeHeight = y + height - rightColumnY - 10;
        context.fill(x, rightColumnY, x + width, rightColumnY + treeHeight, PANEL_BG);
        context.drawBorder(x, rightColumnY, width, treeHeight, BORDER_COLOR);
        context.enableScissor(x, rightColumnY, x + width, rightColumnY + treeHeight);
        int totalTreeHeight = renderRecipeTree(null, expandedRecipeTree, "", x + 15, rightColumnY + 10); // dry run to get height
        recipeTreeMaxScroll = Math.max(0, totalTreeHeight - (rightColumnY + treeHeight));
        renderRecipeTree(context, expandedRecipeTree, "", x + 15, rightColumnY + 10 - recipeTreeScrollOffset);
        context.disableScissor();
    }
    
    private void renderForgeDetails(DrawContext context, int x, int y, int width, int height) {
        int rightColumnY = y + 10;

        context.fill(x, rightColumnY, x + width, rightColumnY + 40, TITLE_BG);
        context.drawBorder(x, rightColumnY, width, 40, BORDER_COLOR);
        drawCenteredText(context, selectedRecipe, x + width / 2, rightColumnY + 15, GOLD);
        rightColumnY += 50;

        context.fill(x, rightColumnY, x + width, rightColumnY + 60, ITEM_BG);
        context.drawBorder(x, rightColumnY, width, 60, BORDER_COLOR);
        context.drawText(textRenderer, "Crafting Amount: ", x + 10, rightColumnY + 10, WHITE, false);
        context.drawText(textRenderer, craftAmount + "", x + 120, rightColumnY + 10, GOLD, false);
        String statusIcon = craftable ? "✓" : "✗";
        String craftableText = craftable ? "Can be crafted!" : "Missing ingredients";
        int craftableColor = craftable ? SUCCESS_GREEN : ERROR_RED;
        context.drawText(textRenderer, statusIcon, x + 10, rightColumnY + 30, craftableColor, false);
        context.drawText(textRenderer, craftableText, x + 30, rightColumnY + 30, craftableColor, false);
        rightColumnY += 70;

        InventoryReader.LOGGER.info("Rendering messages, count: " + messages.size());
            
        if (!messages.isEmpty()) {
            context.fill(x, rightColumnY, x + width, rightColumnY + 30, ITEM_BG_ALT);
            context.drawText(textRenderer, "Crafting Messages", x + 10, rightColumnY + 10, GOLD, false);
            rightColumnY += 35;
            int msgBoxHeight = Math.min(messages.size() * 20 + 10, 100);
            context.fill(x, rightColumnY, x + width, rightColumnY + msgBoxHeight, PANEL_BG);
            context.drawBorder(x, rightColumnY, width, msgBoxHeight, BORDER_COLOR);
            int msgY = rightColumnY + 8;
            for (int i = 0; i < messages.size() && msgY < rightColumnY + msgBoxHeight - 15; i++) {
                String msg = "• " + messages.get(i);
                context.drawText(textRenderer, msg, x + 12, msgY, TEXT_SECONDARY, false);
                InventoryReader.LOGGER.info("Drawing message: " + msg + " at y=" + msgY);
                msgY += 20;
            }
            rightColumnY += msgBoxHeight + 15;
        } else {
            InventoryReader.LOGGER.info("No messages to render");
        }

        if (remainingResult != null && remainingResult.full_recipe != null) {
            context.fill(x, rightColumnY, x + width, rightColumnY + 30, ITEM_BG_ALT);
            context.drawText(textRenderer, "Required Recipe Tree (Click to Expand)", x + 10, rightColumnY + 10, GOLD, false);
            rightColumnY += 35;
            int treeHeight = Math.min(300, y + height - rightColumnY - 10);
            context.fill(x, rightColumnY, x + width, rightColumnY + treeHeight, PANEL_BG);
            context.drawBorder(x, rightColumnY, width, treeHeight, BORDER_COLOR);
            context.enableScissor(x, rightColumnY, x + width, rightColumnY + treeHeight);
            int totalTreeHeight = renderRecipeTree(null, remainingResult.full_recipe, "", x + 15, rightColumnY + 10); // dry run
            forgeTreeMaxScroll = Math.max(0, totalTreeHeight - (rightColumnY + treeHeight));
            renderRecipeTree(context, remainingResult.full_recipe, "", x + 15, rightColumnY + 10 - forgeTreeScrollOffset);
            context.disableScissor();
        }
    }

    private int renderRecipeTree(DrawContext context, Object nodeObj, String path, int x, int y) {
        if (nodeObj == null) return y;

        int lineHeight = 24;
        String name;
        int amount;
        List<?> ingredients;

        if (nodeObj instanceof RecipeManager.RecipeNode node) {
            name = node.name;
            amount = node.amount;
            ingredients = node.ingredients;
        } else if (nodeObj instanceof ResourcesManager.RecipeNode node) {
            name = node.name;
            amount = node.amount;
            ingredients = node.ingredients;
        } else {
            return y;
        }

        String fullPath = path + name;
        boolean isExpanded = expandedNodes.getOrDefault(fullPath, false);
        boolean hasIngredients = ingredients != null && !ingredients.isEmpty();

        int textColor = WHITE;
        if (mode == Mode.FORGE_MODE && amount > 0) {
            Integer resourceAmount = resourcesManager.getResourceByName(name);
            textColor = (resourceAmount != null && resourceAmount >= amount) ? SUCCESS_GREEN : ERROR_RED;
        }

        int nodeWidth = Math.min(300, Math.max(100, textRenderer.getWidth(name) + textRenderer.getWidth(amount + "×") + 40));
        if (context != null) {
            context.fill(x - 5, y, x + nodeWidth, y + lineHeight, ITEM_BG);
            context.drawBorder(x - 5, y, nodeWidth + 5, lineHeight, BORDER_COLOR);
            if (isMouseOver(x - 5, y, nodeWidth, lineHeight)) {
                context.fill(x - 5, y, x + nodeWidth, y + lineHeight, SELECTED_BG);
            }
            if (hasIngredients) {
                clickableElements.add(new ClickableElement(x - 5, y, nodeWidth, lineHeight, () -> toggleNodeExpanded(fullPath)));
            }
            context.drawText(textRenderer, amount + "×", x + 15, y + (lineHeight - textRenderer.fontHeight) / 2, GOLD, false);
            context.drawText(textRenderer, name, x + 15 + textRenderer.getWidth(amount + "×") + 5, y + (lineHeight - textRenderer.fontHeight) / 2, textColor, false);
        }
        int endY = y + lineHeight;
        if (isExpanded && hasIngredients) {
            int childY = y + lineHeight + 2;
            int indent = 24;
            for (Object child : ingredients) {
                int nextY = renderRecipeTree(context, child, fullPath + ".", x + indent, childY);
                childY = nextY + 4;
            }
            endY = childY - 4;
        }
        return endY;
    }

    private void drawCenteredText(DrawContext context, String text, int centerX, int y, int color) {
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }
    

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    public static boolean shouldOpenSandboxViewer = false;

    private void renderModifyResources(DrawContext context, int contentX, int contentY, int contentWidth, int contentHeight) {
        int lineHeight = 30;
        
        int leftPanelWidth = contentWidth - 200;
        int rightPanelX = contentX + leftPanelWidth + 20;
        
        Set<String> currentResourceKeys = new HashSet<>();
        for (ResourcesManager.ResourceEntry resource : filteredResources) {
            currentResourceKeys.add(resource.name);
        }
        
        resourceAmountFields.entrySet().removeIf(entry -> !currentResourceKeys.contains(entry.getKey()));
        
        context.fill(contentX + leftPanelWidth + 10, contentY, contentX + leftPanelWidth + 11, contentY + contentHeight, BORDER_COLOR);
        
        int gridStartY = contentY + 35;
        int totalItems = filteredResources.size();
        int maxVisibleItems = getResourceMaxVisibleItems();
        int startIndex = Math.min(scrollOffset, Math.max(0, totalItems - maxVisibleItems));

        int headerHeight = 24;
        context.fill(contentX, gridStartY, contentX + leftPanelWidth, gridStartY + headerHeight, ITEM_BG_ALT);
        context.drawBorder(contentX, gridStartY, leftPanelWidth, headerHeight, BORDER_COLOR);

        context.drawText(textRenderer, "Resource Name", contentX + 12, gridStartY + 8, GOLD, false);
        context.drawText(textRenderer, "Amount", contentX + leftPanelWidth - 120, gridStartY + 8, GOLD, false);
        context.drawText(textRenderer, "Actions", contentX + leftPanelWidth - 60, gridStartY + 8, GOLD, false);

        int listStartY = gridStartY + headerHeight;
        context.fill(contentX, listStartY, contentX + leftPanelWidth, contentY + contentHeight, PANEL_BG);

        if (totalItems == 0) {
            drawCenteredText(context, "No resources found", contentX + leftPanelWidth / 2, listStartY + 30, TEXT_SECONDARY);
            return;
        }

        //Right panel - Modified Resources
        context.fill(rightPanelX, contentY, rightPanelX + 180, contentY + 40, TITLE_BG);
        context.drawBorder(rightPanelX, contentY, 180, 40, BORDER_COLOR);
        drawCenteredText(context, "Modified Resources", rightPanelX + 90, contentY + 15, GOLD);
        
        context.fill(rightPanelX, contentY + 45, rightPanelX + 180, contentY + contentHeight, PANEL_BG);
        context.drawBorder(rightPanelX, contentY + 45, 180, contentHeight - 45, BORDER_COLOR);
        
        // Render the list of resources with increment/decrement buttons
        for (int i = startIndex; i < Math.min(startIndex + maxVisibleItems, totalItems); i++) {
            ResourcesManager.ResourceEntry resource = filteredResources.get(i);
            int row = i - startIndex;
            int rowY = listStartY + row * lineHeight;
            
            int x = contentX + 5;
            int y = rowY + 5;
            
            int bgColor = (i % 2 == 0) ? ITEM_BG : ITEM_BG_ALT;
            context.fill(x, y, x + leftPanelWidth - 10, y + lineHeight - 10, bgColor);
            
            context.drawText(textRenderer, resource.name, x + 5, y + 7, WHITE, false);
            
            // Create or update text field for this resource amount
            final String resourceKey = resource.name;
            int amountFieldWidth = 60;
            int amountFieldX = x + leftPanelWidth - 140;
            int amountFieldY = y + 2;
            
            TextFieldWidget amountField = resourceAmountFields.get(resourceKey);
            if (amountField == null) {
                // Create a new text field for this resource
                final TextFieldWidget newField = new TextFieldWidget(textRenderer, amountFieldX, amountFieldY, amountFieldWidth, 16, Text.literal(""));
                newField.setText(String.valueOf(resource.amount));
                newField.setMaxLength(10); // Limit to reasonable number length
                
                // Set the changed listener to update resource amount on text change
                newField.setChangedListener(text -> {
                    try {
                        int newAmount = text.isEmpty() ? 0 : Integer.parseInt(text);
                        if (newAmount >= 0) {
                            // Find the resource entry and update its amount
                            ResourcesManager.ResourceEntry resourceEntry = filteredResources.stream()
                                .filter(entry -> entry.name.equals(resourceKey))
                                .findFirst()
                                .orElse(null);
                            
                            if (resourceEntry != null) {
                                resourceEntry.amount = newAmount;
                                updateSelectedResource(resourceEntry);
                            }
                        }
                    } catch (NumberFormatException e) {
                        //Reset to previous value if nan
                        ResourcesManager.ResourceEntry resourceEntry = filteredResources.stream()
                            .filter(entry -> entry.name.equals(resourceKey))
                            .findFirst()
                            .orElse(null);
                        
                        if (resourceEntry != null && resourceAmountFields.containsKey(resourceKey)) {
                            TextFieldWidget fieldToUpdate = resourceAmountFields.get(resourceKey);
                            fieldToUpdate.setText(String.valueOf(resourceEntry.amount));
                        }
                    }
                });
                
                resourceAmountFields.put(resourceKey, newField);
                amountField = newField;
                

                amountField.setDrawsBackground(true);
            } else {
                amountField.setX(amountFieldX);
                amountField.setY(amountFieldY);
                
                String currentText = amountField.getText();
                int currentAmount;
                try {
                    currentAmount = currentText.isEmpty() ? 0 : Integer.parseInt(currentText);
                } catch (NumberFormatException e) {
                    currentAmount = -1;
                }
                
                if (currentAmount != resource.amount && !amountField.isFocused()) {
                    amountField.setText(String.valueOf(resource.amount));
                }
            }
            
            int fieldBgColor = amountField.isFocused() ? 0xFF404040 : 0xFF333333;
            int fieldBorderColor = amountField.isFocused() ? WHITE : BORDER_COLOR;
            
            context.fill(amountFieldX - 1, amountFieldY - 1, amountFieldX + amountFieldWidth + 1, amountFieldY + 18, fieldBorderColor);
            context.fill(amountFieldX, amountFieldY, amountFieldX + amountFieldWidth, amountFieldY + 17, fieldBgColor);
            
            amountField.render(context, (int) MinecraftClient.getInstance().mouse.getX(), 
                              (int) MinecraftClient.getInstance().mouse.getY(), 0);
            
            int buttonSize = 16;
            int buttonY = y + (lineHeight - 10 - buttonSize) / 2;
            
            int minusX = x + leftPanelWidth - 80;
            context.fill(minusX, buttonY, minusX + buttonSize, buttonY + buttonSize, 0xFF444444);
            context.drawBorder(minusX, buttonY, buttonSize, buttonSize, BORDER_COLOR);
            context.drawText(textRenderer, "-", minusX + (buttonSize - textRenderer.getWidth("-")) / 2, 
                             buttonY + (buttonSize - textRenderer.fontHeight) / 2, WHITE, false);
            
            int plusX = x + leftPanelWidth - 40;
            context.fill(plusX, buttonY, plusX + buttonSize, buttonY + buttonSize, 0xFF444444);
            context.drawBorder(plusX, buttonY, buttonSize, buttonSize, BORDER_COLOR);
            context.drawText(textRenderer, "+", plusX + (buttonSize - textRenderer.getWidth("+")) / 2, 
                             buttonY + (buttonSize - textRenderer.fontHeight) / 2, WHITE, false);
            
            final int resourceIndex = i;
            
            // Add click handlers for the buttons
            clickableElements.add(new ClickableElement(minusX, buttonY, buttonSize, buttonSize, 
                                                      () -> decrementResource(resourceIndex)));
            clickableElements.add(new ClickableElement(plusX, buttonY, buttonSize, buttonSize, 
                                                      () -> incrementResource(resourceIndex)));
        }
        
        int modifiedY = contentY + 55;
        int modifiedCount = 0;
        for (ResourcesManager.ResourceEntry entry : selectedResources) {
            context.drawText(textRenderer, entry.name, rightPanelX + 10, modifiedY, WHITE, false);
            context.drawText(textRenderer, String.valueOf(entry.amount), 
                            rightPanelX + 180 - 10 - textRenderer.getWidth(String.valueOf(entry.amount)), 
                            modifiedY, GOLD, false);
            modifiedY += 20;
            modifiedCount++;

            if (modifiedCount >= 15) {
                context.drawText(textRenderer, "...", rightPanelX + 90, modifiedY, TEXT_SECONDARY, false);
                break;
            }
        }
        
        if (selectedResources.isEmpty()) {
            drawCenteredText(context, "No modifications yet", rightPanelX + 90, contentY + 70, TEXT_SECONDARY);
        }

        if (totalItems > maxVisibleItems) {
            int scrollHeight = contentHeight - (listStartY - contentY);
            int scrollThumbHeight = Math.max(32, scrollHeight * maxVisibleItems / totalItems);
            int scrollThumbY = listStartY;
            
            if (totalItems > maxVisibleItems) {
                scrollThumbY += (scrollOffset * (scrollHeight - scrollThumbHeight)) / (totalItems - maxVisibleItems);
            }
            
            context.fill(contentX + leftPanelWidth - 8, listStartY, contentX + leftPanelWidth - 4, contentY + contentHeight, 0xFF333333);
            context.fill(contentX + leftPanelWidth - 8, scrollThumbY, contentX + leftPanelWidth - 4, scrollThumbY + scrollThumbHeight, 0xFF666666);
        }
    }
    
    private void incrementResource(int index) {
        if (index >= 0 && index < filteredResources.size()) {
            ResourcesManager.ResourceEntry resource = filteredResources.get(index);
            resource.amount++;
            updateSelectedResource(resource);
        }
    }
    
    private void decrementResource(int index) {
        if (index >= 0 && index < filteredResources.size()) {
            ResourcesManager.ResourceEntry resource = filteredResources.get(index);
            if (resource.amount > 0) {
                resource.amount--;
                updateSelectedResource(resource);
            }
        }
    }
    
    private void updateSelectedResource(ResourcesManager.ResourceEntry resource) {
        boolean found = false;
        for (int i = 0; i < selectedResources.size(); i++) {
            if (selectedResources.get(i).name.equals(resource.name)) {
                selectedResources.set(i, resource);
                found = true;
                break;
            }
        }
        
        if (!found) {
            selectedResources.add(new ResourcesManager.ResourceEntry(resource.name, resource.amount));
        }

        modifiedResources.put(resource.name, resource.amount);
    }
    

}
