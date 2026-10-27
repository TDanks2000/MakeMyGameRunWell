# Validation — 1.0.0, 27 October 2026

## Build and unit tests

`gradlew clean build` passed on 27 October 2026 for all four jars (Fabric and NeoForge, Minecraft 26.2 and 26.1.2) with 104 JUnit tests and 0 failures: memory advice (8), setting differences (2), hardware change detection (5), the diagnostic report (2), policy and range checks for tuning (20), the benchmark state machine including selective trials (14), gameplay sampling (8), frame rate caps (5), preferences (6), the configuration format (9) and its model (5), frame statistics (6), GPU name classification (9) and CPU/RAM sanity rules (5). The generated `fabric.mod.json` and `neoforge.mods.toml` carry the right Minecraft, Fabric API and NeoForge ranges (26.2: `>=26.2 <26.3`; 26.1.2: `>=26.1.2 <26.2`).

## Real-client tests on the final code

On 27 October 2026, after a final cleanup pass over the code, `gradlew :<loader>-<version>:runClient -PsmokeTest` passed with this mod only on all four combinations: Fabric 26.2 (4m15s), NeoForge 26.2 (3m24s), Fabric 26.1.2 (3m13s) and NeoForge 26.1.2 (3m18s). On 26.1.2 the graphics device type is not reported, so the GPU class comes from the device name alone; this was only tried on the test machine.

The `modpack` and other compatibility profiles were last run on the 1.0.0 code before the cleanup and the 26.1.2 backport, on 26.2 only (the pinned mod downloads are 26.2 only); they were not re-run.

## Real-client tests

**Compatibility runs, earlier 1.0.0 code.** On 27 October 2026, with the GPU otherwise idle, the real-client test passed on Fabric (3m17s) and NeoForge (3m21s) with this mod only, and on both loaders with the full `modpack` profile (every mod in the table below that has a 26.2 release for that loader; 17 jars on Fabric, 15 on NeoForge; about 3m20s each). The only error lines from this mod in those logs are the two deliberate failed-save cases. Benchmarks measured 220 to 430 fps at the High profile. Two earlier attempts on the same code while a large modpack client was using the GPU failed on timing checks (63 fps against a required 90, and 45 fps average), which is why the results above were taken on an idle machine; the test asserts absolute frame rates and is not reliable under GPU load.

**Bug found by the modpack run and fixed.** With Dynamic FPS installed, a minimised window renders no frames, so a benchmark started and then minimised within three seconds was mistaken for a missing frame hook and applied the hardware estimate instead of cancelling. It now cancels and leaves the settings untouched when the window is unfocused at that point. The existing focus-cancellation check caught it.

Each run starts the actual game from the title screen and fails the Gradle task if any check fails. A run:

- opens the real Options screen, finds and presses the injected button, and checks immediately applied settings and the saved `options.txt`;
- presses it again, then Restore, and checks `options.txt` is byte-for-byte identical to its earlier contents, and that FOV and mouse sensitivity were never touched;
- replaces an unreadable backup (keeping the bad one as `.corrupt`) and restores from a backup missing an option;
- checks the default `config/makemygamerunwell.json` is created; that `keepOptions`, `profile`, `maxRenderDistance` and `fpsLimit` take effect; that the backup holds only changed options and grows correctly when a kept option is released, with Restore still exact; that `showButton: false` removes the button; and that a broken configuration file falls back to defaults;
- resizes the screen and checks exactly one button remains;
- creates and joins a world and presses the button there, which starts the real benchmark: checks the button locks and shows progress, that `options.txt` is unchanged and no backup exists until it ends, that the result is applied with the frame rate limit restored, that the backup holds the values from before the benchmark, that the integrated server received the new view and simulation distances and kept running, and that Restore returns `options.txt` byte-for-byte;
- runs the benchmark again through the API and checks it reports a benchmarked result with measured frames and progress;
- closes the Options screen mid-benchmark and checks the benchmark stops and `options.txt`, the live options and the backup are exactly as before (this found and fixed a bug: vanilla saves options when the screen closes, so the rollback now saves as well);
- resizes the screen mid-benchmark and checks the rebuilt buttons are locked, then show the result.

The suite also covers the later features, through `TuningFeatureChecks`: a tune records the hardware it was made for, a changed processor is noticed and an unreadable record is ignored, the changed-settings screen lists the render distance with its original value, **Copy report** puts the report on the clipboard, **Put back** restores only the first setting and drops only it from the backup, and Restore afterwards is byte-for-byte and removes the record; no shader pack is reported without Iris; the **Tune...** screen opens and saves goal and target without touching game settings or other configuration keys; a 144 FPS target caps at 150; a failed `options.txt` save during apply or restore rolls back and is reported as failed; a selective benchmark removes only what is needed and Restore is byte-for-byte; **Learn while I play** is disabled outside a world, never writes options or a backup, ignores time in menus, can be cancelled, and a stale sample can never be applied; a slow sample yields one suggestion (entity distance), applying it changes and backs up only that option, and Restore returns the file exactly; changing preferences cancels a running observation; and a benchmark cancelled by minimising the window rolls back.

The earlier benchmark-only runs, and the runs with Sodium 0.9.2 and Lithium 0.25.3 installed (`-PcompatTest`), also passed on both loaders with Fabric Loader 0.19.5 + Fabric API 0.161.0+26.2 and NeoForge 26.2.0.93. Profiles other than `none` and `modpack` were run on earlier code, before the benchmark and tuning features existed, and were not re-run:

| Profile | Mods installed with it (Fabric / NeoForge where available) |
| --- | --- |
| none | this mod only |
| base | Sodium 0.9.2, Lithium 0.25.3 |
| video-settings | Sodium, Sodium Extra 0.9.4, Reese's Sodium Options 2.2.4, Iris 1.11.4 |
| performance | Sodium, Lithium, FerriteCore 9.0.0, ImmediatelyFast 1.16.5, Entity Culling 1.11.3, MoreCulling 1.8.1, Cloth Config 26.2.155, BadOptimizations 2.4.1, Dynamic FPS 3.11.9, ScalableLux 0.2.1 |
| config-libraries | Mod Menu 20.0.3 (Fabric), Text Placeholder API, Cloth Config, YACL 3.9.7, Configured Defaults 26.2.0 |
| distant-horizons | Distant Horizons 3.3.3 |
| modpack | video-settings + performance + config-libraries |
| rendering-extras | Sodium, Entity Texture Features 7.2.6, Entity Model Features 3.3.12, Continuity 3.0.1 (Fabric), LambDynamicLights 4.12.4 (Fabric), Not Enough Animations 1.12.5, Bobby 5.2.15 (Fabric), GeckoLib 5.5.x |
| ui-and-utility | Xaero's Minimap 26.6.0 and World Map 1.47.0, JourneyMap 6.0.10, AppleSkin 3.0.10, Jade, JEI 30.42.0.236 + Mezz Config, Mouse Tweaks 2.31, Controlling 26.2.4 + Searchables, No Chat Reports 2.20.2, Language Reload (Fabric), BetterF3 19.0.1, Cloth Config, Chat Heads 1.3.2, Freecam 1.5.0, Carry On 2.11.2, Architectury API 21.1.12, Simple Voice Chat 2.6.22 |
| technical (Fabric only) | malilib 0.29.6, Litematica 0.28.8, Tweakeroo 0.29.5, MiniHUD 0.40.7, Replay Mod 2.6.28 |
| kitchen-sink | every mod above except Distant Horizons and the technical set |

The game logs of the 21 earlier runs contain no error from this mod; the final modpack runs contain only the deliberate failed-save cases. The only warnings from it are the deliberate corrupt-backup and invalid-configuration cases in the test. Other mods logged their own first-run messages (for example Tweakeroo and malilib reporting no config file yet); they were not caused by this mod.

The non-fatal mixin configuration was exercised on Fabric by pointing the injection at a method that does not exist: Mixin logged one warning, the game kept running, no crash report was written, and the button was absent. That temporary change was reverted. The same was done for the new frame hook (`MinecraftMixin`): Mixin logged one warning and no crash report was written; pressing the button in a world waited three seconds, logged that no frames arrived, and applied the hardware estimate instead of hanging. That change was also reverted.

Environment: Windows 11, Java 25.0.4.1, eight physical CPU cores, 32 GB RAM, NVIDIA RTX 2080 SUPER, OpenGL backend. NeoForge's cosmetic upstream Sodium/Lithium metadata warnings were logged and their blocking warning screen was disabled only in the isolated smoke instance. Compatibility mods come from Modrinth at exact pinned versions with checksum verification (`scripts/fetch_compat_mods.py`).

## Not tested

The memory line on the Tune screen (only its policy is unit tested; the client test does not assert what the screen draws), the shader-pack note with Iris installed and a pack active (reflection returns false without Iris; it was not run with Iris), the hardware-change tooltip text (only the underlying detection), paging in the changed-settings list with more rows than fit, and the CI matrix on Linux, macOS and Windows runners (CI has not run). Gameplay learning on real hardware under load (the test drives it with a deliberately slowed frame hook; the suggestion it makes is untested advice, not a measured improvement), the benchmark stepping down on a machine that is too slow (only unit tested: the test machine passes the top profile at every attempt), the benchmark with a shader pack active (Iris is in the modpack profile but no pack was loaded), benchmarking with a render distance smaller than the profile's in a real client, Vulkan, shader packs in use, macOS and Linux, dedicated servers, multiplayer servers, mods not listed above, and other releases of the listed mods. The broken-mixin check was run on Fabric only. NeoForge's mod loader was exercised through the normal runs, not a deliberately broken injection. Mods with no 26.2 release at the time of testing (for example Embeddium, ModernFix, OptiFine) could not be tried.

The supplied icon is original generated artwork; the full-size sources are in `artwork/` and both loaders ship a 256x256 copy.

## Release history

0.1.0, 0.1.1 and 0.1.2 were uploaded to CurseForge as Beta (Fabric and NeoForge, Minecraft 26.2, Java 25). 0.1.1 was uploaded after a build and unit tests only. 1.0.0 was uploaded on 27 October 2026 as Release, Fabric (with Fabric API as a required dependency) and NeoForge, after the build, unit tests and client tests described above; CurseForge showed both as Approved a few hours later. The 26.1.2 builds of 1.0.0 (Fabric with Fabric API as a required dependency, and NeoForge) were uploaded on 27 October 2026 as **Beta**, not Release, because the 26.1.2 client test has not yet passed in full (see the 26.1.2 section above); NeoForge showed Approved straight away and Fabric was still uploading when this was written. The 26.1.2 client tests described above passed on both loaders afterwards; those uploads were not changed.
