package com.google.android.libraries.launcherclient;

interface ILauncherOverlayCallback {

    oneway void overlayScrollChanged(float progress);

    oneway void overlayStatusChanged(int status);

}
