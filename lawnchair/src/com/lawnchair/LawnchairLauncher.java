package com.lawnchair;

import com.android.launcher3.Launcher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import com.lawnchair.nexuslauncher.OverlayCallbackImpl;

public class LawnchairLauncher extends Launcher {
    @Override
    protected LauncherOverlayManager getDefaultOverlay() {
        return new OverlayCallbackImpl(this);
    }
}
