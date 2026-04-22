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
import static com.android.launcher3.LauncherAnimUtils.SCRIM_COLORS;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.views.ScrimColors;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.ScrimColorsEvaluator;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.ContextInitListener;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class BaseContainerInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        CONTAINER_TYPE extends RecentsViewContainer & StatefulContainer<STATE_TYPE>> {

    public boolean rotationSupportedByActivity = false;
    protected final STATE_TYPE mBackgroundState;

    protected BaseContainerInterface(STATE_TYPE backgroundState) {
        mBackgroundState = backgroundState;
    }

    @UiThread
    @Nullable
    public abstract <T extends RecentsView<?,?>> T getVisibleRecentsView();

    @UiThread
    public abstract boolean switchToRecentsIfVisible(Animator.AnimatorListener animatorListener);

    @Nullable
    public abstract CONTAINER_TYPE getCreatedContainer();

    @Nullable
    protected Runnable mOnInitBackgroundStateUICallback = null;

    public abstract boolean isInLiveTileMode();

    public abstract void onAssistantVisibilityChanged(float assistantVisibility);

    public abstract boolean isResumed();

    public abstract boolean isStarted();
    public boolean deferStartingActivity(
            @NonNull RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        TaskbarUIController controller = getTaskbarController();
        boolean isEventOverBubbleBarStashHandle =
                controller != null && controller.isEventOverBubbleBarViews(ev);
        boolean isEventOverAnyTaskbarItem =
                controller != null && controller.isEventOverAnyTaskbarItem(ev);
        return deviceState.isInDeferredGestureRegion(ev)
                || deviceState.isImeRenderingNavButtons()
                || isTrackpadMultiFingerSwipe(ev)
                || isEventOverBubbleBarStashHandle
                || isEventOverAnyTaskbarItem;
    }

    /**
     * Returns the color of the scrim behind overview when at rest in this state.
     * Return {@link Color#TRANSPARENT} for no scrim.
     */
    protected abstract ScrimColors getOverviewScrimColorForState(CONTAINER_TYPE container,
            STATE_TYPE state);

    public abstract int getSwipeUpDestinationAndLength(
            DeviceProfile dp, Context context, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler);

    @Nullable
    public abstract TaskbarUIController getTaskbarController();

    public interface AnimationFactory {

        void createContainerInterface(long transitionLength);

        /**
         * @param attached Whether to show RecentsView alongside the app window. If false, recents
         *                 will be hidden by some property we can animate, e.g. alpha.
         * @param animate Whether to animate recents to/from its new attached state.
         * @param updateRunningTaskAlpha Whether to update the running task's attached alpha
         */
        default void setRecentsAttachedToAppWindow(
                boolean attached, boolean animate, boolean updateRunningTaskAlpha) { }

        default boolean isRecentsAttachedToAppWindow() {
            return false;
        }

        default boolean hasRecentsEverAttachedToAppWindow() {
            return false;
        }

        /** Called when the gesture ends and we know what state it is going towards */
        default void setEndTarget(GestureState.GestureEndTarget endTarget) { }
    }

    public abstract BaseContainerInterface.AnimationFactory prepareRecentsUI(
            boolean activityVisible,
            Consumer<AnimatorControllerWithResistance> callback);

    public abstract ContextInitListener createActivityInitListener(
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

    public abstract void onExitOverview(Runnable exitRunnable);

    /**
     * @return {@code true} iff the launcher's -1 page is showing
     */
    public boolean isLauncherOverlayShowing() {
        return false;
    }

    /** Called when the animation to home has fully settled. */
    public void onSwipeUpToHomeComplete() {}

    /**
     * Sets a callback to be run when an activity launch happens while launcher is not yet resumed.
     */
    public void setOnDeferredActivityLaunchCallback(Runnable r) {}
    /**
     * @return Whether the gesture in progress should be cancelled.
     */
    public boolean shouldCancelCurrentGesture() {
        TaskbarUIController uiController = getTaskbarController();
        return uiController != null && uiController.isDraggingItem();
    }

    public void runOnInitBackgroundStateUI(Runnable callback) {
        StatefulContainer container = getCreatedContainer();
        if (container != null
                && container.getStateManager().getState() == mBackgroundState) {
            callback.run();
            onInitBackgroundStateUI();
            return;
        }
        mOnInitBackgroundStateUICallback = callback;
    }

    /**
     * Called when the gesture ends and the animation starts towards the given target. Used to add
     * an optional additional animation with the same duration.
     */
    public @Nullable Animator getParallelAnimationToGestureEndTarget(
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
            ObjectAnimator animScrim = ObjectAnimator.ofObject(scrimView, SCRIM_COLORS,
                    ScrimColorsEvaluator.INSTANCE, getOverviewScrimColorForState(container, state));
            animScrim.setDuration(duration);
            animScrim.setInterpolator(
                    recentsView == null || !recentsView.isKeyboardTaskFocusPending()
                            ? LINEAR : INSTANT);

            return animScrim;
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
        CONTAINER_TYPE container = getCreatedContainer();
        if (container == null) {
            return;
        }
        STATE_TYPE startState = container.getStateManager().getRestState();
        final var context = container.asContext();
        if (DesktopVisibilityController.INSTANCE.get(context).isInDesktopModeAndNotInOverview(
                context.getDisplayId()) && endTarget == null) {
            // When tapping on the Taskbar in Desktop mode, reset to BackgroundApp to avoid the
            // home screen icons flickering. Technically we could probably be do this for
            // non-desktop as well, but limiting to this use case to reduce risk.
            endTarget = LAST_TASK;
        }
        if (endTarget != null) {
            // We were on our way to this state when we got canceled, end there instead.
            startState = stateFromGestureEndTarget(endTarget);
        }
        container.getStateManager().goToState(startState, activityVisible);
    }

    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        if (dp.getDeviceProperties().isTablet()) {
            calculateLargeTileSize(context, dp, outRect);
        } else {
            Resources res = context.getResources();
            float maxScale = res.getFloat(R.dimen.overview_max_scale);
            int taskMargin = dp.getOverviewProfile().getTaskMarginPx();
            // In fake orientation, OverviewActions is hidden and we only leave a margin there.
            int overviewActionsClaimedSpace = orientationHandler.isLayoutNaturalToLauncher()
                    ? dp.getOverviewActionsClaimedSpace()
                    : dp.getOverviewProfile().getActionsTopMarginPx();
            calculateTaskSizeInternal(
                    context,
                    dp,
                    dp.getOverviewProfile().getTaskThumbnailTopMarginPx(),
                    overviewActionsClaimedSpace,
                    res.getDimensionPixelSize(R.dimen.overview_minimum_next_prev_size) + taskMargin,
                    maxScale,
                    Gravity.CENTER,
                    outRect,
                    orientationHandler);
        }
    }

    private void calculateLargeTileSize(Context context, DeviceProfile dp, Rect outRect) {
        Resources res = context.getResources();
        float maxScale = res.getFloat(R.dimen.overview_max_scale);
        Rect gridRect = new Rect();
        calculateGridSize(dp, context, gridRect);
        calculateTaskSizeInternal(context, dp, gridRect, maxScale, Gravity.CENTER, outRect);
    }

    private void calculateTaskSizeInternal(Context context, DeviceProfile dp, int claimedSpaceAbove,
            int claimedSpaceBelow, int minimumHorizontalPadding, float maxScale, int gravity,
            Rect outRect, RecentsPagedOrientationHandler orientationHandler) {
        Rect potentialTaskRect = new Rect(0, 0, dp.getDeviceProperties().getWidthPx(), dp.getDeviceProperties().getHeightPx());

        Rect insets;
        if (orientationHandler.isLayoutNaturalToLauncher()) {
            insets = dp.getInsets();
        } else {
            Rect portraitInsets = dp.getInsets();
            DisplayController displayController = DisplayController.INSTANCE.get(context);
            @Nullable List<WindowBounds> windowBounds =
                    displayController.getInfo().getCurrentBounds();
            Rect deviceRotationInsets = windowBounds != null
                    ? windowBounds.get(orientationHandler.getRotation()).insets
                    : new Rect();
            // Obtain the landscape/seascape insets, and rotate it to portrait perspective.
            orientationHandler.rotateInsets(deviceRotationInsets, outRect);
            // Then combine with portrait's insets to leave space for status bar/nav bar in
            // either orientations.
            outRect.set(
                    Math.max(outRect.left, portraitInsets.left),
                    Math.max(outRect.top, portraitInsets.top),
                    Math.max(outRect.right, portraitInsets.right),
                    Math.max(outRect.bottom, portraitInsets.bottom)
            );
            insets = outRect;
        }
//        potentialTaskRect.inset(insets);

        outRect.set(
                minimumHorizontalPadding,
                claimedSpaceAbove,
                minimumHorizontalPadding,
                claimedSpaceBelow);
        // Rotate the paddings to portrait perspective,
        orientationHandler.rotateInsets(outRect, outRect);
//        potentialTaskRect.inset(outRect);

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
        out.x = dp.getDeviceProperties().getWidthPx();
        out.y = dp.getDeviceProperties().getHeightPx();
        if (dp.getDeviceProperties().isTablet() && !DisplayController.isTransientTaskbar(context)) {
            out.y -= dp.getTaskbarProfile().getHeight();
        }
    }

    /**
     * Calculates the overview grid size for the provided device configuration.
     */
    public final void calculateGridSize(DeviceProfile dp, Context context, Rect outRect) {
        Rect insets = dp.getInsets();
        int topMargin = dp.getOverviewProfile().getTaskThumbnailTopMarginPx();
        int bottomMargin = dp.getOverviewActionsClaimedSpace();
        int sideMargin = dp.getOverviewProfile().getGridSideMargin();

        outRect.set(0, 0, dp.getDeviceProperties().getWidthPx(), dp.getDeviceProperties().getHeightPx());
        if (Utilities.ATLEAST_S) {
            outRect.inset(Math.max(insets.left, sideMargin), insets.top + topMargin,
                Math.max(insets.right, sideMargin), Math.max(insets.bottom, bottomMargin));
        } else {
            outRect.set(
                Math.max(insets.left, sideMargin),
                insets.top + topMargin,
                outRect.width() - Math.max(insets.right, sideMargin),
                outRect.height() - Math.max(insets.bottom, bottomMargin)
            );
        }
    }

    /**
     * Calculates the overview grid non-focused task size for the provided device configuration.
     */
    public final void calculateGridTaskSize(Context context, DeviceProfile dp, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        Resources res = context.getResources();
        Rect potentialTaskRect = new Rect();
        calculateLargeTileSize(context, dp, potentialTaskRect);

        float rowHeight = (potentialTaskRect.height()
                + dp.getOverviewProfile().getTaskThumbnailTopMarginPx()
                - dp.getOverviewProfile().getRowSpacing()) / 2f;

        PointF taskDimension = getTaskDimension(context, dp);
        float scale = (rowHeight - dp.getOverviewProfile().getTaskThumbnailTopMarginPx())
                / taskDimension.y;
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        int gravity = Gravity.TOP;
        gravity |= orientationHandler.getRecentsRtlSetting(res) ? Gravity.RIGHT : Gravity.LEFT;
        Gravity.apply(gravity, outWidth, outHeight, potentialTaskRect, outRect);
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public final void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect,
            RecentsPagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        boolean isGridOnlyOverview = dp.getDeviceProperties().isTablet() && enableGridOnlyOverview();
        int claimedSpaceBelow = isGridOnlyOverview
                ? dp.getOverviewProfile().getActionsTopMarginPx()
                + dp.getOverviewProfile().getActionsHeight()
                    + dp.getTaskbarProfile().getStashedTaskbarHeight()
                : (dp.getDeviceProperties().getHeightPx() - outRect.bottom - dp.getInsets().bottom);
        int minimumHorizontalPadding = 0;
        if (!isGridOnlyOverview) {
            float maxScale = context.getResources().getFloat(R.dimen.overview_modal_max_scale);
            minimumHorizontalPadding =
                    Math.round((dp.getDeviceProperties().getAvailableWidthPx() - outRect.width() * maxScale) / 2);
        }
        calculateTaskSizeInternal(
                context,
                dp,
                dp.getOverviewProfile().getTaskMarginPx(),
                claimedSpaceBelow,
                minimumHorizontalPadding,
                1f /*maxScale*/,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                outRect,
                orientationHandler);
    }

    protected void onInitBackgroundStateUI() {
        if (mOnInitBackgroundStateUICallback != null) {
            mOnInitBackgroundStateUICallback.run();
            mOnInitBackgroundStateUICallback = null;
        }
    }
}
