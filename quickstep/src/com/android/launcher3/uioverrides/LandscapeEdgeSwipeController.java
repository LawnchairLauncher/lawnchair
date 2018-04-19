package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.quickstep.TouchInteractionService.EDGE_NAV_BAR;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.SysuiEventLogger;

/**
 * Touch controller for handling edge swipes in landscape/seascape UI
 */
public class LandscapeEdgeSwipeController extends AbstractStateChangeTouchController {

    private static final String TAG = "LandscapeEdgeSwipeCtrl";

    public LandscapeEdgeSwipeController(Launcher l) {
        super(l, SwipeDetector.HORIZONTAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (mCurrentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
            return false;
        }
        return mLauncher.isInState(NORMAL) && (ev.getEdgeFlags() & EDGE_NAV_BAR) != 0;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        return SwipeDetector.DIRECTION_BOTH;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        boolean draggingFromNav = mLauncher.getDeviceProfile().isSeascape() != isDragTowardPositive;
        return draggingFromNav ? OVERVIEW : NORMAL;
    }

    @Override
    protected float getShiftRange() {
        return mLauncher.getDragLayer().getWidth();
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, maxAccuracy);
        return (mLauncher.getDeviceProfile().isSeascape() ? 2 : -2) / range;
    }

    @Override
    protected int getDirectionForLog() {
        return mLauncher.getDeviceProfile().isSeascape() ? Direction.RIGHT : Direction.LEFT;
    }

    @Override
    protected void onSwipeInteractionCompleted(LauncherState targetState, int logAction) {
        super.onSwipeInteractionCompleted(targetState, logAction);
        if (mFromState == NORMAL && targetState == OVERVIEW) {
            RecentsModel.getInstance(mLauncher).onOverviewShown(true, TAG);
            SysuiEventLogger.writeDummyRecentsTransition(0);
        }
    }
}
