# Omni Launcher — performance audit and device validation (October 2026)

Target: Android 11 / Infinix HOT 10S (Helio G85, ~4 GB RAM). This is a **measurement plan**, not a claim of observed improvements. Keep the original Lawnchair 15 functionality and user customization intact.

## Confirmed source-level work

| Risk in 15-dev | Implemented change | Affected path |
| --- | --- | --- |
| Launcher `onCreate` performed three full SQLite WAL checkpoints synchronously using `runBlocking`. | Build Room on Main as before, then perform one database-wide checkpoint after a 5-second delay on an IO coroutine attached to the activity lifecycle. Failed optional checkpoint is logged rather than crashing Home. | `LawnchairLauncher.kt`, `data/AppDatabase.kt` |
| Launcher `onCreate` also enumerated themed icon packs through PackageManager and synchronously read the always-reload-icons preference. | Perform package enumeration and preference reads off Main, with only the final preference/reload update on Main. Icon reload may now occur after the initial frame when explicitly enabled. | `LawnchairLauncher.kt` |
| Each local search ran preference reads, match scoring and shortcut queries on Launcher3's model update queue. | Copy the app list on the model executor, then perform the searches on the existing cancellable IO scope. Keep generation checks and Main-thread delivery of the resulting adapter items. | `LawnchairLocalSearchAlgorithm.kt`, `engine/provider/apps/AppSearchProvider.kt` |
| Contact photos could be decoded at source resolution for a tiny search result. | Probe image bounds, reopen the stream and sample to a maximum side of about 192px, falling back to initials if unavailable. | `search/adapter/SearchTargetFactory.kt` |
| Every search result ID locked a shared SHA-256 digest across concurrent providers. | Use per-thread digest instances, preserving the exact SHA-256 result format. | `search/adapter/SearchTargetFactory.kt` |
| Wallpaper-blur CPU work continued after a newer wallpaper request invalidated it. | Check the request generation during both bounded CPU passes, releasing the input bitmap when superseded. | `util/WallpaperBlurRenderer.kt` |

No animation timing, layout setting, system permission, UI strings, icon-pack or widget implementation is deliberately changed. The memory cost of thread-local digests is negligible relative to one decoded full-resolution contact bitmap, but must still be profiled on device.

## Build and static checks

From the repo root (JDK 21, matching Android SDK build tools):

```sh
./gradlew spotlessCheck
./gradlew assembleLawnWithQuickstepGithubDebug
./gradlew assembleLawnWithQuickstepGithubRelease
./gradlew assembleLawnWithQuickstepPlayDebug
```

The existing JVM unit test source set is known to yield NO-SOURCE. Do not treat it as an executed test suite. The GitHub CI matrix, source-isolation check and builds must complete successfully; physical-device performance is separate.

## Reproducible checks on the phone

Install matching signed builds; set **Omni Launcher** as Home and keep the same icon-pack, wallpaper, widgets and search-provider settings for baseline vs candidate. Repeat with Arabic (RTL) and English.

```sh
adb shell am force-stop app.lawnchair
adb shell am start -W -n app.lawnchair/app.lawnchair.LawnchairLauncher
adb shell dumpsys gfxinfo app.lawnchair reset
# Manually repeat: Home -> WhatsApp -> Home, open/close App Drawer, scroll lists.
adb shell dumpsys gfxinfo app.lawnchair framestats > launcher-gfxinfo.txt
adb shell dumpsys meminfo app.lawnchair > launcher-memory.txt
```

`am start -W` reports Activity timing, **not** first fully drawn frame or home-return latency. For dependable first-frame, slow/broken frame and binder contention evidence, capture Perfetto/System Trace and use Macrobenchmark (cold start, warm home return, drawer scroll) on-device. Evaluate at least 10 repetitions per scenario, after one warm-up; compare medians and p95s against the previous signed release. Never compare debug to minified release as a performance regression.

### Scenarios to cover

1. Cold launch; three subsequent HOME returns from WhatsApp and a game; record wall time, frames, binder calls and GC.
2. 100+ installed apps: fast typing, deleting and alternating Arabic/English in app-only search; fuzzy on/off, hidden apps and shortcuts.
3. On-device local search with contacts, files and web suggestions; verify query cancellation and that stale results do not replace recent queries.
4. Repeat wallpaper changes, blur enabled/disabled and theme transitions while monitoring CPU, heap and delayed completions.
5. Pin folders/widgets, open Recents/Quickstep (if this ROM grants integration), rotate if supported, change icon packs and restart.
6. Verify OmniLink signed/unsigned Workspace integration and both RTL/dark-light settings, without new consent prompts or main-thread network/disk I/O.

### Acceptance criteria

- No search-related ANR; no missing search results, unexpected background starts or new crashes.
- All three APK build variants and `spotlessCheck` green.
- No increased average/p95 frame time, cold-start time, warm-home-return time or idle-home PSS compared with baseline; investigate any regression before merge.
- Maintain 60 fps target (16.67ms budget) where hardware/ROM supports it; report actual misses instead of promising zero dropped frames.
- Compare APK size with `stat` or `apkanalyzer`; do not claim a size reduction without measurements.
- If the ROM/driver cannot reach the target, label it as hardware-dependent rather than hiding the result.

### Limits

Source inspection alone cannot establish actual frame time, energy, peak memory, APK size or Quickstep compatibility. There is no remote access to the owner's physical device from CI. Additional tuning of icon caches, RecyclerView and touch/animation paths should be based on Perfetto evidence to avoid speculative regressions.


### App inventory and integration settings regression

On the Infinix HOT 10S, open Search aliases and create/edit a rule folder with Arabic and English
apps installed. Verify real app names (no literal `null`), package/profile identity, multilingual
search, accurate rule counts and mutually exclusive Include/Exclude chips. Hide/unhide and
install/remove an app while an editor is active; return to it and verify that the list refreshes.
Repeat after changing the launcher language, with an empty search and a query with no results.
Verify that alias rows open their editor and that clearing aliases removes the saved override.

Open Phone integration. Check standard/system installation and actual enterprise-role/Recents
states against the device setup; an ordinary install must not claim privileged capabilities.
Open the details dialogs, verify long Arabic text scrolls at large font size, and confirm that
switches remain reachable. Select each installed Workspace flavor, return from background,
and verify the saved label/package survives restart. Reset to choose each time. Uninstall the
selected Workspace and verify the stale choice is reported and can be reset. A different-key
Workspace must not enable new launcher-control consent; an existing opt-in can always be revoked.
Inventory stores text labels only, retains one event-driven catalog, and performs no icon decode.
Measure editor entry/typing/scrolling and Home return with the same frame-time and memory budgets.
