-allowaccessmodification
-dontoptimize
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable


-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# Don't touch the restrictionbypass code
-keep class org.chickenhook.restrictionbypass.** { *; }

# Silence warnings from Compose tooling
-dontwarn sun.misc.Unsafe

# Silence warnings about classes that are available at runtime
# These rules are generated automatically by the Android Gradle plugin.
-dontwarn android.animation.AnimationHandler*
-dontwarn android.content.om.**
-dontwarn android.content.pm.**
-dontwarn android.content.res.**
-dontwarn android.hardware.devicestate.DeviceStateManager*
-dontwarn android.provider.**
-dontwarn android.service.wallpaper.IWallpaperEngine*
-dontwarn android.util.**
-dontwarn android.widget.RemoteViews*
-dontwarn androidx.compose.runtime.PrimitiveSnapshotStateKt
-dontwarn androidx.dynamicanimation.animation.AnimationHandler$FrameCallbackScheduler*
-dontwarn com.android.internal.**
-dontwarn com.google.android.collect.Sets*
-dontwarn com.google.protobuf.nano.**
-dontwarn dagger.**
-dontwarn javax.inject.**
-dontwarn android.net.**
-dontwarn android.os.**
-dontwarn android.bluetooth.**
-dontwarn android.appwidget.AppWidgetHost$AppWidgetHostListener
-dontwarn android.compat.Compatibility
-dontwarn android.compat.annotation.UnsupportedAppUsage
-dontwarn com.android.i18n.phonenumbers.**
-dontwarn com.android.wm.shell.animation.**
-dontwarn com.android.wm.shell.bubbles.**
-dontwarn com.android.wm.shell.common.**
-dontwarn com.android.wm.shell.desktopmode.**
-dontwarn com.android.wm.shell.pip.tv.**
-dontwarn dalvik.system.**
-dontwarn java.nio.NioUtils
-dontwarn libcore.content.type.**
-dontwarn libcore.io.IoUtils
-dontwarn libcore.net.NetworkSecurityPolicy
-dontwarn libcore.util.**
-dontwarn lineageos.providers.LineageSettings$System
-dontwarn org.apache.harmony.dalvik.ddmc.**
-dontwarn org.ccil.cowan.tagsoup.**
-dontwarn com.skydoves.balloon.**

# Preserve Protobuf generated code
-keep class com.android.launcher3.tracing.nano.LauncherTraceFileProto$* { *; }
-keep class com.android.launcher3.logger.nano.LauncherAtom$* { *; }
-keep class com.android.launcher3.tracing.nano.LauncherTraceEntryProto$* { *; }
-keep class com.android.launcher3.tracing.nano.TouchInteractionServiceProto$* { *; }
-keep class com.android.launcher3.userevent.nano.LauncherLogProto$* { *; }
-keep class com.android.launcher3.tracing.nano.LauncherTraceProto$* { *; }
-keep class com.android.launcher3.userevent.nano.LauncherLogExtensions$* { *; }
-keep class com.android.launcher3.tracing.LauncherTraceFileProto$* { *; }
-keep class com.android.launcher3.logger.LauncherAtom$* { *; }
-keep class com.android.launcher3.tracing.LauncherTraceEntryProto$* { *; }
-keep class com.android.launcher3.tracing.TouchInteractionServiceProto$* { *; }
-keep class com.android.launcher3.userevent.LauncherLogProto$* { *; }
-keep class com.android.launcher3.tracing.LauncherTraceProto$* { *; }
-keep class com.android.launcher3.userevent.LauncherLogExtensions$* { *; }
-keep class app.lawnchair.LawnchairProto$* { *; }
-keep class app.lawnchair.LawnchairApp { *; }
-keep class com.android.launcher3.Utilities { *; }
-keep class app.lawnchair.LawnchairLauncher { *; }
-keep class com.google.protobuf.Timestamp { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }

-keep class app.lawnchair.compatlib.** {
  *;
}

-keep class com.android.** {
  *;
}

# Keep Smartspacer's client SDK
-keep class com.kieronquinn.app.smartspacer.sdk.**  { *; }
