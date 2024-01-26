# The rules from AOSP are located in proguard.flags file, we can just maintain Lawnchair related rules here.

# Optimization options.
-allowaccessmodification
-dontoptimize
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable


# This is generated automatically by the Android Gradle plugin.
-dontwarn android.appwidget.AppWidgetHost$AppWidgetHostListener
-dontwarn android.util.StatsEvent$Builder
-dontwarn android.util.StatsEvent
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
-dontwarn com.android.org.conscrypt.TrustManagerImpl
-dontwarn com.android.wm.shell.**
-dontwarn com.skydoves.balloon.**
-dontwarn dalvik.system.CloseGuard
-dontwarn lineageos.providers.LineageSettings$System
-dontwarn androidx.compose.runtime.PrimitiveSnapshotStateKt


# Common rules.
-keep class com.android.** { *; }
-keep class android.window.** { *; }
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

# Lawnchair specific rules.
-keep class app.lawnchair.LawnchairProto$* { *; }
-keep class app.lawnchair.LawnchairApp { *; }
-keep class app.lawnchair.LawnchairLauncher { *; }
-keep class app.lawnchair.compatlib.** { *; }

-keep class com.google.protobuf.Timestamp { *; }

# TODO: Remove this after the change in https://github.com/ChickenHook/RestrictionBypass/pull/9 has been released.
-keep class org.chickenhook.restrictionbypass.** { *; }

# TODO: These rules could be removed after Retrofit 2.10.0 released.
# https://github.com/square/retrofit/blob/ef8d867ffb34b419355a323e11ba89db1904f8c2/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro#L38-L45
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
