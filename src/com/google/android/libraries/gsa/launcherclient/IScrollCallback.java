package com.google.android.libraries.gsa.launcherclient;

public interface IScrollCallback {
    void onOverlayScrollChanged(float progress);

    void onServiceStateChanged(boolean overlayAttached);
}
