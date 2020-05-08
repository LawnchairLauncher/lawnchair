/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
@TargetApi(Build.VERSION_CODES.P)
public interface BaseActivityInterface<T extends BaseDraggingActivity> {

    void onTransitionCancelled(boolean activityVisible);

    int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect);

    /**
     * @return The progress of the swipe where we start resisting the user, where 0 is fullscreen
     * and 1 is recents. These values should probably be greater than 1 to let the user swipe past
     * recents before we start resisting them.
     */
    default Pair<Float, Float> getSwipeUpPullbackStartAndMaxProgress() {
        return new Pair<>(1.4f, 1.8f);
    }

    void onSwipeUpToRecentsComplete();

    default void onSwipeUpToHomeComplete() { }
    void onAssistantVisibilityChanged(float visibility);

    @NonNull HomeAnimationFactory prepareHomeUI();

    AnimationFactory prepareRecentsUI(boolean activityVisible, boolean animateActivity,
            Consumer<AnimatorPlaybackController> callback);

    ActivityInitListener createActivityInitListener(Predicate<Boolean> onInitListener);

    /**
     * Sets a callback to be run when an activity launch happens while launcher is not yet resumed.
     */
    default void setOnDeferredActivityLaunchCallback(Runnable r) {}

    @Nullable
    T getCreatedActivity();

    @Nullable
    default DepthController getDepthController() {
        return null;
    }

    default boolean isResumed() {
        BaseDraggingActivity activity = getCreatedActivity();
        return activity != null && activity.hasBeenResumed();
    }

    default boolean isStarted() {
        BaseDraggingActivity activity = getCreatedActivity();
        return activity != null && activity.isStarted();
    }

    @UiThread
    @Nullable
    <T extends View> T getVisibleRecentsView();

    @UiThread
    boolean switchToRecentsIfVisible(Runnable onCompleteCallback);

    Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target);

    boolean allowMinimizeSplitScreen();

    default boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        return true;
    }

    /**
     * Updates the prediction state to the overview state.
     */
    default void updateOverviewPredictionState() {
        // By default overview predictions are not supported
    }

    /**
     * Used for containerType in {@link com.android.launcher3.logging.UserEventDispatcher}
     */
    int getContainerType();

    boolean isInLiveTileMode();

    void onLaunchTaskFailed();

    void onLaunchTaskSuccess();

    default void closeOverlay() { }

    default void switchRunningTaskViewToScreenshot(ThumbnailData thumbnailData,
            Runnable runnable) {}

    interface AnimationFactory {

        default void onRemoteAnimationReceived(RemoteAnimationTargets targets) { }

        void createActivityInterface(long transitionLength);

        default void onTransitionCancelled() { }

        default void setShelfState(ShelfPeekAnim.ShelfAnimState animState,
                Interpolator interpolator, long duration) { }

        /**
         * @param attached Whether to show RecentsView alongside the app window. If false, recents
         *                 will be hidden by some property we can animate, e.g. alpha.
         * @param animate Whether to animate recents to/from its new attached state.
         */
        default void setRecentsAttachedToAppWindow(boolean attached, boolean animate) { }
    }

    interface HomeAnimationFactory {

        /** Return the floating view that will animate in sync with the closing window. */
        default @Nullable View getFloatingView() {
            return null;
        }

        @NonNull RectF getWindowTargetRect();

        @NonNull AnimatorPlaybackController createActivityAnimationToHome();

        default void playAtomicAnimation(float velocity) {
            // No-op
        }

        static RectF getDefaultWindowTargetRect(PagedOrientationHandler orientationHandler,
            DeviceProfile dp) {
            final int halfIconSize = dp.iconSizePx / 2;
            float primaryDimension = orientationHandler
                .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
            float secondaryDimension = orientationHandler
                .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
            final float targetX =  primaryDimension / 2f;
            final float targetY = secondaryDimension - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                    targetX + halfIconSize, targetY + halfIconSize);
        }

    }
}
