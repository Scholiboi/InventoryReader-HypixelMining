package inventoryreader.ir.mixin;

import inventoryreader.ir.ExeDownloadScreen;
import inventoryreader.ir.InventoryReader;
import inventoryreader.ir.ModConfig;
import inventoryreader.ir.SecurityWarningScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    private static boolean hasShownDownloadScreen = false;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        if (!hasShownDownloadScreen) {
            InventoryReader.LOGGER.info("TitleScreenMixin - Showing download screen");
            hasShownDownloadScreen = true;
            if (ModConfig.shouldAlwaysConfirmDownloads()) {
                MinecraftClient.getInstance().setScreen(new SecurityWarningScreen(MinecraftClient.getInstance().currentScreen));
            } else {
                MinecraftClient.getInstance().setScreen(new ExeDownloadScreen());
            }
        }
    }
}