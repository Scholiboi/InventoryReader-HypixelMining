package inventoryreader.ir;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenHandler;

public class StorageViewerMod implements ClientModInitializer{
    private static ScreenHandler lastScreenHandler = null;
    private final StorageReader storageReader = StorageReader.getInstance();
    private final SackReader sackReader = SackReader.getInstance();
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
        Screen currentScreen = client.currentScreen;
        if (currentScreen.getClass().getName().contains("SandboxViewer")) {
            return;
        }
        String title = currentScreen.getTitle().getString();
        storageReader.saveContainerContents(client.player.currentScreenHandler, title);
        if (title.contains("Sack")) {
            sackReader.saveLoreComponents(client.player.currentScreenHandler, title);
        }
    }
}