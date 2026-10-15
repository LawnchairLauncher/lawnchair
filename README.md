# Omni Launcher

**A Lawnchair 15-based Android launcher with Omni assistant search and optional Omni Dev control.**

Omni Launcher builds on [Lawnchair](https://github.com/LawnchairLauncher/lawnchair),
itself based on Android 15 [AOSP Launcher3](https://android.googlesource.com/platform/packages/apps/Launcher3/).
This fork's base is Lawnchair `v15.0.0-beta3.0`; its target branch is `15-dev`.

**Omni fork developer and maintainer:** [Abdelrahman Hussein — عبدالرحمن حسين / Obieda](https://github.com/obieda-hussien).
His role covers Omni-specific development, modifications, maintenance, performance work,
and integration with [OmniDev Workspace](https://github.com/obieda-hussien/OmniDev-Workspace)
and [OmniLinkSDK](https://github.com/obieda-hussien/OmniLinkSDK).
The original Lawnchair, AOSP, and third-party authors retain credit and rights to their work.

> This README describes the `feature/omni-launcher-integration` proposal based on `15-dev`,
> updated 1 October 2026. Integration features become part of the base branch only after merge.
> Policy tests passed; a full APK build and physical-device performance checks are still pending.

## Contents

- [Features and origins](#features-and-origins)
- [Ask Omni from search](#ask-omni-from-search)
- [Workspace control through OmniLink](#workspace-control-through-omnilink)
- [Repository metrics](#repository-metrics)
- [Build and local signing](#build-and-local-signing)
- [Performance and Android integration](#performance-and-android-integration)
- [Documentation and source map](#documentation-and-source-map)
- [Copyright, attribution, and licenses](#copyright-attribution-and-licenses)

## Features and origins

| Feature | Origin / scope | Requirements |
| --- | --- | --- |
| Material You, icon/font/color customization | Lawnchair functionality retained by this fork | Depends on settings and Android version |
| At a Glance and Smartspacer | Lawnchair integrations retained | Optional Smartspacer installation/configuration |
| App/on-device search | Existing Lawnchair search engine | Contacts/files require their own Android permissions |
| QuickSwitch / Quickstep Recents | Upstream integration retained | Compatible Android version and privileged/root setup; installing a launcher alone does not replace SystemUI Recents |
| Home-return recovery and LOW-tier performance work | Omni fork modifications | Device testing required; no measured FPS guarantee |
| Omni search-bar provider | Omni integration in this proposal | Updated Workspace installed |
| Ask Omni search action | Omni integration in this proposal | App-only or on-device local search; configurable fallback or always-show mode |
| Workspace launcher control | OmniLink application capabilities in this proposal | Local opt-in, same signing certificate, supported Workspace flavor and approval for changes |

Upstream namespaces, source headers, historical release notes, and contributor credits remain in place.
See [NOTICE.md](NOTICE.md) for the original project lineage and the scope of the Omni modifications.

## Ask Omni from search

1. Install this Launcher build and an updated OmniDev Workspace build.
2. Open Launcher settings → Search bar → Search provider and select **Omni**.
3. Tapping the bar opens Workspace chat. In app-only/on-device local search, a nonblank query
   without a local match can display **Ask Omni / اسأل Omni**.
4. Tap the action to hand off your question as a draft; review and press Send in Workspace.
   An existing draft and active run are preserved. Typing alone does not send a question to Omni.
5. Use the Omni integration settings to disable suggestions or show them even alongside local matches.

The visible chooser supports multiple installed Workspace flavors. Missing or outdated Workspace
shows an update/install message. Public search handoff does not require matching APK signatures.
The ASI backend is separate; Omni is not a Google Discover feed or a Smartspace provider.

## Workspace control through OmniLink

Workspace discovers the launcher's optional extension and uses the existing `omni_link` tool.
Enable **Allow Omni Dev to control the launcher** in Launcher settings after installing APKs
signed with the same certificate. The switch is off by default and cannot be enabled remotely.
The SDK verifies signing identity, and both applications enforce package/flavor capability ceilings.

| Capability | Behavior |
| --- | --- |
| `launcher.health` | Report connection readiness and local control consent |
| `launcher.get_settings` | Read the configured search provider and Omni suggestion toggles |
| `launcher.list_apps` | Page through current-profile visible launchable components; hidden apps excluded |
| `launcher.set_preference` | Change one validated search-provider/suggestion setting after approval; supports dry run |
| `launcher.open_app` | Open an exact visible component while Home is resumed; approval and dry run supported |
| `launcher.open_drawer` | Open the drawer while Home is resumed; approval and dry run supported |

ADMIN/PRO/OEM can use all six known capabilities. NORM can read and open; LITE can read health only.
All still follow local consent and mutation approval. A foreground refusal is returned to Workspace
instead of attempting to force Android to launch an activity in the background.

The published SDK dependency is pinned to **v3.0.0**; this integration adds no AIDL transaction or
SDK version bump. [Payloads, setup, errors, privacy and test matrix](docs/OMNI_INTEGRATION.md).

## Repository metrics

<!-- OMNI_METRICS_START -->
| Metric | Count |
| --- | ---: |
| Tracked files | **4,975** |
| Production source files | **2,166** |
| Production source lines | **466,832** |
| Test source files | **655** |
| Test source lines | **112,111** |
| Kotlin files | **1,162** |
| Kotlin lines | **161,129** |
| Markdown documents | **38** |
<!-- OMNI_METRICS_END -->

These are physical line counts from `git ls-files`, including upstream Lawnchair/AOSP/vendored
sources tracked by this repository. They are **not** a claim that the Omni maintainer wrote that code.
Comments and blank lines count; generated output, untracked files and submodule contents do not.
Reproduce from the repository root with `python3 scripts/repo_metrics.py`.

## Build and local signing

Clone the fork with its upstream submodule:

```sh
git clone --recurse-submodules https://github.com/obieda-hussien/Omni-launcher.git
cd Omni-launcher
git switch 15-dev
```

To review the proposed integration before merge, use `git switch feature/omni-launcher-integration`.
Use Android Studio or a configured Android SDK host. The Java source/bytecode target is 17;
CI uses JDK 21. Install a full JDK with `javac`, not a Java runtime alone. Versions are recorded in
`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, and `build.gradle`.

```sh
./gradlew assembleLawnWithQuickstepGithubDebug
./gradlew spotlessCheck
./gradlew assembleLawnWithQuickstepGithubRelease
```

The APKs are under `build/outputs/apk/`. Debug builds use Android's debug signing key; the GitHub
release variant is **unsigned**, with `.unsigned.apk` in its name. Sign release APKs locally with a
stable key before installation. An unsigned APK is not an installable update. Preserve that key for
updates; changing a package's certificate cannot update an existing installation.

For OmniLink control, Launcher and Workspace must use the same certificate. Independent debug
builds may use different debug keys. Search draft handoff still works across different certificates.
Back up launcher data before changing package IDs or reinstalling with a different certificate.

## Performance and Android integration

The primary device target is an Infinix HOT 10S, Android 11, Helio G85 and approximately 4 GB RAM.
Keep all device-sensitive code adaptable; see [AGENTS.md](AGENTS.md) for budgets and review rules.
The Omni integration uses an existing search row and a small vector icon. It adds no Home-startup
provider discovery, permanent agent connection, blur surface, or polling timer. Package queries
and app inventory run on IO; service concurrency is bounded. Search scopes close on destruction,
and generation checks prevent old requests from replacing newer search results.

The policy suite passed **13 direct Kotlin/JUnit tests** covering search decisions, public request
validation, and both sides' package/flavor ceilings. Changed Launcher Kotlin files passed ktlint 1.8.0;
manifests/resources parsed successfully. These checks do not constitute an Android APK build.
The local Gradle build reached project configuration and then stopped because the installed Java 17
toolchain lacked `JAVA_COMPILER`. The existing Launcher JVM test wiring can report `NO-SOURCE`;
the direct policy tests were executed separately, without claiming Gradle ran them.

On OEM Android 11, SystemUI owns navigation/Recents unless Quickstep is installed as the privileged
Recents component. A third-party Home app alone cannot replace that component. Use the
[physical-device diagnostics](docs/home-return-recents-diagnostics.md) before claiming a fix.
Backup restoration preserves unrelated databases; wallpaper-only restore does not reset Home layout.

## Documentation and source map

| Resource | Purpose |
| --- | --- |
| [Omni integration](docs/OMNI_INTEGRATION.md) | Setup, SDK contract, capabilities, approvals, errors and validation |
| [Omni changelog](OMNI_CHANGELOG.md) | Fork-specific work, separate from historical upstream releases |
| [Attribution notice](NOTICE.md) | Lawnchair/AOSP/third-party credit and Omni maintainer's contribution scope |
| [Contributing](CONTRIBUTING.md) | Fork workflow plus preserved upstream contribution guidance |
| [Home/Recents diagnostics](docs/home-return-recents-diagnostics.md) | Hardware reproduction steps and privilege limits |
| [Architecture audit](docs/architecture-audit.md) | Existing codebase audit; historical findings need comparison with current code |
| [Recovery notes](docs/omni-recovery.md) | Home-return recovery and testing |
| [AGENTS.md](AGENTS.md) | Performance, LOW-tier, RTL and review requirements |
| `lawnchair/` | Lawnchair and Omni feature/UI sources; preferred location for fork modifications |
| `src/`, `quickstep/`, `systemUI/`, `wmshell/` | Retained Android/Launcher3/SystemUI code and integration |
| `platform_frameworks_libs_systemui/` | Pinned upstream submodule; its own history and notices remain |

## Copyright, attribution, and licenses

The existing [LICENSE.txt](LICENSE.txt) retains its original AOSP and Lawnchair notices and Apache 2.0
text. Original per-file copyright headers, credits, and third-party notices remain authoritative.
New Omni Launcher integration source files carry an additional **Copyright 2026 Abdelrahman Hussein
(عبدالرحمن حسين)** notice and the Apache 2.0 header. That attribution covers the new material;
it does not replace upstream ownership.

OmniLinkSDK retains its own [license](https://github.com/obieda-hussien/OmniLinkSDK/blob/main/LICENSE)
and [notice](https://github.com/obieda-hussien/OmniLinkSDK/blob/main/NOTICE.md). Other dependencies
retain their respective terms. The launcher's Apache notice does not relicense these dependencies.
See [NOTICE.md](NOTICE.md) and the original headers for attribution details.
