package inventoryreader.ir;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenHandler;

public class StorageViewerMod implements ClientModInitializer{
    private static ScreenHandler lastScreenHandler = null;
    private final StorageReader storageReader = StorageReader.getInstance();
    private final SackReader sackReader = new SackReader();
    private int tickCounter = 0;

    private boolean hasHandledMenuOpen = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
    }

    private void onEndClientTick(MinecraftClient client) {
        if (client.currentScreen != null && client.player != null) {
            ScreenHandler currentHandler = client.player.currentScreenHandler;

            if (currentHandler != lastScreenHandler) {
                lastScreenHandler = currentHandler;
                tickCounter = 0;
                hasHandledMenuOpen = false;
            }

            if (tickCounter < 1) {
                tickCounter++;
            } else if (!hasHandledMenuOpen) {
                handleMenuOpen(client);
                hasHandledMenuOpen = true;
            }
        }
    }

    private void handleMenuOpen(MinecraftClient client) {
        assert client.player != null;
        Screen currentScreen = client.currentScreen;
        assert currentScreen != null;
        String title = currentScreen.getTitle().getString();
        InventoryReader.LOGGER.info("Opened container: " + title);
        storageReader.saveContainerContents(client.player.currentScreenHandler, title);
        if (title.contains("Sack")) {
            sackReader.saveLoreComponents(client.player.currentScreenHandler, title);
        }
    }
}