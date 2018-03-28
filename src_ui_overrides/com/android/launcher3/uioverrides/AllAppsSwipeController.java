package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * TouchController to switch between NORMAL and ALL_APPS state.
 */
public class AllAppsSwipeController extends AbstractStateChangeTouchController {

    public AllAppsSwipeController(Launcher l) {
        super(l, SwipeDetector.VERTICAL);
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
        if (!mLauncher.isInState(NORMAL) && !mLauncher.isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (mLauncher.isInState(ALL_APPS) && !mLauncher.getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return true;
    }

    @Override
    protected int getSwipeDirection(MotionEvent ev) {
        if (mLauncher.isInState(ALL_APPS)) {
            mStartContainerType = ContainerType.ALLAPPS;
            return SwipeDetector.DIRECTION_NEGATIVE;
        } else {
            mStartContainerType = mLauncher.getDragLayer().isEventOverHotseat(ev) ?
                    ContainerType.HOTSEAT : ContainerType.WORKSPACE;
            return SwipeDetector.DIRECTION_POSITIVE;
        }
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        return fromState == ALL_APPS ? NORMAL : ALL_APPS;
    }

    @Override
    protected float initCurrentAnimation() {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        mCurrentAnimation = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(mToState, maxAccuracy);
        float startVerticalShift = mFromState.getVerticalProgress(mLauncher) * range;
        float endVerticalShift = mToState.getVerticalProgress(mLauncher) * range;
        float totalShift = endVerticalShift - startVerticalShift;
        return 1 / totalShift;
    }
}
