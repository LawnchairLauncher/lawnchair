package com.android.launcher3.taskbar;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_AWAKE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DEVICE_DOZING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_WAKEFULNESS_MASK;
import static com.android.systemui.shared.system.QuickStepContract.WAKEFULNESS_ASLEEP;

import android.app.KeyguardManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.quickstep.util.SystemUiFlagUtils;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;

import java.io.PrintWriter;

/**
 * Controller for managing keyguard state for taskbar
 */
public class TaskbarKeyguardController implements TaskbarControllers.LoggableTaskbarController {

    private final TaskbarActivityContext mContext;
    private long mKeyguardSysuiFlags;
    private boolean mBouncerShowing;
    private NavbarButtonsViewController mNavbarButtonsViewController;
    private final KeyguardManager mKeyguardManager;

    public TaskbarKeyguardController(TaskbarActivityContext context) {
        mContext = context;
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
    }

    public void init(NavbarButtonsViewController navbarButtonUIController) {
        mNavbarButtonsViewController = navbarButtonUIController;
    }

    public void updateStateForSysuiFlags(@SystemUiStateFlags long systemUiStateFlags) {
        long interestingKeyguardFlags = systemUiStateFlags & SystemUiFlagUtils.KEYGUARD_SYSUI_FLAGS;
        if (interestingKeyguardFlags == mKeyguardSysuiFlags) {
            // No change in keyguard relevant flags
            return;
        }
        mKeyguardSysuiFlags = interestingKeyguardFlags;

        boolean bouncerShowing = (systemUiStateFlags & SYSUI_STATE_BOUNCER_SHOWING) != 0;
        boolean keyguardShowing = (systemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING)
                != 0;
        boolean keyguardOccluded =
                (systemUiStateFlags & SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED) != 0;
        boolean dozing = (systemUiStateFlags & SYSUI_STATE_DEVICE_DOZING) != 0;


        mBouncerShowing = bouncerShowing;

        mNavbarButtonsViewController.setKeyguardVisible(keyguardShowing || dozing,
                keyguardOccluded);
        updateIconsForBouncer();

        boolean asleepOrGoingToSleep = (systemUiStateFlags & SYSUI_STATE_AWAKE) == 0;
        boolean closeFloatingViews = keyguardShowing || asleepOrGoingToSleep;

        if (closeFloatingViews) {
            // animate the closing of the views, unless the screen is already asleep.
            boolean animateViewClosing =
                    (systemUiStateFlags & SYSUI_STATE_WAKEFULNESS_MASK) != WAKEFULNESS_ASLEEP;
            AbstractFloatingView.closeOpenViews(mContext, animateViewClosing, TYPE_ALL);
        }
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


    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarKeyguardController:");

        pw.println(prefix + "\tmKeyguardSysuiFlags=" + QuickStepContract.getSystemUiStateString(
                mKeyguardSysuiFlags));
        pw.println(prefix + "\tmBouncerShowing=" + mBouncerShowing);
    }
}
