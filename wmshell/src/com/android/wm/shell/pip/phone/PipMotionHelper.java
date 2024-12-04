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

package com.android.wm.shell.pip.phone;

import static androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_NO_BOUNCY;
import static androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW;
import static androidx.dynamicanimation.animation.SpringForce.STIFFNESS_MEDIUM;

import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_LEFT;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;
import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND;
import static com.android.wm.shell.pip.phone.PipMenuView.ANIM_TYPE_DISMISS;
import static com.android.wm.shell.pip.phone.PipMenuView.ANIM_TYPE_NONE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Debug;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.animation.FloatProperties;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.magnetictarget.MagnetizedObject;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.pip.PipTaskOrganizer;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A helper to animate and manipulate the PiP.
 */
public class PipMotionHelper implements PipAppOpsListener.Callback,
        FloatingContentCoordinator.FloatingContent {
    private static final String TAG = "PipMotionHelper";
    private static final boolean DEBUG = false;

    private static final int SHRINK_STACK_FROM_MENU_DURATION = 250;
    private static final int EXPAND_STACK_TO_MENU_DURATION = 250;
    private static final int UNSTASH_DURATION = 250;
    private static final int LEAVE_PIP_DURATION = 300;
    private static final int SHIFT_DURATION = 300;

    /** Friction to use for PIP when it moves via physics fling animations. */
    private static final float DEFAULT_FRICTION = 1.9f;
    /** How much of the dismiss circle size to use when scaling down PIP. **/
    private static final float DISMISS_CIRCLE_PERCENT = 0.85f;

    private final Context mContext;
    private final PipTaskOrganizer mPipTaskOrganizer;
    private @NonNull PipBoundsState mPipBoundsState;

    private PhonePipMenuController mMenuController;
    private PipSnapAlgorithm mSnapAlgorithm;

    /** The region that all of PIP must stay within. */
    private final Rect mFloatingAllowedArea = new Rect();

    /** Coordinator instance for resolving conflicts with other floating content. */
    private FloatingContentCoordinator mFloatingContentCoordinator;

    @Nullable private final PipPerfHintController mPipPerfHintController;
    @Nullable private PipPerfHintController.PipHighPerfSession mPipHighPerfSession;

    /**
     * PhysicsAnimator instance for animating {@link PipBoundsState#getMotionBoundsState()}
     * using physics animations.
     */
    private PhysicsAnimator<Rect> mTemporaryBoundsPhysicsAnimator;

    private MagnetizedObject<Rect> mMagnetizedPip;

    /**
     * Update listener that resizes the PIP to {@link PipBoundsState#getMotionBoundsState()}.
     */
    private final PhysicsAnimator.UpdateListener<Rect> mResizePipUpdateListener;

    /** FlingConfig instances provided to PhysicsAnimator for fling gestures. */
    private PhysicsAnimator.FlingConfig mFlingConfigX;
    private PhysicsAnimator.FlingConfig mFlingConfigY;
    /** FlingConfig instances proviced to PhysicsAnimator for stashing. */
    private PhysicsAnimator.FlingConfig mStashConfigX;

    /** SpringConfig to use for fling-then-spring animations. */
    private final PhysicsAnimator.SpringConfig mSpringConfig =
            new PhysicsAnimator.SpringConfig(700f, DAMPING_RATIO_NO_BOUNCY);

    /** SpringConfig used for animating into the dismiss region, matches the one in
     * {@link MagnetizedObject}. */
    private final PhysicsAnimator.SpringConfig mAnimateToDismissSpringConfig =
            new PhysicsAnimator.SpringConfig(STIFFNESS_MEDIUM, DAMPING_RATIO_NO_BOUNCY);

    /** SpringConfig used for animating the pip to catch up to the finger once it leaves the dismiss
     * drag region. */
    private final PhysicsAnimator.SpringConfig mCatchUpSpringConfig =
            new PhysicsAnimator.SpringConfig(5000f, DAMPING_RATIO_NO_BOUNCY);

    /** SpringConfig to use for springing PIP away from conflicting floating content. */
    private final PhysicsAnimator.SpringConfig mConflictResolutionSpringConfig =
            new PhysicsAnimator.SpringConfig(STIFFNESS_LOW, DAMPING_RATIO_NO_BOUNCY);

    private final Consumer<Rect> mUpdateBoundsCallback = (Rect newBounds) -> {
        if (mPipBoundsState.getBounds().equals(newBounds)) {
            return;
        }

        mMenuController.updateMenuLayout(newBounds);
        mPipBoundsState.setBounds(newBounds);
    };

    /**
     * Whether we're springing to the touch event location (vs. moving it to that position
     * instantly). We spring-to-touch after PIP is dragged out of the magnetic target, since it was
     * 'stuck' in the target and needs to catch up to the touch location.
     */
    private boolean mSpringingToTouch = false;

    /**
     * Whether PIP was released in the dismiss target, and will be animated out and dismissed
     * shortly.
     */
    private boolean mDismissalPending = false;

    /**
     * Gets set in {@link #animateToExpandedState(Rect, Rect, Rect, Runnable)}, this callback is
     * used to show menu activity when the expand animation is completed.
     */
    private Runnable mPostPipTransitionCallback;

    private final PipTransitionController.PipTransitionCallback mPipTransitionCallback =
            new PipTransitionController.PipTransitionCallback() {
        @Override
        public void onPipTransitionStarted(int direction, Rect pipBounds) {}

        @Override
        public void onPipTransitionFinished(int direction) {
            if (mPostPipTransitionCallback != null) {
                mPostPipTransitionCallback.run();
                mPostPipTransitionCallback = null;
            }
        }

        @Override
        public void onPipTransitionCanceled(int direction) {}
    };

    public PipMotionHelper(Context context, @NonNull PipBoundsState pipBoundsState,
            PipTaskOrganizer pipTaskOrganizer, PhonePipMenuController menuController,
            PipSnapAlgorithm snapAlgorithm, PipTransitionController pipTransitionController,
            FloatingContentCoordinator floatingContentCoordinator,
            Optional<PipPerfHintController> pipPerfHintControllerOptional) {
        mContext = context;
        mPipTaskOrganizer = pipTaskOrganizer;
        mPipBoundsState = pipBoundsState;
        mMenuController = menuController;
        mSnapAlgorithm = snapAlgorithm;
        mFloatingContentCoordinator = floatingContentCoordinator;
        mPipPerfHintController = pipPerfHintControllerOptional.orElse(null);
        pipTransitionController.registerPipTransitionCallback(mPipTransitionCallback);
        mResizePipUpdateListener = (target, values) -> {
            if (mPipBoundsState.getMotionBoundsState().isInMotion()) {
                mPipTaskOrganizer.scheduleUserResizePip(getBounds(),
                        mPipBoundsState.getMotionBoundsState().getBoundsInMotion(), null);
            }
        };
    }

    public void init() {
        mTemporaryBoundsPhysicsAnimator = PhysicsAnimator.getInstance(
                mPipBoundsState.getMotionBoundsState().getBoundsInMotion());
    }

    @NonNull
    @Override
    public Rect getFloatingBoundsOnScreen() {
        return !mPipBoundsState.getMotionBoundsState().getAnimatingToBounds().isEmpty()
                ? mPipBoundsState.getMotionBoundsState().getAnimatingToBounds() : getBounds();
    }

    @NonNull
    @Override
    public Rect getAllowedFloatingBoundsRegion() {
        return mFloatingAllowedArea;
    }

    @Override
    public void moveToBounds(@NonNull Rect bounds) {
        animateToBounds(bounds, mConflictResolutionSpringConfig);
    }

    /**
     * Synchronizes the current bounds with the pinned stack, cancelling any ongoing animations.
     */
    void synchronizePinnedStackBounds() {
        cancelPhysicsAnimation();
        mPipBoundsState.getMotionBoundsState().onAllAnimationsEnded();

        if (mPipTaskOrganizer.isInPip()) {
            mFloatingContentCoordinator.onContentMoved(this);
        }
    }

    /**
     * Tries to move the pinned stack to the given {@param bounds}.
     */
    void movePip(Rect toBounds) {
        movePip(toBounds, false /* isDragging */);
    }

    /**
     * Tries to move the pinned stack to the given {@param bounds}.
     *
     * @param isDragging Whether this movement is the result of a drag touch gesture. If so, we
     *                   won't notify the floating content coordinator of this move, since that will
     *                   happen when the gesture ends.
     */
    void movePip(Rect toBounds, boolean isDragging) {
        if (!isDragging) {
            mFloatingContentCoordinator.onContentMoved(this);
        }

        if (!mSpringingToTouch) {
            // If we are moving PIP directly to the touch event locations, cancel any animations and
            // move PIP to the given bounds.
            cancelPhysicsAnimation();

            if (!isDragging) {
                resizePipUnchecked(toBounds);
                mPipBoundsState.setBounds(toBounds);
            } else {
                mPipBoundsState.getMotionBoundsState().setBoundsInMotion(toBounds);
                mPipTaskOrganizer.scheduleUserResizePip(getBounds(), toBounds,
                        (Rect newBounds) -> {
                                mMenuController.updateMenuLayout(newBounds);
                        });
            }
        } else {
            // If PIP is 'catching up' after being stuck in the dismiss target, update the animation
            // to spring towards the new touch location.
            mTemporaryBoundsPhysicsAnimator
                    .spring(FloatProperties.RECT_WIDTH, getBounds().width(), mCatchUpSpringConfig)
                    .spring(FloatProperties.RECT_HEIGHT, getBounds().height(), mCatchUpSpringConfig)
                    .spring(FloatProperties.RECT_X, toBounds.left, mCatchUpSpringConfig)
                    .spring(FloatProperties.RECT_Y, toBounds.top, mCatchUpSpringConfig);

            startBoundsAnimator(toBounds.left /* toX */, toBounds.top /* toY */);
        }
    }

    /** Animates the PIP into the dismiss target, scaling it down. */
    void animateIntoDismissTarget(
            MagnetizedObject.MagneticTarget target,
            float velX, float velY,
            boolean flung, Function0<Unit> after) {
        final PointF targetCenter = target.getCenterOnScreen();

        // PIP should fit in the circle
        final float dismissCircleSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.dismiss_circle_size);

        final float width = getBounds().width();
        final float height = getBounds().height();
        final float ratio = width / height;

        // Width should be a little smaller than the circle size.
        final float desiredWidth = dismissCircleSize * DISMISS_CIRCLE_PERCENT;
        final float desiredHeight = desiredWidth / ratio;
        final float destinationX = targetCenter.x - (desiredWidth / 2f);
        final float destinationY = targetCenter.y - (desiredHeight / 2f);

        // If we're already in the dismiss target area, then there won't be a move to set the
        // temporary bounds, so just initialize it to the current bounds.
        if (!mPipBoundsState.getMotionBoundsState().isInMotion()) {
            mPipBoundsState.getMotionBoundsState().setBoundsInMotion(getBounds());
        }
        mTemporaryBoundsPhysicsAnimator
                .spring(FloatProperties.RECT_X, destinationX, velX, mAnimateToDismissSpringConfig)
                .spring(FloatProperties.RECT_Y, destinationY, velY, mAnimateToDismissSpringConfig)
                .spring(FloatProperties.RECT_WIDTH, desiredWidth, mAnimateToDismissSpringConfig)
                .spring(FloatProperties.RECT_HEIGHT, desiredHeight, mAnimateToDismissSpringConfig)
                .withEndActions(after);

        startBoundsAnimator(destinationX, destinationY);
    }

    /** Set whether we're springing-to-touch to catch up after being stuck in the dismiss target. */
    void setSpringingToTouch(boolean springingToTouch) {
        mSpringingToTouch = springingToTouch;
    }

    /**
     * Resizes the pinned stack back to unknown windowing mode, which could be freeform or
     *      * fullscreen depending on the display area's windowing mode.
     */
    void expandLeavePip(boolean skipAnimation) {
        expandLeavePip(skipAnimation, false /* enterSplit */);
    }

    /**
     * Resizes the pinned task to split-screen mode.
     */
    void expandIntoSplit() {
        expandLeavePip(false, true /* enterSplit */);
    }

    /**
     * Resizes the pinned stack back to unknown windowing mode, which could be freeform or
     * fullscreen depending on the display area's windowing mode.
     */
    private void expandLeavePip(boolean skipAnimation, boolean enterSplit) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: exitPip: skipAnimation=%s"
                            + " callers=\n%s", TAG, skipAnimation, Debug.getCallers(5, "    "));
        }
        cancelPhysicsAnimation();
        mMenuController.hideMenu(ANIM_TYPE_NONE, false /* resize */);
        mPipTaskOrganizer.exitPip(skipAnimation ? 0 : LEAVE_PIP_DURATION, enterSplit);
    }

    /**
     * Dismisses the pinned stack.
     */
    @Override
    public void dismissPip() {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: removePip: callers=\n%s", TAG, Debug.getCallers(5, "    "));
        }
        cancelPhysicsAnimation();
        mMenuController.hideMenu(ANIM_TYPE_DISMISS, false /* resize */);
        mPipTaskOrganizer.removePip();
    }

    /** Sets the movement bounds to use to constrain PIP position animations. */
    void onMovementBoundsChanged() {
        rebuildFlingConfigs();

        // The movement bounds represent the area within which we can move PIP's top-left position.
        // The allowed area for all of PIP is those bounds plus PIP's width and height.
        mFloatingAllowedArea.set(mPipBoundsState.getMovementBounds());
        mFloatingAllowedArea.right += getBounds().width();
        mFloatingAllowedArea.bottom += getBounds().height();
    }

    /**
     * @return the PiP bounds.
     */
    private Rect getBounds() {
        return mPipBoundsState.getBounds();
    }

    /**
     * Flings the PiP to the closest snap target.
     */
    void flingToSnapTarget(
            float velocityX, float velocityY, @Nullable Runnable postBoundsUpdateCallback) {
        movetoTarget(velocityX, velocityY, postBoundsUpdateCallback, false /* isStash */);
    }

    /**
     * Stash PiP to the closest edge. We set velocityY to 0 to limit pure horizontal motion.
     */
    void stashToEdge(float velX, float velY, @Nullable Runnable postBoundsUpdateCallback) {
        velY = mPipBoundsState.getStashedState() == STASH_TYPE_NONE ? 0 : velY;
        movetoTarget(velX, velY, postBoundsUpdateCallback, true /* isStash */);
    }

    private void onHighPerfSessionTimeout(PipPerfHintController.PipHighPerfSession session) {}

    private void cleanUpHighPerfSessionMaybe() {
        if (mPipHighPerfSession != null) {
            // Close the high perf session once pointer interactions are over;
            mPipHighPerfSession.close();
            mPipHighPerfSession = null;
        }
    }

    private void movetoTarget(
            float velocityX,
            float velocityY,
            @Nullable Runnable postBoundsUpdateCallback,
            boolean isStash) {
        // If we're flinging to a snap target now, we're not springing to catch up to the touch
        // location now.
        mSpringingToTouch = false;

        mTemporaryBoundsPhysicsAnimator
                .spring(FloatProperties.RECT_WIDTH, getBounds().width(), mSpringConfig)
                .spring(FloatProperties.RECT_HEIGHT, getBounds().height(), mSpringConfig)
                .flingThenSpring(
                        FloatProperties.RECT_X, velocityX,
                        isStash ? mStashConfigX : mFlingConfigX,
                        mSpringConfig, true /* flingMustReachMinOrMax */)
                .flingThenSpring(
                        FloatProperties.RECT_Y, velocityY, mFlingConfigY, mSpringConfig);

        final Rect insetBounds = mPipBoundsState.getDisplayLayout().stableInsets();
        final float leftEdge = isStash
                ? mPipBoundsState.getStashOffset() - mPipBoundsState.getBounds().width()
                + insetBounds.left
                : mPipBoundsState.getMovementBounds().left;
        final float rightEdge = isStash
                ?  mPipBoundsState.getDisplayBounds().right - mPipBoundsState.getStashOffset()
                - insetBounds.right
                : mPipBoundsState.getMovementBounds().right;

        final float xEndValue = velocityX < 0 ? leftEdge : rightEdge;

        final int startValueY = mPipBoundsState.getMotionBoundsState().getBoundsInMotion().top;
        final float estimatedFlingYEndValue =
                PhysicsAnimator.estimateFlingEndValue(startValueY, velocityY, mFlingConfigY);

        startBoundsAnimator(xEndValue /* toX */, estimatedFlingYEndValue /* toY */,
                postBoundsUpdateCallback);
    }

    /**
     * Animates PIP to the provided bounds, using physics animations and the given spring
     * configuration
     */
    void animateToBounds(Rect bounds, PhysicsAnimator.SpringConfig springConfig) {
        if (!mTemporaryBoundsPhysicsAnimator.isRunning()) {
            // Animate from the current bounds if we're not already animating.
            mPipBoundsState.getMotionBoundsState().setBoundsInMotion(getBounds());
        }

        mTemporaryBoundsPhysicsAnimator
                .spring(FloatProperties.RECT_X, bounds.left, springConfig)
                .spring(FloatProperties.RECT_Y, bounds.top, springConfig);
        startBoundsAnimator(bounds.left /* toX */, bounds.top /* toY */);
    }

    /**
     * Animates the dismissal of the PiP off the edge of the screen.
     */
    void animateDismiss() {
        // Animate off the bottom of the screen, then dismiss PIP.
        mTemporaryBoundsPhysicsAnimator
                .spring(FloatProperties.RECT_Y,
                        mPipBoundsState.getMovementBounds().bottom + getBounds().height() * 2,
                        0,
                        mSpringConfig)
                .withEndActions(this::dismissPip);

        startBoundsAnimator(
                getBounds().left /* toX */, getBounds().bottom + getBounds().height() /* toY */);

        mDismissalPending = false;
    }

    /**
     * Animates the PiP to the expanded state to show the menu.
     */
    float animateToExpandedState(Rect expandedBounds, Rect movementBounds,
            Rect expandedMovementBounds, Runnable callback) {
        float savedSnapFraction = mSnapAlgorithm.getSnapFraction(new Rect(getBounds()),
                movementBounds);
        mSnapAlgorithm.applySnapFraction(expandedBounds, expandedMovementBounds, savedSnapFraction);
        mPostPipTransitionCallback = callback;
        resizeAndAnimatePipUnchecked(expandedBounds, EXPAND_STACK_TO_MENU_DURATION);
        return savedSnapFraction;
    }

    /**
     * Animates the PiP from the expanded state to the normal state after the menu is hidden.
     */
    void animateToUnexpandedState(Rect normalBounds, float savedSnapFraction,
            Rect normalMovementBounds, Rect currentMovementBounds, boolean immediate) {
        if (savedSnapFraction < 0f) {
            // If there are no saved snap fractions, then just use the current bounds
            savedSnapFraction = mSnapAlgorithm.getSnapFraction(new Rect(getBounds()),
                    currentMovementBounds, mPipBoundsState.getStashedState());
        }

        mSnapAlgorithm.applySnapFraction(normalBounds, normalMovementBounds, savedSnapFraction,
                mPipBoundsState.getStashedState(), mPipBoundsState.getStashOffset(),
                mPipBoundsState.getDisplayBounds(),
                mPipBoundsState.getDisplayLayout().stableInsets());

        if (immediate) {
            movePip(normalBounds);
        } else {
            resizeAndAnimatePipUnchecked(normalBounds, SHRINK_STACK_FROM_MENU_DURATION);
        }
    }

    /**
     * Animates the PiP to the stashed state, choosing the closest edge.
     */
    void animateToStashedClosestEdge() {
        Rect tmpBounds = new Rect();
        final Rect insetBounds = mPipBoundsState.getDisplayLayout().stableInsets();
        final int stashType =
                mPipBoundsState.getBounds().left == mPipBoundsState.getMovementBounds().left
                ? STASH_TYPE_LEFT : STASH_TYPE_RIGHT;
        final float leftEdge = stashType == STASH_TYPE_LEFT
                ? mPipBoundsState.getStashOffset()
                - mPipBoundsState.getBounds().width() + insetBounds.left
                : mPipBoundsState.getDisplayBounds().right
                        - mPipBoundsState.getStashOffset() - insetBounds.right;
        tmpBounds.set((int) leftEdge,
                mPipBoundsState.getBounds().top,
                (int) (leftEdge + mPipBoundsState.getBounds().width()),
                mPipBoundsState.getBounds().bottom);
        resizeAndAnimatePipUnchecked(tmpBounds, UNSTASH_DURATION);
        mPipBoundsState.setStashed(stashType);
    }

    /**
     * Animates the PiP from stashed state into un-stashed, popping it out from the edge.
     */
    void animateToUnStashedBounds(Rect unstashedBounds) {
        resizeAndAnimatePipUnchecked(unstashedBounds, UNSTASH_DURATION);
    }

    /**
     * Animates the PiP to offset it from the IME or shelf.
     */
    void animateToOffset(Rect originalBounds, int offset) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: animateToOffset: originalBounds=%s offset=%s"
                            + " callers=\n%s", TAG, originalBounds, offset,
                    Debug.getCallers(5, "    "));
        }
        cancelPhysicsAnimation();
        mPipTaskOrganizer.scheduleOffsetPip(originalBounds, offset, SHIFT_DURATION,
                mUpdateBoundsCallback);
    }

    /**
     * Cancels all existing animations.
     */
    private void cancelPhysicsAnimation() {
        mTemporaryBoundsPhysicsAnimator.cancel();
        mPipBoundsState.getMotionBoundsState().onPhysicsAnimationEnded();
        mSpringingToTouch = false;
    }

    /** Set new fling configs whose min/max values respect the given movement bounds. */
    private void rebuildFlingConfigs() {
        mFlingConfigX = new PhysicsAnimator.FlingConfig(DEFAULT_FRICTION,
                mPipBoundsState.getMovementBounds().left,
                mPipBoundsState.getMovementBounds().right);
        mFlingConfigY = new PhysicsAnimator.FlingConfig(DEFAULT_FRICTION,
                mPipBoundsState.getMovementBounds().top,
                mPipBoundsState.getMovementBounds().bottom);
        final Rect insetBounds = mPipBoundsState.getDisplayLayout().stableInsets();
        mStashConfigX = new PhysicsAnimator.FlingConfig(
                DEFAULT_FRICTION,
                mPipBoundsState.getStashOffset() - mPipBoundsState.getBounds().width()
                        + insetBounds.left,
                mPipBoundsState.getDisplayBounds().right - mPipBoundsState.getStashOffset()
                        - insetBounds.right);
    }

    private void startBoundsAnimator(float toX, float toY) {
        startBoundsAnimator(toX, toY, null /* postBoundsUpdateCallback */);
    }

    /**
     * Starts the physics animator which will update the animated PIP bounds using physics
     * animations, as well as the TimeAnimator which will apply those bounds to PIP.
     *
     * This will also add end actions to the bounds animator that cancel the TimeAnimator and update
     * the 'real' bounds to equal the final animated bounds.
     *
     * If one wishes to supply a callback after all the 'real' bounds update has happened,
     * pass @param postBoundsUpdateCallback.
     */
    private void startBoundsAnimator(float toX, float toY, Runnable postBoundsUpdateCallback) {
        if (!mSpringingToTouch) {
            cancelPhysicsAnimation();
        }

        setAnimatingToBounds(new Rect(
                (int) toX,
                (int) toY,
                (int) toX + getBounds().width(),
                (int) toY + getBounds().height()));

        if (!mTemporaryBoundsPhysicsAnimator.isRunning()) {
            if (mPipPerfHintController != null) {
                // Start a high perf session with a timeout callback.
                mPipHighPerfSession = mPipPerfHintController.startSession(
                        this::onHighPerfSessionTimeout, "startBoundsAnimator");
            }
            if (postBoundsUpdateCallback != null) {
                mTemporaryBoundsPhysicsAnimator
                        .addUpdateListener(mResizePipUpdateListener)
                        .withEndActions(this::onBoundsPhysicsAnimationEnd,
                                postBoundsUpdateCallback);
            } else {
                mTemporaryBoundsPhysicsAnimator
                        .addUpdateListener(mResizePipUpdateListener)
                        .withEndActions(this::onBoundsPhysicsAnimationEnd);
            }
        }

        mTemporaryBoundsPhysicsAnimator.start();
    }

    /**
     * Notify that PIP was released in the dismiss target and will be animated out and dismissed
     * shortly.
     */
    void notifyDismissalPending() {
        mDismissalPending = true;
    }

    private void onBoundsPhysicsAnimationEnd() {
        // The physics animation ended, though we may not necessarily be done animating, such as
        // when we're still dragging after moving out of the magnetic target.
        if (!mDismissalPending
                && !mSpringingToTouch
                && !mMagnetizedPip.getObjectStuckToTarget()) {
            // All motion operations have actually finished.
            mPipBoundsState.setBounds(
                    mPipBoundsState.getMotionBoundsState().getBoundsInMotion());
            mPipBoundsState.getMotionBoundsState().onAllAnimationsEnded();
            if (!mDismissalPending) {
                // do not schedule resize if PiP is dismissing, which may cause app re-open to
                // mBounds instead of it's normal bounds.
                mPipTaskOrganizer.scheduleFinishResizePip(getBounds());
            }
        }
        mPipBoundsState.getMotionBoundsState().onPhysicsAnimationEnded();
        mSpringingToTouch = false;
        mDismissalPending = false;
        cleanUpHighPerfSessionMaybe();
    }

    /**
     * Notifies the floating coordinator that we're moving, and sets the animating to bounds so
     * we return these bounds from
     * {@link FloatingContentCoordinator.FloatingContent#getFloatingBoundsOnScreen()}.
     */
    private void setAnimatingToBounds(Rect bounds) {
        mPipBoundsState.getMotionBoundsState().setAnimatingToBounds(bounds);
        mFloatingContentCoordinator.onContentMoved(this);
    }

    /**
     * Directly resizes the PiP to the given {@param bounds}.
     */
    private void resizePipUnchecked(Rect toBounds) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: resizePipUnchecked: toBounds=%s"
                            + " callers=\n%s", TAG, toBounds, Debug.getCallers(5, "    "));
        }
        if (!toBounds.equals(getBounds())) {
            mPipTaskOrganizer.scheduleResizePip(toBounds, mUpdateBoundsCallback);
        }
    }

    /**
     * Directly resizes the PiP to the given {@param bounds}.
     */
    private void resizeAndAnimatePipUnchecked(Rect toBounds, int duration) {
        if (DEBUG) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: resizeAndAnimatePipUnchecked: toBounds=%s"
                            + " duration=%s callers=\n%s", TAG, toBounds, duration,
                    Debug.getCallers(5, "    "));
        }

        // Intentionally resize here even if the current bounds match the destination bounds.
        // This is so all the proper callbacks are performed.
        mPipTaskOrganizer.scheduleAnimateResizePip(toBounds, duration,
                TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND, null /* updateBoundsCallback */);
        setAnimatingToBounds(toBounds);
    }

    /**
     * Returns a MagnetizedObject wrapper for PIP's animated bounds. This is provided to the
     * magnetic dismiss target so it can calculate PIP's size and position.
     */
    MagnetizedObject<Rect> getMagnetizedPip() {
        if (mMagnetizedPip == null) {
            mMagnetizedPip = new MagnetizedObject<Rect>(
                    mContext, mPipBoundsState.getMotionBoundsState().getBoundsInMotion(),
                    FloatProperties.RECT_X, FloatProperties.RECT_Y) {
                @Override
                public float getWidth(@NonNull Rect animatedPipBounds) {
                    return animatedPipBounds.width();
                }

                @Override
                public float getHeight(@NonNull Rect animatedPipBounds) {
                    return animatedPipBounds.height();
                }

                @Override
                public void getLocationOnScreen(
                        @NonNull Rect animatedPipBounds, @NonNull int[] loc) {
                    loc[0] = animatedPipBounds.left;
                    loc[1] = animatedPipBounds.top;
                }
            };
            mMagnetizedPip.setFlingToTargetEnabled(false);
        }

        return mMagnetizedPip;
    }
}
