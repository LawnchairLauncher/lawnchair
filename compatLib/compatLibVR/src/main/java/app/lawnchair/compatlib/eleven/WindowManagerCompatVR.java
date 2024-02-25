package app.lawnchair.compatlib.eleven;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_BOTTOM;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_INVALID;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_LEFT;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_RIGHT;

import android.app.WindowConfiguration;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManagerGlobal;
import androidx.annotation.RequiresApi;

@RequiresApi(30)
public class WindowManagerCompatVR {
    public static final int NAV_BAR_POS_INVALID = NAV_BAR_INVALID;
    public static final int NAV_BAR_POS_LEFT = NAV_BAR_LEFT;
    public static final int NAV_BAR_POS_RIGHT = NAV_BAR_RIGHT;
    public static final int NAV_BAR_POS_BOTTOM = NAV_BAR_BOTTOM;

    public static final int WINDOWING_MODE_SPLIT_SCREEN_PRIMARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;

    public static final int WINDOWING_MODE_SPLIT_SCREEN_SECONDARY =
            WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;

    public static int getNavBarPosition(int displayId) {
        try {
            return WindowManagerGlobal.getWindowManagerService().getNavBarPosition(displayId);
        } catch (RemoteException e) {
            Log.w("WindowManagerCompatVR", "Failed to get nav bar position");
        }
        return NAV_BAR_POS_INVALID;
    }
}
