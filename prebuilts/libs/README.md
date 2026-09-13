# Lawnchair prebuilt libraries

Launcher3 has some dependencies on internal AOSP modules. 
To build Lawnchair, you have to build AOSP and obtain these JARs.

## Usage

Lawnchair relies on these JARs:

| File                       | Command                 | Android Tag                 | Target Configuration                               |
|----------------------------|-------------------------|-----------------------------|----------------------------------------------------|
| framework-16.jar           | `m framework`           | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| SystemUI-statsd-16.jar     | `m SystemUI-statsd`     | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| WindowManager-Shell-16.jar | `m WindowManager-Shell` | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| SystemUI-core-16.jar       | `m SystemUI-core`       | android-16.0.0_r3           | `aosp_cf_x86_64_only_phone-aosp_current-userdebug` |
| framework-15.jar           | `m framework`           | android-15.0.0_r3           |                                                    |
| framework-14.jar           | `m framework`           | android14-release           |                                                    |
| framework-13.jar           | `m framework`           |                             |                                                    |
| framework-12l.jar          | `m framework`           | android12L-platform-release |                                                    |
| framework-12.jar           | `m framework`           | android12-platform-release  |                                                    |
| framework-11.jar           | `m framework`           | android-11.0.0_r18          |                                                    |
| framework-10.jar           | `m framework`           |                             |                                                    |

Location of the generated JARs:

| Module              | Path                                                                                                                             |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Framework           | ./soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar                                   |
| SystemUI-StatsD     | ./soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar         |
| WindowManager-Shell | ./soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar |
| SystemUI-Core       | ./soong/.intermediates/frameworks/base/packages/SystemUI/SystemUI-core/android_common/javac/SystemUI-core.jar                    |

## Android 16 WindowManager Shell ABI adaptation

`WindowManager-Shell-16.jar` is based on the `android-16.0.0_r3` artifact
listed above. The checked-in JAR adapts `IRecentTasks` to the pre-WCT Binder
ABI exposed by the Bliss/e/OS Android 16 SystemUI build used for integration
testing. The reviewable interface change lives in
`wmshell/src/com/android/wm/shell/recents/IRecentTasks.aidl`.

The adaptation preserves all 1,388 archive entries and replaces only these
AIDL-generated classes:

- `IRecentTasks.class`
- `IRecentTasks$Default.class`
- `IRecentTasks$Stub.class`
- `IRecentTasks$Stub$Proxy.class`

Baseline SHA-256:
`e5f26c556ca0670f32339237a6dd30268054cad0fa823d233e36bdb1e29c1753`.
Adapted SHA-256:
`9a8261a98a6231d1546bd70e36d457d73d82c0063ebc780820c5a0a6b4ab9b04`.

To reproduce the prebuilt from source, apply the tracked `IRecentTasks.aidl`
change to the `android-16.0.0_r3` tree and rebuild `WindowManager-Shell` with
the command and target configuration listed above.

Any other JARs not listed here are kept for historical or reference purposes.
