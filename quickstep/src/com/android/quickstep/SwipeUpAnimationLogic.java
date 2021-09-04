/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;

import static com.android.launcher3.anim.Interpolators.ACCEL_1_5;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_SELECT;
import static com.android.launcher3.config.FeatureFlags.PROTOTYPE_APP_CLOSE;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.AppCloseConfig;
import com.android.quickstep.util.LauncherSplitScreenListener;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RectFSpringAnim2;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

import java.util.Arrays;
import java.util.function.Consumer;

public abstract class SwipeUpAnimationLogic implements
        RecentsAnimationCallbacks.RecentsAnimationListener{

    protected static final Rect TEMP_RECT = new Rect();

    protected DeviceProfile mDp;

    protected final Context mContext;
    protected final RecentsAnimationDeviceState mDeviceState;
    protected final GestureState mGestureState;

    protected final RemoteTargetHandle[] mRemoteTargetHandles;
    protected SplitConfigurationOptions.StagedSplitBounds mStagedSplitBounds;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::updateFinalShift);

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;

    protected boolean mIsSwipeForStagedSplit;

    public SwipeUpAnimationLogic(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState, TransformParams transformParams) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;

        mIsSwipeForStagedSplit = ENABLE_SPLIT_SELECT.get() &&
                LauncherSplitScreenListener.INSTANCE.getNoCreate()
                        .getRunningSplitTaskIds().length > 1;

        TaskViewSimulator primaryTVS = new TaskViewSimulator(context,
                gestureState.getActivityInterface());
        primaryTVS.getOrientationState().update(
                mDeviceState.getRotationTouchHelper().getCurrentActiveRotation(),
                mDeviceState.getRotationTouchHelper().getDisplayRotation());
        mRemoteTargetHandles = new RemoteTargetHandle[mIsSwipeForStagedSplit ? 2 : 1];
        mRemoteTargetHandles[0] = new RemoteTargetHandle(primaryTVS, transformParams);

        if (mIsSwipeForStagedSplit) {
            TaskViewSimulator secondaryTVS = new TaskViewSimulator(context,
                    gestureState.getActivityInterface());
            secondaryTVS.getOrientationState().update(
                    mDeviceState.getRotationTouchHelper().getCurrentActiveRotation(),
                    mDeviceState.getRotationTouchHelper().getDisplayRotation());
            mRemoteTargetHandles[1] = new RemoteTargetHandle(secondaryTVS, new TransformParams());
        }
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;
        mTransitionDragLength = mGestureState.getActivityInterface().getSwipeUpDestinationAndLength(
                dp, mContext, TEMP_RECT, mRemoteTargetHandles[0].mTaskViewSimulator
                        .getOrientationState().getOrientationHandler());
        mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;

        for (RemoteTargetHandle remoteHandle : mRemoteTargetHandles) {
            PendingAnimation pendingAnimation = new PendingAnimation(mTransitionDragLength * 2);
            TaskViewSimulator taskViewSimulator = remoteHandle.mTaskViewSimulator;
            taskViewSimulator.setDp(dp);
            taskViewSimulator.addAppToOverviewAnim(pendingAnimation, LINEAR);
            AnimatorPlaybackController playbackController =
                    pendingAnimation.createPlaybackController();

            remoteHandle.mPlaybackController = AnimatorControllerWithResistance.createForRecents(
                    playbackController, mContext, taskViewSimulator.getOrientationState(),
                    mDp, taskViewSimulator.recentsViewScale, AnimatedFloat.VALUE,
                    taskViewSimulator.recentsViewSecondaryTranslation, AnimatedFloat.VALUE
            );
        }
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        float shift;
        if (displacement > mTransitionDragLength * mDragLengthFactor && mTransitionDragLength > 0) {
            shift = mDragLengthFactor;
        } else {
            float translation = Math.max(displacement, 0);
            shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
        }

        mCurrentShift.updateValue(shift);
    }

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    public abstract void updateFinalShift();

    protected PagedOrientationHandler getOrientationHandler() {
        // OrientationHandler should be independent of remote target, can directly take one
        return mRemoteTargetHandles[0].mTaskViewSimulator
                .getOrientationState().getOrientationHandler();
    }

    protected abstract class HomeAnimationFactory {
        protected float mSwipeVelocity;

        public @NonNull RectF getWindowTargetRect() {
            PagedOrientationHandler orientationHandler = getOrientationHandler();
            DeviceProfile dp = mDp;
            final int halfIconSize = dp.iconSizePx / 2;
            float primaryDimension = orientationHandler
                    .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
            float secondaryDimension = orientationHandler
                    .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
            final float targetX =  primaryDimension / 2f;
            final float targetY = secondaryDimension - dp.hotseatBarSizePx;
            // Fallback to animate to center of screen.
            return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                    targetX + halfIconSize, targetY + halfIconSize);
        }

        /** Returns the corner radius of the window at the end of the animation. */
        public float getEndRadius(RectF cropRectF) {
            return cropRectF.width() / 2f;
        }

        public abstract @NonNull AnimatorPlaybackController createActivityAnimationToHome();

        public void setSwipeVelocity(float velocity) {
            mSwipeVelocity = velocity;
        }

        public void playAtomicAnimation(float velocity) {
            // No-op
        }

        public boolean shouldPlayAtomicWorkspaceReveal() {
            return true;
        }

        public void setAnimation(RectFSpringAnim anim) { }

        public boolean keepWindowOpaque() { return false; }

        public void update(@Nullable AppCloseConfig config, RectF currentRect, float progress,
                float radius) { }

        public void onCancel() { }

        /**
         * @return {@code true} if this factory supports animating an Activity to PiP window on
         * swiping up to home.
         */
        public boolean supportSwipePipToHome() {
            return false;
        }

        /**
         * @param progress The progress of the animation to the home screen.
         * @return The current alpha to set on the animating app window.
         */
        protected float getWindowAlpha(float progress) {
            // Alpha interpolates between [1, 0] between progress values [start, end]
            final float start = 0f;
            final float end = 0.85f;

            if (progress <= start) {
                return 1f;
            }
            if (progress >= end) {
                return 0f;
            }
            return Utilities.mapToRange(progress, start, end, 1, 0, ACCEL_1_5);
        }
    }

    /**
     * Update with start progress for window animation to home.
     * @param outMatrix {@link Matrix} to map a rect in Launcher space to window space.
     * @param startProgress The progress of {@link #mCurrentShift} to start thw window from.
     * @return {@link RectF} represents the bounds as starting point in window space.
     */
    protected RectF[] updateProgressForStartRect(Matrix outMatrix, float startProgress) {
        mCurrentShift.updateValue(startProgress);
        RectF[] startRects = new RectF[mRemoteTargetHandles.length];
        for (int i = 0, mRemoteTargetHandlesLength = mRemoteTargetHandles.length;
                i < mRemoteTargetHandlesLength; i++) {
            RemoteTargetHandle remoteHandle = mRemoteTargetHandles[i];
            TaskViewSimulator tvs = remoteHandle.mTaskViewSimulator;
            tvs.apply(remoteHandle.mTransformParams.setProgress(startProgress));

            startRects[i] = new RectF(tvs.getCurrentCropRect());
            tvs.applyWindowToHomeRotation(outMatrix);
            tvs.getCurrentMatrix().mapRect(startRects[i]);
        }
        return startRects;
    }

    /** Helper to avoid writing some for-loops to iterate over {@link #mRemoteTargetHandles} */
    protected void runActionOnRemoteHandles(Consumer<RemoteTargetHandle> consumer) {
        for (RemoteTargetHandle handle : mRemoteTargetHandles) {
            consumer.accept(handle);
        }
    }

    /** @return only the TaskViewSimulators from {@link #mRemoteTargetHandles} */
    protected TaskViewSimulator[] getRemoteTaskViewSimulators() {
        return Arrays.stream(mRemoteTargetHandles)
                .map(remoteTargetHandle -> remoteTargetHandle.mTaskViewSimulator)
                .toArray(TaskViewSimulator[]::new);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        ActiveGestureLog.INSTANCE.addLog("startRecentsAnimationCallback", targets.apps.length);
        RemoteAnimationTargetCompat dividerTarget = targets.getNonAppTargetOfType(
                TYPE_DOCK_DIVIDER);
        RemoteAnimationTargetCompat primaryTaskTarget;
        RemoteAnimationTargetCompat secondaryTaskTarget;

        // TODO(b/197568823) Determine if we need to exclude assistant as one of the targets we
        //  animate
        if (!mIsSwipeForStagedSplit) {
            primaryTaskTarget = targets.findTask(mGestureState.getRunningTaskId());
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(targets);

            if (primaryTaskTarget != null) {
                mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(primaryTaskTarget);
            }
        } else {
            int[] taskIds = LauncherSplitScreenListener.INSTANCE.getNoCreate()
                    .getRunningSplitTaskIds();
            // We're in staged split
            primaryTaskTarget = targets.findTask(taskIds[0]);
            secondaryTaskTarget = targets.findTask(taskIds[1]);
            mStagedSplitBounds = new SplitConfigurationOptions.StagedSplitBounds(
                    primaryTaskTarget.screenSpaceBounds,
                    secondaryTaskTarget.screenSpaceBounds, dividerTarget.screenSpaceBounds);
            mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(primaryTaskTarget,
                    mStagedSplitBounds);
            mRemoteTargetHandles[1].mTaskViewSimulator.setPreview(secondaryTaskTarget,
                    mStagedSplitBounds);
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(primaryTaskTarget));
            mRemoteTargetHandles[1].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(secondaryTaskTarget));
        }
    }

    private RemoteAnimationTargets createRemoteAnimationTargetsForTarget(
            RemoteAnimationTargetCompat target) {
        return new RemoteAnimationTargets(new RemoteAnimationTargetCompat[]{target},
                null, null, MODE_CLOSING);
    }
    /**
     * Creates an animation that transforms the current app window into the home app.
     * @param startProgress The progress of {@link #mCurrentShift} to start the window from.
     * @param homeAnimationFactory The home animation factory.
     */
    protected RectFSpringAnim[] createWindowAnimationToHome(float startProgress,
            HomeAnimationFactory homeAnimationFactory) {
        // TODO(b/195473584) compute separate end targets for different staged split
        final RectF targetRect = homeAnimationFactory.getWindowTargetRect();
        RectFSpringAnim[] out = new RectFSpringAnim[mRemoteTargetHandles.length];
        Matrix homeToWindowPositionMap = new Matrix();
        RectF[] startRects = updateProgressForStartRect(homeToWindowPositionMap, startProgress);
        for (int i = 0, mRemoteTargetHandlesLength = mRemoteTargetHandles.length;
                i < mRemoteTargetHandlesLength; i++) {
            RemoteTargetHandle remoteHandle = mRemoteTargetHandles[i];
            out[i] = getWindowAnimationToHomeInternal(homeAnimationFactory,
                    targetRect, remoteHandle.mTransformParams, remoteHandle.mTaskViewSimulator,
                    startRects[i], homeToWindowPositionMap);
        }
        return out;
    }

    private RectFSpringAnim getWindowAnimationToHomeInternal(
            HomeAnimationFactory homeAnimationFactory, RectF targetRect,
            TransformParams transformParams, TaskViewSimulator taskViewSimulator,
            RectF startRect, Matrix homeToWindowPositionMap) {
        RectF cropRectF = new RectF(taskViewSimulator.getCurrentCropRect());
        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        Matrix windowToHomePositionMap = new Matrix();
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);

        RectFSpringAnim anim;
        if (PROTOTYPE_APP_CLOSE.get()) {
            anim = new RectFSpringAnim2(startRect, targetRect, mContext,
                    taskViewSimulator.getCurrentCornerRadius(),
                    homeAnimationFactory.getEndRadius(cropRectF));
        } else {
            anim = new RectFSpringAnim(startRect, targetRect, mContext);
        }
        homeAnimationFactory.setAnimation(anim);

        SpringAnimationRunner runner = new SpringAnimationRunner(
                homeAnimationFactory, cropRectF, homeToWindowPositionMap,
                transformParams, taskViewSimulator);
        anim.addAnimatorListener(runner);
        anim.addOnUpdateListener(runner);
        return anim;
    }

    protected class SpringAnimationRunner extends AnimationSuccessListener
            implements RectFSpringAnim.OnUpdateListener, BuilderProxy {

        final Rect mCropRect = new Rect();
        final Matrix mMatrix = new Matrix();

        final RectF mWindowCurrentRect = new RectF();
        final Matrix mHomeToWindowPositionMap;
        private final TransformParams mLocalTransformParams;
        final HomeAnimationFactory mAnimationFactory;

        final AnimatorPlaybackController mHomeAnim;
        final RectF mCropRectF;

        final float mStartRadius;
        final float mEndRadius;

        SpringAnimationRunner(HomeAnimationFactory factory, RectF cropRectF,
                Matrix homeToWindowPositionMap, TransformParams transformParams,
                TaskViewSimulator taskViewSimulator) {
            mAnimationFactory = factory;
            mHomeAnim = factory.createActivityAnimationToHome();
            mCropRectF = cropRectF;
            mHomeToWindowPositionMap = homeToWindowPositionMap;
            mLocalTransformParams = transformParams;

            cropRectF.roundOut(mCropRect);

            // End on a "round-enough" radius so that the shape reveal doesn't have to do too much
            // rounding at the end of the animation.
            mStartRadius = taskViewSimulator.getCurrentCornerRadius();
            mEndRadius = factory.getEndRadius(cropRectF);
        }

        @Override
        public void onUpdate(@Nullable AppCloseConfig config, RectF currentRect, float progress) {
            mHomeAnim.setPlayFraction(progress);
            mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, currentRect);

            mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
            float cornerRadius = Utilities.mapRange(progress, mStartRadius, mEndRadius);
            float alpha = mAnimationFactory.getWindowAlpha(progress);
            if (config != null && PROTOTYPE_APP_CLOSE.get()) {
                alpha = config.getWindowAlpha();
                cornerRadius = config.getCornerRadius();
            }
            if (mAnimationFactory.keepWindowOpaque()) {
                alpha = 1f;
            }
            mLocalTransformParams
                    .setTargetAlpha(alpha)
                    .setCornerRadius(cornerRadius);
            mLocalTransformParams.applySurfaceParams(mLocalTransformParams
                    .createSurfaceParams(this));
            mAnimationFactory.update(config, currentRect, progress,
                    mMatrix.mapRadius(cornerRadius));
        }

        @Override
        public void onBuildTargetParams(
                Builder builder, RemoteAnimationTargetCompat app, TransformParams params) {
            builder.withMatrix(mMatrix)
                    .withWindowCrop(mCropRect)
                    .withCornerRadius(params.getCornerRadius());
        }

        @Override
        public void onCancel() {
            mAnimationFactory.onCancel();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mHomeAnim.dispatchOnStart();
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            mHomeAnim.getAnimationPlayer().end();
        }
    }

    /**
     * Container to keep together all the associated objects whose properties need to be updated to
     * animate a single remote app target
     */
    public static class RemoteTargetHandle {
        public TaskViewSimulator mTaskViewSimulator;
        public TransformParams mTransformParams;
        public AnimatorControllerWithResistance mPlaybackController;
        public RemoteTargetHandle(TaskViewSimulator taskViewSimulator,
                TransformParams transformParams) {
            mTransformParams = transformParams;
            mTaskViewSimulator = taskViewSimulator;
        }
    }

    public interface RunningWindowAnim {
        void end();

        void cancel();

        static RunningWindowAnim wrap(Animator animator) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    animator.end();
                }

                @Override
                public void cancel() {
                    animator.cancel();
                }
            };
        }

        static RunningWindowAnim wrap(RectFSpringAnim rectFSpringAnim) {
            return new RunningWindowAnim() {
                @Override
                public void end() {
                    rectFSpringAnim.end();
                }

                @Override
                public void cancel() {
                    rectFSpringAnim.cancel();
                }
            };
        }
    }
}
