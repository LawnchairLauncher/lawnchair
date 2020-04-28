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
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.RECENTS_CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.QuickstepAppTransitionManagerImpl.ALL_APPS_PROGRESS_OFF_SCREEN;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.StateListener;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.LayoutUtils;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.RecentsExtraCard;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<Launcher> implements StateListener {

    private static final Rect sTempRect = new Rect();

    private final TransformParams mTransformParams = new TransformParams();

    private RecentsExtraCard mRecentsExtraCardPlugin;
    private RecentsExtraViewContainer mRecentsExtraViewContainer;
    private PluginListener<RecentsExtraCard> mRecentsExtraCardPluginListener =
            new PluginListener<RecentsExtraCard>() {
        @Override
        public void onPluginConnected(RecentsExtraCard recentsExtraCard, Context context) {
            createRecentsExtraCard();
            mRecentsExtraCardPlugin = recentsExtraCard;
            mRecentsExtraCardPlugin.setupView(context, mRecentsExtraViewContainer, mActivity);
        }

        @Override
        public void onPluginDisconnected(RecentsExtraCard plugin) {
            removeView(mRecentsExtraViewContainer);
            mRecentsExtraCardPlugin = null;
            mRecentsExtraViewContainer = null;
        }
    };

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setContentAlpha(0);
        mActivity.getStateManager().addStateListener(this);
    }

    @Override
    public void startHome() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            LauncherState state = mActivity.getStateManager().getState();
            if (state == OVERVIEW || state == ALL_APPS) {
                redrawLiveTile(false);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    protected void onTaskStackUpdated() {
        // Lazily update the empty message only when the task stack is reapplied
        updateEmptyMessage();
    }

    /**
     * Animates adjacent tasks and translate hotseat off screen as well.
     */
    @Override
    public AnimatorSet createAdjacentPageAnimForTaskLaunch(TaskView tv,
            ClipAnimationHelper helper) {
        AnimatorSet anim = super.createAdjacentPageAnimForTaskLaunch(tv, helper);

        if (!SysUINavigationMode.getMode(mActivity).hasGestures) {
            // Hotseat doesn't move when opening recents with the button,
            // so don't animate it here either.
            return anim;
        }

        float allAppsProgressOffscreen = ALL_APPS_PROGRESS_OFF_SCREEN;
        LauncherState state = mActivity.getStateManager().getState();
        if ((state.getVisibleElements(mActivity) & ALL_APPS_HEADER_EXTRA) != 0) {
            float maxShiftRange = mActivity.getDeviceProfile().heightPx;
            float currShiftRange = mActivity.getAllAppsController().getShiftRange();
            allAppsProgressOffscreen = 1f + (maxShiftRange - currShiftRange) / maxShiftRange;
        }
        anim.play(ObjectAnimator.ofFloat(
                mActivity.getAllAppsController(), ALL_APPS_PROGRESS, allAppsProgressOffscreen));

        ObjectAnimator dragHandleAnim = ObjectAnimator.ofInt(
                mActivity.findViewById(R.id.scrim_view), ScrimView.DRAG_HANDLE_ALPHA, 0);
        dragHandleAnim.setInterpolator(Interpolators.ACCEL_2);
        anim.play(dragHandleAnim);

        return anim;
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(getContext(), dp, outRect);
    }

    /**
     * @return The translationX to apply to this view so that the first task is just offscreen.
     */
    public float getOffscreenTranslationX(float recentsScale) {
        float offscreenX = NORMAL.getOverviewScaleAndTranslation(mActivity).translationX;
        // Offset since scale pushes tasks outwards.
        getTaskSize(sTempRect);
        int taskWidth = sTempRect.width();
        offscreenX += taskWidth * (recentsScale - 1) / 2;
        if (mRunningTaskTileHidden) {
            // The first task is hidden, so offset by its width.
            offscreenX -= (taskWidth + getPageSpacing()) * recentsScale;
        }
        if (isRtl()) {
            offscreenX = -offscreenX;
        }
        return offscreenX;
    }

    @Override
    protected void onTaskLaunchAnimationUpdate(float progress, TaskView tv) {
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            if (mRecentsAnimationWrapper.targetSet != null && tv.isRunningTask()) {
                mTransformParams.setProgress(1 - progress)
                        .setSyncTransactionApplier(mSyncTransactionApplier)
                        .setForLiveTile(true);
                mClipAnimationHelper.applyTransform(mRecentsAnimationWrapper.targetSet,
                        mTransformParams);
            } else {
                redrawLiveTile(true);
            }
        }
    }

    @Override
    protected void onTaskLaunched(boolean success) {
        if (success) {
            mActivity.getStateManager().goToState(NORMAL, false /* animate */);
        } else {
            LauncherState state = mActivity.getStateManager().getState();
            mActivity.getAllAppsController().setState(state);
        }
        super.onTaskLaunched(success);
    }

    @Override
    public boolean shouldUseMultiWindowTaskSizeStrategy() {
        return mActivity.isInMultiWindowMode();
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(x, y);
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && mEnableDrawingLiveTile) {
            redrawLiveTile(true);
        }
    }

    @Override
    public void redrawLiveTile(boolean mightNeedToRefill) {
        if (!mEnableDrawingLiveTile || mRecentsAnimationWrapper == null
                || mClipAnimationHelper == null) {
            return;
        }
        TaskView taskView = getRunningTaskView();
        if (taskView != null) {
            taskView.getThumbnail().getGlobalVisibleRect(mTempRect);
            int offsetX = (int) (mTaskWidth * taskView.getScaleX() * getScaleX()
                    - mTempRect.width());
            int offsetY = (int) (mTaskHeight * taskView.getScaleY() * getScaleY()
                    - mTempRect.height());
            if (((mCurrentPage != 0) || mightNeedToRefill) && offsetX > 0) {
                if (mTempRect.left - offsetX < 0) {
                    mTempRect.left -= offsetX;
                } else {
                    mTempRect.right += offsetX;
                }
            }
            if (mightNeedToRefill && offsetY > 0) {
                mTempRect.top -= offsetY;
            }
            mTempRectF.set(mTempRect);
            mTransformParams.setProgress(1f)
                    .setCurrentRectAndTargetAlpha(mTempRectF, taskView.getAlpha())
                    .setSyncTransactionApplier(mSyncTransactionApplier);
            if (mRecentsAnimationWrapper.targetSet != null) {
                mClipAnimationHelper.applyTransform(mRecentsAnimationWrapper.targetSet,
                        mTransformParams);
            }
        }
    }

    @Override
    public void reset() {
        super.reset();

        // We are moving to home or some other UI with no recents. Switch back to the home client,
        // the home predictions should have been updated when the activity was resumed.
        PredictionUiStateManager.INSTANCE.get(getContext()).switchClient(Client.HOME);
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) {
        setOverviewStateEnabled(toState.overviewUi);
        setFreezeViewVisibility(true);
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (finalState == NORMAL || finalState == SPRING_LOADED) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        setOverlayEnabled(finalState == OVERVIEW);
        setFreezeViewVisibility(false);
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        super.setOverviewStateEnabled(enabled);
        if (enabled) {
            LauncherState state = mActivity.getStateManager().getState();
            boolean hasClearAllButton = (state.getVisibleElements(mActivity)
                    & RECENTS_CLEAR_ALL_BUTTON) != 0;
            setDisallowScrollToClearAll(!hasClearAllButton);
        }
    }

    @Override
    protected boolean shouldStealTouchFromSiblingsBelow(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // Allow touches to go through to the hotseat.
            Hotseat hotseat = mActivity.getHotseat();
            boolean touchingHotseat = hotseat.isShown()
                    && mActivity.getDragLayer().isEventOverView(hotseat, ev, this);
            return !touchingHotseat;
        }
        return super.shouldStealTouchFromSiblingsBelow(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext())
                .addPluginListener(mRecentsExtraCardPluginListener, RecentsExtraCard.class);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(
                mRecentsExtraCardPluginListener);
    }

    @Override
    protected int computeMinScrollX() {
        if (canComputeScrollX() && !mIsRtl) {
            return computeScrollX();
        }
        return super.computeMinScrollX();
    }

    @Override
    protected int computeMaxScrollX() {
        if (canComputeScrollX() && mIsRtl) {
            return computeScrollX();
        }
        return super.computeMaxScrollX();
    }

    private boolean canComputeScrollX() {
        return mRecentsExtraCardPlugin != null && getTaskViewCount() > 0
                && !mDisallowScrollToClearAll;
    }

    private int computeScrollX() {
        int scrollIndex = getTaskViewStartIndex() - 1;
        while (scrollIndex >= 0 && getChildAt(scrollIndex) instanceof RecentsExtraViewContainer
                && ((RecentsExtraViewContainer) getChildAt(scrollIndex)).isScrollable()) {
            scrollIndex--;
        }
        return getScrollForPage(scrollIndex + 1);
    }

    private void createRecentsExtraCard() {
        mRecentsExtraViewContainer = new RecentsExtraViewContainer(getContext());
        FrameLayout.LayoutParams helpCardParams =
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
        mRecentsExtraViewContainer.setLayoutParams(helpCardParams);
        mRecentsExtraViewContainer.setScrollable(true);
        addView(mRecentsExtraViewContainer, 0);
    }

    @Override
    public void resetTaskVisuals() {
        super.resetTaskVisuals();
        if (mRecentsExtraViewContainer != null) {
            mRecentsExtraViewContainer.setAlpha(mContentAlpha);
        }
    }
}
