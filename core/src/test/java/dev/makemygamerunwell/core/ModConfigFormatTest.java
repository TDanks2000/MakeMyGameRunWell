package dev.makemygamerunwell.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModConfigFormatTest {
    private static final Set<String> OPTIONS = Set.of("enableVsync", "renderClouds", "renderDistance");

    private final List<String> warnings = new ArrayList<>();

    private ModConfig parse(String text) {
        return ModConfigFormat.parse(text, OPTIONS, warnings::add);
    }

    @Test void theGeneratedTemplateIsTheDefaultConfigurationAndWarnsAboutNothing() {
        assertEquals(ModConfig.DEFAULT, parse(ModConfigFormat.TEMPLATE));
        assertEquals(List.of(), warnings);
    }

    @Test void everySettingCanBeChanged() {
        var config = parse("""
                {"showButton": false, "benchmark": false, "profile": "Medium", "maxProfile": "low", "maxRenderDistance": 10,
                 "maxSimulationDistance": 6, "fpsLimit": 90, "gpuClass": "HIGH_END",
                 "keepOptions": ["enableVsync", "renderClouds"]}""");
        assertFalse(config.showButton());
        assertFalse(config.benchmark());
        assertEquals(Optional.of(TuningPlan.Tier.MEDIUM), config.limits().forcedTier());
        assertEquals(TuningPlan.Tier.LOW, config.limits().maxTier());
        assertEquals(10, config.limits().maxRenderDistance());
        assertEquals(6, config.limits().maxSimulationDistance());
        assertEquals(90, config.limits().fpsLimit());
        assertEquals(Optional.of(Hardware.Gpu.HIGH_END), config.limits().gpuClass());
        assertEquals(Set.of("enableVsync", "renderClouds"), config.keepOptions());
        assertEquals(List.of(), warnings);
    }

    @Test void goalsAndTargetsAreReadIndependentlyAndInvalidValuesFallBack() {
        var config = parse("{\"goal\": \"Quiet\", \"targetFps\": 144}");
        assertEquals(new TuningPreferences(TuningPreferences.Goal.QUIET, 144), config.preferences());
        assertEquals(List.of(), warnings);
        for (String value : new String[]{"-1", "29", "241", "60.5", "null", "[]", "\"fast\""}) {
            warnings.clear();
            config = parse("{\"goal\": \"beautiful\", \"targetFps\": " + value + "}");
            assertEquals(new TuningPreferences(TuningPreferences.Goal.BEAUTIFUL, 60), config.preferences());
            assertEquals(1, warnings.size());
        }
        warnings.clear();
        config = parse("{\"goal\": [], \"targetFps\": 0}");
        assertEquals(new TuningPreferences(TuningPreferences.Goal.SMOOTH, 0), config.preferences());
        assertEquals(1, warnings.size());
    }

    @Test void commentsAreAccepted() {
        var config = parse("// note\n{ /* off */ \"showButton\": false // trailing\n}");
        assertFalse(config.showButton());
        assertEquals(List.of(), warnings);
    }

    @Test void anEmptyObjectMeansDefaults() {
        assertEquals(ModConfig.DEFAULT, parse("{}"));
        assertEquals(List.of(), warnings);
    }

    @Test void unreadableFilesFallBackToDefaultsWithOneWarning() {
        for (String text : new String[]{"", "not json", "[1, 2]", "{\"showButton\": ", "{\"showButton\": false,}"}) {
            warnings.clear();
            assertEquals(ModConfig.DEFAULT, parse(text), text);
            assertEquals(1, warnings.size(), text);
        }
    }

    @Test void aBadSettingIsReplacedByItsDefaultAndTheRestStillApplies() {
        var config = parse("""
                {"showButton": "yes", "benchmark": 1, "profile": "ultra", "maxProfile": 5, "maxRenderDistance": 1,
                 "maxSimulationDistance": 4.5, "fpsLimit": 300, "gpuClass": "unknown", "keepOptions": "all"}""");
        assertEquals(ModConfig.DEFAULT, config);
        assertEquals(9, warnings.size(), warnings.toString());

        warnings.clear();
        config = parse("{\"maxRenderDistance\": 8, \"fpsLimit\": \"fast\"}");
        assertEquals(8, config.limits().maxRenderDistance());
        assertEquals(0, config.limits().fpsLimit());
        assertEquals(1, warnings.size());
    }

    @Test void settingsOfTheWrongShapeAreReported() {
        parse("{\"showButton\": [], \"benchmark\": {}, \"maxRenderDistance\": {}, \"profile\": [], \"gpuClass\": 3}");
        assertEquals(5, warnings.size(), warnings.toString());
    }

    @Test void unknownSettingsAndUnknownOptionNamesAreReportedNotFatal() {
        var config = parse("{\"showbutton\": false, \"keepOptions\": [\"enableVsync\", \"fov\", 7]}");
        assertTrue(config.showButton());
        assertEquals(Set.of("enableVsync"), config.keepOptions());
        assertEquals(3, warnings.size(), warnings.toString());
    }
}
