package inventoryreader.ir;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ReminderManager {
    private static final int REMINDER_INTERVAL = 100; // 5 seconds
    private static int tickCounter = 0;
    
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (SackReader.getNeedsReminder()) {
                tickCounter++;
                
                if (tickCounter >= REMINDER_INTERVAL) {
                    tickCounter = 0;
                    
                    Text message = Text.literal("[Inventory Reader] ")
                        .formatted(Formatting.GOLD)
                        .append(Text.literal("Remember to open a sack or type ")
                            .formatted(Formatting.WHITE))
                        .append(Text.literal("/ir done")
                            .formatted(Formatting.YELLOW))
                        .append(Text.literal(" to stop this reminder.")
                            .formatted(Formatting.WHITE));
                    
                    MinecraftClient.getInstance().inGameHud.getChatHud()
                        .addMessage(message);
                }
            } else {
                tickCounter = 0;
            }
        });
    }
}