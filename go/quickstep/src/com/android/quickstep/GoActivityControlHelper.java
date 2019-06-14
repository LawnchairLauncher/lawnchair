package com.android.quickstep;

import android.content.Context;
import android.graphics.Rect;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * Base activity control helper for Go that stubs out most of the functionality that is not needed
 * for Go.
 *
 * @param <T> activity that contains the overview
 */
public abstract class GoActivityControlHelper<T extends BaseDraggingActivity> implements
        ActivityControlHelper<T> {

    @Override
    public void onTransitionCancelled(T activity, boolean activityVisible) {
        // Go transitions to overview are all atomic.
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        // TODO Implement outRect depending on where the task should animate to.
        // Go does not support swipe up gesture.
        return 0;
    }

    @Override
    public void onSwipeUpToRecentsComplete(T activity) {
        // Go does not support swipe up gesture.
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // Go does not support assistant visibility transitions.
    }

    @Override
    public HomeAnimationFactory prepareHomeUI(T activity) {
        // Go does not support gestures from app to home.
        return null;
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        // Go does not support gestures to overview.
        return null;
    }

    @Override
    public boolean shouldMinimizeSplitScreen() {
        // Go does not support split screen.
        return true;
    }

    @Override
    public boolean isInLiveTileMode() {
        // Go does not support live tiles.
        return false;
    }

    @Override
    public void onLaunchTaskFailed(T activity) {
        // Go does not support gestures from one task to another.
    }
}
