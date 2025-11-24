# System One

[![Build Android APK](https://github.com/chasejarrett988/iota-launcher/actions/workflows/build.yml/badge.svg)](https://github.com/chasejarrett988/iota-launcher/actions/workflows/build.yml)

System One is a free, open-source home app for Android. Based on Launcher3—Android's default home app—it provides Pixel Launcher features with rich customization options.

Based on Launcher3 from Android 15.

## Features

-   **Material You Theming:** Adapts to your wallpaper and system theme.
-   **At a Glance Widget:** Displays information *at a glance* with support for [Smartspacer](https://github.com/KieronQuinn/Smartspacer).
-   **QuickSwitch Support:** Integrates with Android Recents on Android 10 and newer. (requires root)
-   **Global Search:** Allows quick access to apps, contacts, and web results from the home screen.
-   **Customization Options:** Provides options to tweak icons, fonts, and colors to your liking.

## Building

```bash
./gradlew assembleLawnWithQuickstepNightlyDebug
```

The APK will be output to `build/outputs/apk/lawnWithQuickstepNightly/debug/`.

## Contributing

Please visit the [Contributing Guidelines](CONTRIBUTING.md) for information and tips on contributing.

## License

Licensed under the Apache License, Version 2.0.
