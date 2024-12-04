/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.unfold.animation;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;

import android.animation.RectEvaluator;
import android.animation.TypeEvaluator;
import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Trace;
import android.util.SparseArray;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.WindowInsets;

import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreen;
import com.android.wm.shell.splitscreen.SplitScreen.SplitScreenListener;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.unfold.UnfoldAnimationController;
import com.android.wm.shell.unfold.UnfoldBackgroundController;

import dagger.Lazy;

import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * This helper class contains logic that calculates scaling and cropping parameters
 * for the folding/unfolding animation. As an input it receives TaskInfo objects and
 * surfaces leashes and as an output it could fill surface transactions with required
 * transformations.
 *
 * This class is used by
 * {@link com.android.wm.shell.unfold.UnfoldTransitionHandler} and
 * {@link UnfoldAnimationController}.
 * They use independent instances of SplitTaskUnfoldAnimator.
 */
public class SplitTaskUnfoldAnimator implements UnfoldTaskAnimator,
        DisplayInsetsController.OnInsetsChangedListener, SplitScreenListener,
        ConfigurationChangeListener {

    private static final TypeEvaluator<Rect> RECT_EVALUATOR = new RectEvaluator(new Rect());
    private static final float CROPPING_START_MARGIN_FRACTION = 0.05f;

    private final Context mContext;
    private final Executor mExecutor;
    private final DisplayInsetsController mDisplayInsetsController;
    private final SparseArray<AnimationContext> mAnimationContextByTaskId = new SparseArray<>();
    private final ShellController mShellController;
    private final Lazy<Optional<SplitScreenController>> mSplitScreenController;
    private final UnfoldBackgroundController mUnfoldBackgroundController;

    private final Rect mMainStageBounds = new Rect();
    private final Rect mSideStageBounds = new Rect();
    private final Rect mRootStageBounds = new Rect();

    private float mWindowCornerRadiusPx;
    private InsetsSource mExpandedTaskbarInsetsSource;

    @SplitPosition
    private int mMainStagePosition = SPLIT_POSITION_UNDEFINED;
    @SplitPosition
    private int mSideStagePosition = SPLIT_POSITION_UNDEFINED;

    public SplitTaskUnfoldAnimator(Context context, Executor executor,
            Lazy<Optional<SplitScreenController>> splitScreenController,
            ShellController shellController, UnfoldBackgroundController unfoldBackgroundController,
            DisplayInsetsController displayInsetsController) {
        mDisplayInsetsController = displayInsetsController;
        mExecutor = executor;
        mContext = context;
        mShellController = shellController;
        mUnfoldBackgroundController = unfoldBackgroundController;
        mSplitScreenController = splitScreenController;
        mWindowCornerRadiusPx = ScreenDecorationsUtils.getWindowCornerRadius(context);
    }

    /** Initializes the animator, this should be called only once */
    @Override
    public void init() {
        mDisplayInsetsController.addInsetsChangedListener(DEFAULT_DISPLAY, this);
        mShellController.addConfigurationChangeListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
        Trace.beginSection("SplitTaskUnfoldAnimator#onConfigurationChanged");
        mWindowCornerRadiusPx = ScreenDecorationsUtils.getWindowCornerRadius(mContext);
        Trace.endSection();
    }

    /**
     * Starts listening for split-screen changes and gets initial split-screen
     * layout information through the listener
     */
    @Override
    public void start() {
        mSplitScreenController.get().get().asSplitScreen()
                .registerSplitScreenListener(this, mExecutor);
    }

    /**
     * Stops listening for the split-screen layout changes
     */
    @Override
    public void stop() {
        mSplitScreenController.get().get().asSplitScreen()
                .unregisterSplitScreenListener(this);
    }

    @Override
    public void insetsChanged(InsetsState insetsState) {
        mExpandedTaskbarInsetsSource = getExpandedTaskbarSource(insetsState);
        updateContexts();
    }

    private static InsetsSource getExpandedTaskbarSource(InsetsState state) {
        for (int i = state.sourceSize() - 1; i >= 0; i--) {
            final InsetsSource source = state.sourceAt(i);
            if (source.getType() == WindowInsets.Type.navigationBars()
                    && source.hasFlags(InsetsSource.FLAG_INSETS_ROUNDED_CORNER)) {
                return source;
            }
        }
        return null;
    }

    @Override
    public void onTaskStageChanged(int taskId, int stage, boolean visible) {
        final AnimationContext context = mAnimationContextByTaskId.get(taskId);
        if (context != null) {
            context.mStageType = stage;
            context.update();
        }
    }

    @Override
    public void onStagePositionChanged(int stage, int position) {
        if (stage == STAGE_TYPE_MAIN) {
            mMainStagePosition = position;
        } else {
            mSideStagePosition = position;
        }
        updateContexts();
    }

    @Override
    public void onSplitBoundsChanged(Rect rootBounds, Rect mainBounds, Rect sideBounds) {
        mRootStageBounds.set(rootBounds);
        mMainStageBounds.set(mainBounds);
        mSideStageBounds.set(sideBounds);
        updateContexts();
    }

    private void updateContexts() {
        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            AnimationContext context = mAnimationContextByTaskId.valueAt(i);
            context.update();
        }
    }

    /**
     * Register a split task in the animator
     * @param taskInfo info of the task
     * @param leash the surface of the task
     */
    @Override
    public void onTaskAppeared(TaskInfo taskInfo, SurfaceControl leash) {
        AnimationContext context = new AnimationContext(leash);
        mAnimationContextByTaskId.put(taskInfo.taskId, context);
    }

    /**
     * Unregister the task from the unfold animation
     * @param taskInfo info of the task
     */
    @Override
    public void onTaskVanished(TaskInfo taskInfo) {
        mAnimationContextByTaskId.remove(taskInfo.taskId);
    }

    @Override
    public boolean isApplicableTask(TaskInfo taskInfo) {
        return taskInfo.hasParentTask()
                && taskInfo.isRunning
                && taskInfo.realActivity != null // to filter out parents created by organizer
                && taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW;
    }

    /**
     * Clear all registered tasks
     */
    @Override
    public void clearTasks() {
        mAnimationContextByTaskId.clear();
    }

    /**
     * Reset transformations of the task that could have been applied by the animator
     * @param taskInfo task to reset
     * @param transaction a transaction to write the changes to
     */
    @Override
    public void resetSurface(TaskInfo taskInfo, Transaction transaction) {
        AnimationContext context = mAnimationContextByTaskId.get(taskInfo.taskId);
        if (context != null) {
            resetSurface(transaction, context);
        }
    }

    /**
     * Reset all surface transformation that could have been introduced by the animator
     * @param transaction to write changes to
     */
    @Override
    public void resetAllSurfaces(Transaction transaction) {
        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            final AnimationContext context = mAnimationContextByTaskId.valueAt(i);
            resetSurface(transaction, context);
        }
    }

    @Override
    public void applyAnimationProgress(float progress, Transaction transaction) {
        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            AnimationContext context = mAnimationContextByTaskId.valueAt(i);
            if (context.mStageType == STAGE_TYPE_UNDEFINED) {
                continue;
            }

            context.mCurrentCropRect.set(RECT_EVALUATOR
                    .evaluate(progress, context.mStartCropRect, context.mEndCropRect));

            transaction.setWindowCrop(context.mLeash, context.mCurrentCropRect)
                    .setCornerRadius(context.mLeash, mWindowCornerRadiusPx);
        }
    }

    @Override
    public void prepareStartTransaction(Transaction transaction) {
        mUnfoldBackgroundController.ensureBackground(transaction);
    }

    @Override
    public void prepareFinishTransaction(Transaction transaction) {
        mUnfoldBackgroundController.removeBackground(transaction);
    }

    /**
     * @return true if there are tasks to animate
     */
    @Override
    public boolean hasActiveTasks() {
        return mAnimationContextByTaskId.size() > 0;
    }

    private void resetSurface(SurfaceControl.Transaction transaction, AnimationContext context) {
        transaction
                .setWindowCrop(context.mLeash, null)
                .setCornerRadius(context.mLeash, 0.0F);
    }

    @Override
    public void onSplitVisibilityChanged(boolean visible) {
        mUnfoldBackgroundController.onSplitVisibilityChanged(visible);
    }

    private class AnimationContext {
        final SurfaceControl mLeash;

        final Rect mStartCropRect = new Rect();
        final Rect mEndCropRect = new Rect();
        final Rect mCurrentCropRect = new Rect();

        @SplitScreen.StageType
        int mStageType = STAGE_TYPE_UNDEFINED;

        private AnimationContext(SurfaceControl leash) {
            mLeash = leash;
            update();
        }

        private void update() {
            final Rect stageBounds = mStageType == STAGE_TYPE_MAIN
                    ? mMainStageBounds : mSideStageBounds;

            mStartCropRect.set(stageBounds);

            boolean taskbarExpanded = isTaskbarExpanded();
            if (taskbarExpanded) {
                // Only insets the cropping window with taskbar when taskbar is expanded
                mStartCropRect.inset(mExpandedTaskbarInsetsSource.calculateVisibleInsets(
                        mStartCropRect));
            }

            // Offset to surface coordinates as layout bounds are in screen coordinates
            mStartCropRect.offsetTo(0, 0);

            mEndCropRect.set(mStartCropRect);

            int maxSize = Math.max(mEndCropRect.width(), mEndCropRect.height());
            int margin = (int) (maxSize * CROPPING_START_MARGIN_FRACTION);

            // Sides adjacent to split bar or task bar are not be animated.
            Insets margins;
            final boolean isLandscape = mRootStageBounds.width() > mRootStageBounds.height();
            if (isLandscape) { // Left and right splits.
                margins = getLandscapeMargins(margin, taskbarExpanded);
            } else { // Top and bottom splits.
                margins = getPortraitMargins(margin, taskbarExpanded);
            }
            mStartCropRect.inset(margins);
        }

        private Insets getLandscapeMargins(int margin, boolean taskbarExpanded) {
            int left = margin;
            int right = margin;
            int bottom = taskbarExpanded ? 0 : margin; // Taskbar margin.
            final int splitPosition = mStageType == STAGE_TYPE_MAIN
                    ? mMainStagePosition : mSideStagePosition;
            if (splitPosition == SPLIT_POSITION_TOP_OR_LEFT) {
                right = 0; // Divider margin.
            } else {
                left = 0; // Divider margin.
            }
            return Insets.of(left, /* top= */ margin, right, bottom);
        }

        private Insets getPortraitMargins(int margin, boolean taskbarExpanded) {
            int bottom = margin;
            int top = margin;
            final int splitPosition = mStageType == STAGE_TYPE_MAIN
                    ? mMainStagePosition : mSideStagePosition;
            if (splitPosition == SPLIT_POSITION_TOP_OR_LEFT) {
                bottom = 0; // Divider margin.
            } else { // Bottom split.
                top = 0; // Divider margin.
                if (taskbarExpanded) {
                    bottom = 0; // Taskbar margin.
                }
            }
            return Insets.of(/* left= */ margin, top, /* right= */ margin, bottom);
        }

        private boolean isTaskbarExpanded() {
            return mExpandedTaskbarInsetsSource != null;
        }
    }
}
