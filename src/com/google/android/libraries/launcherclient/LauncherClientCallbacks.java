package com.google.android.libraries.launcherclient;

public interface LauncherClientCallbacks {
    void onOverlayScrollChanged(float progress);

    void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive);
}
