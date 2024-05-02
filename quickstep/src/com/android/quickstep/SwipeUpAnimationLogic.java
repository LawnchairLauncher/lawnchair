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

import static com.android.app.animation.Interpolators.ACCELERATE_1_5;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.PagedView.INVALID_PAGE;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.views.ClipIconView;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RectFSpringAnim.DefaultSpringConfig;
import com.android.quickstep.util.RectFSpringAnim.TaskbarHotseatSpringConfig;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

import java.util.Arrays;
import java.util.function.Consumer;

public abstract class SwipeUpAnimationLogic implements
        RecentsAnimationCallbacks.RecentsAnimationListener{

    protected static final Rect TEMP_RECT = new Rect();
    protected final RemoteTargetGluer mTargetGluer;

    protected DeviceProfile mDp;

    protected final Context mContext;
    protected final RecentsAnimationDeviceState mDeviceState;
    protected final GestureState mGestureState;

    protected RemoteTargetHandle[] mRemoteTargetHandles;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    protected final AnimatedFloat mCurrentShift = new AnimatedFloat(this::onCurrentShiftUpdated);
    protected float mCurrentDisplacement;

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;

    protected boolean mIsSwipeForSplit;

    public SwipeUpAnimationLogic(Context context, RecentsAnimationDeviceState deviceState,
            GestureState gestureState) {
        mContext = context;
        mDeviceState = deviceState;
        mGestureState = gestureState;
        updateIsGestureForSplit(TopTaskTracker.INSTANCE.get(context)
                .getRunningSplitTaskIds().length);

        mTargetGluer = new RemoteTargetGluer(mContext, mGestureState.getContainerInterface());
        mRemoteTargetHandles = mTargetGluer.getRemoteTargetHandles();
        runActionOnRemoteHandles(remoteTargetHandle ->
                remoteTargetHandle.getTaskViewSimulator().getOrientationState().update(
                        mDeviceState.getRotationTouchHelper().getCurrentActiveRotation(),
                        mDeviceState.getRotationTouchHelper().getDisplayRotation()
                ));
    }

    protected void initTransitionEndpoints(DeviceProfile dp) {
        mDp = dp;
        mTransitionDragLength = mGestureState.getContainerInterface()
                .getSwipeUpDestinationAndLength(dp, mContext, TEMP_RECT,
                        mRemoteTargetHandles[0].getTaskViewSimulator().getOrientationState()
                                .getOrientationHandler());
        mDragLengthFactor = (float) dp.heightPx / mTransitionDragLength;

        for (RemoteTargetHandle remoteHandle : mRemoteTargetHandles) {
            PendingAnimation pendingAnimation = new PendingAnimation(mTransitionDragLength * 2);
            TaskViewSimulator taskViewSimulator = remoteHandle.getTaskViewSimulator();
            taskViewSimulator.setDp(dp);
            taskViewSimulator.addAppToOverviewAnim(pendingAnimation, LINEAR);
            AnimatorPlaybackController playbackController =
                    pendingAnimation.createPlaybackController();

            remoteHandle.setPlaybackController(AnimatorControllerWithResistance.createForRecents(
                    playbackController, mContext, taskViewSimulator.getOrientationState(),
                    mDp, taskViewSimulator.recentsViewScale, AnimatedFloat.VALUE,
                    taskViewSimulator.recentsViewSecondaryTranslation, AnimatedFloat.VALUE
            ));
        }
    }

    @UiThread
    public void updateDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = overrideDisplacementForTransientTaskbar(-displacement);
        mCurrentDisplacement = displacement;

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
     * When Transient Taskbar is enabled, subclasses can override the displacement to keep the app
     * window at the bottom of the screen while taskbar is being swiped in.
     * @param displacement The distance the user has swiped up from the bottom of the screen. This
     *                     value will be positive unless the user swipe downwards.
     * @return the overridden displacement.
     */
    protected float overrideDisplacementForTransientTaskbar(float displacement) {
        return displacement;
    }

    /**
     * Called when the value of {@link #mCurrentShift} changes
     */
    @UiThread
    public abstract void onCurrentShiftUpdated();

    protected RecentsPagedOrientationHandler getOrientationHandler() {
        // OrientationHandler should be independent of remote target, can directly take one
        return mRemoteTargetHandles[0].getTaskViewSimulator()
                .getOrientationState().getOrientationHandler();
    }

    protected abstract class HomeAnimationFactory {
        protected float mSwipeVelocity;

        /**
         * Returns true if we know the home animation involves an item in the hotseat.
         */
        public boolean isInHotseat() {
            return false;
        }

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

        public void setAnimation(RectFSpringAnim anim) { }

        public void update(RectF currentRect, float progress, float radius, int overlayAlpha) { }

        public void onCancel() { }

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
            return Utilities.mapToRange(progress, start, end, 1, 0, ACCELERATE_1_5);
        }

        /**
         * Sets a {@link com.android.launcher3.views.ClipIconView.TaskViewArtist} that should be
         * used draw a {@link TaskView} during this home animation.
         */
        public void setTaskViewArtist(ClipIconView.TaskViewArtist taskViewArtist) { }

        public boolean isAnimationReady() {
            return true;
        }

        public boolean isAnimatingIntoIcon() {
            return false;
        }

        @Nullable
        public TaskView getTargetTaskView() {
            return null;
        }

        public boolean isRtl() {
            return Utilities.isRtl(mContext.getResources());
        }

        public boolean isPortrait() {
            return !mDp.isLandscape && !mDp.isSeascape();
        }
    }

    /**
     * Update with start progress for window animation to home.
     * @param outMatrix {@link Matrix} to map a rect in Launcher space to window space.
     * @param startProgress The progress of {@link #mCurrentShift} to start thw window from.
     * @return {@link RectF} represents the bounds as starting point in window space.
     */
    protected RectF[] updateProgressForStartRect(Matrix[] outMatrix, float startProgress) {
        mCurrentShift.updateValue(startProgress);
        RectF[] startRects = new RectF[mRemoteTargetHandles.length];
        for (int i = 0, mRemoteTargetHandlesLength = mRemoteTargetHandles.length;
                i < mRemoteTargetHandlesLength; i++) {
            RemoteTargetHandle remoteHandle = mRemoteTargetHandles[i];
            TaskViewSimulator tvs = remoteHandle.getTaskViewSimulator();
            tvs.apply(remoteHandle.getTransformParams().setProgress(startProgress));

            startRects[i] = new RectF(tvs.getCurrentCropRect());
            outMatrix[i] = new Matrix();
            tvs.applyWindowToHomeRotation(outMatrix[i]);
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
                .map(remoteTargetHandle -> remoteTargetHandle.getTaskViewSimulator())
                .toArray(TaskViewSimulator[]::new);
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
        Matrix[] homeToWindowPositionMap = new Matrix[mRemoteTargetHandles.length];
        RectF[] startRects = updateProgressForStartRect(homeToWindowPositionMap, startProgress);
        for (int i = 0, mRemoteTargetHandlesLength = mRemoteTargetHandles.length;
                i < mRemoteTargetHandlesLength; i++) {
            RemoteTargetHandle remoteHandle = mRemoteTargetHandles[i];
            out[i] = getWindowAnimationToHomeInternal(
                    homeAnimationFactory,
                    targetRect,
                    remoteHandle.getTransformParams(),
                    remoteHandle.getTaskViewSimulator(),
                    startRects[i],
                    homeToWindowPositionMap[i]);
        }
        return out;
    }

    protected void updateIsGestureForSplit(int targetCount) {
        mIsSwipeForSplit = targetCount > 1;
    }

    private RectFSpringAnim getWindowAnimationToHomeInternal(
            HomeAnimationFactory homeAnimationFactory,
            RectF targetRect,
            TransformParams transformParams,
            TaskViewSimulator taskViewSimulator,
            RectF startRect,
            Matrix homeToWindowPositionMap) {
        RectF cropRectF = new RectF(taskViewSimulator.getCurrentCropRect());
        // Move the startRect to Launcher space as floatingIconView runs in Launcher
        Matrix windowToHomePositionMap = new Matrix();

        TaskView targetTaskView = homeAnimationFactory.getTargetTaskView();
        if (targetTaskView == null) {
            // If the start rect ends up overshooting too much to the left/right offscreen, bring it
            // back to fullscreen. This can happen when the recentsScroll value isn't aligned with
            // the pageScroll value for a given taskView, see b/228829958#comment12
            mRemoteTargetHandles[0].getTaskViewSimulator()
                    .getOrientationState()
                    .getOrientationHandler()
                    .fixBoundsForHomeAnimStartRect(startRect, mDp);

        }
        homeToWindowPositionMap.invert(windowToHomePositionMap);
        windowToHomePositionMap.mapRect(startRect);
        RectF invariantStartRect = new RectF(startRect);

        if (targetTaskView != null) {
            Rect thumbnailBounds = new Rect();
            targetTaskView.getThumbnailBounds(thumbnailBounds, /* relativeToDragLayer= */ true);

            invariantStartRect = new RectF(thumbnailBounds);
            invariantStartRect.offsetTo(startRect.left, thumbnailBounds.top);
            startRect = new RectF(thumbnailBounds);
        }

        boolean useTaskbarHotseatParams = mDp.isTaskbarPresent
                && homeAnimationFactory.isInHotseat();
        RectFSpringAnim anim = new RectFSpringAnim(useTaskbarHotseatParams
                ? new TaskbarHotseatSpringConfig(mContext, startRect, targetRect)
                : new DefaultSpringConfig(mContext, mDp, startRect, targetRect));
        homeAnimationFactory.setAnimation(anim);

        SpringAnimationRunner runner = new SpringAnimationRunner(
                homeAnimationFactory,
                cropRectF,
                homeToWindowPositionMap,
                transformParams,
                taskViewSimulator,
                invariantStartRect);
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

        final RectF mRunningTaskViewStartRectF;
        @Nullable
        final TaskView mTargetTaskView;
        final float mRunningTaskViewScrollOffset;
        final float mTaskViewWidth;
        final float mTaskViewHeight;
        final boolean mIsPortrait;
        final Rect mThumbnailStartBounds = new Rect();

        // Store the mTargetTaskView view properties onAnimationStart so that we can reset them
        // when cleaning up.
        float mTaskViewAlpha;
        float mTaskViewTranslationX;
        float mTaskViewTranslationY;
        float mTaskViewScaleX;
        float mTaskViewScaleY;

        SpringAnimationRunner(
                HomeAnimationFactory factory,
                RectF cropRectF,
                Matrix homeToWindowPositionMap,
                TransformParams transformParams,
                TaskViewSimulator taskViewSimulator,
                RectF invariantStartRect) {
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

            mRunningTaskViewStartRectF = invariantStartRect;
            mTargetTaskView = factory.getTargetTaskView();
            mTaskViewWidth = mTargetTaskView == null ? 0 : mTargetTaskView.getWidth();
            mTaskViewHeight = mTargetTaskView == null ? 0 : mTargetTaskView.getHeight();
            mIsPortrait = factory.isPortrait();
            // Use the running task's start position to determine how much it needs to be offset
            // to end up offscreen.
            mRunningTaskViewScrollOffset = factory.isRtl()
                    ? (Math.min(0, -invariantStartRect.right))
                    : (Math.max(0, mDp.widthPx - invariantStartRect.left));
        }

        @Override
        public void onUpdate(RectF currentRect, float progress) {
            float cornerRadius = Utilities.mapRange(progress, mStartRadius, mEndRadius);
            float alpha = mAnimationFactory.getWindowAlpha(progress);

            mHomeAnim.setPlayFraction(progress);
            if (mTargetTaskView == null) {
                mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, currentRect);
                mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
                mLocalTransformParams
                        .setTargetAlpha(alpha)
                        .setCornerRadius(cornerRadius);
            } else {
                mHomeToWindowPositionMap.mapRect(mWindowCurrentRect, mRunningTaskViewStartRectF);
                mWindowCurrentRect.offset(mRunningTaskViewScrollOffset * progress, 0f);
                mMatrix.setRectToRect(mCropRectF, mWindowCurrentRect, ScaleToFit.FILL);
                mLocalTransformParams.setCornerRadius(mStartRadius);
            }

            mLocalTransformParams.applySurfaceParams(
                    mLocalTransformParams.createSurfaceParams(this));
            mAnimationFactory.update(
                    currentRect, progress, mMatrix.mapRadius(cornerRadius), (int) (alpha * 255));

            if (mTargetTaskView == null) {
                return;
            }
            if (mAnimationFactory.isAnimatingIntoIcon() && mAnimationFactory.isAnimationReady()) {
                mTargetTaskView.setAlpha(0f);
                return;
            }
            mTargetTaskView.setAlpha(mAnimationFactory.isAnimatingIntoIcon() ? 1f : alpha);
            float width = mThumbnailStartBounds.width();
            float height =  mThumbnailStartBounds.height();
            float scale = Math.min(currentRect.width(), currentRect.height())
                    / Math.min(width, height);

            mTargetTaskView.setScaleX(scale);
            mTargetTaskView.setScaleY(scale);
            mTargetTaskView.setTranslationX(
                    currentRect.centerX() - mThumbnailStartBounds.centerX());
            mTargetTaskView.setTranslationY(
                    currentRect.centerY() - mThumbnailStartBounds.centerY());
        }

        @Override
        public void onBuildTargetParams(SurfaceProperties builder, RemoteAnimationTarget app,
                TransformParams params) {
            builder.setMatrix(mMatrix)
                    .setWindowCrop(mCropRect)
                    .setCornerRadius(params.getCornerRadius());
        }

        @Override
        public void onCancel() {
            cleanUp();
            mAnimationFactory.onCancel();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            setUp();
            mHomeAnim.dispatchOnStart();
            if (mTargetTaskView == null) {
                return;
            }
            Rect thumbnailBounds = new Rect();
            // Use bounds relative to mTargetTaskView since it will be scaled afterwards
            mTargetTaskView.getThumbnailBounds(thumbnailBounds);
            mAnimationFactory.setTaskViewArtist(new ClipIconView.TaskViewArtist(
                    mTargetTaskView::draw,
                    0f,
                    -thumbnailBounds.top,
                    Math.min(mTaskViewHeight, mTaskViewWidth),
                    mIsPortrait));
        }

        private void setUp() {
            if (mTargetTaskView == null) {
                return;
            }
            RecentsView recentsView = mTargetTaskView.getRecentsView();
            if (recentsView != null) {
                recentsView.setOffsetMidpointIndexOverride(
                        recentsView.indexOfChild(mTargetTaskView));
            }
            mTargetTaskView.getThumbnailBounds(
                    mThumbnailStartBounds, /* relativeToDragLayer= */ true);
            mTaskViewAlpha = mTargetTaskView.getAlpha();
            if (mAnimationFactory.isAnimatingIntoIcon()) {
                return;
            }
            mTaskViewTranslationX = mTargetTaskView.getTranslationX();
            mTaskViewTranslationY = mTargetTaskView.getTranslationY();
            mTaskViewScaleX = mTargetTaskView.getScaleX();
            mTaskViewScaleY = mTargetTaskView.getScaleY();
        }

        private void cleanUp() {
            if (mTargetTaskView == null) {
                return;
            }
            RecentsView recentsView = mTargetTaskView.getRecentsView();
            if (recentsView != null) {
                recentsView.setOffsetMidpointIndexOverride(INVALID_PAGE);
            }
            mTargetTaskView.setAlpha(mTaskViewAlpha);
            if (!mAnimationFactory.isAnimatingIntoIcon()) {
                mTargetTaskView.setTranslationX(mTaskViewTranslationX);
                mTargetTaskView.setTranslationY(mTaskViewTranslationY);
                mTargetTaskView.setScaleX(mTaskViewScaleX);
                mTargetTaskView.setScaleY(mTaskViewScaleY);
                return;
            }
            mAnimationFactory.setTaskViewArtist(null);
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            cleanUp();
            mHomeAnim.getAnimationPlayer().end();
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
