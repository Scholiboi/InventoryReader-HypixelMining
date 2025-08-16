package inventoryreader.ir;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;


public class WelcomeManager {
    private static final File WELCOME_FLAG_FILE = new File(FilePathManager.MOD_DIR, "welcome_shown.txt");
    private static boolean isFirstTimeUser = true;

    public static void initialize() {
        checkFirstTimeUser();
        if (!isFirstTimeUser) {
            return;
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                try {
                    checkFirstTimeUser();
                    if (isFirstTimeUser) {
                        showWelcomeMessage(client);
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
    }

    private static void checkFirstTimeUser() {
        isFirstTimeUser = !WELCOME_FLAG_FILE.exists();
    }

    private static void showWelcomeMessage(MinecraftClient client) {
        if (client.player == null) return;

        String divider = "§6§l" + "=".repeat(40);
        
        client.player.sendMessage(Text.literal(divider), false);
        client.player.sendMessage(Text.literal("§b§lSkyblock Resource Calculator").setStyle(
            Style.EMPTY.withBold(true).withColor(Formatting.AQUA)
        ), false);
        
        client.player.sendMessage(Text.literal("§eTrack resources and recipes for Hypixel Skyblock mining."), false);
        client.player.sendMessage(Text.literal(""), false);
        
        MutableText commandsText = Text.literal("§6§lCommands:").append(Text.literal("\n§e- Press "));
        commandsText.append(Text.literal("§b[V]").setStyle(Style.EMPTY.withBold(true).withColor(Formatting.AQUA)));
        commandsText.append(Text.literal("§e to open the Sandbox Viewer"));
        commandsText.append(Text.literal("\n§e- Press "));
        commandsText.append(Text.literal("§b[B]").setStyle(Style.EMPTY.withBold(true).withColor(Formatting.AQUA)));
        commandsText.append(Text.literal("§e to customize the HUD widget"));
        client.player.sendMessage(commandsText, false);
        
        MutableText chatCommandsText = Text.literal("§6§lChat Commands:").setStyle(
            Style.EMPTY.withBold(true).withColor(Formatting.GOLD)
        );
        client.player.sendMessage(chatCommandsText, false);
        client.player.sendMessage(Text.literal("§e- §b/ir menu§e: Open Sandbox Viewer"), false);
        client.player.sendMessage(Text.literal("§e- §b/ir widget§e: Open Widget Customization"), false);
        client.player.sendMessage(Text.literal("§e- §b/ir reset§e: Reset all mod data"), false);
        client.player.sendMessage(Text.literal("§e- §b/ir done§e: Acknowledge reminders"), false);
        client.player.sendMessage(Text.literal("§e- §b/ir§e: Show all available commands"), false);

        client.player.sendMessage(Text.literal(""), false);
        MutableText firstTimeText = Text.literal("§d§lFirst-Time Setup:").setStyle(
            Style.EMPTY.withBold(true).withColor(Formatting.LIGHT_PURPLE)
        );
        client.player.sendMessage(firstTimeText, false);
        MutableText warningText = Text.literal("⚠️ For the mod to work, open all your Sacks, Backpacks and Ender Chests once in Skyblock. ⚠️").setStyle(
            Style.EMPTY.withBold(true).withColor(Formatting.RED)
        );
        client.player.sendMessage(warningText, false);
        client.player.sendMessage(Text.literal("§e1. Press §b[V]§e to open the Sandbox Viewer"), false);
        client.player.sendMessage(Text.literal("§e2. Use the 'Modify' tab to modify resource amounts, if needed"), false);
        client.player.sendMessage(Text.literal("§e3. In the 'Forge' tab, select recipes to view progress"), false);
        client.player.sendMessage(Text.literal("§e4. Enable the HUD widget to track resources while mining"), false);
        
        try {
            WELCOME_FLAG_FILE.createNewFile();
        } catch (IOException e) {
            InventoryReader.LOGGER.error("Failed to create welcome flag file", e);
        }
        
        isFirstTimeUser = false;
        client.player.sendMessage(Text.literal(divider), false);
    }
}
