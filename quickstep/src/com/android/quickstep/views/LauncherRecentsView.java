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

import static com.android.launcher3.LauncherState.CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.LauncherState.SPRING_LOADED;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.widget.FrameLayout;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.LauncherActivityInterface;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.RecentsExtraCard;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<BaseQuickstepLauncher, LauncherState>
        implements StateListener<LauncherState> {

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
        super(context, attrs, defStyleAttr, LauncherActivityInterface.INSTANCE);
        mActivity.getStateManager().addStateListener(this);
    }

    @Override
    public void init(OverviewActionsView actionsView, SplitPlaceholderView splitPlaceholderView) {
        super.init(actionsView, splitPlaceholderView);
        setContentAlpha(0);
    }

    @Override
    public void startHome() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    protected void onTaskLaunchAnimationEnd(boolean success) {
        if (success) {
            mActivity.getStateManager().moveToRestState();
        } else {
            LauncherState state = mActivity.getStateManager().getState();
            mActivity.getAllAppsController().setState(state);
        }
        super.onTaskLaunchAnimationEnd(success);
    }

    @Override
    public void reset() {
        super.reset();

        setLayoutRotation(Surface.ROTATION_0, Surface.ROTATION_0);
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) {
        setOverviewStateEnabled(toState.overviewUi);
        setOverviewGridEnabled(toState.displayOverviewTasksAsGrid(mActivity.getDeviceProfile()));
        setOverviewFullscreenEnabled(toState.getOverviewFullscreenProgress() == 1);
        setFreezeViewVisibility(true);
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        if (finalState == NORMAL || finalState == SPRING_LOADED) {
            // Clean-up logic that occurs when recents is no longer in use/visible.
            reset();
        }
        setOverlayEnabled(finalState == OVERVIEW || finalState == OVERVIEW_MODAL_TASK);
        setFreezeViewVisibility(false);
    }

    @Override
    public void setOverviewStateEnabled(boolean enabled) {
        super.setOverviewStateEnabled(enabled);
        if (enabled) {
            LauncherState state = mActivity.getStateManager().getState();
            boolean hasClearAllButton = (state.getVisibleElements(mActivity)
                    & CLEAR_ALL_BUTTON) != 0;
            setDisallowScrollToClearAll(!hasClearAllButton);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        // Do not let touch escape to siblings below this view.
        return result || mActivity.getStateManager().getState().overviewUi;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).addPluginListener(
                mRecentsExtraCardPluginListener, RecentsExtraCard.class);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        PluginManagerWrapper.INSTANCE.get(getContext()).removePluginListener(
                mRecentsExtraCardPluginListener);
    }

    @Override
    protected int computeMinScroll() {
        if (canComputeScrollX() && !mIsRtl) {
            return computeScrollX();
        }
        return super.computeMinScroll();
    }

    @Override
    protected int computeMaxScroll() {
        if (canComputeScrollX() && mIsRtl) {
            return computeScrollX();
        }
        return super.computeMaxScroll();
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
    public boolean hasRecentsExtraCard() {
        return mRecentsExtraViewContainer != null;
    }

    @Override
    public void setContentAlpha(float alpha) {
        super.setContentAlpha(alpha);
        if (mRecentsExtraViewContainer != null) {
            mRecentsExtraViewContainer.setAlpha(alpha);
        }
    }

    @Override
    protected DepthController getDepthController() {
        return mActivity.getDepthController();
    }

    @Override
    public void setModalStateEnabled(boolean isModalState) {
        super.setModalStateEnabled(isModalState);
        if (isModalState) {
            mActivity.getStateManager().goToState(LauncherState.OVERVIEW_MODAL_TASK);
        } else {
            if (mActivity.isInState(LauncherState.OVERVIEW_MODAL_TASK)) {
                mActivity.getStateManager().goToState(LauncherState.OVERVIEW);
            }
        }
    }

    @Override
    protected void onDismissAnimationEnds() {
        super.onDismissAnimationEnds();
        if (mActivity.isInState(OVERVIEW_SPLIT_SELECT)) {
            // We want to keep the tasks translations in this temporary state
            // after resetting the rest above
            setTaskViewsPrimarySplitTranslation(mTaskViewsPrimarySplitTranslation);
            setTaskViewsSecondarySplitTranslation(mTaskViewsSecondarySplitTranslation);
        }
    }

    @Override
    public void initiateSplitSelect(TaskView taskView,
            SplitConfigurationOptions.SplitPositionOption splitPositionOption) {
        super.initiateSplitSelect(taskView, splitPositionOption);
        mActivity.getStateManager().goToState(LauncherState.OVERVIEW_SPLIT_SELECT);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // If overview is in modal state when rotate, reset it to overview state without running
        // animation.
        if (mActivity.isInState(OVERVIEW_MODAL_TASK)) {
            mActivity.getStateManager().goToState(LauncherState.OVERVIEW, false);
            resetModalVisuals();
        }
    }
}
