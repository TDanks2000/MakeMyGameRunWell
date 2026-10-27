# Changelog

## 1.0.0

First stable release, for Minecraft 26.2 and 26.1.2 on Fabric and NeoForge. Supersedes the 0.1.x Beta builds.

- Added Minecraft 26.1.2 builds for both loaders. They behave the same as the 26.2 builds, except that 26.1.2 does not report whether the graphics device is integrated or discrete, so the mod decides from the device's name alone and treats a name it does not recognise as the conservative profile.

- Added **Tune... > Changed settings...**: every setting the mod changed with its original and current value, a **Put back** button for each (only that setting is restored and dropped from the backup), and **Copy report** for bug reports.
- Added a memory line to Tune... when the allocated heap looks too small for the render distance or leaves little for the system. It only advises; the JVM is never touched.
- The button's tooltip now notes when the processor, memory, graphics class or refresh rate differs from the last tune (recorded in `config/makemygamerunwell/last-tune.json`, removed by Restore).
- The result tooltip notes when an Iris shader pack was rendering during the benchmark.
- Added **Tune...** with Smooth, Beautiful, and Quiet goals and selectable target FPS, including display refresh. Preferences persist in the existing configuration without changing game settings. The default target and final cap are 60 FPS; `fpsLimit` takes priority.
- Added a selective in-world benchmark. It measures the starting hardware plan, tries individual reductions, discards ineffective changes, and reports when the target remains unmet. The lowest hardware tier is measured too. Texture quality is excluded from trials, and leaf reductions preserve vignette.
- Added opt-in **Learn while I play**: up to two minutes of read-only active-play measurement, excluding menus and unfocused time. Review one explicitly untested reduction, apply only that option, and Restore it exactly. Changed setups invalidate samples; cap-limited samples explain the cap instead of recommending lower quality.
- Kept the hardware fallback, non-fatal frame hook, original-value backup, configuration limits, and `keepOptions`. Closing a quick benchmark rolls it back; a missing hook times out instead of hanging.
- Corrected failure rollback to compare against the failed live settings when deciding whether textures need refreshing.
- Fixed a benchmark started and then minimised within three seconds being mistaken for a missing frame hook (seen with Dynamic FPS, which stops rendering an unfocused window); it now cancels and leaves your settings alone.
- A stalled hardware query now costs one three-second wait per session instead of one per click, and the two frame-hook watchdogs share one implementation.
- Added unit and real-client coverage for the policy and controls. See VALIDATION.md for the actual runs and remaining limitations.

## 0.1.2

**Configuration.** A new `config/makemygamerunwell.json`, created on first launch with every setting documented in the file. Players and modpack authors can hide the buttons (`showButton`), force or cap the profile (`profile`, `maxProfile`), cap render and simulation distance, set the frame rate limit, override graphics detection (`gpuClass`), and list options the mod must never change (`keepOptions`). Edits apply without restarting, and a mistake in the file never breaks the game: bad settings fall back to their defaults and are logged. See the README.

**Backup.** The backup now holds the original value of every option the mod has changed, so Restore stays exact when `keepOptions` changes between clicks. Backups written by earlier versions still restore.

**Reliability.**
- The button can no longer stall the game on slow hardware queries: CPU and RAM details are read on a background thread, and a click waits at most three seconds before using the JVM's own figures.
- Zero, negative, or absurd CPU and memory readings from the operating system are replaced with safe estimates instead of skewing the chosen profile.
- GPU recognition: Ryzen APUs named "Radeon RX Vega N Graphics" and "Radeon(TM) 780M Graphics" are now integrated graphics rather than discrete cards; short Intel Arc model names are recognised.

**Compatibility.** Tested with 48 other mods on both loaders, including Sodium, Iris, Distant Horizons, entity-rendering add-ons, map and HUD mods, and a large combined pack, plus applying and restoring inside a joined world. The Options-screen injection was also verified to fail safely. See VALIDATION.md.

**Packaging.** Jars are about 2.4 MB smaller: the icon is shipped at 256x256 and the unused first icon is no longer bundled. Added author and logo metadata for both loaders.

**Tests.** 41 unit tests (was 17). The client test now proves Restore returns `options.txt` byte-for-byte, exercises the configuration file, and runs inside a world.

## 0.1.1

- Beta release for Minecraft 26.2 on Fabric and NeoForge.
