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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.util.MathUtils.lerp;
import static android.view.Display.DEFAULT_DISPLAY;

import android.animation.RectEvaluator;
import android.animation.TypeEvaluator;
import android.annotation.NonNull;
import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
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
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.unfold.UnfoldAnimationController;
import com.android.wm.shell.unfold.UnfoldBackgroundController;

/**
 * This helper class contains logic that calculates scaling and cropping parameters
 * for the folding/unfolding animation. As an input it receives TaskInfo objects and
 * surfaces leashes and as an output it could fill surface transactions with required
 * transformations.
 *
 * This class is used by
 * {@link com.android.wm.shell.unfold.UnfoldTransitionHandler} and
 * {@link UnfoldAnimationController}. They use independent
 * instances of FullscreenUnfoldTaskAnimator.
 */
public class FullscreenUnfoldTaskAnimator implements UnfoldTaskAnimator,
        DisplayInsetsController.OnInsetsChangedListener, ConfigurationChangeListener {

    private static final float[] FLOAT_9 = new float[9];
    private static final TypeEvaluator<Rect> RECT_EVALUATOR = new RectEvaluator(new Rect());

    private static final float HORIZONTAL_START_MARGIN = 0.08f;
    private static final float VERTICAL_START_MARGIN = 0.03f;
    private static final float END_SCALE = 1f;
    private static final float START_SCALE = END_SCALE - VERTICAL_START_MARGIN * 2;

    private final SparseArray<AnimationContext> mAnimationContextByTaskId = new SparseArray<>();
    private final DisplayInsetsController mDisplayInsetsController;
    private final UnfoldBackgroundController mBackgroundController;
    private final Context mContext;
    private final ShellController mShellController;

    private InsetsSource mExpandedTaskbarInsetsSource;
    private float mWindowCornerRadiusPx;

    public FullscreenUnfoldTaskAnimator(Context context,
            @NonNull UnfoldBackgroundController backgroundController,
            ShellController shellController, DisplayInsetsController displayInsetsController) {
        mContext = context;
        mDisplayInsetsController = displayInsetsController;
        mBackgroundController = backgroundController;
        mShellController = shellController;
        mWindowCornerRadiusPx = ScreenDecorationsUtils.getWindowCornerRadius(context);
    }

    public void init() {
        mDisplayInsetsController.addInsetsChangedListener(DEFAULT_DISPLAY, this);
        mShellController.addConfigurationChangeListener(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
        Trace.beginSection("FullscreenUnfoldTaskAnimator#onConfigurationChanged");
        mWindowCornerRadiusPx = ScreenDecorationsUtils.getWindowCornerRadius(mContext);
        Trace.endSection();
    }

    @Override
    public void insetsChanged(InsetsState insetsState) {
        mExpandedTaskbarInsetsSource = getExpandedTaskbarSource(insetsState);
        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            AnimationContext context = mAnimationContextByTaskId.valueAt(i);
            context.update(mExpandedTaskbarInsetsSource, context.mTaskInfo);
        }
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

    public boolean hasActiveTasks() {
        return mAnimationContextByTaskId.size() > 0;
    }

    @Override
    public void onTaskAppeared(TaskInfo taskInfo, SurfaceControl leash) {
        AnimationContext animationContext = new AnimationContext(
                leash, mExpandedTaskbarInsetsSource, taskInfo);
        mAnimationContextByTaskId.put(taskInfo.taskId, animationContext);
    }

    @Override
    public void onTaskChanged(TaskInfo taskInfo) {
        AnimationContext animationContext = mAnimationContextByTaskId.get(taskInfo.taskId);
        if (animationContext != null) {
            animationContext.update(mExpandedTaskbarInsetsSource, taskInfo);
        }
    }

    @Override
    public void onTaskVanished(TaskInfo taskInfo) {
        mAnimationContextByTaskId.remove(taskInfo.taskId);
    }

    @Override
    public void clearTasks() {
        mAnimationContextByTaskId.clear();
    }

    @Override
    public void resetSurface(TaskInfo taskInfo, Transaction transaction) {
        final AnimationContext context = mAnimationContextByTaskId.get(taskInfo.taskId);
        if (context != null) {
            resetSurface(context, transaction);
        }
    }

    @Override
    public void applyAnimationProgress(float progress, Transaction transaction) {
        if (mAnimationContextByTaskId.size() == 0) return;

        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            final AnimationContext context = mAnimationContextByTaskId.valueAt(i);

            context.mCurrentCropRect.set(RECT_EVALUATOR
                    .evaluate(progress, context.mStartCropRect, context.mEndCropRect));

            float scale = lerp(START_SCALE, END_SCALE, progress);
            context.mMatrix.setScale(scale, scale, context.mCurrentCropRect.exactCenterX(),
                    context.mCurrentCropRect.exactCenterY());

            transaction.setWindowCrop(context.mLeash, context.mCurrentCropRect)
                    .setMatrix(context.mLeash, context.mMatrix, FLOAT_9)
                    .setCornerRadius(context.mLeash, mWindowCornerRadiusPx)
                    .show(context.mLeash);
        }
    }

    @Override
    public void prepareStartTransaction(Transaction transaction) {
        mBackgroundController.ensureBackground(transaction);
    }

    @Override
    public void prepareFinishTransaction(Transaction transaction) {
        mBackgroundController.removeBackground(transaction);
    }

    @Override
    public boolean isApplicableTask(TaskInfo taskInfo) {
        return taskInfo != null && taskInfo.isVisible()
                && taskInfo.realActivity != null // to filter out parents created by organizer
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN
                && taskInfo.getActivityType() != ACTIVITY_TYPE_HOME;
    }

    @Override
    public void resetAllSurfaces(Transaction transaction) {
        for (int i = mAnimationContextByTaskId.size() - 1; i >= 0; i--) {
            final AnimationContext context = mAnimationContextByTaskId.valueAt(i);
            resetSurface(context, transaction);
        }
    }

    private void resetSurface(AnimationContext context, Transaction transaction) {
        transaction
                .setWindowCrop(context.mLeash, null)
                .setCornerRadius(context.mLeash, 0.0F)
                .setMatrix(context.mLeash, 1.0F, 0.0F, 0.0F, 1.0F)
                .setPosition(context.mLeash,
                        (float) context.mTaskInfo.positionInParent.x,
                        (float) context.mTaskInfo.positionInParent.y);
    }

    private class AnimationContext {
        final SurfaceControl mLeash;
        final Rect mStartCropRect = new Rect();
        final Rect mEndCropRect = new Rect();
        final Rect mCurrentCropRect = new Rect();
        final Matrix mMatrix = new Matrix();

        TaskInfo mTaskInfo;

        private AnimationContext(SurfaceControl leash, InsetsSource taskBarInsetsSource,
                TaskInfo taskInfo) {
            mLeash = leash;
            update(taskBarInsetsSource, taskInfo);
        }

        private void update(InsetsSource taskBarInsetsSource, TaskInfo taskInfo) {
            mTaskInfo = taskInfo;
            mStartCropRect.set(mTaskInfo.getConfiguration().windowConfiguration.getBounds());

            if (taskBarInsetsSource != null) {
                mStartCropRect.inset(taskBarInsetsSource.calculateVisibleInsets(mStartCropRect));
            }

            mEndCropRect.set(mStartCropRect);

            int horizontalMargin = (int) (mEndCropRect.width() * HORIZONTAL_START_MARGIN);
            mStartCropRect.left = mEndCropRect.left + horizontalMargin;
            mStartCropRect.right = mEndCropRect.right - horizontalMargin;
            int verticalMargin = (int) (mEndCropRect.height() * VERTICAL_START_MARGIN);
            mStartCropRect.top = mEndCropRect.top + verticalMargin;
            mStartCropRect.bottom = mEndCropRect.bottom - verticalMargin;
        }
    }
}
