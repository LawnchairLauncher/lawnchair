# Omni Launcher — attribution and modification notice

## Original project lineage

Omni Launcher is a fork of [Lawnchair 15](https://github.com/LawnchairLauncher/lawnchair), based on
Android 15 AOSP Launcher3. The fork base recorded in this repository is `v15.0.0-beta3.0`.

The existing LICENSE.txt contains these original notices, which remain unchanged:

- Copyright (c) 2005–2008, The Android Open Source Project.
- Copyright (c) 2024, Lawnchair.

Those notices do not replace other dates/authors in individual files. All original source headers,
Lawnchair contributor/team credits, upstream documentation attribution, and license texts are
preserved. Android Launcher3/SystemUI/WM Shell code and the pinned upstream submodule retain
original namespaces, provenance and terms. The historical GITHUB_CHANGELOG.md and
TELEGRAM_CHANGELOG.txt remain upstream release records.

## Omni fork developer and maintainer

**Abdelrahman Hussein — عبدالرحمن حسين / Obieda**
[GitHub: obieda-hussien](https://github.com/obieda-hussien)

Role: developer and maintainer of the Omni fork; modifications, updates, performance work,
Arabic/RTL integration, and interoperability with OmniDev Workspace and OmniLinkSDK.
This role does not claim authorship or ownership of the original Lawnchair/AOSP code.

Copyright (c) 2026 Abdelrahman Hussein applies to new Omni-authored material, including the
launcher integration introduced by feature/omni-launcher-integration:

- `lawnchair/src/app/lawnchair/omni/` — public assistant handoff, integration preferences,
  search policy, receiver-owned flavor policy and launcher extension service.
- `lawnchair/src/app/lawnchair/qsb/providers/Omni.kt` — Omni search-bar provider.
- `lawnchair/src/test/kotlin/app/lawnchair/omni/` — policy regression tests.
- `lawnchair/res/*/omni_strings.xml` and `ic_omni_assistant.xml` — new localized resources/icon.
- `docs/OMNI_INTEGRATION.md`, `OMNI_CHANGELOG.md`, and fork-specific documentation additions.

Search/QSB/preference/manifest/build changes are modifications to the existing fork, not replacement
ownership claims. The new Launcher integration Kotlin files preserve the repository's Apache 2.0
source licensing convention. LICENSE.txt is not rewritten or replaced by this notice.

## Dependencies and separate projects

OmniLinkSDK is an independently maintained project with its own Omni Reference Source License 1.0
and owner notices. Importing its artifact does not convert it to Apache 2.0 or change its ownership.
See the SDK's [LICENSE](https://github.com/obieda-hussien/OmniLinkSDK/blob/main/LICENSE) and
[NOTICE.md](https://github.com/obieda-hussien/OmniLinkSDK/blob/main/NOTICE.md).

Other dependencies, artwork, fonts, code and vendored modules retain their respective authors and
licenses. This notice is a project-lineage and modification record, not a replacement dependency
license inventory. Existing per-file notices and the build's dependency license report take precedence
for their respective material. No upstream sponsorship or endorsement is implied.
