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
import static com.android.launcher3.QuickstepAppTransitionManagerImpl.ALL_APPS_PROGRESS_OFF_SCREEN;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.config.FeatureFlags.ENABLE_HINTS_IN_OVERVIEW;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.OverviewInteractionState;
import com.android.quickstep.hints.ChipsContainer;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;
import com.android.quickstep.util.LayoutUtils;

/**
 * {@link RecentsView} used in Launcher activity
 */
@TargetApi(Build.VERSION_CODES.O)
public class LauncherRecentsView extends RecentsView<Launcher> {

    public static final FloatProperty<LauncherRecentsView> TRANSLATION_Y_FACTOR =
            new FloatProperty<LauncherRecentsView>("translationYFactor") {

                @Override
                public void setValue(LauncherRecentsView view, float v) {
                    view.setTranslationYFactor(v);
                }

                @Override
                public Float get(LauncherRecentsView view) {
                    return view.mTranslationYFactor;
                }
            };

    /**
     * A ratio representing the view's relative placement within its padded space. For example, 0
     * is top aligned and 0.5 is centered vertically.
     */
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTranslationYFactor;

    private final TransformParams mTransformParams = new TransformParams();
    private ChipsContainer mChipsContainer;

    public LauncherRecentsView(Context context) {
        this(context, null);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setContentAlpha(0);
    }

    @Override
    public void startHome() {
        mActivity.getStateManager().goToState(NORMAL);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setTranslationYFactor(mTranslationYFactor);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mChipsContainer = mActivity.findViewById(R.id.hints);
        BaseDragLayer.LayoutParams params = (BaseDragLayer.LayoutParams) mChipsContainer.getLayoutParams();
        params.bottomMargin = mActivity.getDeviceProfile().chipHintBottomMarginPx;
    }

    public void setTranslationYFactor(float translationFactor) {
        mTranslationYFactor = translationFactor;
        setTranslationY(computeTranslationYForFactor(mTranslationYFactor));
        if (ENABLE_QUICKSTEP_LIVE_TILE.get()) {
            LauncherState state = mActivity.getStateManager().getState();
            if (state == OVERVIEW || state == ALL_APPS) {
                redrawLiveTile(false);
            }
        }
    }

    public float computeTranslationYForFactor(float translationYFactor) {
        return translationYFactor * (getPaddingBottom() - getPaddingTop());
    }

    public void setHintVisibility(float v) {
        if (mChipsContainer != null && ENABLE_HINTS_IN_OVERVIEW.get()) {
            mChipsContainer.setHintVisibility(v);
        }
    }

    public ChipsContainer getChipsContainer() {
        return mChipsContainer;
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

        if (!OverviewInteractionState.INSTANCE.get(mActivity).isSwipeUpGestureEnabled()) {
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
    public PendingAnimation createTaskLauncherAnimation(TaskView tv, long duration) {
        PendingAnimation anim = super.createTaskLauncherAnimation(tv, duration);

        if (ENABLE_HINTS_IN_OVERVIEW.get()) {
            anim.anim.play(ObjectAnimator.ofFloat(
                    mChipsContainer, ChipsContainer.HINT_VISIBILITY, 0));
        }

        return anim;
    }

    @Override
    public PendingAnimation createTaskDismissAnimation(TaskView taskView, boolean animateTaskView,
            boolean shouldRemoveTask, long duration) {
        PendingAnimation anim = super.createTaskDismissAnimation(taskView, animateTaskView,
                shouldRemoveTask, duration);

        if (ENABLE_HINTS_IN_OVERVIEW.get()) {
            anim.anim.play(ObjectAnimator.ofFloat(
                    mChipsContainer, ChipsContainer.HINT_VISIBILITY, 0));
            anim.addEndListener(onEndListener -> {
                if (!onEndListener.isSuccess) {
                    mChipsContainer.setHintVisibility(1);
                }
            });
        }

        return anim;
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(getContext(), dp, outRect);
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
}
