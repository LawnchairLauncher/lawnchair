# Lawnchair Prebuilt JARs

Launcher3 has some dependencies on internal AOSP modules. 
To build Lawnchair, you have to build AOSP and obtain these JARs.

## Usage

Lawnchair rely on these JAR:

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

Any other JARs not listed here are kept for historical or reference purposes.
