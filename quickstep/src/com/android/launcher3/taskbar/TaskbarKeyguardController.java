package com.android.launcher3.taskbar;

import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DOZING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;

/**
 * Controller for managing keyguard state for taskbar
 */
public class TaskbarKeyguardController {

    private static final int KEYGUARD_SYSUI_FLAGS = SYSUI_STATE_BOUNCER_SHOWING |
            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING | SYSUI_STATE_DEVICE_DOZING;

    private final TaskbarActivityContext mContext;
    private int mDisabledNavIcons;
    private int mKeyguardSysuiFlags;
    private boolean mBouncerShowing;
    private NavbarButtonsViewController mNavbarButtonsViewController;
    private final KeyguardManager mKeyguardManager;
    private boolean mIsScreenOff;

    private final BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIsScreenOff = true;
        }
    };

    public TaskbarKeyguardController(TaskbarActivityContext context) {
        mContext = context;
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
    }

    public void init(NavbarButtonsViewController navbarButtonUIController) {
        mNavbarButtonsViewController = navbarButtonUIController;
        mContext.registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    public void updateStateForSysuiFlags(int systemUiStateFlags) {
        boolean bouncerShowing = (systemUiStateFlags & SYSUI_STATE_BOUNCER_SHOWING) != 0;
        boolean keyguardShowing = (systemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING)
                != 0;
        boolean dozing = (systemUiStateFlags & SYSUI_STATE_DEVICE_DOZING) != 0;

        int interestingKeyguardFlags = systemUiStateFlags & KEYGUARD_SYSUI_FLAGS;
        if (interestingKeyguardFlags == mKeyguardSysuiFlags) {
            return;
        }
        mKeyguardSysuiFlags = interestingKeyguardFlags;

        mBouncerShowing = bouncerShowing;
        if (!mContext.isThreeButtonNav()) {
            // For gesture nav we don't need to deal with bouncer or showing taskbar when locked
            return;
        }

        mNavbarButtonsViewController.setKeyguardVisible(keyguardShowing || dozing);
        updateIconsForBouncer();
    }

    public boolean isScreenOff() {
        return mIsScreenOff;
    }

    public void setScreenOn() {
        mIsScreenOff = false;
    }

    public void disableNavbarElements(int state1, int state2) {
        if (mDisabledNavIcons == state1) {
            // no change
            return;
        }
        mDisabledNavIcons = state1;
        updateIconsForBouncer();
    }

    /**
     * Hides/shows taskbar when keyguard is up
     */
    private void updateIconsForBouncer() {
        boolean disableBack = (mDisabledNavIcons & View.STATUS_BAR_DISABLE_BACK) != 0;
        boolean disableRecent = (mDisabledNavIcons & View.STATUS_BAR_DISABLE_RECENT) != 0;
        boolean disableHome = (mDisabledNavIcons & View.STATUS_BAR_DISABLE_HOME) != 0;
        boolean onlyBackEnabled = !disableBack && disableRecent && disableHome;

        boolean showBackForBouncer = onlyBackEnabled &&
                mKeyguardManager.isDeviceSecure() &&
                mBouncerShowing;
        mNavbarButtonsViewController.setBackForBouncer(showBackForBouncer);
    }

    public void onDestroy() {
        mContext.unregisterReceiver(mScreenOffReceiver);
    }
}
