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
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
@TargetApi(Build.VERSION_CODES.P)
public interface ActivityControlHelper<T extends BaseDraggingActivity> {

    void onTransitionCancelled(T activity, boolean activityVisible);

    int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect);

    void onSwipeUpToRecentsComplete(T activity);

    default void onSwipeUpToHomeComplete(T activity) { }
    void onAssistantVisibilityChanged(float visibility);

    @NonNull HomeAnimationFactory prepareHomeUI(T activity);

    AnimationFactory prepareRecentsUI(T activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback);

    ActivityInitListener createActivityInitListener(BiPredicate<T, Boolean> onInitListener);

    @Nullable
    T getCreatedActivity();

    default boolean isResumed() {
        BaseDraggingActivity activity = getCreatedActivity();
        return activity != null && activity.hasBeenResumed();
    }

    @UiThread
    @Nullable
    <T extends View> T getVisibleRecentsView();

    @UiThread
    boolean switchToRecentsIfVisible(Runnable onCompleteCallback);

    Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target);

    boolean shouldMinimizeSplitScreen();

    default boolean deferStartingActivity(Region activeNavBarRegion, MotionEvent ev) {
        return true;
    }

    /**
     * Used for containerType in {@link com.android.launcher3.logging.UserEventDispatcher}
     */
    int getContainerType();

    boolean isInLiveTileMode();

    void onLaunchTaskFailed(T activity);

    void onLaunchTaskSuccess(T activity);

    interface ActivityInitListener {

        void register();

        void unregister();

        void registerAndStartActivity(Intent intent, RemoteAnimationProvider animProvider,
                Context context, Handler handler, long duration);
    }

    interface AnimationFactory {

        default void onRemoteAnimationReceived(RemoteAnimationTargetSet targets) { }

        void createActivityController(long transitionLength);

        default void adjustActivityControllerInterpolators() { }

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

        static RectF getDefaultWindowTargetRect(DeviceProfile dp) {
            final int halfIconSize = dp.iconSizePx / 2;
            final float targetCenterX = dp.availableWidthPx / 2f;
            final float targetCenterY = dp.availableHeightPx - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetCenterX - halfIconSize, targetCenterY - halfIconSize,
                    targetCenterX + halfIconSize, targetCenterY + halfIconSize);
        }

    }
}
