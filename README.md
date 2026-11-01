# NIX OS

**Version:** `Nix_OS_v1.3_Beta_Hotfix`

NIX OS is an Android home-screen launcher project. It customizes the Android home experience; it is **not** a standalone Android operating system, custom ROM, or firmware replacement.

This project is based on the Android Launcher3 codebase and the Lawnchair 15 open-source project. Upstream license notices, acknowledgements, and contributor credits are retained.

## Current launcher features

- Home screen, dock, app drawer, folders, and widgets
- Android wallpaper-based Material You colors and system theme support
- Icon, grid, font, and layout customization
- Gesture actions and supported Android Recents integration
- Search and supported At a Glance integrations

Available features can vary by Android version, device manufacturer, and build variant.

## Build

The repository includes a GitHub Actions workflow at [`.github/workflows/build_release_apk.yml`](.github/workflows/build_release_apk.yml). Pushes to the `jarvis-os-lawnchair-15` branch and manual workflow runs build the release variants and upload an artifact named **Release APK**.

The configured release task is:

```bash
./gradlew assembleLawnWithQuickstepGithubRelease bundleLawnWithQuickstepPlayRelease
```

Release builds require a stable signing key configured as GitHub Actions repository secrets: `KEYSTORE` (base64-encoded JKS), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. The workflow stops instead of silently producing a temporary-key-signed release APK. Use the same keystore and alias that signed the previously installed NIX OS APK to support in-place updates.

## Package identity

The existing Android application IDs and namespace are intentionally retained for build and installation compatibility. The visible application name is **NIX OS**.

## AI status

An AI assistant is not included or connected in this launcher build. The launcher does not claim AI chat or advanced AI phone-control features.

## Open-source notices

NIX OS is a modified derivative. See [`LICENSE.txt`](LICENSE.txt), the in-app acknowledgements, and source-file notices for applicable upstream and third-party licensing information. Lawnchair and Android/AOSP attribution is preserved.
