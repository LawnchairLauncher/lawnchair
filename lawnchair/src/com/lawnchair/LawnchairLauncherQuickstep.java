package com.lawnchair;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import com.lawnchair.nexuslauncher.OverlayCallbackImpl;

public class LawnchairLauncherQuickstep extends QuickstepLauncher {
    @Override
    protected LauncherOverlayManager getDefaultOverlay() {
        return new OverlayCallbackImpl(this);
    }
}
