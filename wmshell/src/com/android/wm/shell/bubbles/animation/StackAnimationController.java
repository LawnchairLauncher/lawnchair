/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.bubbles.animation;

import static com.android.wm.shell.bubbles.BubblePositioner.NUM_VISIBLE_WHEN_RESTING;
import static com.android.wm.shell.bubbles.animation.FlingToDismissUtils.getFlingToDismissTargetWidth;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.BadgedImageView;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleStackView;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.shared.animation.PhysicsAnimator;
import com.android.wm.shell.shared.magnetictarget.MagnetizedObject;

import com.google.android.collect.Sets;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Animation controller for bubbles when they're in their stacked state. Stacked bubbles sit atop
 * each other with a slight offset to the left or right (depending on which side of the screen they
 * are on). Bubbles 'follow' each other when dragged, and can be flung to the left or right sides of
 * the screen.
 */
public class StackAnimationController extends
        PhysicsAnimationLayout.PhysicsAnimationController {

    private static final String TAG = "Bubbs.StackCtrl";

    /** Value to use for animating bubbles in and springing stack after fling. */
    private static final float STACK_SPRING_STIFFNESS = 700f;

    /** Values to use for animating updated bubble to top of stack. */
    private static final float NEW_BUBBLE_START_SCALE = 0.5f;
    private static final float NEW_BUBBLE_START_Y = 100f;
    private static final long BUBBLE_SWAP_DURATION = 300L;

    /**
     * Values to use for the default {@link SpringForce} provided to the physics animation layout.
     */
    public static final int SPRING_TO_TOUCH_STIFFNESS = 12000;
    public static final float IME_ANIMATION_STIFFNESS = SpringForce.STIFFNESS_LOW;
    private static final int CHAIN_STIFFNESS = 800;
    public static final float DEFAULT_BOUNCINESS = 0.9f;

    private final PhysicsAnimator.SpringConfig mAnimateOutSpringConfig =
            new PhysicsAnimator.SpringConfig(
                    STACK_SPRING_STIFFNESS, SpringForce.DAMPING_RATIO_NO_BOUNCY);

    /**
     * Friction applied to fling animations. Since the stack must land on one of the sides of the
     * screen, we want less friction horizontally so that the stack has a better chance of making it
     * to the side without needing a spring.
     */
    private static final float FLING_FRICTION = 1.9f;

    private static final float SPRING_AFTER_FLING_DAMPING_RATIO = 0.85f;

    /** Sentinel value for unset position value. */
    private static final float UNSET = -Float.MIN_VALUE;

    /**
     * Minimum fling velocity required to trigger moving the stack from one side of the screen to
     * the other.
     */
    private static final float ESCAPE_VELOCITY = 750f;

    /** Velocity required to dismiss the stack without dragging it into the dismiss target. */
    private static final float FLING_TO_DISMISS_MIN_VELOCITY = 4000f;

    /**
     * The canonical position of the stack. This is typically the position of the first bubble, but
     * we need to keep track of it separately from the first bubble's translation in case there are
     * no bubbles, or the first bubble was just added and being animated to its new position.
     */
    private PointF mStackPosition = new PointF(-1, -1);

    /**
     * MagnetizedObject instance for the stack, which is used by the touch handler for the magnetic
     * dismiss target.
     */
    private MagnetizedObject<StackAnimationController> mMagnetizedStack;

    /**
     * The area that Bubbles will occupy after all animations end. This is used to move other
     * floating content out of the way proactively.
     */
    private Rect mAnimatingToBounds = new Rect();

    /** Whether or not the stack's start position has been set. */
    private boolean mStackMovedToStartPosition = false;

    /**
     * The Y position of the stack before the IME became visible, or {@link Float#MIN_VALUE} if the
     * IME is not visible or the user moved the stack since the IME became visible.
     */
    private float mPreImeY = UNSET;

    /**
     * Animations on the stack position itself, which would have been started in
     * {@link #flingThenSpringFirstBubbleWithStackFollowing}. These animations dispatch to
     * {@link #moveFirstBubbleWithStackFollowing} to move the entire stack (with 'following' effect)
     * to a legal position on the side of the screen.
     */
    private HashMap<DynamicAnimation.ViewProperty, DynamicAnimation> mStackPositionAnimations =
            new HashMap<>();

    /**
     * Whether the current motion of the stack is due to a fling animation (vs. being dragged
     * manually).
     */
    private boolean mIsMovingFromFlinging = false;

    /**
     * Whether the first bubble is springing towards the touch point, rather than using the default
     * behavior of moving directly to the touch point with the rest of the stack following it.
     *
     * This happens when the user's finger exits the dismiss area while the stack is magnetized to
     * the center. Since the touch point differs from the stack location, we need to animate the
     * stack back to the touch point to avoid a jarring instant location change from the center of
     * the target to the touch point just outside the target bounds.
     *
     * This is reset once the spring animations end, since that means the first bubble has
     * successfully 'caught up' to the touch.
     */
    private boolean mFirstBubbleSpringingToTouch = false;

    /**
     * Whether to spring the stack to the next touch event coordinates. This is used to animate the
     * stack (including the first bubble) out of the magnetic dismiss target to the touch location.
     * Once it 'catches up' and the animation ends, we'll revert to moving the first bubble directly
     * and only animating the following bubbles.
     */
    private boolean mSpringToTouchOnNextMotionEvent = false;

    /** Offset of bubbles in the stack (i.e. how much they overlap). */
    private float mStackOffset;
    /** Offset between stack y and animation y for bubble swap. */
    private float mSwapAnimationOffset;
    /** Max number of bubbles to show in the expanded bubble row. */
    private int mMaxBubbles;
    /** Default bubble elevation. */
    private int mElevation;
    /** Diameter of the bubble. */
    private int mBubbleSize;
    /**
     * The amount of space to add between the bubbles and certain UI elements, such as the top of
     * the screen or the IME. This does not apply to the left/right sides of the screen since the
     * stack goes offscreen intentionally.
     */
    private int mBubblePaddingTop;
    /** Contains display size, orientation, and inset information. */
    private BubblePositioner mPositioner;

    /** FloatingContentCoordinator instance for resolving floating content conflicts. */
    private FloatingContentCoordinator mFloatingContentCoordinator;

    /**
     * FloatingContent instance that returns the stack's location on the screen, and moves it when
     * requested.
     */
    private final FloatingContentCoordinator.FloatingContent mStackFloatingContent =
            new FloatingContentCoordinator.FloatingContent() {

        private final Rect mFloatingBoundsOnScreen = new Rect();

        @Override
        public void moveToBounds(@NonNull Rect bounds) {
            springStack(bounds.left, bounds.top, STACK_SPRING_STIFFNESS);
        }

        @NonNull
        @Override
        public Rect getAllowedFloatingBoundsRegion() {
            final Rect floatingBounds = getFloatingBoundsOnScreen();
            final Rect allowableStackArea = new Rect();
            mPositioner.getAllowableStackPositionRegion(getBubbleCount())
                    .roundOut(allowableStackArea);
            allowableStackArea.right += floatingBounds.width();
            allowableStackArea.bottom += floatingBounds.height();
            return allowableStackArea;
        }

        @NonNull
        @Override
        public Rect getFloatingBoundsOnScreen() {
            if (!mAnimatingToBounds.isEmpty()) {
                return mAnimatingToBounds;
            }

            if (mLayout.getChildCount() > 0) {
                // Calculate the bounds using stack position + bubble size so that we don't need to
                // wait for the bubble views to lay out.
                mFloatingBoundsOnScreen.set(
                        (int) mStackPosition.x,
                        (int) mStackPosition.y,
                        (int) mStackPosition.x + mBubbleSize,
                        (int) mStackPosition.y + mBubbleSize + mBubblePaddingTop);
            } else {
                mFloatingBoundsOnScreen.setEmpty();
            }

            return mFloatingBoundsOnScreen;
        }
    };

    /** Returns the number of 'real' bubbles (excluding the overflow bubble). */
    private IntSupplier mBubbleCountSupplier;

    /**
     * Callback to run whenever any bubble is animated out. The BubbleStackView will check if the
     * end of this animation means we have no bubbles left, and notify the BubbleController.
     */
    private Runnable mOnBubbleAnimatedOutAction;

    /**
     * Callback to run whenever the stack is finished being flung somewhere.
     */
    private Runnable mOnStackAnimationFinished;

    public StackAnimationController(
            FloatingContentCoordinator floatingContentCoordinator,
            IntSupplier bubbleCountSupplier,
            Runnable onBubbleAnimatedOutAction,
            Runnable onStackAnimationFinished,
            BubblePositioner positioner) {
        mFloatingContentCoordinator = floatingContentCoordinator;
        mBubbleCountSupplier = bubbleCountSupplier;
        mOnBubbleAnimatedOutAction = onBubbleAnimatedOutAction;
        mOnStackAnimationFinished = onStackAnimationFinished;
        mPositioner = positioner;
    }

    /**
     * Instantly move the first bubble to the given point, and animate the rest of the stack behind
     * it with the 'following' effect.
     */
    public void moveFirstBubbleWithStackFollowing(float x, float y) {
        // If we're moving the bubble around, we're not animating to any bounds.
        mAnimatingToBounds.setEmpty();

        // If we manually move the bubbles with the IME open, clear the return point since we don't
        // want the stack to snap away from the new position.
        mPreImeY = UNSET;

        moveFirstBubbleWithStackFollowing(DynamicAnimation.TRANSLATION_X, x);
        moveFirstBubbleWithStackFollowing(DynamicAnimation.TRANSLATION_Y, y);

        // This method is called when the stack is being dragged manually, so we're clearly no
        // longer flinging.
        mIsMovingFromFlinging = false;
    }

    /**
     * The position of the stack - typically the position of the first bubble; if no bubbles have
     * been added yet, it will be where the first bubble will go when added.
     */
    public PointF getStackPosition() {
        return mStackPosition;
    }

    /** Whether the stack is on the left side of the screen. */
    public boolean isStackOnLeftSide() {
        return mPositioner.isStackOnLeft(mStackPosition);
    }

    /**
     * Fling stack to given corner, within allowable screen bounds.
     * Note that we need new SpringForce instances per animation despite identical configs because
     * SpringAnimation uses SpringForce's internal (changing) velocity while the animation runs.
     */
    public void springStack(
            float destinationX, float destinationY, float stiffness) {
        notifyFloatingCoordinatorStackAnimatingTo(destinationX, destinationY);

        springFirstBubbleWithStackFollowing(DynamicAnimation.TRANSLATION_X,
                new SpringForce()
                        .setStiffness(stiffness)
                        .setDampingRatio(SPRING_AFTER_FLING_DAMPING_RATIO),
                0 /* startXVelocity */,
                destinationX);

        springFirstBubbleWithStackFollowing(DynamicAnimation.TRANSLATION_Y,
                new SpringForce()
                        .setStiffness(stiffness)
                        .setDampingRatio(SPRING_AFTER_FLING_DAMPING_RATIO),
                0 /* startYVelocity */,
                destinationY);
    }

    /**
     * Springs the stack to the specified x/y coordinates, with the stiffness used for springs after
     * flings.
     */
    public void springStackAfterFling(float destinationX, float destinationY) {
        springStack(destinationX, destinationY, STACK_SPRING_STIFFNESS);
    }

    /**
     * Flings the stack starting with the given velocities, springing it to the nearest edge
     * afterward.
     *
     * @return The X value that the stack will end up at after the fling/spring.
     */
    public float flingStackThenSpringToEdge(float x, float velX, float velY) {
        final boolean stackOnLeftSide = x - mBubbleSize / 2 < mLayout.getWidth() / 2;

        final boolean stackShouldFlingLeft = stackOnLeftSide
                ? velX < ESCAPE_VELOCITY
                : velX < -ESCAPE_VELOCITY;

        final RectF stackBounds = mPositioner.getAllowableStackPositionRegion(getBubbleCount());

        // Target X translation (either the left or right side of the screen).
        final float destinationRelativeX = stackShouldFlingLeft
                ? stackBounds.left : stackBounds.right;

        // If all bubbles were removed during a drag event, just return the X we would have animated
        // to if there were still bubbles.
        if (mLayout == null || mLayout.getChildCount() == 0) {
            return destinationRelativeX;
        }

        final ContentResolver contentResolver = mLayout.getContext().getContentResolver();
        final float stiffness = Settings.Secure.getFloat(contentResolver, "bubble_stiffness",
                STACK_SPRING_STIFFNESS /* default */);
        final float dampingRatio = Settings.Secure.getFloat(contentResolver, "bubble_damping",
                SPRING_AFTER_FLING_DAMPING_RATIO);
        final float friction = Settings.Secure.getFloat(contentResolver, "bubble_friction",
                FLING_FRICTION);

        // Minimum velocity required for the stack to make it to the targeted side of the screen,
        // taking friction into account (4.2f is the number that friction scalars are multiplied by
        // in DynamicAnimation.DragForce). This is an estimate - it could possibly be slightly off,
        // but the SpringAnimation at the end will ensure that it reaches the destination X
        // regardless.
        final float minimumVelocityToReachEdge =
                (destinationRelativeX - x) * (friction * 4.2f);

        final float estimatedY = PhysicsAnimator.estimateFlingEndValue(
                mStackPosition.y, velY,
                new PhysicsAnimator.FlingConfig(
                        friction, stackBounds.top, stackBounds.bottom));

        notifyFloatingCoordinatorStackAnimatingTo(destinationRelativeX, estimatedY);

        // Use the touch event's velocity if it's sufficient, otherwise use the minimum velocity so
        // that it'll make it all the way to the side of the screen.
        final float startXVelocity = stackShouldFlingLeft
                ? Math.min(minimumVelocityToReachEdge, velX)
                : Math.max(minimumVelocityToReachEdge, velX);



        flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_X,
                startXVelocity,
                friction,
                new SpringForce()
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio),
                destinationRelativeX);

        flingThenSpringFirstBubbleWithStackFollowing(
                DynamicAnimation.TRANSLATION_Y,
                velY,
                friction,
                new SpringForce()
                        .setStiffness(stiffness)
                        .setDampingRatio(dampingRatio),
                /* destination */ null);

        // If we're flinging now, there's no more touch event to catch up to.
        mFirstBubbleSpringingToTouch = false;
        mIsMovingFromFlinging = true;
        return destinationRelativeX;
    }

    /**
     * Where the stack would be if it were snapped to the nearest horizontal edge (left or right).
     */
    public PointF getStackPositionAlongNearestHorizontalEdge() {
        final PointF stackPos = getStackPosition();
        final boolean onLeft = mLayout.isFirstChildXLeftOfCenter(stackPos.x);
        final RectF bounds = mPositioner.getAllowableStackPositionRegion(getBubbleCount());

        stackPos.x = onLeft ? bounds.left : bounds.right;
        return stackPos;
    }

    /** Description of current animation controller state. */
    public void dump(PrintWriter pw) {
        pw.println("StackAnimationController state:");
        pw.print("  isActive:             "); pw.println(isActiveController());
        pw.print("  restingStackPos:      ");
        pw.println(mPositioner.getRestingPosition().toString());
        pw.print("  currentStackPos:      "); pw.println(mStackPosition.toString());
        pw.print("  isMovingFromFlinging: "); pw.println(mIsMovingFromFlinging);
        pw.print("  withinDismiss:        "); pw.println(isStackStuckToTarget());
        pw.print("  firstBubbleSpringing: "); pw.println(mFirstBubbleSpringingToTouch);
    }

    /**
     * Flings the first bubble along the given property's axis, using the provided configuration
     * values. When the animation ends - either by hitting the min/max, or by friction sufficiently
     * reducing momentum - a SpringAnimation takes over to snap the bubble to the given final
     * position.
     */
    protected void flingThenSpringFirstBubbleWithStackFollowing(
            DynamicAnimation.ViewProperty property,
            float vel,
            float friction,
            SpringForce spring,
            Float finalPosition) {
        if (!isActiveController()) {
            return;
        }

        Log.d(TAG, String.format("Flinging %s.",
                PhysicsAnimationLayout.getReadablePropertyName(property)));

        StackPositionProperty firstBubbleProperty = new StackPositionProperty(property);
        final float currentValue = firstBubbleProperty.getValue(this);
        final RectF bounds = mPositioner.getAllowableStackPositionRegion(getBubbleCount());
        final float min =
                property.equals(DynamicAnimation.TRANSLATION_X)
                        ? bounds.left
                        : bounds.top;
        final float max =
                property.equals(DynamicAnimation.TRANSLATION_X)
                        ? bounds.right
                        : bounds.bottom;

        FlingAnimation flingAnimation = new FlingAnimation(this, firstBubbleProperty);
        flingAnimation.setFriction(friction)
                .setStartVelocity(vel)

                // If the bubble's property value starts beyond the desired min/max, use that value
                // instead so that the animation won't immediately end. If, for example, the user
                // drags the bubbles into the navigation bar, but then flings them upward, we want
                // the fling to occur despite temporarily having a value outside of the min/max. If
                // the bubbles are out of bounds and flung even farther out of bounds, the fling
                // animation will halt immediately and the SpringAnimation will take over, springing
                // it in reverse to the (legal) final position.
                .setMinValue(Math.min(currentValue, min))
                .setMaxValue(Math.max(currentValue, max))

                .addEndListener((animation, canceled, endValue, endVelocity) -> {
                    if (!canceled) {
                        mPositioner.setRestingPosition(mStackPosition);

                        springFirstBubbleWithStackFollowing(property, spring, endVelocity,
                                finalPosition != null
                                        ? finalPosition
                                        : Math.max(min, Math.min(max, endValue)));
                    }
                });

        cancelStackPositionAnimation(property);
        mStackPositionAnimations.put(property, flingAnimation);
        flingAnimation.start();
    }

    /**
     * Cancel any stack position animations that were started by calling
     * @link #flingThenSpringFirstBubbleWithStackFollowing}, and remove any corresponding end
     * listeners.
     */
    public void cancelStackPositionAnimations() {
        cancelStackPositionAnimation(DynamicAnimation.TRANSLATION_X);
        cancelStackPositionAnimation(DynamicAnimation.TRANSLATION_Y);

        removeEndActionForProperty(DynamicAnimation.TRANSLATION_X);
        removeEndActionForProperty(DynamicAnimation.TRANSLATION_Y);
    }

    /**
     * Animates the stack either away from the newly visible IME, or back to its original position
     * due to the IME going away.
     *
     * @return The destination Y value of the stack due to the IME movement (or the current position
     * of the stack if it's not moving).
     */
    public float animateForImeVisibility(boolean imeVisible) {
        final float maxBubbleY = mPositioner.getAllowableStackPositionRegion(
                getBubbleCount()).bottom;
        float destinationY = UNSET;

        if (imeVisible) {
            // Stack is lower than it should be and overlaps the now-visible IME.
            if (mStackPosition.y > maxBubbleY && mPreImeY == UNSET) {
                mPreImeY = mStackPosition.y;
                destinationY = maxBubbleY;
            }
        } else {
            if (mPreImeY != UNSET) {
                destinationY = mPreImeY;
                mPreImeY = UNSET;
            }
        }

        if (destinationY != UNSET) {
            springFirstBubbleWithStackFollowing(
                    DynamicAnimation.TRANSLATION_Y,
                    getSpringForce(DynamicAnimation.TRANSLATION_Y, /* view */ null)
                            .setStiffness(IME_ANIMATION_STIFFNESS),
                    /* startVel */ 0f,
                    destinationY);

            notifyFloatingCoordinatorStackAnimatingTo(mStackPosition.x, destinationY);
        }

        return destinationY != UNSET ? destinationY : mStackPosition.y;
    }

    /**
     * Notifies the floating coordinator that we're moving, and sets {@link #mAnimatingToBounds} so
     * we return these bounds from
     * {@link FloatingContentCoordinator.FloatingContent#getFloatingBoundsOnScreen()}.
     */
    private void notifyFloatingCoordinatorStackAnimatingTo(float x, float y) {
        final Rect floatingBounds = mStackFloatingContent.getFloatingBoundsOnScreen();
        floatingBounds.offsetTo((int) x, (int) y);
        mAnimatingToBounds = floatingBounds;
        mFloatingContentCoordinator.onContentMoved(mStackFloatingContent);
    }

    /** Moves the stack in response to a touch event. */
    public void moveStackFromTouch(float x, float y) {
        // Begin the spring-to-touch catch up animation if needed.
        if (mSpringToTouchOnNextMotionEvent) {
            springStack(x, y, SPRING_TO_TOUCH_STIFFNESS);
            mSpringToTouchOnNextMotionEvent = false;
            mFirstBubbleSpringingToTouch = true;
        } else if (mFirstBubbleSpringingToTouch) {
            final SpringAnimation springToTouchX =
                    (SpringAnimation) mStackPositionAnimations.get(
                            DynamicAnimation.TRANSLATION_X);
            final SpringAnimation springToTouchY =
                    (SpringAnimation) mStackPositionAnimations.get(
                            DynamicAnimation.TRANSLATION_Y);

            // If either animation is still running, we haven't caught up. Update the animations.
            if (springToTouchX.isRunning() || springToTouchY.isRunning()) {
                springToTouchX.animateToFinalPosition(x);
                springToTouchY.animateToFinalPosition(y);
            } else {
                // If the animations have finished, the stack is now at the touch point. We can
                // resume moving the bubble directly.
                mFirstBubbleSpringingToTouch = false;
            }
        }

        if (!mFirstBubbleSpringingToTouch && !isStackStuckToTarget()) {
            moveFirstBubbleWithStackFollowing(x, y);
        }
    }

    /** Notify the controller that the stack has been unstuck from the dismiss target. */
    public void onUnstuckFromTarget() {
        mSpringToTouchOnNextMotionEvent = true;
    }

    /**
     * 'Implode' the stack by shrinking the bubbles, fading them out, and translating them down.
     */
    public void animateStackDismissal(float translationYBy, Runnable after) {
        animationsForChildrenFromIndex(0, (index, animation) ->
                animation
                        .scaleX(0f)
                        .scaleY(0f)
                        .alpha(0f)
                        .translationY(
                                mLayout.getChildAt(index).getTranslationY() + translationYBy)
                        .withStiffness(SpringForce.STIFFNESS_HIGH))
                .startAll(after);
    }

    /**
     * Springs the first bubble to the given final position, with the rest of the stack 'following'.
     */
    protected void springFirstBubbleWithStackFollowing(
            DynamicAnimation.ViewProperty property, SpringForce spring,
            float vel, float finalPosition, @Nullable Runnable... after) {

        if (mLayout.getChildCount() == 0 || !isActiveController()) {
            return;
        }

        Log.d(TAG, String.format("Springing %s to final position %f.",
                PhysicsAnimationLayout.getReadablePropertyName(property),
                finalPosition));

        // Whether we're springing towards the touch location, rather than to a position on the
        // sides of the screen.
        final boolean isSpringingTowardsTouch = mSpringToTouchOnNextMotionEvent;

        StackPositionProperty firstBubbleProperty = new StackPositionProperty(property);
        SpringAnimation springAnimation =
                new SpringAnimation(this, firstBubbleProperty)
                        .setSpring(spring)
                        .addEndListener((dynamicAnimation, b, v, v1) -> {
                            if (!isSpringingTowardsTouch) {
                                // If we're springing towards the touch position, don't save the
                                // resting position - the touch location is not a valid resting
                                // position. We'll set this when the stack springs to the left or
                                // right side of the screen after the touch gesture ends.
                                mPositioner.setRestingPosition(mStackPosition);
                            }

                            if (mOnStackAnimationFinished != null) {
                                mOnStackAnimationFinished.run();
                            }

                            if (after != null) {
                                for (Runnable callback : after) {
                                    callback.run();
                                }
                            }
                        })
                        .setStartVelocity(vel);

        cancelStackPositionAnimation(property);
        mStackPositionAnimations.put(property, springAnimation);
        springAnimation.animateToFinalPosition(finalPosition);
    }

    @Override
    Set<DynamicAnimation.ViewProperty> getAnimatedProperties() {
        return Sets.newHashSet(
                DynamicAnimation.TRANSLATION_X, // For positioning.
                DynamicAnimation.TRANSLATION_Y,
                DynamicAnimation.ALPHA,         // For fading in new bubbles.
                DynamicAnimation.SCALE_X,       // For 'popping in' new bubbles.
                DynamicAnimation.SCALE_Y);
    }

    @Override
    int getNextAnimationInChain(DynamicAnimation.ViewProperty property, int index) {
        if (property.equals(DynamicAnimation.TRANSLATION_X)
                || property.equals(DynamicAnimation.TRANSLATION_Y)) {
            return index + 1;
        } else {
            return NONE;
        }
    }


    @Override
    float getOffsetForChainedPropertyAnimation(DynamicAnimation.ViewProperty property, int index) {
        if (property.equals(DynamicAnimation.TRANSLATION_Y)) {
            // If we're in the dismiss target, have the bubbles pile on top of each other with no
            // offset.
            if (isStackStuckToTarget()) {
                return 0f;
            } else {
                // We only show the first two bubbles in the stack & the rest hide behind them
                // so they don't need an offset.
                return index > (NUM_VISIBLE_WHEN_RESTING - 1) ? 0f : mStackOffset;
            }
        } else {
            return 0f;
        }
    }

    @Override
    SpringForce getSpringForce(DynamicAnimation.ViewProperty property, View view) {
        final ContentResolver contentResolver = mLayout.getContext().getContentResolver();
        final float dampingRatio = Settings.Secure.getFloat(contentResolver, "bubble_damping",
                DEFAULT_BOUNCINESS);

        return new SpringForce()
                .setDampingRatio(dampingRatio)
                .setStiffness(CHAIN_STIFFNESS);
    }

    @Override
    void onChildAdded(View child, int index) {
        // Don't animate additions within the dismiss target.
        if (isStackStuckToTarget()) {
            return;
        }

        if (getBubbleCount() == 1) {
            // If this is the first child added, position the stack in its starting position.
            moveStackToStartPosition();
        } else if (isStackPositionSet() && mLayout.indexOfChild(child) == 0) {
            // Otherwise, animate the bubble in if it's the newest bubble. If we're adding a bubble
            // to the back of the stack, it'll be largely invisible so don't bother animating it in.
            animateInBubble(child, index);
        } else {
            // We are not animating the bubble in. Make sure it has the right alpha and scale values
            // in case this view was previously removed and is being re-added.
            child.setAlpha(1f);
            child.setScaleX(1f);
            child.setScaleY(1f);
        }
    }

    @Override
    void onChildRemoved(View child, int index, Runnable finishRemoval) {
        PhysicsAnimator.getInstance(child)
                .spring(DynamicAnimation.ALPHA, 0f)
                .spring(DynamicAnimation.SCALE_X, 0f, mAnimateOutSpringConfig)
                .spring(DynamicAnimation.SCALE_Y, 0f, mAnimateOutSpringConfig)
                .withEndActions(finishRemoval, mOnBubbleAnimatedOutAction)
                .start();

        // If there are other bubbles, pull them into the correct position.
        if (getBubbleCount() > 0) {
            animationForChildAtIndex(0).translationX(mStackPosition.x).start();
        } else {
            // When all children are removed ensure stack position is sane
            mPositioner.setRestingPosition(mPositioner.getRestingPosition());

            // Remove the stack from the coordinator since we don't have any bubbles and aren't
            // visible.
            mFloatingContentCoordinator.onContentRemoved(mStackFloatingContent);
        }
    }

    public void animateReorder(List<View> bubbleViews, Runnable after) {
        // After the bubble going to index 0 springs above stack, update all icons
        // at the same time, to avoid visibly changing bubble order before the animation completes.
        Runnable updateAllIcons = () -> {
            for (int newIndex = 0; newIndex < bubbleViews.size(); newIndex++) {
                View view = bubbleViews.get(newIndex);
                updateBadgesAndZOrder(view, newIndex);
            }
        };

        boolean swapped = false;
        for (int newIndex = 0; newIndex < bubbleViews.size(); newIndex++) {
            View view = bubbleViews.get(newIndex);
            if (view != null) {
                final int oldIndex = mLayout.indexOfChild(view);
                swapped |= animateSwap(view, oldIndex, newIndex, updateAllIcons, after);
            }
        }
        if (!swapped) {
            // All bubbles were at the right position. Make sure badges and z order is correct.
            updateAllIcons.run();
        }
    }

    private boolean animateSwap(View view, int oldIndex, int newIndex,
            Runnable updateAllIcons, Runnable finishReorder) {
        if (newIndex == oldIndex) {
            // View order did not change. Make sure position is correct.
            moveToFinalIndex(view, newIndex, finishReorder);
            return false;
        } else {
            // Reorder existing bubbles
            if (newIndex == 0) {
                animateToFrontThenUpdateIcons(view, updateAllIcons, finishReorder);
            } else {
                moveToFinalIndex(view, newIndex, finishReorder);
            }
            return true;
        }
    }

    private void animateToFrontThenUpdateIcons(View v, Runnable updateAllIcons,
            Runnable finishReorder) {
        final ViewPropertyAnimator animator = v.animate()
                .translationY(getStackPosition().y - mSwapAnimationOffset)
                .setDuration(BUBBLE_SWAP_DURATION)
                .withEndAction(() -> {
                    updateAllIcons.run();
                    moveToFinalIndex(v, 0 /* index */, finishReorder);
                });
        v.setTag(R.id.reorder_animator_tag, animator);
    }

    private void moveToFinalIndex(View view, int newIndex,
            Runnable finishReorder) {
        final ViewPropertyAnimator animator = view.animate()
                .translationY(getStackPosition().y
                        + Math.min(newIndex, NUM_VISIBLE_WHEN_RESTING - 1) * mStackOffset)
                .setDuration(BUBBLE_SWAP_DURATION)
                .withEndAction(() -> {
                    view.setTag(R.id.reorder_animator_tag, null);
                    finishReorder.run();
                });
        view.setTag(R.id.reorder_animator_tag, animator);
    }

    // TODO: do we need this & BubbleStackView#updateBadgesAndZOrder?
    private void updateBadgesAndZOrder(View v, int index) {
        v.setZ(index < NUM_VISIBLE_WHEN_RESTING ? (mMaxBubbles * mElevation) - index : 0f);
        BadgedImageView bv = (BadgedImageView) v;
        if (index == 0) {
            bv.showDotAndBadge(!isStackOnLeftSide());
        } else {
            bv.hideDotAndBadge(!isStackOnLeftSide());
        }
    }

    @Override
    void onChildReordered(View child, int oldIndex, int newIndex) {}

    @Override
    void onActiveControllerForLayout(PhysicsAnimationLayout layout) {
        Resources res = layout.getResources();
        mStackOffset = mPositioner.getStackOffset();
        mSwapAnimationOffset = res.getDimensionPixelSize(R.dimen.bubble_swap_animation_offset);
        mMaxBubbles = res.getInteger(R.integer.bubbles_max_rendered);
        mElevation = res.getDimensionPixelSize(R.dimen.bubble_elevation);
        mBubbleSize = mPositioner.getBubbleSize();
        mBubblePaddingTop = mPositioner.getBubblePaddingTop();
    }

    /**
     * Update resources.
     */
    public void updateResources() {
        if (mLayout != null) {
            Resources res = mLayout.getContext().getResources();
            mBubblePaddingTop = res.getDimensionPixelSize(R.dimen.bubble_padding_top);
            updateFlingToDismissTargetWidth();
        }
    }

    private void updateFlingToDismissTargetWidth() {
        if (mLayout != null && mMagnetizedStack != null) {
            int screenWidthPx = mLayout.getResources().getDisplayMetrics().widthPixels;
            mMagnetizedStack.setFlingToTargetWidthPercent(
                    getFlingToDismissTargetWidth(screenWidthPx));
        }
    }

    private boolean isStackStuckToTarget() {
        return mMagnetizedStack != null && mMagnetizedStack.getObjectStuckToTarget();
    }

    /** Moves the stack, without any animation, to the starting position. */
    private void moveStackToStartPosition() {
        // Post to ensure that the layout's width and height have been calculated.
        mLayout.setVisibility(View.INVISIBLE);
        mLayout.post(() -> {
            setStackPosition(mPositioner.getRestingPosition());

            mStackMovedToStartPosition = true;
            mLayout.setVisibility(View.VISIBLE);

            // Animate in the top bubble now that we're visible.
            if (mLayout.getChildCount() > 0) {
                // Add the stack to the floating content coordinator now that we have a bubble and
                // are visible.
                mFloatingContentCoordinator.onContentAdded(mStackFloatingContent);

                animateInBubble(mLayout.getChildAt(0), 0 /* index */);
            }
        });
    }

    /**
     * Moves the first bubble instantly to the given X or Y translation, and instructs subsequent
     * bubbles to animate 'following' to the new location.
     */
    private void moveFirstBubbleWithStackFollowing(
            DynamicAnimation.ViewProperty property, float value) {

        // Update the canonical stack position.
        if (property.equals(DynamicAnimation.TRANSLATION_X)) {
            mStackPosition.x = value;
        } else if (property.equals(DynamicAnimation.TRANSLATION_Y)) {
            mStackPosition.y = value;
        }

        if (mLayout.getChildCount() > 0) {
            property.setValue(mLayout.getChildAt(0), value);
            if (mLayout.getChildCount() > 1) {
                float newValue = value + getOffsetForChainedPropertyAnimation(property, 0);
                animationForChildAtIndex(1)
                        .property(property, newValue)
                        .start();
            }
        }
    }

    /** Moves the stack to a position instantly, with no animation. */
    public void setStackPosition(PointF pos) {
        Log.d(TAG, String.format("Setting position to (%f, %f).", pos.x, pos.y));
        mStackPosition.set(pos.x, pos.y);

        mPositioner.setRestingPosition(mStackPosition);

        // If we're not the active controller, we don't want to physically move the bubble views.
        if (isActiveController()) {
            // Cancel animations that could be moving the views.
            mLayout.cancelAllAnimationsOfProperties(
                    DynamicAnimation.TRANSLATION_X, DynamicAnimation.TRANSLATION_Y);
            cancelStackPositionAnimations();

            // Since we're not using the chained animations, apply the offsets manually.
            final float xOffset = getOffsetForChainedPropertyAnimation(
                    DynamicAnimation.TRANSLATION_X, 0);
            final float yOffset = getOffsetForChainedPropertyAnimation(
                    DynamicAnimation.TRANSLATION_Y, 0);
            for (int i = 0; i < mLayout.getChildCount(); i++) {
                float index = Math.min(i, NUM_VISIBLE_WHEN_RESTING - 1);
                mLayout.getChildAt(i).setTranslationX(pos.x + (index * xOffset));
                mLayout.getChildAt(i).setTranslationY(pos.y + (index * yOffset));
            }
        }
    }

    public void setStackPosition(BubbleStackView.RelativeStackPosition position) {
        setStackPosition(position.getAbsolutePositionInRegion(
                mPositioner.getAllowableStackPositionRegion(getBubbleCount())));
    }

    private boolean isStackPositionSet() {
        return mStackMovedToStartPosition;
    }

    /** Animates in the given bubble. */
    private void animateInBubble(View v, int index) {
        if (!isActiveController()) {
            return;
        }

        final float yOffset =
                getOffsetForChainedPropertyAnimation(DynamicAnimation.TRANSLATION_Y, 0);
        float endY = mStackPosition.y + yOffset * index;
        float endX = mStackPosition.x;
        if (mPositioner.showBubblesVertically()) {
            v.setTranslationY(endY);
            final float startX = isStackOnLeftSide()
                    ? endX - NEW_BUBBLE_START_Y
                    : endX + NEW_BUBBLE_START_Y;
            v.setTranslationX(startX);
        } else {
            v.setTranslationX(mStackPosition.x);
            final float startY = endY + NEW_BUBBLE_START_Y;
            v.setTranslationY(startY);
        }
        v.setScaleX(NEW_BUBBLE_START_SCALE);
        v.setScaleY(NEW_BUBBLE_START_SCALE);
        v.setAlpha(0f);
        final ViewPropertyAnimator animator = v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(BUBBLE_SWAP_DURATION)
                .withEndAction(() -> {
                    v.setTag(R.id.reorder_animator_tag, null);
                });
        v.setTag(R.id.reorder_animator_tag, animator);
        if (mPositioner.showBubblesVertically()) {
            animator.translationX(endX);
        } else {
            animator.translationY(endY);
        }
    }

    /**
     * Cancels any outstanding first bubble property animations that are running. This does not
     * affect the SpringAnimations controlling the individual bubbles' 'following' effect - it only
     * cancels animations started from {@link #springFirstBubbleWithStackFollowing} and
     * {@link #flingThenSpringFirstBubbleWithStackFollowing}.
     */
    private void cancelStackPositionAnimation(DynamicAnimation.ViewProperty property) {
        if (mStackPositionAnimations.containsKey(property)) {
            mStackPositionAnimations.get(property).cancel();
        }
    }

    /**
     * Returns the {@link MagnetizedObject} instance for the bubble stack.
     */
    public MagnetizedObject<StackAnimationController> getMagnetizedStack() {
        if (mMagnetizedStack == null) {
            mMagnetizedStack = new MagnetizedObject<StackAnimationController>(
                    mLayout.getContext(),
                    this,
                    new StackPositionProperty(DynamicAnimation.TRANSLATION_X),
                    new StackPositionProperty(DynamicAnimation.TRANSLATION_Y)
            ) {
                @Override
                public float getWidth(@NonNull StackAnimationController underlyingObject) {
                    return mBubbleSize;
                }

                @Override
                public float getHeight(@NonNull StackAnimationController underlyingObject) {
                    return mBubbleSize;
                }

                @Override
                public void getLocationOnScreen(@NonNull StackAnimationController underlyingObject,
                        @NonNull int[] loc) {
                    loc[0] = (int) mStackPosition.x;
                    loc[1] = (int) mStackPosition.y;
                }
            };
            mMagnetizedStack.setHapticsEnabled(true);
            mMagnetizedStack.setFlingToTargetMinVelocity(FLING_TO_DISMISS_MIN_VELOCITY);
            updateFlingToDismissTargetWidth();
        }
        return mMagnetizedStack;
    }

    /** Returns the number of 'real' bubbles (excluding overflow). */
    private int getBubbleCount() {
        return mBubbleCountSupplier.getAsInt();
    }

    /**
     * FloatProperty that uses {@link #moveFirstBubbleWithStackFollowing} to set the first bubble's
     * translation and animate the rest of the stack with it. A DynamicAnimation can animate this
     * property directly to move the first bubble and cause the stack to 'follow' to the new
     * location.
     *
     * <p>This could also be achieved by simply animating the first bubble view and adding an update
     * listener to dispatch movement to the rest of the stack. However, this would require
     * duplication of logic in that update handler - it's simpler to keep all logic contained in the
     * {@link #moveFirstBubbleWithStackFollowing} method.
     */
    private class StackPositionProperty
            extends FloatPropertyCompat<StackAnimationController> {
        private final DynamicAnimation.ViewProperty mProperty;

        private StackPositionProperty(DynamicAnimation.ViewProperty property) {
            super(property.toString());
            mProperty = property;
        }

        @Override
        public float getValue(StackAnimationController controller) {
            return mLayout.getChildCount() > 0 ? mProperty.getValue(mLayout.getChildAt(0)) : 0;
        }

        @Override
        public void setValue(StackAnimationController controller, float value) {
            moveFirstBubbleWithStackFollowing(mProperty, value);
        }
    }
}

