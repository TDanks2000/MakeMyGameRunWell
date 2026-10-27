package dev.makemygamerunwell.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An option whose value differs from what the player had before the mod changed it. */
public record SettingChange(String option, String original, String current) {
    /**
     * @param originals the values saved before tuning, in display order
     * @param currents the values now; options missing here are skipped
     */
    public static List<SettingChange> differing(Map<String, String> originals, Map<String, String> currents) {
        List<SettingChange> changes = new ArrayList<>();
        originals.forEach((option, original) -> {
            String current = currents.get(option);
            if (current != null && !current.equals(original)) changes.add(new SettingChange(option, original, current));
        });
        return List.copyOf(changes);
    }
}
