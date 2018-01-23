package com.google.android.apps.nexuslauncher;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.google.android.libraries.launcherclient.ISerializableScrollCallback;
import com.google.android.libraries.launcherclient.GoogleNow;

public class NexusLauncherOverlay implements Launcher.LauncherOverlay, ISerializableScrollCallback {
    private GoogleNow mNowConnection;
    private Launcher.LauncherOverlayCallbacks mOverlayCallbacks;
    private boolean mRestartOnStop;
    private int mFlags;
    private boolean mAttached;
    private final Launcher mLauncher;

    public NexusLauncherOverlay(Launcher launcher) {
        mAttached = false;
        mLauncher = launcher;
        mFlags = Utilities.getDevicePrefs(launcher).getInt("pref_persistent_flags", 0);
        mRestartOnStop = false;
    }

    public void setNowConnection(GoogleNow nowConnection) {
        mNowConnection = nowConnection;
    }

    public void stop() {
        if (mRestartOnStop) {
            mLauncher.recreate();
        }
    }

    @Override
    public void setPersistentFlags(int flags) {
        flags |= (16 | 8);
        if (flags != mFlags) {
            mRestartOnStop = true;
            mFlags = flags;
            Utilities.getDevicePrefs(mLauncher).edit().putInt("pref_persistent_flags", flags).apply();
        }
    }

    @Override
    public void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive) {
        if (overlayAttached != mAttached) {
            mAttached = overlayAttached;
            mLauncher.setLauncherOverlay(overlayAttached ? this : null);
        }
    }

    @Override
    public void onOverlayScrollChanged(float n) {
        if (mOverlayCallbacks != null) {
            mOverlayCallbacks.onScrollChanged(n);
        }
    }

    @Override
    public void onScrollChange(float progress, boolean rtl) {
        mNowConnection.onScroll(progress);
    }

    @Override
    public void onScrollInteractionBegin() {
        mNowConnection.startScroll();
    }

    @Override
    public void onScrollInteractionEnd() {
        mNowConnection.endScroll();
    }

    @Override
    public void setOverlayCallbacks(Launcher.LauncherOverlayCallbacks cb) {
        mOverlayCallbacks = cb;
    }
}
