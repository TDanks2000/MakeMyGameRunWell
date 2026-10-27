package dev.makemygamerunwell.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingChangeTest {
    @Test void onlyOptionsThatDifferAreListedInTheOriginalOrder() {
        Map<String, String> originals = new LinkedHashMap<>();
        originals.put("renderDistance", "23");
        originals.put("ao", "true");
        originals.put("particles", "\"all\"");
        Map<String, String> currents = Map.of("renderDistance", "8", "ao", "true", "particles", "\"decreased\"");
        assertEquals(List.of(new SettingChange("renderDistance", "23", "8"),
                new SettingChange("particles", "\"all\"", "\"decreased\"")), SettingChange.differing(originals, currents));
    }

    @Test void optionsTheGameNoLongerHasAreSkipped() {
        assertEquals(List.of(), SettingChange.differing(Map.of("gone", "1"), Map.of()));
    }
}
