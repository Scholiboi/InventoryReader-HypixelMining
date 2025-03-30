package inventoryreader.ir;

import java.util.concurrent.atomic.AtomicBoolean;

public class SendingManager {
    private static final AtomicBoolean skipNext = new AtomicBoolean(false);

    public static void blockNextDataSend() {
        InventoryReader.LOGGER.info("Blocking next data send");
        skipNext.set(true);
    }

    public static void unblockDataSend() {
        InventoryReader.LOGGER.info("Unblocking next data send");
        skipNext.set(false);
    }

    public static boolean shouldSkipNextSend() {
        return skipNext.compareAndSet(true, false);
    }
}
