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

import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.SysUINavigationMode.getMode;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.quickstep.util.SplitScreenBounds;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class BaseActivityInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE>> {

    private final PointF mTempPoint = new PointF();
    public final boolean rotationSupportedByActivity;

    protected BaseActivityInterface(boolean rotationSupportedByActivity) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
    }

    public void onTransitionCancelled(boolean activityVisible) {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        STATE_TYPE startState = activity.getStateManager().getRestState();
        activity.getStateManager().goToState(startState, activityVisible);
    }

    public abstract int getSwipeUpDestinationAndLength(
            DeviceProfile dp, Context context, Rect outRect);

    public void onSwipeUpToRecentsComplete() {
        // Re apply state in case we did something funky during the transition.
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.getStateManager().reapplyState();
    }

    public void onSwipeUpToHomeComplete() { }

    public abstract void onAssistantVisibilityChanged(float visibility);

    public abstract AnimationFactory prepareRecentsUI(
            boolean activityVisible, Consumer<AnimatorPlaybackController> callback);

    public abstract ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener);

    /**
     * Sets a callback to be run when an activity launch happens while launcher is not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable r) {}

    @Nullable
    public abstract ACTIVITY_TYPE getCreatedActivity();

    @Nullable
    public DepthController getDepthController() {
        return null;
    }

    public final boolean isResumed() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        return activity != null && activity.hasBeenResumed();
    }

    public final boolean isStarted() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        return activity != null && activity.isStarted();
    }

    @UiThread
    @Nullable
    public abstract <T extends RecentsView> T getVisibleRecentsView();

    @UiThread
    public abstract boolean switchToRecentsIfVisible(Runnable onCompleteCallback);

    public abstract Rect getOverviewWindowBounds(
            Rect homeBounds, RemoteAnimationTargetCompat target);

    public abstract boolean allowMinimizeSplitScreen();

    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        return true;
    }

    /**
     * Updates the prediction state to the overview state.
     */
    public void updateOverviewPredictionState() {
        // By public overview predictions are not supported
    }

    /**
     * Used for containerType in {@link com.android.launcher3.logging.UserEventDispatcher}
     */
    public abstract int getContainerType();

    public abstract boolean isInLiveTileMode();

    public abstract void onLaunchTaskFailed();

    public void onLaunchTaskSuccess() {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.getStateManager().moveToRestState();
    }

    public void closeOverlay() { }

    public void switchRunningTaskViewToScreenshot(ThumbnailData thumbnailData, Runnable runnable) {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        RecentsView recentsView = activity.getOverviewPanel();
        if (recentsView == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        recentsView.switchToScreenshot(thumbnailData, runnable);
    }

    public void setHintUserWillBeActive() {}

    /**
     * Sets the expected window size in multi-window mode
     */
    public abstract void getMultiWindowSize(Context context, DeviceProfile dp, PointF out);

    /**
     * Calculates the taskView size for the provided device configuration
     */
    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        calculateTaskSize(context, dp, getExtraSpace(context, dp), outRect);
    }

    protected abstract float getExtraSpace(Context context, DeviceProfile dp);

    private void calculateTaskSize(
            Context context, DeviceProfile dp, float extraVerticalSpace, Rect outRect) {
        Resources res = context.getResources();
        final boolean showLargeTaskSize = showOverviewActions(context);

        final int paddingResId;
        if (dp.isMultiWindowMode) {
            paddingResId = R.dimen.multi_window_task_card_horz_space;
        } else if (dp.isVerticalBarLayout()) {
            paddingResId = R.dimen.landscape_task_card_horz_space;
        } else if (showLargeTaskSize) {
            paddingResId = R.dimen.portrait_task_card_horz_space_big_overview;
        } else {
            paddingResId = R.dimen.portrait_task_card_horz_space;
        }
        float paddingHorz = res.getDimension(paddingResId);
        float paddingVert = showLargeTaskSize
                ? 0 : res.getDimension(R.dimen.task_card_vert_space);

        calculateTaskSizeInternal(context, dp, extraVerticalSpace, paddingHorz, paddingVert,
                res.getDimension(R.dimen.task_thumbnail_top_margin), outRect);
    }

    private void calculateTaskSizeInternal(Context context, DeviceProfile dp,
            float extraVerticalSpace, float paddingHorz, float paddingVert, float topIconMargin,
            Rect outRect) {
        float taskWidth, taskHeight;
        Rect insets = dp.getInsets();
        if (dp.isMultiWindowMode) {
            WindowBounds bounds = SplitScreenBounds.INSTANCE.getSecondaryWindowBounds(context);
            taskWidth = bounds.availableSize.x;
            taskHeight = bounds.availableSize.y;
        } else {
            taskWidth = dp.availableWidthPx;
            taskHeight = dp.availableHeightPx;
        }

        // Note this should be same as dp.availableWidthPx and dp.availableHeightPx unless
        // we override the insets ourselves.
        int launcherVisibleWidth = dp.widthPx - insets.left - insets.right;
        int launcherVisibleHeight = dp.heightPx - insets.top - insets.bottom;

        float availableHeight = launcherVisibleHeight
                - topIconMargin - extraVerticalSpace - paddingVert;
        float availableWidth = launcherVisibleWidth - paddingHorz;

        float scale = Math.min(availableWidth / taskWidth, availableHeight / taskHeight);
        float outWidth = scale * taskWidth;
        float outHeight = scale * taskHeight;

        // Center in the visible space
        float x = insets.left + (launcherVisibleWidth - outWidth) / 2;
        float y = insets.top + Math.max(topIconMargin,
                (launcherVisibleHeight - extraVerticalSpace - outHeight) / 2);
        outRect.set(Math.round(x), Math.round(y),
                Math.round(x) + Math.round(outWidth), Math.round(y) + Math.round(outHeight));
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        float paddingHorz = context.getResources().getDimension(dp.isMultiWindowMode
                ? R.dimen.multi_window_task_card_horz_space
                : dp.isVerticalBarLayout()
                        ? R.dimen.landscape_task_card_horz_space
                        : R.dimen.portrait_modal_task_card_horz_space);
        float extraVerticalSpace = getOverviewActionsHeight(context);
        float paddingVert = 0;
        float topIconMargin = 0;
        calculateTaskSizeInternal(context, dp, extraVerticalSpace, paddingHorz, paddingVert,
                topIconMargin, outRect);
    }

    /** Gets the space that the overview actions will take, including margins. */
    public float getOverviewActionsHeight(Context context) {
        Resources res = context.getResources();
        float actionsBottomMargin = 0;
        if (getMode(context) == Mode.THREE_BUTTONS) {
            actionsBottomMargin = res.getDimensionPixelSize(
                    R.dimen.overview_actions_bottom_margin_three_button);
        } else {
            actionsBottomMargin = res.getDimensionPixelSize(
                    R.dimen.overview_actions_bottom_margin_gesture);
        }
        float overviewActionsHeight = actionsBottomMargin
                + res.getDimensionPixelSize(R.dimen.overview_actions_height);
        return overviewActionsHeight;
    }

    public interface AnimationFactory {

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

    protected static boolean showOverviewActions(Context context) {
        return ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(context);
    }
}
