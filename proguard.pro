-keep,allowshrinking,allowoptimization class com.android.launcher3.** {
  *;
}

-keep class com.android.launcher3.graphics.ShadowDrawable {
  public <init>(...);
}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**
-keep class ** extends android.app.Fragment {
    public <init>(...);
}

## Prevent obfuscating various overridable objects
-keep class ** implements com.android.launcher3.util.ResourceBasedOverride {
    public <init>(...);
}

-keep interface com.android.launcher3.userevent.nano.LauncherLogProto.** {
  *;
}
-keep interface com.android.launcher3.model.nano.LauncherDumpProto.** {
  *;
}

# Discovery bounce animation
-keep class com.android.launcher3.allapps.DiscoveryBounce$VerticalProgressWrapper {
  public void setProgress(float);
  public float getProgress();
}

# BUG(70852369): Suppress additional warnings after changing from Proguard to R8
-dontwarn android.app.**
-dontwarn android.graphics.**
-dontwarn android.os.**
-dontwarn android.view.**
-dontwarn android.window.**

# Ignore warnings for hidden utility classes referenced from the shared lib
-dontwarn com.android.internal.util.**

################ Do not optimize recents lib #############
-keep class com.android.systemui.** {
  *;
}

-keep class com.android.quickstep.** {
  *;
}

# Don't touch the restrictionbypass code
-keep class org.chickenhook.restrictionbypass.** { *; }

# Silence warnings from Compose tooling
-dontwarn sun.misc.Unsafe

# Silence warnings about classes that are available at runtime
-dontwarn android.provider.DeviceConfig
-dontwarn com.android.internal.colorextraction.ColorExtractor$GradientColors
-dontwarn com.android.internal.logging.MetricsLogger
-dontwarn com.android.internal.os.SomeArgs
-dontwarn android.content.pm.ParceledListSlice
-dontwarn com.android.internal.policy.ScreenDecorationsUtils
-dontwarn android.util.StatsEvent
-dontwarn android.service.wallpaper.IWallpaperEngine
-dontwarn android.content.pm.UserInfo
-dontwarn com.android.internal.app.IVoiceInteractionManagerService$Stub
-dontwarn com.android.internal.app.IVoiceInteractionManagerService
-dontwarn com.android.internal.annotations.VisibleForTesting
-dontwarn android.provider.DeviceConfig$OnPropertiesChangedListener
-dontwarn android.util.StatsEvent$Builder
-dontwarn com.android.internal.colorextraction.types.Tonal
-dontwarn android.content.pm.LauncherApps$AppUsageLimit
-dontwarn android.provider.SearchIndexablesContract
-dontwarn android.provider.SearchIndexablesProvider
-dontwarn android.content.pm.IPackageManager

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
-keep class com.google.protobuf.Timestamp { *; }
-keepattributes InnerClasses

-keep class app.lawnchair.compatlib.** {
  *;
}

-keep class com.android.** {
  *;
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# https://github.com/square/okhttp/blob/339732e3a1b78be5d792860109047f68a011b5eb/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro#L11-L14
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
