package inventoryreader.ir;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ReminderManager {
    private static final int REMINDER_INTERVAL = 1200; //60
    private static int tickCounter = 0;
    
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (SackReader.getNeedsReminder()) {
                tickCounter++;
                
                if (tickCounter >= REMINDER_INTERVAL) {
                    tickCounter = 0;
                    
                    client.player.sendMessage(
                        Text.literal("[Inventory Reader] ")
                            .setStyle(Style.EMPTY.withColor(Formatting.GOLD))
                            .append(Text.literal("Remember to open a sack or type ")
                                .setStyle(Style.EMPTY.withColor(Formatting.WHITE)))
                            .append(Text.literal("/ir done")
                                .setStyle(Style.EMPTY.withColor(Formatting.YELLOW)))
                            .append(Text.literal(" to stop this reminder.")
                                .setStyle(Style.EMPTY.withColor(Formatting.WHITE)))
                    );
                }
            } else {
                tickCounter = 0;
            }
        });
    }
}