# The rules from AOSP are located in proguard.flags file, we can just maintain Lawnchair related rules here.


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

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn android.appwidget.AppWidgetHost$AppWidgetHostListener
-dontwarn android.util.StatsEvent$Builder
-dontwarn android.util.StatsEvent
-dontwarn androidx.window.extensions.WindowExtensions
-dontwarn androidx.window.extensions.WindowExtensionsProvider
-dontwarn androidx.window.extensions.layout.DisplayFeature
-dontwarn androidx.window.extensions.layout.FoldingFeature
-dontwarn androidx.window.extensions.layout.WindowLayoutComponent
-dontwarn androidx.window.extensions.layout.WindowLayoutInfo
-dontwarn androidx.window.sidecar.SidecarDeviceState
-dontwarn androidx.window.sidecar.SidecarDisplayFeature
-dontwarn androidx.window.sidecar.SidecarInterface$SidecarCallback
-dontwarn androidx.window.sidecar.SidecarInterface
-dontwarn androidx.window.sidecar.SidecarProvider
-dontwarn androidx.window.sidecar.SidecarWindowLayoutInfo
-dontwarn com.android.org.conscrypt.TrustManagerImpl
-dontwarn com.android.wm.shell.animation.FloatProperties
-dontwarn com.android.wm.shell.animation.PhysicsAnimator$FlingConfig
-dontwarn com.android.wm.shell.animation.PhysicsAnimator$SpringConfig
-dontwarn com.android.wm.shell.animation.PhysicsAnimator$UpdateListener
-dontwarn com.android.wm.shell.animation.PhysicsAnimator
-dontwarn com.android.wm.shell.bubbles.BubbleDataRepository
-dontwarn com.android.wm.shell.bubbles.BubbleOverflow
-dontwarn com.android.wm.shell.bubbles.DismissView
-dontwarn com.android.wm.shell.bubbles.ManageEducationView
-dontwarn com.android.wm.shell.bubbles.RelativeTouchListener
-dontwarn com.android.wm.shell.bubbles.StackEducationView
-dontwarn com.android.wm.shell.common.FloatingContentCoordinator$FloatingContent
-dontwarn com.android.wm.shell.common.FloatingContentCoordinator
-dontwarn com.android.wm.shell.common.magnetictarget.MagnetizedObject$MagnetListener
-dontwarn com.android.wm.shell.common.magnetictarget.MagnetizedObject$MagneticTarget
-dontwarn com.android.wm.shell.common.magnetictarget.MagnetizedObject
-dontwarn com.android.wm.shell.desktopmode.DesktopModeTaskRepository$ActiveTasksListener
-dontwarn com.android.wm.shell.desktopmode.DesktopModeTaskRepository
-dontwarn com.android.wm.shell.desktopmode.DesktopTasksController
-dontwarn com.android.wm.shell.pip.tv.TvPipKeepClearAlgorithm$Placement
-dontwarn com.android.wm.shell.pip.tv.TvPipKeepClearAlgorithm
-dontwarn com.skydoves.balloon.ArrowPositionRules
-dontwarn com.skydoves.balloon.Balloon$Builder
-dontwarn com.skydoves.balloon.Balloon
-dontwarn com.skydoves.balloon.BalloonAnimation
-dontwarn dalvik.system.CloseGuard
-dontwarn lineageos.providers.LineageSettings$System


-keep class app.lawnchair.LawnchairProto$* { *; }
-keep class app.lawnchair.LawnchairApp { *; }
-keep class app.lawnchair.LawnchairLauncher { *; }
-keep class app.lawnchair.compatlib.**

-keep class com.google.protobuf.Timestamp { *; }

# TODO: Remove this after the change in https://github.com/ChickenHook/RestrictionBypass/pull/9 has been released.
-keep class org.chickenhook.restrictionbypass.**
# TODO: Remove this after the change in https://github.com/KieronQuinn/Smartspacer/pull/58 has been released.
-keep class com.kieronquinn.app.smartspacer.sdk.**
