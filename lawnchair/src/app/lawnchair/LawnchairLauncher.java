package app.lawnchair;

import com.android.launcher3.Launcher;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

import app.lawnchair.nexuslauncher.OverlayCallbackImpl;

public class LawnchairLauncher extends Launcher {
    @Override
    protected LauncherOverlayManager getDefaultOverlay() {
        return new OverlayCallbackImpl(this);
    }
}
