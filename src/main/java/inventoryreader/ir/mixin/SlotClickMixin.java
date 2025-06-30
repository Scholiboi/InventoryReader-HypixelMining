package inventoryreader.ir.mixin;

import inventoryreader.ir.StorageReader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class SlotClickMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void onClickSlotHead(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        handleSlotClick(slotIndex, button, actionType, player);
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void onClickSlotReturn(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        handleSlotClick(slotIndex, button, actionType, player);
    }

    @Unique
    private void handleSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;
        String title = currentScreen != null ? currentScreen.getTitle().getString() : "Unknown";

        if (!title.contains("Backpack") && !title.contains("Ender Chest")) {
            return;
        }
        StorageReader storageReader = StorageReader.getInstance();
        Map<String, java.util.Map<String, Integer>> allcontainerData = storageReader.loadAllContainerDataFromFile();
        storageReader.compareContainerData((ScreenHandler)(Object)this, title, allcontainerData);
    }
}