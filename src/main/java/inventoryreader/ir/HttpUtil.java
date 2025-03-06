package inventoryreader.ir;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpUtil {
    public static final ExecutorService HTTP_EXECUTOR = Executors.newCachedThreadPool();

    public static void shutdown() {
        HTTP_EXECUTOR.shutdown();
        try {
            if (!HTTP_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                HTTP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            HTTP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}