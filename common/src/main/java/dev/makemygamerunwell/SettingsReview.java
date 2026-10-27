package dev.makemygamerunwell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.makemygamerunwell.core.DiagnosticReport;
import dev.makemygamerunwell.core.Hardware;
import dev.makemygamerunwell.core.HardwareChange;
import dev.makemygamerunwell.core.HeapAdvice;
import dev.makemygamerunwell.core.SettingChange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What the mod has done and what it knows about this machine, for the player to inspect, partly undo, or report. */
public final class SettingsReview {
    private static final Logger LOG = LoggerFactory.getLogger(SettingsReview.class);

    private SettingsReview() {}

    /** The options the mod changed that still differ from the player's own values, in a fixed order. */
    public static List<SettingChange> changedSettings() {
        Minecraft mc = Minecraft.getInstance();
        return BackupStore.readIfValid(mc)
                .map(backup -> SettingChange.differing(texts(backup), texts(OwnedOptions.capture(mc.options))))
                .orElse(List.of());
    }

    /** Forgets {@code option} from the backup once it has been put back, removing the backup if it was the last. */
    static boolean revert(Minecraft mc, String option) {
        JsonObject before = OwnedOptions.capture(mc.options);
        try {
            JsonObject backup = BackupStore.read(mc);
            if (!backup.has(option)) return false;
            JsonObject only = new JsonObject();
            only.add(option, backup.get(option));
            OwnedOptions.restore(mc.options, only);
            OptionsPersistence.commit(mc, before);
            backup.remove(option);
            if (backup.isEmpty()) {
                BackupStore.delete(mc);
                LastTuneStore.delete(mc);
            } else {
                BackupStore.write(mc, backup);
            }
            return true;
        } catch (Exception e) {
            LOG.error("Could not put {} back; settings left as they were", option, e);
            AutoTuner.rollback(mc, before);
            return false;
        }
    }

    /** How the allocated memory suits the current render distance. */
    public static HeapAdvice memoryAdvice() {
        Minecraft mc = Minecraft.getInstance();
        Hardware hardware = HardwareProbe.detect(mc);
        return HeapAdvice.assess(hardware.heapMiB(), hardware.memoryMiB(), mc.options.renderDistance().get());
    }

    /** What has changed on this machine since the last tune; empty until the hardware query has finished. */
    public static Set<HardwareChange> hardwareChangedSinceLastTune() {
        Minecraft mc = Minecraft.getInstance();
        return HardwareProbe.detectIfReady(mc)
                .flatMap(now -> LastTuneStore.read(mc).map(last -> HardwareChange.between(last, now)))
                .orElse(Set.of());
    }

    /** A text report for bug reports: hardware, configuration, and every setting the mod changed. */
    public static String report() {
        Minecraft mc = Minecraft.getInstance();
        Hardware hardware = HardwareProbe.detect(mc);
        List<String> environment = List.of(
                "Minecraft " + mc.getLaunchedVersion(),
                "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                "OS " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"),
                "Shader pack in use: " + ShaderPack.active());
        return DiagnosticReport.format(environment, hardware, HardwareProbe.gpuName(), ConfigStore.load(mc),
                memoryAdvice(), hardwareChangedSinceLastTune(), changedSettings());
    }

    /** Option values as the text shown to the player and compared with each other. */
    private static Map<String, String> texts(JsonObject snapshot) {
        Map<String, String> texts = new LinkedHashMap<>();
        snapshot.entrySet().forEach(entry -> texts.put(entry.getKey(), text(entry.getValue())));
        return texts;
    }

    private static String text(JsonElement value) {
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
}
