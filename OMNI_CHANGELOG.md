# Omni fork changelog

Maintained by **Abdelrahman Hussein (عبدالرحمن حسين / Obieda)** for Omni-specific development,
updates, modifications and ecosystem integration. Original Lawnchair/AOSP authors retain their
credits and rights; see [NOTICE.md](NOTICE.md). Upstream GITHUB_CHANGELOG.md and
TELEGRAM_CHANGELOG.txt remain historical upstream release notes.

## Direct search/dock handoff — 17 October 2026

- Route Ask Omni and the Omni dock directly from the visible Home Activity through one controller, avoiding a transparent intermediary over the Launcher.
- Fix the packaged SDK's OmniJson getter/field mismatch exposed by Android-11 instrumentation; serialize the same typed public payload with a local codec instead of calling the missing getter.
- Keep loading/selection owned by the active window, deduplicate taps, clean up cancellation/failure and wait for resumed compatibility entry points.
- Launch before optional preferred-target persistence and tolerate independent preference cancellation; preserve bounded discovery and existing public SDK validation.
- Add clean-emulator Android-11 cross-process regressions using actual search/dock views, Arabic payloads, multi-target selection, Cancel/retry and the compatibility entry.

## Search handoff and verified Home folder execution — 16 October 2026

- Fix the multi-install Workspace chooser's hidden list and bound discovery/preference waits without retaining the Activity behind a slow Binder call.
- Reuse the tested bounded system-call worker for widget and handoff operations; idle workers expire and outstanding work remains capped.
- Add launcher.create_home_folder with revision checks, unique visible icon IDs, protected occupancy validation, one DB transaction and post-reload verification metadata. Catalog version 3 advertises 25 capabilities.
- Matching Workspace opens search text as a reviewable floating-assistant draft and requires real mutation/readback evidence for Launcher execution requests. Discovery reports incorrect extension IDs directly.
- Expand the direct suite to 46 tests; Android/physical-device validation is tracked separately.

## OmniLink discovery and Home control — 16 October 2026

- Advertise 24 capabilities and report app/catalog versions in launcher.health to identify an old installed APK.
- Add paged Home layout snapshots, revision-checked atomic moves of existing visible app icons, normal Home grid migration and drawer rule-folder creation.
- Preserve widget/folder/dock/hidden-item positions during app moves; reject overlapping, out-of-grid, stale and duplicate requests before writes. Use existing model executor/DB transaction APIs without changing AOSP sources.
- Publish complete nested move/rule schemas; retain receiver tier ceilings, local opt-in, approval, dry-run and late queued-work checks.
- Expand the direct Launcher suite to 44 tests; companion Workspace fixes scoped discovery, update/reconnect cache invalidation and dry-run transport.

## App identity and integration settings — 16 October 2026

- Populate localized app titles in the shared inventory: AppInfo construction alone leaves title null. This fixes labels, name-based folder rules, picker search, Shelf discovery and OmniLink app names together.
- Load and locale-sort text labels on IO, serialize cold inventory loads, and keep component/profile identity without loading icons or adding a second permanent catalog.
- Refresh app pickers on package, hidden-app and locale changes. Search and rule previews run on background dispatchers, with loading/failure/retry/empty states.
- Replace blank alias cards with tappable native preference rows showing app identity and current aliases; add package/profile details and chip spacing to folder overrides.
- Show actual Android capability states, shorten Omni switch summaries and keep full consent/signing explanations in a details dialog. Choose and display the preferred Workspace from the same eligible target discovery used by search handoff.
- The standalone suite now executes 36 tests, including missing/localized label and name-rule regressions. Phone screenshots and ANR traces still require physical Infinix validation.

## Widget editor responsiveness — 16 October 2026

- Bound initial widget loading and Binder waits; keep cancellation and late callbacks from holding the editor open.
- Move host construction and tier checks out of first-frame/composition work; load provider names after controls become available.
- Add retryable loading failure, serialize picker/mutation work and protect committed IDs during configuration cleanup.
- Collect theme accent/style asynchronously and fix negative empty-group/skipped-divider layout heights.
- Add six executable concurrency/layout regressions; the full standalone suite now has 34 tests. The exact reported ANR stack is not available from the screenshot.

## Theme-consistent Hub and Hijri dates — 16 October 2026

- Replace Hub chip navigation with a native preference overview and focused feature screens; use the same preference theme in the widget editor and Shelf media controls.
- Add Hijri/Gregorian/dual dates, live Home-view preview, calendar calculation/day correction, language, numerals, format, weekday/year/era and dual-date layout/order controls.
- Remove blocking date preference initialization and change the date ticker from every second to every minute.
- Preserve editor input on failed saves/rotation, prevent repeated Save, confirm removals, edit Shelf notes and release evicted document grants.
- Search app pickers and preview rule matches; reorder scenes and widget members without changing the selected widget identity.
- Restore the original appearance when automatic scene conditions expire or automation is disabled; mirror stack navigation in RTL.
- Expand the executable JVM suite to 28 tests including calendars, DST, scene expiry, stack identity and RTL gestures.

## Home experiences — 16 October 2026

- Deliver app search results independently of slower providers; cancel obsolete contact/file/HTTP work.
- Share Unicode/transliterated/alias matching between app and on-device modes; add result filters and action search.
- Bind gesture/search lifecycle work to Home; avoid blocking search setup reads and clear stale keyboard highlights.
- Add on-demand Omni Hub with Shelf notes/document pins/media controls and preferred Workspace selection.
- Add saved appearance scenes, overnight/power/headphone rules and undo, without a background service.
- Add dynamic drawer folder rules and replace repeated per-member app enumeration with one inventory.
- Host real Home widget stacks with one inflated member, LOW-tier limits and Android binding/configuration.
- Extend signed OmniLink capabilities, accessible-profile identity and actual enterprise-owner detection.
- Add executable JVM rule/search/policy tests and broader device benchmark journeys.
- No Root or Shizuku implementation added. Actual device behavior and performance remain separately measurable.

## Unreleased — Omni assistant integration (1 October 2026)

- Fixed recycled-row web click handlers and stale icon updates; route Ask Omni row/icon/keyboard
  actions to Workspace explicitly. Bypass blocking QSB preference reads for Omni, add visible
  cancellable discovery with a three-second UI deadline, and use the Workspace robot vector.
- Added Omni as a selectable search-bar provider and localized Ask Omni action for app-only and
  on-device local search, with fallback, always-show and disable settings.
- Added a user-visible Workspace draft handoff with missing-app handling and variant selection.
- Added six optional OmniLink launcher capabilities with local opt-in, same-signer checks,
  receiving-side flavor ceilings, bounded app paging, hidden-app exclusion and mutation metadata.
- Kept remote control consent local-only and guarded foreground app/drawer actions.
- Added generation checks and search-scope cleanup; moved app-only search work off Main.
- Documented setup, payloads, privacy, ownership and validation across the three repositories.
- Handled unavailable LauncherApps services with an explicit capability failure.
- Restricted production roots to the app/com/dev namespaces, keeping conventional unit tests out
  of actual variant compiler inputs. Added a three-variant CI source-isolation check and disabled
  matrix fail-fast to retain complete diagnostics after an independent build failure.
- Direct shared-policy suite: 13 passing Kotlin/JUnit tests; Kotlin lint and XML checks passed.
  Full Android compilation/device performance validation remain pending.

This is a proposed branch change, not a published APK or a claim that the base branch already
contains the integration. It uses the existing OmniLinkSDK v3.0.0 contract without changing SDK ABI.
