-keep,allowshrinking,allowoptimization class com.android.launcher3.** {
  *;
}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

# Proguard will strip methods required for talkback to properly scroll to
# next row when focus is on the last item of last row when using a RecyclerView
# Keep optimized and shrunk proguard to prevent issues like this when using
# support jar.
-keep class androidx.recyclerview.widget.RecyclerView { *; }

# Fragments
-keep class ** extends androidx.fragment.app.Fragment {
    public <init>(...);
}
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

# BUG(70852369): Surpress additional warnings after changing from Proguard to R8
-dontwarn android.app.**
-dontwarn android.graphics.**
-dontwarn android.os.**
-dontwarn android.view.**
-dontwarn android.window.**

# Ignore warnings for hidden utility classes referenced from the shared lib
-dontwarn com.android.internal.util.**

################ Do not optimize recents lib #############
-keep class com.android.systemui.shared.** {
  *;
}

-keep class com.android.quickstep.** {
  *;
}
