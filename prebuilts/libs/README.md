# Lawnchair prebuilt JARs

Launcher3 has some dependencies on internal AOSP modules. To build Lawnchair, you have to build AOSP and obtain these JARs.

| File | Path |
|------|------|
| framework-12l.jar | ./soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar |
| framework-statsd.jar | ./soong/.intermediates/packages/modules/StatsD/framework/framework-statsd/android_common_apex30/javac/framework-statsd.jar |
| SystemUI-statsd.jar | ./soong/.intermediates/frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar |
| WindowManager-Shell.jar | ./soong/.intermediates/frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar
