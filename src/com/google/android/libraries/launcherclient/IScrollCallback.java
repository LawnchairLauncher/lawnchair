package com.google.android.libraries.launcherclient;

public interface IScrollCallback {
    void onOverlayScrollChanged(float progress);

    void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive);
}
