package inventoryreader.ir;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class IrCommandManager implements ClientModInitializer{

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerIrCommands(dispatcher);
        });
    }
    
    public static void registerIrCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("ir")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Inventory Reader Commands:")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)));
                    context.getSource().sendFeedback(Text.literal("- /ir reset: Reset all mod data")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    context.getSource().sendFeedback(Text.literal("- /ir done: Acknowledge reminder")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    context.getSource().sendFeedback(Text.literal("- /ir menu: Open Inventory Reader menu")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    context.getSource().sendFeedback(Text.literal("- /ir widget: Open Widget Customization Menu")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    context.getSource().sendFeedback(Text.literal("- /ir credits: Show credits")
                        .setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
                    return 1;
                })
                .then(literal("reset")
                    .executes(context -> {
                        InventoryReader.LOGGER.info("Executing complete mod reset");
                        SendingManager.blockNextDataSend();
                        StorageReader.getInstance().clearAllData();
                        FilePathManager.reInitializeFiles();
                        SackReader.setNeedsReminder(true);
                        context.getSource().sendFeedback(
                            Text.literal("Inventory Reader data reset! ")
                                .setStyle(Style.EMPTY.withColor(Formatting.GREEN))
                                .append(Text.literal("Open a sack or type /ir done to stop reminders.")
                                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                        );
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
                .then(literal("menu")
                    .executes(context -> {
                        InventoryReader.LOGGER.info("Opening SandboxViewer GUI (deferred)");
                        try {
                            inventoryreader.ir.InventoryReaderClient.shouldOpenSandboxViewer = true;
                        } catch (Exception e) {
                            InventoryReader.LOGGER.error("Failed to schedule SandboxViewer GUI", e);
                        }
                        return 1;
                    })
                )
                .then(literal("widget")
                    .executes(context -> {
                        inventoryreader.ir.InventoryReaderClient.shouldOpenWidgetCustomization = true;
                        return 1;
                    })
                )
                .then(literal("credits")
                    .executes(context -> {
                        context.getSource().sendFeedback(
                            Text.literal("Inventory Reader by Scholiboi")
                                .setStyle(Style.EMPTY.withColor(Formatting.GOLD))
                        );
                        context.getSource().sendFeedback(
                            Text.literal("Data source: NotEnoughUpdates-REPO")
                                .setStyle(Style.EMPTY.withColor(Formatting.WHITE))
                                .append(Text.literal(" • "))
                                .append(Text.literal("https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO")
                                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA)))
                        );
                        return 1;
                    })
                )
        );
    }
}