-allowaccessmodification
-dontoptimize
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable

-keep class com.android.**

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
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
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
-dontwarn com.android.wm.shell.**
-dontwarn dalvik.system.**
-dontwarn java.nio.NioUtils
-dontwarn libcore.**
-dontwarn lineageos.providers.LineageSettings$System
-dontwarn org.apache.harmony.dalvik.ddmc.**
-dontwarn org.ccil.cowan.tagsoup.**
-dontwarn com.skydoves.balloon.**

-keep class app.lawnchair.LawnchairProto$* { *; }
-keep class app.lawnchair.LawnchairApp { *; }
-keep class app.lawnchair.LawnchairLauncher { *; }
-keep class app.lawnchair.compatlib.**

-keep class com.google.protobuf.Timestamp { *; }

# TODO: Remove this after the change in https://github.com/ChickenHook/RestrictionBypass/pull/9 has been released.
-keep class org.chickenhook.restrictionbypass.**
# TODO: Remove this after the change in https://github.com/KieronQuinn/Smartspacer/pull/58 has been released.
-keep class com.kieronquinn.app.smartspacer.sdk.**
