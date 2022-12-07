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

import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.AbsSwipeUpHandler.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.GestureState.GestureEndTarget.RECENTS;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class which abstracts out the logical differences between Launcher and RecentsActivity.
 */
@TargetApi(Build.VERSION_CODES.P)
public abstract class BaseActivityInterface<STATE_TYPE extends BaseState<STATE_TYPE>,
        ACTIVITY_TYPE extends StatefulActivity<STATE_TYPE>> {

    public final boolean rotationSupportedByActivity;

    private final STATE_TYPE mBackgroundState;

    private STATE_TYPE mTargetState;

    @Nullable private Runnable mOnInitBackgroundStateUICallback = null;

    protected BaseActivityInterface(boolean rotationSupportedByActivity,
            STATE_TYPE overviewState, STATE_TYPE backgroundState) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
        mTargetState = overviewState;
        mBackgroundState = backgroundState;
    }

    /**
     * Called when the current gesture transition is cancelled.
     * @param activityVisible Whether the user can see the changes we make here, so try to animate.
     * @param endTarget If the gesture ended before we got cancelled, where we were headed.
     */
    public void onTransitionCancelled(boolean activityVisible,
            @Nullable GestureState.GestureEndTarget endTarget) {
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        STATE_TYPE startState = activity.getStateManager().getRestState();
        if (endTarget != null) {
            // We were on our way to this state when we got canceled, end there instead.
            startState = stateFromGestureEndTarget(endTarget);
        }
        activity.getStateManager().goToState(startState, activityVisible);
    }

    public abstract int getSwipeUpDestinationAndLength(
            DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler);

    /** Called when the animation to home has fully settled. */
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {}

    public abstract void onAssistantVisibilityChanged(float visibility);

    public abstract AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback);

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

    @Nullable
    public DesktopVisibilityController getDesktopVisibilityController() {
        return null;
    }

    @Nullable
    public abstract TaskbarUIController getTaskbarController();

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
            Rect homeBounds, RemoteAnimationTarget target);

    public abstract boolean allowMinimizeSplitScreen();

    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        return deviceState.isInDeferredGestureRegion(ev) || deviceState.isImeRenderingNavButtons();
    }

    /**
     * @return Whether the gesture in progress should be cancelled.
     */
    public boolean shouldCancelCurrentGesture() {
        return false;
    }

    public abstract void onExitOverview(RotationTouchHelper deviceState,
            Runnable exitRunnable);

    public abstract boolean isInLiveTileMode();

    public abstract void onLaunchTaskFailed();

    /**
     * Closes any overlays.
     */
    public void closeOverlay() {
        Optional.ofNullable(getTaskbarController()).ifPresent(
                TaskbarUIController::hideOverlayWindow);
    }

    public void switchRunningTaskViewToScreenshot(HashMap<Integer, ThumbnailData> thumbnailDatas,
            Runnable runnable) {
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
        recentsView.switchToScreenshot(thumbnailDatas, runnable);
    }

    /**
     * Calculates the taskView size for the provided device configuration.
     */
    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        Resources res = context.getResources();
        float maxScale = res.getFloat(R.dimen.overview_max_scale);
        if (dp.isTablet) {
            Rect gridRect = new Rect();
            calculateGridSize(dp, gridRect);

            calculateTaskSizeInternal(context, dp, gridRect, maxScale, Gravity.CENTER, outRect);
        } else {
            int taskMargin = dp.overviewTaskMarginPx;
            calculateTaskSizeInternal(context, dp,
                    dp.overviewTaskThumbnailTopMarginPx,
                    dp.getOverviewActionsClaimedSpace(),
                    res.getDimensionPixelSize(R.dimen.overview_minimum_next_prev_size) + taskMargin,
                    maxScale,
                    Gravity.CENTER,
                    outRect);
        }
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
            Rect potentialTaskRect, float maxScale, int gravity, Rect outRect) {
        PointF taskDimension = getTaskDimension(dp);

        float scale = Math.min(
                potentialTaskRect.width() / taskDimension.x,
                potentialTaskRect.height() / taskDimension.y);
        scale = Math.min(scale, maxScale);
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        Gravity.apply(gravity, outWidth, outHeight, potentialTaskRect, outRect);
    }

    private static PointF getTaskDimension(DeviceProfile dp) {
        PointF dimension = new PointF();
        getTaskDimension(dp, dimension);
        return dimension;
    }

    /**
     * Gets the dimension of the task in the current system state.
     */
    public static void getTaskDimension(DeviceProfile dp, PointF out) {
        out.x = dp.widthPx;
        out.y = dp.heightPx;
        if (dp.isTablet) {
            out.y -= dp.taskbarSize;
        }
    }

    /**
     * Calculates the overview grid size for the provided device configuration.
     */
    public final void calculateGridSize(DeviceProfile dp, Rect outRect) {
        Rect insets = dp.getInsets();
        int topMargin = dp.overviewTaskThumbnailTopMarginPx;
        int bottomMargin = dp.getOverviewActionsClaimedSpace();
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
        Rect taskRect = new Rect();
        calculateTaskSize(context, dp, taskRect);

        float rowHeight =
                (taskRect.height() + dp.overviewTaskThumbnailTopMarginPx - dp.overviewRowSpacing)
                        / 2f;

        PointF taskDimension = getTaskDimension(dp);
        float scale = (rowHeight - dp.overviewTaskThumbnailTopMarginPx) / taskDimension.y;
        int outWidth = Math.round(scale * taskDimension.x);
        int outHeight = Math.round(scale * taskDimension.y);

        int gravity = Gravity.TOP;
        gravity |= orientedState.getRecentsRtlSetting(res) ? Gravity.RIGHT : Gravity.LEFT;
        Gravity.apply(gravity, outWidth, outHeight, taskRect, outRect);
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public final void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        calculateTaskSize(context, dp, outRect);
        float maxScale = context.getResources().getFloat(R.dimen.overview_modal_max_scale);
        calculateTaskSizeInternal(
                context, dp,
                dp.overviewTaskMarginPx,
                dp.heightPx - outRect.bottom - dp.getInsets().bottom,
                Math.round((dp.availableWidthPx - outRect.width() * maxScale) / 2),
                1f /*maxScale*/,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
                outRect);
    }

    /**
     * Called when the gesture ends and the animation starts towards the given target. Used to add
     * an optional additional animation with the same duration.
     */
    public @Nullable Animator getParallelAnimationToLauncher(
            GestureState.GestureEndTarget endTarget, long duration,
            RecentsAnimationCallbacks callbacks) {
        if (endTarget == RECENTS) {
            ACTIVITY_TYPE activity = getCreatedActivity();
            if (activity == null) {
                return null;
            }
            STATE_TYPE state = stateFromGestureEndTarget(endTarget);
            ScrimView scrimView = activity.getScrimView();
            ObjectAnimator anim = ObjectAnimator.ofArgb(scrimView, VIEW_BACKGROUND_COLOR,
                    getOverviewScrimColorForState(activity, state));
            anim.setDuration(duration);
            return anim;
        }
        return null;
    }

    /**
     * Returns the color of the scrim behind overview when at rest in this state.
     * Return {@link Color#TRANSPARENT} for no scrim.
     */
    protected abstract int getOverviewScrimColorForState(ACTIVITY_TYPE activity, STATE_TYPE state);

    /**
     * Returns the expected STATE_TYPE from the provided GestureEndTarget.
     */
    public abstract STATE_TYPE stateFromGestureEndTarget(GestureState.GestureEndTarget endTarget);

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

    protected void runOnInitBackgroundStateUI(Runnable callback) {
        mOnInitBackgroundStateUICallback = callback;
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity != null && activity.getStateManager().getState() == mBackgroundState) {
            onInitBackgroundStateUI();
        }
    }

    private void onInitBackgroundStateUI() {
        if (mOnInitBackgroundStateUICallback != null) {
            mOnInitBackgroundStateUICallback.run();
            mOnInitBackgroundStateUICallback = null;
        }
    }

    public interface AnimationFactory {

        void createActivityInterface(long transitionLength);

        /**
         * @param attached Whether to show RecentsView alongside the app window. If false, recents
         *                 will be hidden by some property we can animate, e.g. alpha.
         * @param animate Whether to animate recents to/from its new attached state.
         */
        default void setRecentsAttachedToAppWindow(boolean attached, boolean animate) { }

        default boolean isRecentsAttachedToAppWindow() {
            return false;
        }

        default boolean hasRecentsEverAttachedToAppWindow() {
            return false;
        }

        /** Called when the gesture ends and we know what state it is going towards */
        default void setEndTarget(GestureState.GestureEndTarget endTarget) { }
    }

    class DefaultAnimationFactory implements AnimationFactory {

        protected final ACTIVITY_TYPE mActivity;
        private final STATE_TYPE mStartState;
        private final Consumer<AnimatorControllerWithResistance> mCallback;

        private boolean mIsAttachedToWindow;
        private boolean mHasEverAttachedToWindow;

        DefaultAnimationFactory(Consumer<AnimatorControllerWithResistance> callback) {
            mCallback = callback;

            mActivity = getCreatedActivity();
            mStartState = mActivity.getStateManager().getState();
        }

        protected ACTIVITY_TYPE initBackgroundStateUI() {
            STATE_TYPE resetState = mStartState;
            if (mStartState.shouldDisableRestore()) {
                resetState = mActivity.getStateManager().getRestState();
            }
            mActivity.getStateManager().setRestState(resetState);
            mActivity.getStateManager().goToState(mBackgroundState, false);
            onInitBackgroundStateUI();
            return mActivity;
        }

        @Override
        public void createActivityInterface(long transitionLength) {
            PendingAnimation pa = new PendingAnimation(transitionLength * 2);
            createBackgroundToOverviewAnim(mActivity, pa);
            AnimatorPlaybackController controller = pa.createPlaybackController();
            mActivity.getStateManager().setCurrentUserControlledAnimation(controller);

            // Since we are changing the start position of the UI, reapply the state, at the end
            controller.setEndAction(() -> mActivity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? mTargetState : mBackgroundState,
                    false));

            RecentsView recentsView = mActivity.getOverviewPanel();
            AnimatorControllerWithResistance controllerWithResistance =
                    AnimatorControllerWithResistance.createForRecents(controller, mActivity,
                            recentsView.getPagedViewOrientedState(), mActivity.getDeviceProfile(),
                            recentsView, RECENTS_SCALE_PROPERTY, recentsView,
                            TASK_SECONDARY_TRANSLATION);
            mCallback.accept(controllerWithResistance);

            // Creating the activity controller animation sometimes reapplies the launcher state
            // (because we set the animation as the current state animation), so we reapply the
            // attached state here as well to ensure recents is shown/hidden appropriately.
            if (DisplayController.getNavigationMode(mActivity) == NavigationMode.NO_BUTTON) {
                setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
            }
        }

        @Override
        public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_FADE_ANIM);
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    mIsAttachedToWindow = attached;
                    if (attached) {
                        mHasEverAttachedToWindow = true;
                    }
                }});

            long animationDuration = animate ? RECENTS_ATTACH_DURATION : 0;
            Animator fadeAnim = mActivity.getStateManager()
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);
            fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
            fadeAnim.setDuration(animationDuration);
            animatorSet.play(fadeAnim);

            float fromTranslation = ADJACENT_PAGE_HORIZONTAL_OFFSET.get(
                    mActivity.getOverviewPanel());
            float toTranslation = attached ? 0 : 1;

            Animator translationAnimator = mActivity.getStateManager().createStateElementAnimation(
                    INDEX_RECENTS_TRANSLATE_X_ANIM, fromTranslation, toTranslation);
            translationAnimator.setDuration(animationDuration);
            animatorSet.play(translationAnimator);
            animatorSet.start();
        }

        @Override
        public boolean isRecentsAttachedToAppWindow() {
            return mIsAttachedToWindow;
        }

        @Override
        public boolean hasRecentsEverAttachedToAppWindow() {
            return mHasEverAttachedToWindow;
        }

        @Override
        public void setEndTarget(GestureState.GestureEndTarget endTarget) {
            mTargetState = stateFromGestureEndTarget(endTarget);
        }

        protected void createBackgroundToOverviewAnim(ACTIVITY_TYPE activity, PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = activity.getOverviewPanel();
            pa.addFloat(recentsView, RECENTS_SCALE_PROPERTY,
                    recentsView.getMaxScaleForFullScreen(), 1, LINEAR);
            pa.addFloat(recentsView, FULLSCREEN_PROGRESS, 1, 0, LINEAR);

            pa.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    TaskbarUIController taskbarUIController = getTaskbarController();
                    if (taskbarUIController != null) {
                        taskbarUIController.setSystemGestureInProgress(true);
                    }
                }
            });
        }
    }
}
