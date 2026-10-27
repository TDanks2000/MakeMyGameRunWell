# Agent guide

Client-only Minecraft mod, one jar per loader (Fabric, NeoForge) per supported Minecraft version (26.2, 26.1.2). Adds a "Make My Game Run Well" button to Options that applies hardware-based performance settings, with Restore.

## Layout

- `core/` — pure Java policy (`TuningPlan`, `FrameBenchmark`, `FrameStats`, `TuningLimits`, `GpuClassifier`, `MachineSpecs`, `Hardware`, `HeapAdvice`, `HardwareChange`, `SettingChange`, `DiagnosticReport`, `ModConfig`/`ModConfigFormat` for the config file). No Minecraft types. All tuning decisions live here and are unit tested.
- `common/` — Minecraft-facing code shared by both loaders (compiled into each jar). One class per responsibility: `AutoTuner` (orchestration), `BenchmarkRun` (one live benchmark; the only code that applies options temporarily), `OwnedOptions` (which options we own, snapshot/restore), `BackupStore` (backup file), `SettingsReview` (changed settings, put-back, report), `LastTuneStore` (machine of the last tune), `ShaderPack` (Iris detection by reflection), `ConfigStore` (loads `config/makemygamerunwell.json`), `SettingsApplier` (plan -> options), `OptionsPersistence` (save, verify on disk, refresh), `HardwareProbe`, `ui/OptionsButtons`, `ui/TuningScreen`, `ui/ChangesScreen`, and the mixins.
- `loaders/fabric/`, `loaders/neoforge/` — loader build script and metadata only. Do not put logic here.
- `versions/<minecraft version>/` — one directory per supported game version: `version.properties` (Minecraft, Fabric Loader/API, NeoForge versions and supported range), `src/main/java/dev/makemygamerunwell/GameCompat.java` (the only place that calls Minecraft APIs that differ between versions; every version has its own copy with the same methods), and the generated per-loader project directories (`fabric/`, `neoforge/`) that hold `build/`, `run/` and `run-smoke/`. Shared code must never mention a version: when a Minecraft call changes, add a method to every `GameCompat` instead.
- Adding a version: copy a `versions/` directory, edit `version.properties`, fix what the compiler reports in `GameCompat`, then run the build and both loaders' smoke tests for it.
- `smoke/` — opt-in real-client integration test (mixin + `smoke.gradle`), never shipped.
- The mod version lives in `gradle.properties`; Minecraft and loader versions live in `versions/*/version.properties`. Do not hardcode either elsewhere.

## Commands

Use JDK 25 (`JAVA_HOME`). On this machine a bootstrapped JDK and Gradle are in `.bootstrap/`.

- `gradlew build` — compile, unit tests, jars for both loaders and every version (`versions/<mc>/<loader>/build/libs`).
- `gradlew :fabric-<mc>:runClient -PsmokeTest` and `:neoforge-<mc>:runClient -PsmokeTest` (for example `:fabric-26.2`) — real-client test; run it for every version you touched or that shares code you changed. Add `-PcompatTest` (after `python scripts/fetch_compat_mods.py`) to include Sodium and Lithium.

## Rules

- Mod-owned options are listed only in `OwnedOptions`, and `SettingsApplier` must change exactly those. Never write or restore anything else; never touch other mods' config files (our own `config/makemygamerunwell*` files are fine).
- The config file may never be able to break the game or a tune: bad settings fall back to defaults and are logged.
- Every apply/restore is all-or-nothing: failure rolls back and is logged.
- The mixin must stay thin and non-fatal (`required: false`); logic belongs in normal classes.
- Fabric API is a real dependency: without it the mod's lang files are not loaded. Do not remove it as "unused".
- Anything that changes behaviour needs a unit test in `core/` if it is policy, or a smoke-test assertion if it touches the game.

## Mandatory cleanup pass before you finish any task

Before reporting work as done, review everything you touched, and the code around it, and fix what you find. Do this every time, not only when asked.

1. **Slop**: dead code, unused fields/imports/parameters, leftover debugging, comments that restate the code or describe history, magic numbers without a name, `new int[]{...}[i]`-style inline tables, copy-pasted blocks, fully qualified names where an import works.
2. **Structure**: no file doing several jobs. If a class mixes UI, file I/O, policy, or loader glue, split it by responsibility. Pure decisions go in `core/`.
3. **Bugs and unworking code**: re-read for error paths, off-by-one, platform differences (Windows/macOS/Linux), values that can be zero/negative/unknown, resources not closed, fallbacks that cannot succeed. Do not trust that something works because it compiles.
4. **Quality of life**: stalls on the render thread, unclear failure messages, states the UI can get stuck in, settings the user can't recover from.
5. **Docs and metadata**: README, `VALIDATION.md`, lang files, and loader metadata match the code.
6. **Prove it**: `gradlew build` and both loaders' smoke tests for every supported version must pass; update `VALIDATION.md` with what was actually run. Report anything you could not test instead of implying it.
