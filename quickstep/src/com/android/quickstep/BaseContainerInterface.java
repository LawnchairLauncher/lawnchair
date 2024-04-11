/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;
import static com.android.quickstep.GestureState.GestureEndTarget.LAST_TASK;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class BaseContainerInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        CONTAINER_TYPE extends RecentsViewContainer> {

    public boolean rotationSupportedByActivity = false;

    @Nullable
    public abstract CONTAINER_TYPE getCreatedContainer();

    public abstract boolean isInLiveTileMode();

    public abstract void onAssistantVisibilityChanged(float assistantVisibility);

    public abstract boolean allowMinimizeSplitScreen();

    public abstract boolean isResumed();

    public abstract boolean isStarted();
    public abstract boolean deferStartingActivity(RecentsAnimationDeviceState deviceState,
            MotionEvent ev);

    /** @return whether to allow going to All Apps from Overview. */
    public abstract boolean allowAllAppsFromOverview();

    /**
     * Returns the color of the scrim behind overview when at rest in this state.
     * Return {@link Color#TRANSPARENT} for no scrim.
     */
    protected abstract int getOverviewScrimColorForState(CONTAINER_TYPE container,
            STATE_TYPE state);

    public abstract int getSwipeUpDestinationAndLength(
            DeviceProfile dp, Context context, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler);

    @Nullable
    public abstract TaskbarUIController getTaskbarController();

    public abstract BaseActivityInterface.AnimationFactory prepareRecentsUI(
            RecentsAnimationDeviceState deviceState, boolean activityVisible,
            Consumer<AnimatorControllerWithResistance> callback);

    public abstract ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener);
    /**
     * Returns the expected STATE_TYPE from the provided GestureEndTarget.
     */
    public abstract STATE_TYPE stateFromGestureEndTarget(GestureState.GestureEndTarget endTarget);

    public abstract void switchRunningTaskViewToScreenshot(HashMap<Integer,
            ThumbnailData> thumbnailDatas, Runnable runnable);

    public abstract void closeOverlay();

    public abstract Rect getOverviewWindowBounds(
            Rect homeBounds, RemoteAnimationTarget target);

    public abstract void onLaunchTaskFailed();

    public abstract void onExitOverview(RotationTouchHelper deviceState,
            Runnable exitRunnable);

    /** Called when the animation to home has fully settled. */
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {}

    /**
     * Sets a callback to be run when an activity launch happens while launcher is not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable r) {}
    /**
     * @return Whether the gesture in progress should be cancelled.
     */
    public boolean shouldCancelCurrentGesture() {
        return false;
    }

    @Nullable
    public DesktopVisibilityController getDesktopVisibilityController() {
        return null;
    }

    /**
     * Called when the gesture ends and the animation starts towards the given target. Used to add
     * an optional additional animation with the same duration.
     */
    public @Nullable Animator getParallelAnimationToLauncher(
            GestureState.GestureEndTarget endTarget, long duration,
            RecentsAnimationCallbacks callbacks) {
        if (endTarget == RECENTS) {
            CONTAINER_TYPE container = getCreatedContainer();
            if (container == null) {
                return null;
            }
            RecentsView recentsView = container.getOverviewPanel();
            STATE_TYPE state = stateFromGestureEndTarget(endTarget);
            ScrimView scrimView = container.getScrimView();
            ObjectAnimator anim = ObjectAnimator.ofArgb(scrimView, VIEW_BACKGROUND_COLOR,
                    getOverviewScrimColorForState(container, state));
            anim.setDuration(duration);
            anim.setInterpolator(recentsView == null || !recentsView.isKeyboardTaskFocusPending()
                    ? LINEAR : INSTANT);
            return anim;
        }
        return null;
    }

    /**
     * Called when the animation to the target has finished, but right before updating the state.
     * @return A View that needs to draw before ending the recents animation to LAST_TASK.
     * (This is a hack to ensure Taskbar draws its background first to avoid flickering.)
     */
    public @Nullable View onSettledOnEndTarget(GestureState.GestureEndTarget endTarget) {
        TaskbarUIController taskbarUIController = getTaskbarController();
        if (taskbarUIController != null) {
            taskbarUIController.setSystemGestureInProgress(false);
            return taskbarUIController.getRootView();
        }
        return null;
    }

    /**
     * Called when the current gesture transition is cancelled.
     * @param activityVisible Whether the user can see the changes we make here, so try to animate.
     * @param endTarget If the gesture ended before we got cancelled, where we were headed.
     */
    public void onTransitionCancelled(boolean activityVisible,
            @Nullable GestureState.GestureEndTarget endTarget) {
        RecentsViewContainer container = getCreatedContainer();
        if (container == null) {
            return;
        }
        RecentsView recentsView = container.getOverviewPanel();
        BaseState startState = recentsView.getStateManager().getRestState();
        if (endTarget != null) {
            // We were on our way to this state when we got canceled, end there instead.
            startState = stateFromGestureEndTarget(endTarget);
            DesktopVisibilityController controller = getDesktopVisibilityController();
            if (controller != null && controller.areDesktopTasksVisible()
                    && endTarget == LAST_TASK) {
                // When we are cancelling the transition and going back to last task, move to
                // rest state instead when desktop tasks are visible.
                // If a fullscreen task is visible, launcher goes to normal state when the
                // activity is stopped. This does not happen when desktop tasks are visible
                // on top of launcher. Force the launcher state to rest state here.
                startState = recentsView.getStateManager().getRestState();
                // Do not animate the transition
                activityVisible = false;
            }
        }
        recentsView.getStateManager().goToState(startState, activityVisible);
    }

    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        if (dp.isTablet) {
            if (Flags.enableGridOnlyOverview()) {
                calculateGridTaskSize(context, dp, outRect, orientedState);
            } else {
                calculateFocusTaskSize(context, dp, outRect);
            }
        } else {
            Resources res = context.getResources();
            float maxScale = res.getFloat(R.dimen.overview_max_scale);
            int taskMargin = dp.overviewTaskMarginPx;
            calculateTaskSizeInternal(
                    context,
                    dp,
                    dp.overviewTaskThumbnailTopMarginPx,
                    dp.getOverviewActionsClaimedSpace(),
                    res.getDimensionPixelSize(R.dimen.overview_minimum_next_prev_size) + taskMargin,
                    maxScale,
                    Gravity.CENTER,
                    outRect);
        }
    }

    /**
     * Calculates the taskView size for carousel during app to overview animation on tablets.
     */
    public final void calculateCarouselTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        if (dp.isTablet && dp.isGestureMode) {
            Resources res = context.getResources();
            float minScale = res.getFloat(R.dimen.overview_carousel_min_scale);
            Rect gridRect = new Rect();
            calculateGridSize(dp, context, gridRect);
            calculateTaskSizeInternal(context, dp, gridRect, minScale, Gravity.CENTER | Gravity.TOP,
                    outRect);
        } else {
            calculateTaskSize(context, dp, outRect, orientedState);
        }
    }

    private void calculateFocusTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        Resources res = context.getResources();
        float maxScale = res.getFloat(R.dimen.overview_max_scale);
        Rect gridRect = new Rect();
        calculateGridSize(dp, context, gridRect);
        calculateTaskSizeInternal(context, dp, gridRect, maxScale, Gravity.CENTER, outRect);
    }

    private void calculateTaskSizeInternal(Context context, DeviceProfile dp, int claimedSpaceAbove,
            int claimedSpaceBelow, int minimumHorizontalPadding, float maxScale, int gravity,
            Rect outRect) {
        Rect insets = dp.getInsets();

        Rect potentialTaskRect = new Rect(0, 0, dp.widthPx, dp.heightPx);
        potentialTaskRect.inset(insets.left, insets.top, insets.right, insets.bottom);
        potentialTaskRect.inset(
                minimumHorizontalPadding,
                claimedSpaceAbove,
                minimumHorizontalPadding,
                claimedSpaceBelow);

        calculateTaskSizeInternal(context, dp, potentialTaskRect, maxScale, gravity, outRect);
    }

    private void calculateTaskSizeInternal(Context context, DeviceProfile dp,
            Rect potentialTaskRect, float targetScale, int gravity, Rect outRect) {
        PointF taskDimension = getTaskDimension(context, dp);

        float scale = Math.min(
                potentialTaskRect.width() / taskDimension.x,
                potentialTaskRect.height() / taskDimension.y);
        scale = Math.min(scale, targetScale);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        Gravity.apply(gravity, outWidth, outHeight, potentialTaskRect, outRect);
    }

    private static PointF getTaskDimension(Context context, DeviceProfile dp) {
        PointF dimension = new PointF();
        getTaskDimension(context, dp, dimension);
        return dimension;
    }

    /**
     * Gets the dimension of the task in the current system state.
     */
    public static void getTaskDimension(Context context, DeviceProfile dp, PointF out) {
        out.x = dp.widthPx;
        out.y = dp.heightPx;
        if (dp.isTablet && !DisplayController.isTransientTaskbar(context)) {
            out.y -= dp.taskbarHeight;
        }
    }

    /**
     * Calculates the overview grid size for the provided device configuration.
     */
    public final void calculateGridSize(DeviceProfile dp, Context context, Rect outRect) {
        Rect insets = dp.getInsets();
        int topMargin = dp.overviewTaskThumbnailTopMarginPx;
        int bottomMargin = dp.getOverviewActionsClaimedSpace();
        if (dp.isTaskbarPresent && Flags.enableGridOnlyOverview()) {
            topMargin += context.getResources().getDimensionPixelSize(
                    R.dimen.overview_top_margin_grid_only);
            bottomMargin += context.getResources().getDimensionPixelSize(
                    R.dimen.overview_bottom_margin_grid_only);
        }
        int sideMargin = dp.overviewGridSideMargin;

        outRect.set(0, 0, dp.widthPx, dp.heightPx);
        outRect.inset(Math.max(insets.left, sideMargin), insets.top + topMargin,
                Math.max(insets.right, sideMargin), Math.max(insets.bottom, bottomMargin));
    }

    /**
     * Calculates the overview grid non-focused task size for the provided device configuration.
     */
    public final void calculateGridTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        Resources res = context.getResources();
        Rect potentialTaskRect = new Rect();
        if (Flags.enableGridOnlyOverview()) {
            calculateGridSize(dp, context, potentialTaskRect);
        } else {
            calculateFocusTaskSize(context, dp, potentialTaskRect);
        }

        float rowHeight = (potentialTaskRect.height() + dp.overviewTaskThumbnailTopMarginPx
                - dp.overviewRowSpacing) / 2f;

        PointF taskDimension = getTaskDimension(context, dp);
        float scale = (rowHeight - dp.overviewTaskThumbnailTopMarginPx) / taskDimension.y;
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        int gravity = Gravity.TOP;
        gravity |= orientedState.getRecentsRtlSetting(res) ? Gravity.RIGHT : Gravity.LEFT;
        Gravity.apply(gravity, outWidth, outHeight, potentialTaskRect, outRect);
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public final void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        calculateTaskSize(context, dp, outRect, orientedState);
        boolean isGridOnlyOverview = dp.isTablet && Flags.enableGridOnlyOverview();
        int claimedSpaceBelow = isGridOnlyOverview
                ? dp.overviewActionsTopMarginPx + dp.overviewActionsHeight + dp.stashedTaskbarHeight
                : (dp.heightPx - outRect.bottom - dp.getInsets().bottom);
        int minimumHorizontalPadding = 0;
        if (!isGridOnlyOverview) {
            float maxScale = context.getResources().getFloat(R.dimen.overview_modal_max_scale);
            minimumHorizontalPadding =
                    Math.round((dp.availableWidthPx - outRect.width() * maxScale) / 2);
        }
        calculateTaskSizeInternal(
                context,
                dp,
                dp.overviewTaskMarginPx,
                claimedSpaceBelow,
                minimumHorizontalPadding,
                1f /*maxScale*/,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                outRect);
    }
}
