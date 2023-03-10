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
-dontwarn androidx.dynamicanimation.animation.AnimationHandler$FrameCallbackScheduler*
-dontwarn com.android.internal.**
-dontwarn com.google.android.collect.Sets*
-dontwarn com.google.protobuf.nano.**
-dontwarn dagger.**
-dontwarn javax.inject.**
# We can remove these rules after updating to OkHttp 4.10.1
# https://github.com/square/okhttp/blob/339732e3a1b78be5d792860109047f68a011b5eb/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro#L11-L14
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

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
