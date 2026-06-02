# Lawnchair Quickstep compat module

The `compatLib` module provides a compatibility layer that lets Lawnchair integrate with Quickstep
(a system component that provides the Recents screen) across different Android versions, even when
the Quickstep implementation does not match the system version.

This enables Recents integration when using tools that replace the system Recents provider,
such as QuickSwitch or the older Lawnstep app.

Each submodule is named after the Android version codename, using the first letter
for Android 15 and below and the full codename starting from Android 16 onward.

| Module            | Android version |
|-------------------|-----------------|
| compatLibVQ       | 10              |
| compatLibVR       | 11              |
| compatLibVS       | 12              |
| compatLibVT       | 13              |
| compatLibVU       | 14              |
| compatLibVV       | 15              |
| compatLibVBaklava | 16              |

This list does not guarantee Recents compatibility across all Android versions,
as the implementation may be incomplete or not fully functional.

