# Omni fork changelog

Maintained by **Abdelrahman Hussein (عبدالرحمن حسين / Obieda)** for Omni-specific development,
updates, modifications and ecosystem integration. Original Lawnchair/AOSP authors retain their
credits and rights; see [NOTICE.md](NOTICE.md). Upstream GITHUB_CHANGELOG.md and
TELEGRAM_CHANGELOG.txt remain historical upstream release notes.

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
