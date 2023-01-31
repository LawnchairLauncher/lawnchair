package com.android.launcher3.taskbar;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DOZING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

import android.app.KeyguardManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.ScreenOnTracker.ScreenOnListener;
import com.android.systemui.shared.system.QuickStepContract;

import java.io.PrintWriter;

/**
 * Controller for managing keyguard state for taskbar
 */
public class TaskbarKeyguardController implements TaskbarControllers.LoggableTaskbarController {

    private static final int KEYGUARD_SYSUI_FLAGS = SYSUI_STATE_BOUNCER_SHOWING |
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING | SYSUI_STATE_DEVICE_DOZING |
            SYSUI_STATE_OVERVIEW_DISABLED | SYSUI_STATE_HOME_DISABLED |
            SYSUI_STATE_BACK_DISABLED | SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;

    private final ScreenOnListener mScreenOnListener;
    private final TaskbarActivityContext mContext;
    private int mKeyguardSysuiFlags;
    private boolean mBouncerShowing;
    private NavbarButtonsViewController mNavbarButtonsViewController;
    private final KeyguardManager mKeyguardManager;
    private boolean mIsScreenOff;

    public TaskbarKeyguardController(TaskbarActivityContext context) {
        mContext = context;
        mScreenOnListener = isOn -> {
            if (!isOn) {
                mIsScreenOff = true;
                AbstractFloatingView.closeOpenViews(mContext, false, TYPE_ALL);
            }
        };
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
    }

    public void init(NavbarButtonsViewController navbarButtonUIController) {
        mNavbarButtonsViewController = navbarButtonUIController;
        ScreenOnTracker.INSTANCE.get(mContext).addListener(mScreenOnListener);
    }

    public void updateStateForSysuiFlags(int systemUiStateFlags) {
        boolean bouncerShowing = (systemUiStateFlags & SYSUI_STATE_BOUNCER_SHOWING) != 0;
        boolean keyguardShowing = (systemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING)
                != 0;
        boolean keyguardOccluded =
                (systemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0;
        boolean dozing = (systemUiStateFlags & SYSUI_STATE_DEVICE_DOZING) != 0;

        int interestingKeyguardFlags = systemUiStateFlags & KEYGUARD_SYSUI_FLAGS;
        if (interestingKeyguardFlags == mKeyguardSysuiFlags) {
            return;
        }
        mKeyguardSysuiFlags = interestingKeyguardFlags;

        mBouncerShowing = bouncerShowing;

        mNavbarButtonsViewController.setKeyguardVisible(keyguardShowing || dozing,
                keyguardOccluded);
        updateIconsForBouncer();

        if (keyguardShowing) {
            AbstractFloatingView.closeOpenViews(mContext, true, TYPE_ALL);
        }
    }

    public boolean isScreenOff() {
        return mIsScreenOff;
    }

    public void setScreenOn() {
        mIsScreenOff = false;
    }

    /**
     * Hides/shows taskbar when keyguard is up
     */
    private void updateIconsForBouncer() {
        boolean disableBack = (mKeyguardSysuiFlags & SYSUI_STATE_BACK_DISABLED) != 0;
        boolean showBackForBouncer =
                !disableBack && mKeyguardManager.isDeviceSecure() && mBouncerShowing;
        mNavbarButtonsViewController.setBackForBouncer(showBackForBouncer);
    }

    public void onDestroy() {
        ScreenOnTracker.INSTANCE.get(mContext).removeListener(mScreenOnListener);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarKeyguardController:");

        pw.println(prefix + "\tmKeyguardSysuiFlags=" + QuickStepContract.getSystemUiStateString(
                mKeyguardSysuiFlags));
        pw.println(prefix + "\tmBouncerShowing=" + mBouncerShowing);
        pw.println(prefix + "\tmIsScreenOff=" + mIsScreenOff);
    }
}
