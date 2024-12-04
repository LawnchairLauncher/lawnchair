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

package com.android.wm.shell.pip;

import static android.util.RotationUtils.rotateBounds;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import android.animation.AnimationHandler;
import android.animation.Animator;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.TaskInfo;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TaskSnapshot;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.graphics.SfVsyncFrameCallbackProvider;
import com.android.internal.protolog.common.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.transition.Transitions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Controller class of PiP animations (both from and to PiP mode).
 */
public class PipAnimationController {
    static final float FRACTION_START = 0f;
    static final float FRACTION_END = 1f;

    public static final int ANIM_TYPE_BOUNDS = 0;
    public static final int ANIM_TYPE_ALPHA = 1;

    @IntDef(prefix = { "ANIM_TYPE_" }, value = {
            ANIM_TYPE_BOUNDS,
            ANIM_TYPE_ALPHA
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationType {}

    /**
     * The alpha type is set for swiping to home. But the swiped task may not enter PiP. And if
     * another task enters PiP by non-swipe ways, e.g. call API in foreground or switch to 3-button
     * navigation, then the alpha type is unexpected. So use a timeout to avoid applying wrong
     * animation style to an unrelated task.
     */
    private static final int ONE_SHOT_ALPHA_ANIMATION_TIMEOUT_MS = 800;

    public static final int TRANSITION_DIRECTION_NONE = 0;
    public static final int TRANSITION_DIRECTION_SAME = 1;
    public static final int TRANSITION_DIRECTION_TO_PIP = 2;
    public static final int TRANSITION_DIRECTION_LEAVE_PIP = 3;
    public static final int TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN = 4;
    public static final int TRANSITION_DIRECTION_REMOVE_STACK = 5;
    public static final int TRANSITION_DIRECTION_SNAP_AFTER_RESIZE = 6;
    public static final int TRANSITION_DIRECTION_USER_RESIZE = 7;
    public static final int TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND = 8;

    @IntDef(prefix = { "TRANSITION_DIRECTION_" }, value = {
            TRANSITION_DIRECTION_NONE,
            TRANSITION_DIRECTION_SAME,
            TRANSITION_DIRECTION_TO_PIP,
            TRANSITION_DIRECTION_LEAVE_PIP,
            TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN,
            TRANSITION_DIRECTION_REMOVE_STACK,
            TRANSITION_DIRECTION_SNAP_AFTER_RESIZE,
            TRANSITION_DIRECTION_USER_RESIZE,
            TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransitionDirection {}

    public static boolean isInPipDirection(@TransitionDirection int direction) {
        return direction == TRANSITION_DIRECTION_TO_PIP;
    }

    public static boolean isOutPipDirection(@TransitionDirection int direction) {
        return direction == TRANSITION_DIRECTION_LEAVE_PIP
                || direction == TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN;
    }

    /** Whether the given direction represents removing PIP. */
    public static boolean isRemovePipDirection(@TransitionDirection int direction) {
        return direction == TRANSITION_DIRECTION_REMOVE_STACK;
    }

    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    private final ThreadLocal<AnimationHandler> mSfAnimationHandlerThreadLocal =
            ThreadLocal.withInitial(() -> {
                AnimationHandler handler = new AnimationHandler();
                handler.setProvider(new SfVsyncFrameCallbackProvider());
                return handler;
            });

    private PipTransitionAnimator mCurrentAnimator;
    @AnimationType
    private int mOneShotAnimationType = ANIM_TYPE_BOUNDS;
    private long mLastOneShotAlphaAnimationTime;

    public PipAnimationController(PipSurfaceTransactionHelper helper) {
        mSurfaceTransactionHelper = helper;
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    public PipTransitionAnimator getAnimator(TaskInfo taskInfo, SurfaceControl leash,
            Rect destinationBounds, float alphaStart, float alphaEnd) {
        if (mCurrentAnimator == null) {
            mCurrentAnimator = setupPipTransitionAnimator(
                    PipTransitionAnimator.ofAlpha(taskInfo, leash, destinationBounds, alphaStart,
                            alphaEnd));
        } else if (mCurrentAnimator.getAnimationType() == ANIM_TYPE_ALPHA
                && Objects.equals(destinationBounds, mCurrentAnimator.getDestinationBounds())
                && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.updateEndValue(alphaEnd);
        } else {
            mCurrentAnimator.cancel();
            mCurrentAnimator = setupPipTransitionAnimator(
                    PipTransitionAnimator.ofAlpha(taskInfo, leash, destinationBounds, alphaStart,
                            alphaEnd));
        }
        return mCurrentAnimator;
    }

    @SuppressWarnings("unchecked")
    /**
     * Construct and return an animator that animates from the {@param startBounds} to the
     * {@param endBounds} with the given {@param direction}. If {@param direction} is type
     * {@link ANIM_TYPE_BOUNDS}, then {@param sourceHintRect} will be used to animate
     * in a better, more smooth manner. If the original bound was rotated and a reset needs to
     * happen, pass in {@param startingAngle}.
     *
     * In the case where one wants to start animation during an intermediate animation (for example,
     * if the user is currently doing a pinch-resize, and upon letting go now PiP needs to animate
     * to the correct snap fraction region), then provide the base bounds, which is current PiP
     * leash bounds before transformation/any animation. This is so when we try to construct
     * the different transformation matrices for the animation, we are constructing this based off
     * the PiP original bounds, rather than the {@param startBounds}, which is post-transformed.
     *
     * If non-zero {@param rotationDelta} is given, it means that the display will be rotated by
     * leaving PiP to fullscreen, and the {@param endBounds} is the fullscreen bounds before the
     * rotation change.
     */
    @VisibleForTesting
    public PipTransitionAnimator getAnimator(TaskInfo taskInfo, SurfaceControl leash,
            Rect baseBounds, Rect startBounds, Rect endBounds, Rect sourceHintRect,
            @PipAnimationController.TransitionDirection int direction, float startingAngle,
            @Surface.Rotation int rotationDelta) {
        if (mCurrentAnimator == null) {
            mCurrentAnimator = setupPipTransitionAnimator(
                    PipTransitionAnimator.ofBounds(taskInfo, leash, startBounds, startBounds,
                            endBounds, sourceHintRect, direction, 0 /* startingAngle */,
                            rotationDelta));
        } else if (mCurrentAnimator.getAnimationType() == ANIM_TYPE_ALPHA
                && mCurrentAnimator.isRunning()) {
            // If we are still animating the fade into pip, then just move the surface and ensure
            // we update with the new destination bounds, but don't interrupt the existing animation
            // with a new bounds
            mCurrentAnimator.setDestinationBounds(endBounds);
        } else if (mCurrentAnimator.getAnimationType() == ANIM_TYPE_BOUNDS
                && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.setDestinationBounds(endBounds);
            // construct new Rect instances in case they are recycled
            mCurrentAnimator.updateEndValue(new Rect(endBounds));
        } else {
            mCurrentAnimator.cancel();
            mCurrentAnimator = setupPipTransitionAnimator(
                    PipTransitionAnimator.ofBounds(taskInfo, leash, baseBounds, startBounds,
                            endBounds, sourceHintRect, direction, startingAngle, rotationDelta));
        }
        return mCurrentAnimator;
    }

    public PipTransitionAnimator getCurrentAnimator() {
        return mCurrentAnimator;
    }

    /** Reset animator state to prevent it from being used after its lifetime. */
    public void resetAnimatorState() {
        mCurrentAnimator = null;
    }

    private PipTransitionAnimator setupPipTransitionAnimator(PipTransitionAnimator animator) {
        animator.setSurfaceTransactionHelper(mSurfaceTransactionHelper);
        animator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        animator.setFloatValues(FRACTION_START, FRACTION_END);
        animator.setAnimationHandler(mSfAnimationHandlerThreadLocal.get());
        return animator;
    }

    /**
     * Returns true if the PiP window is currently being animated.
     */
    public boolean isAnimating() {
        PipAnimationController.PipTransitionAnimator animator = getCurrentAnimator();
        if (animator != null && animator.isRunning()) {
            return true;
        }
        return false;
    }

    /**
     * Quietly cancel the animator by removing the listeners first.
     */
    static void quietCancel(@NonNull ValueAnimator animator) {
        animator.removeAllUpdateListeners();
        animator.removeAllListeners();
        animator.cancel();
    }

    /**
     * Sets the preferred enter animation type for one time. This is typically used to set the
     * animation type to {@link PipAnimationController#ANIM_TYPE_ALPHA}.
     * <p>
     * For example, gesture navigation would first fade out the PiP activity, and the transition
     * should be responsible to animate in (such as fade in) the PiP.
     */
    public void setOneShotEnterAnimationType(@AnimationType int animationType) {
        mOneShotAnimationType = animationType;
        if (animationType == ANIM_TYPE_ALPHA) {
            mLastOneShotAlphaAnimationTime = SystemClock.uptimeMillis();
        }
    }

    /** Returns the preferred animation type and consumes the one-shot type if needed. */
    @AnimationType
    public int takeOneShotEnterAnimationType() {
        final int type = mOneShotAnimationType;
        if (type == ANIM_TYPE_ALPHA) {
            // Restore to default type.
            mOneShotAnimationType = ANIM_TYPE_BOUNDS;
            if (SystemClock.uptimeMillis() - mLastOneShotAlphaAnimationTime
                    > ONE_SHOT_ALPHA_ANIMATION_TIMEOUT_MS) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Alpha animation is expired. Use bounds animation.");
                return ANIM_TYPE_BOUNDS;
            }
        }
        return type;
    }

    /**
     * Additional callback interface for PiP animation
     */
    public static class PipAnimationCallback {
        /**
         * Called when PiP animation is started.
         */
        public void onPipAnimationStart(TaskInfo taskInfo, PipTransitionAnimator animator) {}

        /**
         * Called when PiP animation is ended.
         */
        public void onPipAnimationEnd(TaskInfo taskInfo, SurfaceControl.Transaction tx,
                PipTransitionAnimator animator) {}

        /**
         * Called when PiP animation is cancelled.
         */
        public void onPipAnimationCancel(TaskInfo taskInfo, PipTransitionAnimator animator) {}
    }

    /**
     * A handler class that could register itself to apply the transaction instead of the
     * animation controller doing it. For example, the menu controller can be one such handler.
     */
    public static class PipTransactionHandler {

        /**
         * Called when the animation controller is about to apply a transaction. Allow a registered
         * handler to apply the transaction instead.
         *
         * @return true if handled by the handler, false otherwise.
         */
        public boolean handlePipTransaction(SurfaceControl leash, SurfaceControl.Transaction tx,
                Rect destinationBounds, float alpha) {
            return false;
        }
    }

    /**
     * Animator for PiP transition animation which supports both alpha and bounds animation.
     * @param <T> Type of property to animate, either alpha (float) or bounds (Rect)
     */
    public abstract static class PipTransitionAnimator<T> extends ValueAnimator implements
            ValueAnimator.AnimatorUpdateListener,
            ValueAnimator.AnimatorListener {
        private final TaskInfo mTaskInfo;
        private final SurfaceControl mLeash;
        private final @AnimationType int mAnimationType;
        private final Rect mDestinationBounds = new Rect();

        private T mBaseValue;
        protected T mCurrentValue;
        protected T mStartValue;
        private T mEndValue;
        private PipAnimationCallback mPipAnimationCallback;
        private PipTransactionHandler mPipTransactionHandler;
        private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
                mSurfaceControlTransactionFactory;
        private PipSurfaceTransactionHelper mSurfaceTransactionHelper;
        private @TransitionDirection int mTransitionDirection;
        protected PipContentOverlay mContentOverlay;
        // Flag to avoid double-end
        private boolean mHasRequestedEnd;

        private PipTransitionAnimator(TaskInfo taskInfo, SurfaceControl leash,
                @AnimationType int animationType,
                Rect destinationBounds, T baseValue, T startValue, T endValue) {
            mTaskInfo = taskInfo;
            mLeash = leash;
            mAnimationType = animationType;
            mDestinationBounds.set(destinationBounds);
            mBaseValue = baseValue;
            mStartValue = startValue;
            mEndValue = endValue;
            addListener(this);
            addUpdateListener(this);
            mSurfaceControlTransactionFactory =
                    new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mCurrentValue = mStartValue;
            onStartTransaction(mLeash, mSurfaceControlTransactionFactory.getTransaction());
            if (mPipAnimationCallback != null) {
                mPipAnimationCallback.onPipAnimationStart(mTaskInfo, this);
            }
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (mHasRequestedEnd) return;
            applySurfaceControlTransaction(mLeash,
                    mSurfaceControlTransactionFactory.getTransaction(),
                    animation.getAnimatedFraction());
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mHasRequestedEnd) return;
            mHasRequestedEnd = true;
            mCurrentValue = mEndValue;
            final SurfaceControl.Transaction tx =
                    mSurfaceControlTransactionFactory.getTransaction();
            onEndTransaction(mLeash, tx, mTransitionDirection);
            if (mPipAnimationCallback != null) {
                mPipAnimationCallback.onPipAnimationEnd(mTaskInfo, tx, this);
            }
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            if (mPipAnimationCallback != null) {
                mPipAnimationCallback.onPipAnimationCancel(mTaskInfo, this);
            }
            mTransitionDirection = TRANSITION_DIRECTION_NONE;
        }

        @Override public void onAnimationRepeat(Animator animation) {}

        @VisibleForTesting
        @AnimationType public int getAnimationType() {
            return mAnimationType;
        }

        @VisibleForTesting
        public PipTransitionAnimator<T> setPipAnimationCallback(PipAnimationCallback callback) {
            mPipAnimationCallback = callback;
            return this;
        }

        PipTransitionAnimator<T> setPipTransactionHandler(PipTransactionHandler handler) {
            mPipTransactionHandler = handler;
            return this;
        }

        boolean handlePipTransaction(SurfaceControl leash, SurfaceControl.Transaction tx,
                Rect destinationBounds, float alpha) {
            if (mPipTransactionHandler != null) {
                return mPipTransactionHandler.handlePipTransaction(
                        leash, tx, destinationBounds, alpha);
            }
            return false;
        }

        SurfaceControl getContentOverlayLeash() {
            return mContentOverlay == null ? null : mContentOverlay.mLeash;
        }

        void setColorContentOverlay(Context context) {
            reattachContentOverlay(new PipContentOverlay.PipColorOverlay(context));
        }

        void setSnapshotContentOverlay(TaskSnapshot snapshot, Rect sourceRectHint) {
            reattachContentOverlay(
                    new PipContentOverlay.PipSnapshotOverlay(snapshot, sourceRectHint));
        }

        void setAppIconContentOverlay(Context context, Rect appBounds, Rect destinationBounds,
                ActivityInfo activityInfo, int appIconSizePx) {
            reattachContentOverlay(
                    new PipContentOverlay.PipAppIconOverlay(context, appBounds, destinationBounds,
                            new IconProvider(context).getIcon(activityInfo), appIconSizePx));
        }

        private void reattachContentOverlay(PipContentOverlay overlay) {
            final SurfaceControl.Transaction tx =
                    mSurfaceControlTransactionFactory.getTransaction();
            if (mContentOverlay != null) {
                mContentOverlay.detach(tx);
            }
            mContentOverlay = overlay;
            mContentOverlay.attach(tx, mLeash);
        }

        /**
         * Clears the {@link #mContentOverlay}, this should be done after the content overlay is
         * faded out, such as in {@link PipTaskOrganizer#fadeOutAndRemoveOverlay}
         */
        void clearContentOverlay() {
            mContentOverlay = null;
        }

        @VisibleForTesting
        @TransitionDirection public int getTransitionDirection() {
            return mTransitionDirection;
        }

        @VisibleForTesting
        public PipTransitionAnimator<T> setTransitionDirection(@TransitionDirection int direction) {
            if (direction != TRANSITION_DIRECTION_SAME) {
                mTransitionDirection = direction;
            }
            return this;
        }

        T getStartValue() {
            return mStartValue;
        }

        T getBaseValue() {
            return mBaseValue;
        }

        @VisibleForTesting
        public T getEndValue() {
            return mEndValue;
        }

        Rect getDestinationBounds() {
            return mDestinationBounds;
        }

        void setDestinationBounds(Rect destinationBounds) {
            mDestinationBounds.set(destinationBounds);
            if (mAnimationType == ANIM_TYPE_ALPHA) {
                onStartTransaction(mLeash, mSurfaceControlTransactionFactory.getTransaction());
            }
        }

        void setCurrentValue(T value) {
            mCurrentValue = value;
        }

        boolean shouldApplyCornerRadius() {
            return !isOutPipDirection(mTransitionDirection);
        }

        boolean shouldApplyShadowRadius() {
            return !isOutPipDirection(mTransitionDirection)
                    && !isRemovePipDirection(mTransitionDirection);
        }

        boolean inScaleTransition() {
            if (mAnimationType != ANIM_TYPE_BOUNDS) return false;
            final int direction = getTransitionDirection();
            return !isInPipDirection(direction) && !isOutPipDirection(direction);
        }

        /**
         * Updates the {@link #mEndValue}.
         *
         * NOTE: Do not forget to call {@link #setDestinationBounds(Rect)} for bounds animation.
         * This is typically used when we receive a shelf height adjustment during the bounds
         * animation. In which case we can update the end bounds and keep the existing animation
         * running instead of cancelling it.
         */
        public void updateEndValue(T endValue) {
            mEndValue = endValue;
        }

        @VisibleForTesting
        public void setSurfaceControlTransactionFactory(
                PipSurfaceTransactionHelper.SurfaceControlTransactionFactory factory) {
            mSurfaceControlTransactionFactory = factory;
        }

        PipSurfaceTransactionHelper getSurfaceTransactionHelper() {
            return mSurfaceTransactionHelper;
        }

        void setSurfaceTransactionHelper(PipSurfaceTransactionHelper helper) {
            mSurfaceTransactionHelper = helper;
        }

        void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {}

        void onEndTransaction(SurfaceControl leash, SurfaceControl.Transaction tx,
                @TransitionDirection int transitionDirection) {}

        abstract void applySurfaceControlTransaction(SurfaceControl leash,
                SurfaceControl.Transaction tx, float fraction);

        static PipTransitionAnimator<Float> ofAlpha(TaskInfo taskInfo, SurfaceControl leash,
                Rect destinationBounds, float startValue, float endValue) {
            return new PipTransitionAnimator<Float>(taskInfo, leash, ANIM_TYPE_ALPHA,
                    destinationBounds, startValue, startValue, endValue) {
                @Override
                void applySurfaceControlTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, float fraction) {
                    final float alpha = getStartValue() * (1 - fraction) + getEndValue() * fraction;
                    setCurrentValue(alpha);
                    getSurfaceTransactionHelper().alpha(tx, leash, alpha)
                            .round(tx, leash, shouldApplyCornerRadius())
                            .shadow(tx, leash, shouldApplyShadowRadius());
                    if (!handlePipTransaction(leash, tx, destinationBounds, alpha)) {
                        tx.apply();
                    }
                }

                @Override
                void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
                    if (getTransitionDirection() == TRANSITION_DIRECTION_REMOVE_STACK) {
                        // while removing the pip stack, no extra work needs to be done here.
                        return;
                    }
                    getSurfaceTransactionHelper()
                            .resetScale(tx, leash, getDestinationBounds())
                            .crop(tx, leash, getDestinationBounds())
                            .round(tx, leash, shouldApplyCornerRadius())
                            .shadow(tx, leash, shouldApplyShadowRadius());
                    tx.show(leash);
                    tx.apply();
                }

                @Override
                public void updateEndValue(Float endValue) {
                    super.updateEndValue(endValue);
                    mStartValue = mCurrentValue;
                }
            };
        }

        static PipTransitionAnimator<Rect> ofBounds(TaskInfo taskInfo, SurfaceControl leash,
                Rect baseValue, Rect startValue, Rect endValue, Rect sourceRectHint,
                @PipAnimationController.TransitionDirection int direction, float startingAngle,
                @Surface.Rotation int rotationDelta) {
            final boolean isOutPipDirection = isOutPipDirection(direction);
            final boolean isInPipDirection = isInPipDirection(direction);
            // Just for simplicity we'll interpolate between the source rect hint insets and empty
            // insets to calculate the window crop
            final Rect initialSourceValue;
            if (isOutPipDirection) {
                initialSourceValue = new Rect(endValue);
            } else {
                initialSourceValue = new Rect(baseValue);
            }

            final Rect rotatedEndRect;
            final Rect lastEndRect;
            final Rect initialContainerRect;
            if (rotationDelta == ROTATION_90 || rotationDelta == ROTATION_270) {
                lastEndRect = new Rect(endValue);
                rotatedEndRect = new Rect(endValue);
                // Rotate the end bounds according to the rotation delta because the display will
                // be rotated to the same orientation.
                rotateBounds(rotatedEndRect, initialSourceValue, rotationDelta);
                // Use the rect that has the same orientation as the hint rect.
                initialContainerRect = isOutPipDirection ? rotatedEndRect : initialSourceValue;
            } else {
                rotatedEndRect = lastEndRect = null;
                initialContainerRect = initialSourceValue;
            }

            final Rect adjustedSourceRectHint = new Rect();
            if (sourceRectHint == null || sourceRectHint.isEmpty()) {
                // Crop a Rect matches the aspect ratio and pivots at the center point.
                // This is done for entering case only.
                if (isInPipDirection(direction)) {
                    final float aspectRatio = endValue.width() / (float) endValue.height();
                    adjustedSourceRectHint.set(PipUtils.getEnterPipWithOverlaySrcRectHint(
                            startValue, aspectRatio));
                }
            } else {
                adjustedSourceRectHint.set(sourceRectHint);
            }
            final Rect sourceHintRectInsets = new Rect();
            if (!adjustedSourceRectHint.isEmpty()) {
                sourceHintRectInsets.set(
                        adjustedSourceRectHint.left - initialContainerRect.left,
                        adjustedSourceRectHint.top - initialContainerRect.top,
                        initialContainerRect.right - adjustedSourceRectHint.right,
                        initialContainerRect.bottom - adjustedSourceRectHint.bottom);
            }
            final Rect zeroInsets = new Rect(0, 0, 0, 0);

            // construct new Rect instances in case they are recycled
            return new PipTransitionAnimator<Rect>(taskInfo, leash, ANIM_TYPE_BOUNDS,
                    endValue, new Rect(baseValue), new Rect(startValue), new Rect(endValue)) {
                private final RectEvaluator mRectEvaluator = new RectEvaluator(new Rect());
                private final RectEvaluator mInsetsEvaluator = new RectEvaluator(new Rect());

                @Override
                void applySurfaceControlTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, float fraction) {
                    final Rect base = getBaseValue();
                    final Rect start = getStartValue();
                    final Rect end = getEndValue();
                    Rect bounds = mRectEvaluator.evaluate(fraction, start, end);
                    if (mContentOverlay != null) {
                        mContentOverlay.onAnimationUpdate(tx, bounds, fraction);
                    }
                    if (rotatedEndRect != null) {
                        // Animate the bounds in a different orientation. It only happens when
                        // switching between PiP and fullscreen.
                        applyRotation(tx, leash, fraction, start, end);
                        return;
                    }
                    float angle = (1.0f - fraction) * startingAngle;
                    setCurrentValue(bounds);
                    if (inScaleTransition() || adjustedSourceRectHint.isEmpty()) {
                        if (isOutPipDirection) {
                            getSurfaceTransactionHelper().crop(tx, leash, end)
                                    .scale(tx, leash, end, bounds);
                        } else {
                            getSurfaceTransactionHelper().crop(tx, leash, base)
                                    .scale(tx, leash, base, bounds, angle)
                                    .round(tx, leash, base, bounds)
                                    .shadow(tx, leash, shouldApplyShadowRadius());
                        }
                    } else {
                        final Rect insets = computeInsets(fraction);
                        getSurfaceTransactionHelper().scaleAndCrop(tx, leash,
                                adjustedSourceRectHint, initialSourceValue, bounds, insets,
                                isInPipDirection, fraction);
                        if (shouldApplyCornerRadius()) {
                            final Rect sourceBounds = new Rect(initialContainerRect);
                            sourceBounds.inset(insets);
                            getSurfaceTransactionHelper()
                                    .round(tx, leash, sourceBounds, bounds)
                                    .shadow(tx, leash, shouldApplyShadowRadius());
                        }
                    }
                    if (!handlePipTransaction(leash, tx, bounds, /* alpha= */ 1f)) {
                        tx.apply();
                    }
                }

                private void applyRotation(SurfaceControl.Transaction tx, SurfaceControl leash,
                        float fraction, Rect start, Rect end) {
                    if (!end.equals(lastEndRect)) {
                        // If the end bounds are changed during animating (e.g. shelf height), the
                        // rotated end bounds also need to be updated.
                        rotatedEndRect.set(endValue);
                        rotateBounds(rotatedEndRect, initialSourceValue, rotationDelta);
                        lastEndRect.set(end);
                    }
                    final Rect bounds = mRectEvaluator.evaluate(fraction, start, rotatedEndRect);
                    setCurrentValue(bounds);
                    final Rect insets = computeInsets(fraction);
                    final float degree, x, y;
                    if (Transitions.SHELL_TRANSITIONS_ROTATION) {
                        if (rotationDelta == ROTATION_90) {
                            degree = 90 * (1 - fraction);
                            x = fraction * (end.left - start.left)
                                    + start.left + start.width() * (1 - fraction);
                            y = fraction * (end.top - start.top) + start.top;
                        } else {
                            degree = -90 * (1 - fraction);
                            x = fraction * (end.left - start.left) + start.left;
                            y = fraction * (end.top - start.top)
                                    + start.top + start.height() * (1 - fraction);
                        }
                    } else {
                        if (rotationDelta == ROTATION_90) {
                            degree = 90 * fraction;
                            x = fraction * (end.right - start.left) + start.left;
                            y = fraction * (end.top - start.top) + start.top;
                        } else {
                            degree = -90 * fraction;
                            x = fraction * (end.left - start.left) + start.left;
                            y = fraction * (end.bottom - start.top) + start.top;
                        }
                    }
                    final Rect sourceBounds = new Rect(initialContainerRect);
                    sourceBounds.inset(insets);
                    getSurfaceTransactionHelper()
                            .rotateAndScaleWithCrop(tx, leash, initialContainerRect, bounds,
                                    insets, degree, x, y, isOutPipDirection,
                                    rotationDelta == ROTATION_270 /* clockwise */);
                    if (shouldApplyCornerRadius()) {
                        getSurfaceTransactionHelper()
                                .round(tx, leash, sourceBounds, bounds)
                                .shadow(tx, leash, shouldApplyShadowRadius());
                    }
                    if (!handlePipTransaction(leash, tx, bounds, 1f /* alpha */)) {
                        tx.apply();
                    }
                }

                private Rect computeInsets(float fraction) {
                    final Rect startRect = isOutPipDirection ? sourceHintRectInsets : zeroInsets;
                    final Rect endRect = isOutPipDirection ? zeroInsets : sourceHintRectInsets;
                    return mInsetsEvaluator.evaluate(fraction, startRect, endRect);
                }

                @Override
                void onStartTransaction(SurfaceControl leash, SurfaceControl.Transaction tx) {
                    getSurfaceTransactionHelper()
                            .alpha(tx, leash, 1f)
                            .round(tx, leash, shouldApplyCornerRadius())
                            .shadow(tx, leash, shouldApplyShadowRadius());
                    tx.show(leash);
                    tx.apply();
                }

                @Override
                void onEndTransaction(SurfaceControl leash, SurfaceControl.Transaction tx,
                        int transitionDirection) {
                    // NOTE: intentionally does not apply the transaction here.
                    // this end transaction should get executed synchronously with the final
                    // WindowContainerTransaction in task organizer
                    final Rect destBounds = getDestinationBounds();
                    getSurfaceTransactionHelper().resetScale(tx, leash, destBounds);
                    if (isOutPipDirection(transitionDirection)) {
                        // Exit pip, clear scale, position and crop.
                        tx.setMatrix(leash, 1, 0, 0, 1);
                        tx.setPosition(leash, 0, 0);
                        tx.setWindowCrop(leash, 0, 0);
                    } else {
                        getSurfaceTransactionHelper().crop(tx, leash, destBounds);
                    }
                    if (mContentOverlay != null) {
                        clearContentOverlay();
                    }
                }

                @Override
                public void updateEndValue(Rect endValue) {
                    super.updateEndValue(endValue);
                    if (mStartValue != null && mCurrentValue != null) {
                        mStartValue.set(mCurrentValue);
                    }
                }
            };
        }
    }
}
