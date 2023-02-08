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

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn android.animation.AnimationHandler$AnimationFrameCallbackProvider
-dontwarn android.animation.AnimationHandler
-dontwarn android.content.om.IOverlayManager$Stub
-dontwarn android.content.om.IOverlayManager
-dontwarn android.content.om.OverlayInfo
-dontwarn android.content.res.CompatibilityInfo
-dontwarn android.hardware.devicestate.DeviceStateManager$DeviceStateCallback
-dontwarn android.hardware.devicestate.DeviceStateManager$FoldStateListener
-dontwarn android.hardware.devicestate.DeviceStateManager
-dontwarn android.provider.DeviceConfig$Properties
-dontwarn android.util.MathUtils
-dontwarn android.util.MergedConfiguration
-dontwarn android.util.PathParser
-dontwarn android.util.Pools$SynchronizedPool
-dontwarn android.util.RotationUtils
-dontwarn android.util.Slog
-dontwarn android.util.SparseSetArray
-dontwarn android.widget.RemoteViews$ColorResources
-dontwarn android.widget.RemoteViews$InteractionHandler
-dontwarn androidx.dynamicanimation.animation.AnimationHandler$FrameCallbackScheduler
-dontwarn com.android.internal.R$styleable
-dontwarn com.android.internal.accessibility.AccessibilityShortcutController
-dontwarn com.android.internal.annotations.GuardedBy
-dontwarn com.android.internal.annotations.VisibleForTesting$Visibility
-dontwarn com.android.internal.graphics.ColorUtils
-dontwarn com.android.internal.graphics.SfVsyncFrameCallbackProvider
-dontwarn com.android.internal.graphics.palette.Palette$Builder
-dontwarn com.android.internal.graphics.palette.Palette$Swatch
-dontwarn com.android.internal.graphics.palette.Palette
-dontwarn com.android.internal.graphics.palette.Quantizer
-dontwarn com.android.internal.graphics.palette.VariationalKMeansQuantizer
-dontwarn com.android.internal.jank.InteractionJankMonitor$Configuration$Builder
-dontwarn com.android.internal.jank.InteractionJankMonitor
-dontwarn com.android.internal.logging.InstanceId
-dontwarn com.android.internal.logging.InstanceIdSequence
-dontwarn com.android.internal.logging.UiEventLogger$UiEventEnum
-dontwarn com.android.internal.logging.UiEventLogger
-dontwarn com.android.internal.logging.UiEventLoggerImpl
-dontwarn com.android.internal.os.IResultReceiver
-dontwarn com.android.internal.policy.AttributeCache
-dontwarn com.android.internal.policy.DecorView$ColorViewAttributes
-dontwarn com.android.internal.policy.DecorView
-dontwarn com.android.internal.policy.DividerSnapAlgorithm$SnapTarget
-dontwarn com.android.internal.policy.DividerSnapAlgorithm
-dontwarn com.android.internal.policy.DockedDividerUtils
-dontwarn com.android.internal.policy.SystemBarUtils
-dontwarn com.android.internal.policy.TaskResizingAlgorithm
-dontwarn com.android.internal.policy.TransitionAnimation
-dontwarn com.android.internal.protolog.BaseProtoLogImpl$LogLevel
-dontwarn com.android.internal.protolog.BaseProtoLogImpl
-dontwarn com.android.internal.protolog.ProtoLogViewerConfigReader
-dontwarn com.android.internal.protolog.common.IProtoLogGroup
-dontwarn com.android.internal.statusbar.IStatusBarService$Stub
-dontwarn com.android.internal.statusbar.IStatusBarService
-dontwarn com.android.internal.view.BaseIWindow
-dontwarn com.android.internal.view.IInputMethodManager$Stub
-dontwarn com.android.internal.view.IInputMethodManager
-dontwarn com.android.internal.view.RotationPolicy
-dontwarn com.google.android.collect.Sets
-dontwarn com.google.protobuf.nano.CodedInputByteBufferNano
-dontwarn com.google.protobuf.nano.CodedOutputByteBufferNano
-dontwarn com.google.protobuf.nano.InternalNano
-dontwarn com.google.protobuf.nano.MessageNano
-dontwarn com.google.protobuf.nano.WireFormatNano
-dontwarn dagger.BindsOptionalOf
-dontwarn dagger.Lazy
-dontwarn dagger.Module
-dontwarn dagger.Provides
-dontwarn dagger.internal.DoubleCheck
-dontwarn dagger.internal.Factory
-dontwarn dagger.internal.Preconditions
-dontwarn javax.inject.Inject
-dontwarn javax.inject.Provider
-dontwarn javax.inject.Qualifier
-dontwarn javax.inject.Scope
