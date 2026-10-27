package dev.makemygamerunwell;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;

/**
 * Without the frame hook nothing would ever advance a run, so a run would wait forever. This notices that no frame
 * arrived within {@link #TIMEOUT_MILLIS} and tells the run, on the render thread.
 */
final class FrameHookWatchdog {
    static final long TIMEOUT_MILLIS = 3_000;

    private FrameHookWatchdog() {}

    /**
     * @param name thread name, for thread dumps
     * @param frameSeen whether the run has received a frame (or already ended, which also needs no action)
     * @param onTimeout runs on the render thread when no frame arrived in time
     */
    static void start(Minecraft mc, String name, BooleanSupplier frameSeen, Runnable onTimeout) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            mc.execute(() -> {
                if (!frameSeen.getAsBoolean()) onTimeout.run();
            });
        }, name);
        thread.setDaemon(true);
        thread.start();
    }
}
