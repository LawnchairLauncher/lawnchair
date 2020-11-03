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

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.BaseSwipeUpHandlerV2.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.SysUINavigationMode.getMode;
import static com.android.quickstep.SysUINavigationMode.hideShelfInTwoButtonLandscape;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.quickstep.util.RecentsAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
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

    public final boolean rotationSupportedByActivity;

    private final STATE_TYPE mOverviewState, mBackgroundState;

    protected BaseActivityInterface(boolean rotationSupportedByActivity,
            STATE_TYPE overviewState, STATE_TYPE backgroundState) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
        mOverviewState = overviewState;
        mBackgroundState = backgroundState;
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
            DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler);

    public void onSwipeUpToRecentsComplete() {
        // Re apply state in case we did something funky during the transition.
        ACTIVITY_TYPE activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.getStateManager().reapplyState();
    }

    public abstract void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState);

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
        return deviceState.isInDeferredGestureRegion(ev);
    }

    public abstract void onExitOverview(RotationTouchHelper deviceState,
            Runnable exitRunnable);

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

    /**
     * Calculates the taskView size for the provided device configuration
     */
    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect,
            PagedOrientationHandler orientedState) {
        calculateTaskSize(context, dp, getExtraSpace(context, dp, orientedState),
                outRect, orientedState);
    }

    protected abstract float getExtraSpace(Context context, DeviceProfile dp,
            PagedOrientationHandler orientedState);

    private void calculateTaskSize(
            Context context, DeviceProfile dp, float extraVerticalSpace, Rect outRect,
            PagedOrientationHandler orientationHandler) {
        Resources res = context.getResources();
        final boolean showLargeTaskSize = showOverviewActions(context) ||
                hideShelfInTwoButtonLandscape(context, orientationHandler);

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
    public final void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect) {
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
    public final float getOverviewActionsHeight(Context context) {
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

    class DefaultAnimationFactory implements AnimationFactory {

        protected final ACTIVITY_TYPE mActivity;
        private final STATE_TYPE mStartState;
        private final Consumer<AnimatorControllerWithResistance> mCallback;

        private boolean mIsAttachedToWindow;

        DefaultAnimationFactory(Consumer<AnimatorControllerWithResistance> callback) {
            mCallback = callback;

            mActivity = getCreatedActivity();
            mStartState = mActivity.getStateManager().getState();
        }

        protected ACTIVITY_TYPE initUI() {
            STATE_TYPE resetState = mStartState;
            if (mStartState.shouldDisableRestore()) {
                resetState = mActivity.getStateManager().getRestState();
            }
            mActivity.getStateManager().setRestState(resetState);
            mActivity.getStateManager().goToState(mBackgroundState, false);
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
                    controller.getInterpolatedProgress() > 0.5 ? mOverviewState : mBackgroundState,
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
            if (SysUINavigationMode.getMode(mActivity) == Mode.NO_BUTTON) {
                setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
            }
        }

        @Override
        public void onTransitionCancelled() {
            mActivity.getStateManager().goToState(mStartState, false /* animate */);
        }

        @Override
        public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
            if (mIsAttachedToWindow == attached && animate) {
                return;
            }
            mIsAttachedToWindow = attached;
            RecentsView recentsView = mActivity.getOverviewPanel();
            Animator fadeAnim = mActivity.getStateManager()
                    .createStateElementAnimation(INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);

            float fromTranslation = attached ? 1 : 0;
            float toTranslation = attached ? 0 : 1;
            mActivity.getStateManager()
                    .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);
            if (!recentsView.isShown() && animate) {
                ADJACENT_PAGE_OFFSET.set(recentsView, fromTranslation);
            } else {
                fromTranslation = ADJACENT_PAGE_OFFSET.get(recentsView);
            }
            if (!animate) {
                ADJACENT_PAGE_OFFSET.set(recentsView, toTranslation);
            } else {
                mActivity.getStateManager().createStateElementAnimation(
                        INDEX_RECENTS_TRANSLATE_X_ANIM,
                        fromTranslation, toTranslation).start();
            }

            fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
            fadeAnim.setDuration(animate ? RECENTS_ATTACH_DURATION : 0).start();
        }

        protected void createBackgroundToOverviewAnim(ACTIVITY_TYPE activity, PendingAnimation pa) {
            //  Scale down recents from being full screen to being in overview.
            RecentsView recentsView = activity.getOverviewPanel();
            pa.addFloat(recentsView, RECENTS_SCALE_PROPERTY,
                    recentsView.getMaxScaleForFullScreen(), 1, LINEAR);
            pa.addFloat(recentsView, FULLSCREEN_PROGRESS, 1, 0, LINEAR);
        }
    }

    protected static boolean showOverviewActions(Context context) {
        return ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(context);
    }
}
