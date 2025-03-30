package inventoryreader.ir;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class IrCommandManager {
    
    public static void registerCommands() {
        InventoryReader.LOGGER.info("Registering IR client commands...");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerIrCommands(dispatcher);
            InventoryReader.LOGGER.info("IR client commands registered successfully!");
        });
    }
    
    private static void registerIrCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("ir")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Inventory Reader Commands:")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)));
                    context.getSource().sendFeedback(Text.literal("- /ir reset: Reset all mod data")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    context.getSource().sendFeedback(Text.literal("- /ir done: Acknowledge reminder")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    return 1;
                })
                
                .then(literal("reset")
                    .executes(context -> {
                        InventoryReader.LOGGER.info("Executing complete mod reset");

                        SendingManager.blockNextDataSend();

                        SackReader.getInstance().clearSacks();
                        StorageReader.getInstance().clearAllData();
                        InventoryReader.clearAllserverData();
                        InventoryReader.clearAlljsonData();
                        SackReader.setNeedsReminder(true);
                        
                        context.getSource().sendFeedback(Text.literal("Inventory Reader data reset! ")
                            .setStyle(Style.EMPTY.withColor(Formatting.GREEN))
                            .append(Text.literal("Open a sack or type /ir done to stop reminders.")
                                .setStyle(Style.EMPTY.withColor(Formatting.YELLOW))));
                        return 1;
                    })
                )
                
                .then(literal("done")
                    .executes(context -> {
                        SackReader.setNeedsReminder(false);

                        SendingManager.unblockDataSend();
                        
                        context.getSource().sendFeedback(Text.literal("Acknowledged! Reminders stopped.")
                            .setStyle(Style.EMPTY.withColor(Formatting.GREEN)));
                        return 1;
                    })
                )
        );
    }
}