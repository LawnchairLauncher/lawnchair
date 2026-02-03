# Lawnchair Quickstep Compat Library

The `compatLib` library helps integrate Lawnchair with Recents 
(also known as QuickSwitch, Quickstep, or sometimes, Lawnstep) 
while ensuring backward-compatibility with older Android versions.

Each subdirectory of the `compatLib`, denoted by a letter (e.g., `compatLibVQ` for Android 10), 
refers to the compatibility code for that specific Android version.

Starting with Android 16 and above, the `compatLib` will denoted by the codename of the Android version
(e.g., `compatLibVBaklava` for Android 16).

| Library           | Android version |
|-------------------|-----------------|
| compatLibVQ       | 10              |
| compatLibVR       | 11              |
| compatLibVS       | 12              |
| compatLibVT       | 13              |
| compatLibVU       | 14              |
| compatLibVV       | 15              |
| compatLibVBaklava | 16              |

Keep in mind that this list does not guarantee Recents compatibility with your Android versions, 
as the implementation may still be in progress or not fully functional.
