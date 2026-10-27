# Make My Game Run Well

[![Build](https://github.com/TDanks2000/MakeMyGameRunWell/actions/workflows/build.yml/badge.svg)](https://github.com/TDanks2000/MakeMyGameRunWell/actions/workflows/build.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A client mod for **Minecraft Java 26.2 and 26.1.2**, with separate Fabric and NeoForge jars for each version.

Open **Options** and click **Make My Game Run Well**. Inside a world, the mod measures frame rate and tries individual setting reductions until your target is met or its bounded search finishes. From the title screen it applies a hardware estimate immediately. **Restore** returns settings to their values before the first tune, including after restarting Minecraft. Repeated tunes preserve those original values until you restore them.

**Tune...** lets you choose Smooth, Beautiful, or Quiet and a target FPS. It also offers **Learn while I play**, an explicit, read-only gameplay observation with an optional suggestion to review afterwards.

## Installation

Use Java 25 and install exactly one jar in your client's `mods` folder:

| Minecraft | Fabric | NeoForge |
| --- | --- | --- |
| 26.2 | Fabric Loader 0.19.3 or later and Fabric API 0.161.0 or later | NeoForge 26.2.0.93 or later |
| 26.1.2 | Fabric Loader 0.19.3 or later and Fabric API 0.155.3 or later | NeoForge 26.1.2.124 or later |

Build outputs are in `versions/<Minecraft version>/fabric/build/libs` and `versions/<Minecraft version>/neoforge/build/libs`. Use the jars without `-sources`. This is a client mod; servers do not need it.

## What it changes

The button sets render and simulation distance, entity distance, FPS limit, VSync, clouds and cloud range, weather radius, leaves, transparency, ambient occlusion, particles, entity shadows, biome blending, mipmaps, texture filtering, anisotropy, chunk update priority, chunk fading, menu blur, and vignette.

It first considers physical CPU cores and clock speed, system RAM, Minecraft's allocated heap, the active graphics device, framebuffer resolution, and display refresh rate. Four conservative tiers use 5/8/12/16 chunks of render distance. High resolution and memory limits reduce the chosen tier. Unknown hardware falls back to the lowest tier. GPU recognition is by device name and is approximate. That tier provides the starting quality ceiling: the benchmark reduces individual settings without raising quality above it. CPU and RAM details are read on a background thread when Options opens; if that query is slow or fails, the button falls back to the JVM's own figures instead of freezing the game.

### Goals and target FPS

Open **Tune...** to cycle the goal and target, then **Save preferences**. Saving changes this mod's configuration, not your game settings; press the main tuning button to use the preferences. Other JSON settings are retained when saving; comments are replaced by formatted JSON.

- **Smooth** (default) requires a paused benchmark average of 1.5 times your target and slow-frame FPS at least equal to the target.
- **Beautiful** uses 1.15 times the target and tries distance reductions before removing visual effects.
- **Quiet** uses 1.8 times the target, seeking more spare performance before applying the FPS cap. It does not measure temperature, power use, or battery life.

The default target is **60 FPS**. The menu offers 30, 60, 90, 120, 144, 165, 240, and display refresh. The configuration accepts any whole number from 30 to 240. Unknown refresh falls back to 60, and display targets are bounded to 30-240. The final cap rounds up to Minecraft's next step of ten: a 144 target gets a 150 cap. The existing `fpsLimit` override takes priority over the target and cap; an unlimited override uses a 240 FPS benchmark target.

### The quick benchmark

The world keeps rendering behind Options while the button shows progress. The benchmark first measures the hardware plan, then tries one setting reduction at a time: clouds, entity shadows, weather radius, particles, biome blending, entity distance, render distance, simulation distance, ambient occlusion, or leaf detail. A reduction that improves average or slow-frame FPS by at least 5%, without losing over 5% in the other metric, becomes the basis for the next trial. Ineffective reductions are discarded. The search ends when a trial passes, no further supported trials remain, or 24 measurements have run.

Each measurement waits at least one second for chunk rebuilding (up to eight seconds), then samples about two and a half seconds of frames. A fast machine often finishes in about four seconds; an unsuccessful search can take several minutes. Even the lowest hardware profile is measured. The result tooltip reports the starting hardware tier, measured FPS, final cap, and whether the benchmark target was reached. Missing the target is explicitly reported.

- Nothing is saved until the benchmark ends. Closing Options, leaving the world, losing window focus, or Minecraft throttling its frame rate cancels the test and restores the pre-click values.
- Chunks beyond the loaded distance cannot be measured directly. The test draws at the loaded distance and requires extra headroom, up to twice the normal threshold. Reducing untested excess distance can also be retained when measured FPS stays within 5%. This remains an estimate; test with the desired chunks already loaded for a more representative result.
- Mipmaps, filtering, and anisotropy are excluded from trials to avoid texture reloads. They are applied with the final plan. Individual leaf trials preserve vignette.
- Keeping `maxFps` or `enableVsync`, forcing a `profile`, or setting `benchmark` to `false` skips the quick benchmark. Hardware estimation still respects your target, limits, and kept options.
- A missing frame hook times out after three seconds and falls back to the hardware estimate.

The benchmark samples a paused scene. Its reserve helps account for gameplay, but it does not promise an in-play frame rate or find the fastest possible settings combination.

### Learn while I play

Join a world, open **Tune...**, and choose **Learn while I play**. The menu closes and observation begins without changing options, lifting caps, or writing a backup. Play normally for up to two minutes. Menus, overlays, pause, lost focus, and Minecraft's own throttling do not count towards that time. The sample is bounded to 240,000 frame intervals and expires after ten minutes of wall time. **Stop learning** cancels it.

A notification tells you when the sample is ready. Return to **Options > Tune...** to see average and slow-frame FPS (the reciprocal of the 95th-percentile frame interval). Gameplay passes at 95% of the requested average and 90% of the requested slow-frame target, allowing for normal frame-cap timing. If an FPS cap or VSync is below the target, the result explains that instead of recommending lower detail.

When gameplay falls short, the menu can suggest **one** reduction in the goal's preferred order. This is explicitly an **untested suggestion**, not a diagnosed bottleneck or a measured improvement. **Apply suggested reduction** changes only that option, saves it with rollback protection, and backs up its original value. Restore undoes it, including when a previous tuning backup exists. Learn again to measure the effect. Forced profiles and `keepOptions` are respected.

Changing owned settings, framebuffer size, display refresh, resource-pack selection, world, or tuning configuration invalidates an observation. Samples stay in memory and are never uploaded. Mod-specific shader settings are not observed, so learn again after changing them.

### Changed settings, put-back, and reports

**Tune... > Changed settings...** lists every setting the mod changed that still differs from your own value, with both values. **Put back** returns just that one setting and removes it from the backup, leaving the rest of the tune in place; like every other change it is all-or-nothing and rolled back on failure. **Copy report** puts a plain-text report on the clipboard for a bug report: Minecraft, Java and operating system versions, CPU, memory and graphics device, display, the mod's configuration, and the changed settings. It contains no account or personal data.

### Advice that needs no tuning

- **Memory.** Tune... shows a line when the memory Minecraft was started with looks too small for your render distance, or so large it starves the system. It suggests a figure in MB. The mod never changes JVM memory; raise or lower it in your launcher.
- **Hardware changes.** After a tune the mod records the machine it was made for in `config/makemygamerunwell/last-tune.json` (removed by Restore). If the processor, memory, graphics class or refresh rate later differs, the button's tooltip says so. Window size is ignored. The hint appears once the background hardware query has finished, so the first Options screen of a session may not show it.
- **Shader packs.** If Iris is rendering a shader pack, the result tooltip says the pack's cost was part of the measurement and that these settings cannot reduce it. The mod does not read or change shader settings.

Controls, FOV, sensitivity, audio, language, accessibility preferences, resource packs, display mode, and graphics backend retain their existing values. The mod does not change JVM memory allocation, server settings, or world difficulty. Minecraft may label the resulting graphics preset **Custom**; restore returns the individual settings rather than the old preset label.

The backup is `config/makemygamerunwell/previous-settings.json` inside your Minecraft instance. It holds the original value of every option the mod has changed, and an entry is never overwritten once it exists (Put back removes an entry). A failed backup prevents tuning. Saved values are verified against `options.txt`; failures are reported in the log and trigger a rollback. An unreadable backup is set aside as `previous-settings.json.corrupt` and replaced on the next click; a backup from an older version that lacks some options still restores the rest.

## Configuration

The first launch creates `config/makemygamerunwell.json` with every setting documented in the file itself. Edit it and save; the changes apply the next time the button is used or Options opens, with no restart. Modpack authors can ship their own copy in the pack's `config` folder.

| Setting | Default | Meaning |
| --- | --- | --- |
| `showButton` | `true` | Show the buttons on the Options screen. |
| `benchmark` | `true` | Measure frame rate and try individual reductions. `false` applies the hardware estimate immediately. |
| `goal` | `"smooth"` | `"smooth"`, `"beautiful"`, or `"quiet"`; controls benchmark reserve and reduction order. |
| `targetFps` | `60` | Target FPS, 30-240; `0` uses display refresh. The final cap rounds up to a step of ten. |
| `profile` | `"auto"` | `"auto"` chooses from the hardware. `"minimal"`, `"low"`, `"medium"` or `"high"` forces that profile on every machine. |
| `maxProfile` | `"high"` | The highest profile `"auto"` may choose. Lower it for heavy modpacks. |
| `maxRenderDistance` | `0` | Cap in chunks (2 to 32); `0` for none. A cap only lowers the distance. |
| `maxSimulationDistance` | `0` | Cap in chunks (5 to 32); `0` for none. |
| `fpsLimit` | `0` | Frame rate limit instead of the automatic one, a multiple of 10 from 10 to 260; `0` for automatic. |
| `gpuClass` | `"auto"` | Overrides graphics detection with `"software"`, `"integrated"`, `"discrete"` or `"high_end"`. |
| `keepOptions` | `[]` | Options the mod must never change, named as in `options.txt`. |

`keepOptions` accepts: `renderDistance`, `simulationDistance`, `entityDistanceScaling`, `maxFps`, `enableVsync`, `renderClouds`, `cloudRange`, `weatherRadius`, `cutoutLeaves`, `improvedTransparency`, `ao`, `particles`, `entityShadows`, `biomeBlendRadius`, `mipmapLevels`, `textureFiltering`, `maxAnisotropyBit`, `prioritizeChunkUpdates`, `chunkSectionFadeInTime`, `menuBackgroundBlurriness`, `vignette`. Use it to stop the mod fighting another mod or a setting you prefer, for example `["enableVsync"]`. Kept options are neither changed nor added to the backup; **Restore** still puts back everything the mod did change.

The file may contain `//` comments. A mistake in it never breaks the game: an invalid setting is logged and replaced by its default while the rest still applies, and an unreadable file keeps the last good configuration. The tooltip says when the configuration held the profile back.

## Compatibility

This mod only calls Minecraft's own option setters, adds one row to the main Options footer and a tuning preferences screen, and never edits another mod's files, patches the renderer or chunk logic, or force-disables anything. Hardware detection uses the graphics abstraction rather than direct OpenGL queries. The footer injection is non-fatal (`required: false`): if another mod or a game update changes the Options screen so it no longer applies, Mixin logs one warning and the button is simply absent. This was tested by breaking the injection on purpose; the game ran on without a crash.

If another mod manages an option this mod sets, add that option to `keepOptions` and the mod will leave it alone.

Tested together with this mod (see [VALIDATION.md](VALIDATION.md) for versions and results):

- **Video settings:** Sodium, Sodium Extra, Reese's Sodium Options, Iris Shaders, Distant Horizons
- **Performance:** Lithium, FerriteCore, ImmediatelyFast, Entity Culling, MoreCulling, BadOptimizations, Dynamic FPS, ScalableLux
- **Rendering add-ons:** Entity Texture Features, Entity Model Features, Continuity, LambDynamicLights (Fabric), Not Enough Animations, Bobby (Fabric), GeckoLib
- **Interface and utility:** Mod Menu (Fabric), Cloth Config, YACL, Configured Defaults, Xaero's Minimap and World Map, JourneyMap, AppleSkin, Jade, JEI, Mouse Tweaks, Controlling, No Chat Reports, Language Reload (Fabric), BetterF3, Chat Heads, Freecam, Carry On, Architectury API, Simple Voice Chat
- **Technical (Fabric):** Litematica, Tweakeroo, MiniHUD, malilib, Replay Mod
- All of the above together in one large pack, on both loaders where the mod exists for 26.2.

The automated client test covers the button, immediate application, persistence, repeated clicks, exact restoration, the configuration file, resizing, and, inside a joined world, the benchmark itself: its progress, that nothing is saved before it ends, the result, interrupting it, and resizing the screen during it. Results come from one machine and one release of each mod. Compatibility checks of the newer tuning and learning features are identified separately in VALIDATION.md; the broad mod list includes historical runs. This does not establish compatibility with every mod or shader pack. Mod-specific graphics settings and shaders are not configured by this mod, and real-world performance depends on the world and modpack.

## Development

Set `JAVA_HOME` to a JDK 25 installation, then run:

```powershell
.\gradlew.bat build
```

The build uses Gradle 9.5.1, Loom 1.17.21, and ModDevGradle 2.0.148. Shared game integration lives in `common`, hardware policy and unit tests in `core`, loader metadata in `loaders/fabric` and `loaders/neoforge`, and everything that differs per Minecraft version in `versions/<Minecraft version>`. See [AGENTS.md](AGENTS.md) for the layout and contribution rules.

Launch the automated client integration tests on a machine with graphics and audio support:

```powershell
.\gradlew.bat :fabric-26.2:runClient -PsmokeTest
.\gradlew.bat :neoforge-26.2:runClient -PsmokeTest
python scripts/fetch_compat_mods.py modpack
.\gradlew.bat :fabric-26.2:runClient -PsmokeTest -PcompatTest=modpack
.\gradlew.bat :neoforge-26.2:runClient -PsmokeTest -PcompatTest=modpack
```

Use `fabric-26.1.2` and `neoforge-26.1.2` for the other version. The compatibility mod downloads in `scripts/fetch_compat_mods.py` are pinned to 26.2 only.

These use separate `run-smoke` game directories, create and join a throwaway world, close the client automatically, and fail the Gradle task if assertions fail. `-PcompatTest` alone uses the `base` profile (Sodium and Lithium); `scripts/fetch_compat_mods.py` lists the other profiles. Smoke code is excluded from normal builds; jar creation is blocked when smoke mode is enabled. Compatibility dependencies are downloaded from upstream with checksum verification and are not bundled or redistributed.

### Adding a Minecraft version

Create `versions/<Minecraft version>/` with a `version.properties` (copy an existing one and change the Minecraft, Fabric API and NeoForge versions) and a `src/main/java/dev/makemygamerunwell/GameCompat.java` that implements the handful of Minecraft calls that changed in that version. Gradle finds the directory by itself and adds `fabric-<version>` and `neoforge-<version>` projects. The compiler lists what needs a shim; everything else is shared.

See [CHANGELOG.md](CHANGELOG.md) for release notes. The mod is released under the [MIT License](LICENSE).

API references: [Fabric 26.2 changes](https://fabricmc.net/2026/06/15/262.html), [Fabric example mod](https://github.com/FabricMC/fabric-example-mod/tree/26.2), [NeoForge MDK](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle), [Sodium](https://github.com/CaffeineMC/sodium), [Lithium](https://github.com/CaffeineMC/lithium).
