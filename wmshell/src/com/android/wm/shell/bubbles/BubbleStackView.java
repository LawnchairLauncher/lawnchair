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

package com.android.wm.shell.bubbles;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.wm.shell.animation.Interpolators.ALPHA_IN;
import static com.android.wm.shell.animation.Interpolators.ALPHA_OUT;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.bubbles.BubblePositioner.NUM_VISIBLE_WHEN_RESTING;
import static com.android.wm.shell.bubbles.BubblePositioner.StackPinnedEdge.LEFT;
import static com.android.wm.shell.bubbles.BubblePositioner.StackPinnedEdge.RIGHT;
import static com.android.wm.shell.common.bubbles.BubbleConstants.BUBBLE_EXPANDED_SCRIM_ALPHA;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManagerPolicyConstants;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.window.ScreenCapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.FrameworkStatsLog;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.bubbles.BubblesNavBarMotionEventHandler.MotionEventListener;
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix;
import com.android.wm.shell.bubbles.animation.ExpandedAnimationController;
import com.android.wm.shell.bubbles.animation.ExpandedViewAnimationController;
import com.android.wm.shell.bubbles.animation.ExpandedViewAnimationControllerImpl;
import com.android.wm.shell.bubbles.animation.PhysicsAnimationLayout;
import com.android.wm.shell.bubbles.animation.StackAnimationController;
import com.android.wm.shell.common.FloatingContentCoordinator;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.bubbles.DismissView;
import com.android.wm.shell.common.bubbles.RelativeTouchListener;
import com.android.wm.shell.common.magnetictarget.MagnetizedObject;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Renders bubbles in a stack and handles animating expanded and collapsed states.
 */
public class BubbleStackView extends FrameLayout
        implements ViewTreeObserver.OnComputeInternalInsetsListener {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleStackView" : TAG_BUBBLES;

    /** How far the flyout needs to be dragged before it's dismissed regardless of velocity. */
    static final float FLYOUT_DRAG_PERCENT_DISMISS = 0.25f;

    /** Velocity required to dismiss the flyout via drag. */
    private static final float FLYOUT_DISMISS_VELOCITY = 2000f;

    /**
     * Factor for attenuating translation when the flyout is overscrolled (8f = flyout moves 1 pixel
     * for every 8 pixels overscrolled).
     */
    private static final float FLYOUT_OVERSCROLL_ATTENUATION_FACTOR = 8f;

    private static final int FADE_IN_DURATION = 320;

    /** How long to wait, in milliseconds, before hiding the flyout. */
    @VisibleForTesting
    static final int FLYOUT_HIDE_AFTER = 5000;

    private static final float EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT = 0.1f;

    private static final float OPEN_OVERFLOW_ANIMATE_SCALE_AMOUNT = 0.5f;

    private static final int EXPANDED_VIEW_ALPHA_ANIMATION_DURATION = 150;

    /** Minimum alpha value for scrim when alpha is being changed via drag */
    private static final float MIN_SCRIM_ALPHA_FOR_DRAG = 0.2f;

    /**
     * How long to wait to animate the stack temporarily invisible after a drag/flyout hide
     * animation ends, if we are in fact temporarily invisible.
     */
    private static final int ANIMATE_TEMPORARILY_INVISIBLE_DELAY = 1000;

    /**
     * Percent of the bubble that is hidden while stashed.
     */
    private static final float PERCENT_HIDDEN_WHEN_STASHED = 0.55f;
    /**
     * How long to wait to animate the stack for stashing.
     */
    private static final int ANIMATE_STASH_DELAY = 700;

    private static final PhysicsAnimator.SpringConfig FLYOUT_IME_ANIMATION_SPRING_CONFIG =
            new PhysicsAnimator.SpringConfig(
                    StackAnimationController.IME_ANIMATION_STIFFNESS,
                    StackAnimationController.DEFAULT_BOUNCINESS);

    private final PhysicsAnimator.SpringConfig mScaleInSpringConfig =
            new PhysicsAnimator.SpringConfig(300f, 0.9f);

    private final PhysicsAnimator.SpringConfig mScaleOutSpringConfig =
            new PhysicsAnimator.SpringConfig(900f, 1f);

    private final PhysicsAnimator.SpringConfig mTranslateSpringConfig =
            new PhysicsAnimator.SpringConfig(
                    SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY);

    /**
     * Handler to use for all delayed animations - this way, we can easily cancel them before
     * starting a new animation.
     */
    private final ShellExecutor mMainExecutor;
    private Runnable mDelayedAnimation;

    /**
     * Interface to synchronize {@link View} state and the screen.
     *
     * {@hide}
     */
    public interface SurfaceSynchronizer {
        /**
         * Wait until requested change on a {@link View} is reflected on the screen.
         *
         * @param callback callback to run after the change is reflected on the screen.
         */
        void syncSurfaceAndRun(Runnable callback);
    }

    private static final SurfaceSynchronizer DEFAULT_SURFACE_SYNCHRONIZER =
            new SurfaceSynchronizer() {
                @Override
                public void syncSurfaceAndRun(Runnable callback) {
                    Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
                        // Just wait 2 frames. There is no guarantee, but this is usually enough
                        // time that the requested change is reflected on the screen.
                        // TODO: Once SurfaceFlinger provide APIs to sync the state of
                        //  {@code View} and surfaces, rewrite this logic with them.
                        private int mFrameWait = 2;

                        @Override
                        public void doFrame(long frameTimeNanos) {
                            if (--mFrameWait > 0) {
                                Choreographer.getInstance().postFrameCallback(this);
                            } else {
                                callback.run();
                            }
                        }
                    };
                    Choreographer.getInstance().postFrameCallback(frameCallback);
                }
            };
    private final BubbleStackViewManager mManager;
    private final BubbleData mBubbleData;
    private final Bubbles.SysuiProxy.Provider mSysuiProxyProvider;
    private StackViewState mStackViewState = new StackViewState();

    private final ValueAnimator mDismissBubbleAnimator;

    private PhysicsAnimationLayout mBubbleContainer;
    private StackAnimationController mStackAnimationController;
    private ExpandedAnimationController mExpandedAnimationController;
    private ExpandedViewAnimationController mExpandedViewAnimationController;

    private View mScrim;
    @Nullable
    private ViewPropertyAnimator mScrimAnimation;
    private View mManageMenuScrim;
    private FrameLayout mExpandedViewContainer;

    /** Matrix used to scale the expanded view container with a given pivot point. */
    private final AnimatableScaleMatrix mExpandedViewContainerMatrix = new AnimatableScaleMatrix();

    /**
     * SurfaceView that we draw screenshots of animating-out bubbles into. This allows us to animate
     * between bubble activities without needing both to be alive at the same time.
     */
    private SurfaceView mAnimatingOutSurfaceView;
    private boolean mAnimatingOutSurfaceReady;

    /** Container for the animating-out SurfaceView. */
    private FrameLayout mAnimatingOutSurfaceContainer;

    /** Animator for animating the alpha value of the animating out SurfaceView. */
    private final ValueAnimator mAnimatingOutSurfaceAlphaAnimator = ValueAnimator.ofFloat(0f, 1f);

    /**
     * Buffer containing a screenshot of the animating-out bubble. This is drawn into the
     * SurfaceView during animations.
     */
    private ScreenCapture.ScreenshotHardwareBuffer mAnimatingOutBubbleBuffer;

    private BubbleFlyoutView mFlyout;
    /** Runnable that fades out the flyout and then sets it to GONE. */
    private Runnable mHideFlyout = () -> animateFlyoutCollapsed(true, 0 /* velX */);
    /**
     * Callback to run after the flyout hides. Also called if a new flyout is shown before the
     * previous one animates out.
     */
    private Runnable mAfterFlyoutHidden;
    /**
     * Set when the flyout is tapped, so that we can expand the bubble associated with the flyout
     * once it collapses.
     */
    @Nullable
    private BubbleViewProvider mBubbleToExpandAfterFlyoutCollapse = null;

    /** Layout change listener that moves the stack to the nearest valid position on rotation. */
    private OnLayoutChangeListener mOrientationChangedListener;

    @Nullable private RelativeStackPosition mRelativeStackPositionBeforeRotation;

    private int mBubbleSize;
    private int mBubbleElevation;
    private int mBubbleTouchPadding;
    private int mExpandedViewPadding;
    private int mCornerRadius;
    @Nullable private BubbleViewProvider mExpandedBubble;
    private boolean mIsExpanded;

    /** Whether the stack is currently on the left side of the screen, or animating there. */
    private boolean mStackOnLeftOrWillBe = true;

    /** Whether a touch gesture, such as a stack/bubble drag or flyout drag, is in progress. */
    private boolean mIsGestureInProgress = false;

    /** Whether or not the stack is temporarily invisible off the side of the screen. */
    private boolean mTemporarilyInvisible = false;

    /** Whether we're in the middle of dragging the stack around by touch. */
    private boolean mIsDraggingStack = false;

    /** Whether the expanded view has been hidden, because we are dragging out a bubble. */
    private boolean mExpandedViewTemporarilyHidden = false;

    /**
     * Whether the last bubble is being removed when expanded, which impacts the collapse animation.
     */
    private boolean mRemovingLastBubbleWhileExpanded = false;

    /**
     * Whether sensitive notification protection should disable flyout
     */
    private boolean mSensitiveNotificationProtectionActive = false;

    /** Animator for animating the expanded view's alpha (including the TaskView inside it). */
    private final ValueAnimator mExpandedViewAlphaAnimator = ValueAnimator.ofFloat(0f, 1f);

    /**
     * The pointer index of the ACTION_DOWN event we received prior to an ACTION_UP. We'll ignore
     * touches from other pointer indices.
     */
    private int mPointerIndexDown = -1;

    /** Indicates whether bubbles should be reordered at the end of a gesture. */
    private boolean mShouldReorderBubblesAfterGestureCompletes = false;

    @Nullable
    private BubblesNavBarGestureTracker mBubblesNavBarGestureTracker;

    /** Description of current animation controller state. */
    public void dump(PrintWriter pw) {
        pw.println("Stack view state:");

        String bubblesOnScreen = BubbleDebugConfig.formatBubblesString(
                getBubblesOnScreen(), getExpandedBubble());
        pw.println("  bubbles on screen:       "); pw.println(bubblesOnScreen);
        pw.print("  gestureInProgress:       "); pw.println(mIsGestureInProgress);
        pw.print("  showingDismiss:          "); pw.println(mDismissView.isShowing());
        pw.print("  isExpansionAnimating:    "); pw.println(mIsExpansionAnimating);
        pw.print("  expandedContainerVis:    "); pw.println(mExpandedViewContainer.getVisibility());
        pw.print("  expandedContainerAlpha:  "); pw.println(mExpandedViewContainer.getAlpha());
        pw.print("  expandedContainerMatrix: ");
        pw.println(mExpandedViewContainer.getAnimationMatrix());
        pw.print("  stack visibility :       "); pw.println(getVisibility());
        pw.print("  temporarilyInvisible:    "); pw.println(mTemporarilyInvisible);
        mStackAnimationController.dump(pw);
        mExpandedAnimationController.dump(pw);

        if (mExpandedBubble != null) {
            pw.println("Expanded bubble state:");
            pw.println("  expandedBubbleKey: " + mExpandedBubble.getKey());

            final BubbleExpandedView expandedView = getExpandedView();

            if (expandedView != null) {
                pw.println("  expandedViewVis:    " + expandedView.getVisibility());
                pw.println("  expandedViewAlpha:  " + expandedView.getAlpha());
                pw.println("  expandedViewTaskId: " + expandedView.getTaskId());

                final View av = expandedView.getTaskView();

                if (av != null) {
                    pw.println("  activityViewVis:    " + av.getVisibility());
                    pw.println("  activityViewAlpha:  " + av.getAlpha());
                } else {
                    pw.println("  activityView is null");
                }
            } else {
                pw.println("Expanded bubble view state: expanded bubble view is null");
            }
        } else {
            pw.println("Expanded bubble state: expanded bubble is null");
        }
    }

    private Bubbles.BubbleExpandListener mExpandListener;

    /** Callback to run when we want to unbubble the given notification's conversation. */
    private Consumer<String> mUnbubbleConversationCallback;

    private boolean mViewUpdatedRequested = false;
    private boolean mIsExpansionAnimating = false;
    private boolean mIsBubbleSwitchAnimating = false;

    /** The view to shrink and apply alpha to when magneted to the dismiss target. */
    @Nullable private View mViewBeingDismissed;

    private Rect mTempRect = new Rect();

    private final List<Rect> mSystemGestureExclusionRects = Collections.singletonList(new Rect());

    private ViewTreeObserver.OnPreDrawListener mViewUpdater =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(mViewUpdater);
                    updateExpandedView();
                    mViewUpdatedRequested = false;
                    return true;
                }
            };

    private ViewTreeObserver.OnDrawListener mSystemGestureExcludeUpdater =
            this::updateSystemGestureExcludeRects;

    /** Float property that 'drags' the flyout. */
    private final FloatPropertyCompat mFlyoutCollapseProperty =
            new FloatPropertyCompat("FlyoutCollapseSpring") {
                @Override
                public float getValue(Object o) {
                    return mFlyoutDragDeltaX;
                }

                @Override
                public void setValue(Object o, float v) {
                    setFlyoutStateForDragLength(v);
                }
            };

    /** SpringAnimation that springs the flyout collapsed via onFlyoutDragged. */
    private final SpringAnimation mFlyoutTransitionSpring =
            new SpringAnimation(this, mFlyoutCollapseProperty);

    /** Distance the flyout has been dragged in the X axis. */
    private float mFlyoutDragDeltaX = 0f;

    /**
     * Runnable that animates in the flyout. This reference is needed to cancel delayed postings.
     */
    private Runnable mAnimateInFlyout;

    /**
     * End listener for the flyout spring that either posts a runnable to hide the flyout, or hides
     * it immediately.
     */
    private final DynamicAnimation.OnAnimationEndListener mAfterFlyoutTransitionSpring =
            (dynamicAnimation, b, v, v1) -> {
                if (mFlyoutDragDeltaX == 0) {
                    mFlyout.postDelayed(mHideFlyout, FLYOUT_HIDE_AFTER);
                } else {
                    mFlyout.hideFlyout();
                }
            };

    @NonNull
    private final SurfaceSynchronizer mSurfaceSynchronizer;

    /**
     * The currently magnetized object, which is being dragged and will be attracted to the magnetic
     * dismiss target.
     *
     * This is either the stack itself, or an individual bubble.
     */
    private MagnetizedObject<?> mMagnetizedObject;

    /**
     * The MagneticTarget instance for our circular dismiss view. This is added to the
     * MagnetizedObject instances for the stack and any dragged-out bubbles.
     */
    private MagnetizedObject.MagneticTarget mMagneticTarget;

    /** Magnet listener that handles animating and dismissing individual dragged-out bubbles. */
    private final MagnetizedObject.MagnetListener mIndividualBubbleMagnetListener =
            new MagnetizedObject.MagnetListener() {

                @Override
                public void onStuckToTarget(@NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject) {
                    Object underlyingObject = draggedObject.getUnderlyingObject();
                    if (underlyingObject instanceof View) {
                        View view = (View) underlyingObject;
                        animateDismissBubble(view, true);
                    }
                }

                @Override
                public void onUnstuckFromTarget(@NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject,
                        float velX, float velY, boolean wasFlungOut) {
                    Object underlyingObject = draggedObject.getUnderlyingObject();
                    if (underlyingObject instanceof View) {
                        View view = (View) underlyingObject;
                        animateDismissBubble(view, false);

                        if (wasFlungOut) {
                            mExpandedAnimationController.snapBubbleBack(view, velX, velY);
                            mDismissView.hide();
                        } else {
                            mExpandedAnimationController.onUnstuckFromTarget();
                        }
                    }
                }

                @Override
                public void onReleasedInTarget(@NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject) {
                    Object underlyingObject = draggedObject.getUnderlyingObject();
                    if (underlyingObject instanceof View) {
                        View view = (View) underlyingObject;
                        mExpandedAnimationController.dismissDraggedOutBubble(
                                view /* bubble */,
                                mDismissView.getHeight() /* translationYBy */,
                                () -> dismissBubbleIfExists(
                                        mBubbleData.getBubbleWithView(view)) /* after */);
                    }

                    mDismissView.hide();
                }
            };

    /** Magnet listener that handles animating and dismissing the entire stack. */
    private final MagnetizedObject.MagnetListener mStackMagnetListener =
            new MagnetizedObject.MagnetListener() {
                @Override
                public void onStuckToTarget(
                        @NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject) {
                    animateDismissBubble(mBubbleContainer, true);
                }

                @Override
                public void onUnstuckFromTarget(@NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject,
                        float velX, float velY, boolean wasFlungOut) {
                    animateDismissBubble(mBubbleContainer, false);
                    if (wasFlungOut) {
                        mStackAnimationController.flingStackThenSpringToEdge(
                                mStackAnimationController.getStackPosition().x, velX, velY);
                        mDismissView.hide();
                    } else {
                        mStackAnimationController.onUnstuckFromTarget();
                    }
                }

                @Override
                public void onReleasedInTarget(@NonNull MagnetizedObject.MagneticTarget target,
                        @NonNull MagnetizedObject<?> draggedObject) {
                    mStackAnimationController.animateStackDismissal(
                            mDismissView.getHeight() /* translationYBy */,
                            () -> {
                                mBubbleData.dismissAll(Bubbles.DISMISS_USER_GESTURE);
                                resetDismissAnimator();
                            } /*after */);
                    mDismissView.hide();
                }
            };

    /**
     * Click listener set on each bubble view. When collapsed, clicking a bubble expands the stack.
     * When expanded, clicking a bubble either expands that bubble, or collapses the stack.
     */
    private OnClickListener mBubbleClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            // If the touch ended in a click, we're no longer dragging.
            onDraggingEnded();

            // Bubble clicks either trigger expansion/collapse or a bubble switch, both of which we
            // shouldn't interrupt. These are quick transitions, so it's not worth trying to adjust
            // the animations inflight.
            if (mIsExpansionAnimating || mIsBubbleSwitchAnimating) {
                return;
            }

            final Bubble clickedBubble = mBubbleData.getBubbleWithView(view);

            // If the bubble has since left us, ignore the click.
            if (clickedBubble == null) {
                return;
            }

            final boolean clickedBubbleIsCurrentlyExpandedBubble = mExpandedBubble != null
                            && clickedBubble.getKey().equals(mExpandedBubble.getKey());

            if (isExpanded()) {
                mExpandedAnimationController.onGestureFinished();
            }

            if (isExpanded() && !clickedBubbleIsCurrentlyExpandedBubble) {
                if (clickedBubble != mBubbleData.getSelectedBubble()) {
                    // Select the clicked bubble.
                    mBubbleData.setSelectedBubble(clickedBubble);
                } else {
                    // If the clicked bubble is the selected bubble (but not the expanded bubble),
                    // that means overflow was previously expanded. Set the selected bubble
                    // internally without going through BubbleData (which would ignore it since it's
                    // already selected).
                    setSelectedBubble(clickedBubble);
                }
            } else {
                // Otherwise, we either tapped the stack (which means we're collapsed
                // and should expand) or the currently selected bubble (we're expanded
                // and should collapse).
                if (!maybeShowStackEdu() && !mShowedUserEducationInTouchListenerActive) {
                    mBubbleData.setExpanded(!mBubbleData.isExpanded());
                }
                mShowedUserEducationInTouchListenerActive = false;
            }
        }
    };

    /**
     * Touch listener set on each bubble view. This enables dragging and dismissing the stack (when
     * collapsed), or individual bubbles (when expanded).
     */
    private RelativeTouchListener mBubbleTouchListener = new RelativeTouchListener() {

        @Override
        public boolean onDown(@NonNull View v, @NonNull MotionEvent ev) {
            // If we're expanding or collapsing, consume but ignore all touch events.
            if (mIsExpansionAnimating) {
                return true;
            }

            mShowedUserEducationInTouchListenerActive = false;
            if (maybeShowStackEdu()) {
                mShowedUserEducationInTouchListenerActive = true;
                return true;
            } else if (isStackEduVisible()) {
                mStackEduView.hide(false /* fromExpansion */);
            }

            // If the manage menu is visible, just hide it.
            if (mShowingManage) {
                showManageMenu(false /* show */);
            }

            if (mBubbleData.isExpanded()) {
                if (mManageEduView != null) {
                    mManageEduView.hide();
                }

                // If we're expanded, tell the animation controller to prepare to drag this bubble,
                // dispatching to the individual bubble magnet listener.
                mExpandedAnimationController.prepareForBubbleDrag(
                        v /* bubble */,
                        mMagneticTarget,
                        mIndividualBubbleMagnetListener);

                hideCurrentInputMethod();

                // Save the magnetized individual bubble so we can dispatch touch events to it.
                mMagnetizedObject = mExpandedAnimationController.getMagnetizedBubbleDraggingOut();
            } else {
                // If we're collapsed, prepare to drag the stack. Cancel active animations, set the
                // animation controller, and hide the flyout.
                mStackAnimationController.cancelStackPositionAnimations();
                mBubbleContainer.setActiveController(mStackAnimationController);
                hideFlyoutImmediate();

                // Save the magnetized stack so we can dispatch touch events to it.
                mMagnetizedObject = mStackAnimationController.getMagnetizedStack();
                mMagnetizedObject.clearAllTargets();
                mMagnetizedObject.addTarget(mMagneticTarget);
                mMagnetizedObject.setMagnetListener(mStackMagnetListener);

                mIsDraggingStack = true;

                // Cancel animations to make the stack temporarily invisible, since we're now
                // dragging it.
                updateTemporarilyInvisibleAnimation(false /* hideImmediately */);
            }

            passEventToMagnetizedObject(ev);

            // Bubbles are always interested in all touch events!
            return true;
        }

        @Override
        public void onMove(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy) {
            // If we're expanding or collapsing, ignore all touch events.
            if (mIsExpansionAnimating || mShowedUserEducationInTouchListenerActive) {
                return;
            }

            // Show the dismiss target, if we haven't already.
            mDismissView.show();

            if (mIsExpanded && mExpandedBubble != null && v.equals(mExpandedBubble.getIconView())) {
                // Hide the expanded view if we're dragging out the expanded bubble, and we haven't
                // already hidden it.
                hideExpandedViewIfNeeded();
            }

            // First, see if the magnetized object consumes the event - if so, we shouldn't move the
            // bubble since it's stuck to the target.
            if (!passEventToMagnetizedObject(ev)) {
                updateBubbleShadows(true /* isExpanded */);
                if (mBubbleData.isExpanded()) {
                    mExpandedAnimationController.dragBubbleOut(
                            v, viewInitialX + dx, viewInitialY + dy);
                } else {
                    if (isStackEduVisible()) {
                        mStackEduView.hide(false /* fromExpansion */);
                    }
                    mStackAnimationController.moveStackFromTouch(
                            viewInitialX + dx, viewInitialY + dy);
                }
            }
        }

        @Override
        public void onUp(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy, float velX, float velY) {
            // If we're expanding or collapsing, ignore all touch events.
            if (mIsExpansionAnimating) {
                return;
            }
            if (mShowedUserEducationInTouchListenerActive) {
                mShowedUserEducationInTouchListenerActive = false;
                return;
            }

            // First, see if the magnetized object consumes the event - if so, the bubble was
            // released in the target or flung out of it, and we should ignore the event.
            if (!passEventToMagnetizedObject(ev)) {
                if (mBubbleData.isExpanded()) {
                    mExpandedAnimationController.snapBubbleBack(v, velX, velY);

                    // Re-show the expanded view if we hid it.
                    showExpandedViewIfNeeded();
                } else {
                    // Fling the stack to the edge, and save whether or not it's going to end up on
                    // the left side of the screen.
                    final boolean oldOnLeft = mStackOnLeftOrWillBe;
                    mStackOnLeftOrWillBe =
                            mStackAnimationController.flingStackThenSpringToEdge(
                                    viewInitialX + dx, velX, velY) <= 0;
                    final boolean updateForCollapsedStack = oldOnLeft != mStackOnLeftOrWillBe;
                    updateBadges(updateForCollapsedStack);
                    logBubbleEvent(null /* no bubble associated with bubble stack move */,
                            FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__STACK_MOVED);
                }
                mDismissView.hide();
            }

            onDraggingEnded();

            // Hide the stack after a delay, if needed.
            updateTemporarilyInvisibleAnimation(false /* hideImmediately */);
            animateStashedState(false /* stashImmediately */);
        }

        @Override
        public void onCancel(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY) {
            animateStashedState(false /* stashImmediately */);
        }
    };

    /** Touch listener set on the whole view that forwards event to the swipe up listener. */
    private final RelativeTouchListener mContainerSwipeListener = new RelativeTouchListener() {
        @Override
        public boolean onDown(@NonNull View v, @NonNull MotionEvent ev) {
            // Pass move event on to swipe listener
            mSwipeUpListener.onDown(ev.getX(), ev.getY());
            return true;
        }

        @Override
        public void onMove(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy) {
            // Pass move event on to swipe listener
            mSwipeUpListener.onMove(dx, dy);
        }

        @Override
        public void onUp(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy, float velX, float velY) {
            // Pass up even on to swipe listener
            mSwipeUpListener.onUp(velX, velY);
        }
    };

    /** MotionEventListener that listens from home gesture swipe event. */
    private final MotionEventListener mSwipeUpListener = new MotionEventListener() {
        @Override
        public void onDown(float x, float y) {}

        @Override
        public void onMove(float dx, float dy) {
            if (isManageEduVisible() || isStackEduVisible()) {
                return;
            }

            if (mShowingManage) {
                showManageMenu(false /* show */);
            }
            // Only allow up, normalize for up direction
            float collapsed = -Math.min(dy, 0);
            mExpandedViewAnimationController.updateDrag((int) collapsed);

            // Update scrim if it's not animating already
            if (mScrimAnimation == null) {
                mScrim.setAlpha(getScrimAlphaForDrag(collapsed));
            }
        }

        @Override
        public void onCancel() {
            mExpandedViewAnimationController.animateBackToExpanded();
        }

        @Override
        public void onUp(float velX, float velY) {
            mExpandedViewAnimationController.setSwipeVelocity(velY);
            if (mExpandedViewAnimationController.shouldCollapse()) {
                // Update data first and start the animation when we are processing change
                mBubbleData.setExpanded(false);
            } else {
                mExpandedViewAnimationController.animateBackToExpanded();

                // Update scrim if it's not animating already
                if (mScrimAnimation == null) {
                    showScrim(true, null /* runnable */);
                }
            }
        }

        private float getScrimAlphaForDrag(float dragAmount) {
            // dragAmount should be negative as we allow scroll up only
            BubbleExpandedView expandedView = getExpandedView();
            if (expandedView != null) {
                float alphaRange = BUBBLE_EXPANDED_SCRIM_ALPHA - MIN_SCRIM_ALPHA_FOR_DRAG;

                int dragMax = expandedView.getContentHeight();
                float dragFraction = dragAmount / dragMax;

                return Math.max(BUBBLE_EXPANDED_SCRIM_ALPHA - alphaRange * dragFraction,
                        MIN_SCRIM_ALPHA_FOR_DRAG);
            }
            return BUBBLE_EXPANDED_SCRIM_ALPHA;
        }
    };

    /** Click listener set on the flyout, which expands the stack when the flyout is tapped. */
    private OnClickListener mFlyoutClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (maybeShowStackEdu()) {
                // If we're showing user education, don't open the bubble show the education first
                mBubbleToExpandAfterFlyoutCollapse = null;
            } else {
                mBubbleToExpandAfterFlyoutCollapse = mBubbleData.getSelectedBubble();
            }

            mFlyout.removeCallbacks(mHideFlyout);
            mHideFlyout.run();
        }
    };

    /** Touch listener for the flyout. This enables the drag-to-dismiss gesture on the flyout. */
    private RelativeTouchListener mFlyoutTouchListener = new RelativeTouchListener() {

        @Override
        public boolean onDown(@NonNull View v, @NonNull MotionEvent ev) {
            mFlyout.removeCallbacks(mHideFlyout);
            return true;
        }

        @Override
        public void onMove(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy) {
            setFlyoutStateForDragLength(dx);
        }

        @Override
        public void onUp(@NonNull View v, @NonNull MotionEvent ev, float viewInitialX,
                float viewInitialY, float dx, float dy, float velX, float velY) {
            final boolean onLeft = mStackAnimationController.isStackOnLeftSide();
            final boolean metRequiredVelocity =
                    onLeft ? velX < -FLYOUT_DISMISS_VELOCITY : velX > FLYOUT_DISMISS_VELOCITY;
            final boolean metRequiredDeltaX =
                    onLeft
                            ? dx < -mFlyout.getWidth() * FLYOUT_DRAG_PERCENT_DISMISS
                            : dx > mFlyout.getWidth() * FLYOUT_DRAG_PERCENT_DISMISS;
            final boolean isCancelFling = onLeft ? velX > 0 : velX < 0;
            final boolean shouldDismiss = metRequiredVelocity
                    || (metRequiredDeltaX && !isCancelFling);

            mFlyout.removeCallbacks(mHideFlyout);
            animateFlyoutCollapsed(shouldDismiss, velX);

            maybeShowStackEdu();
        }
    };

    private boolean mShowingOverflow;
    private BubbleOverflow mBubbleOverflow;
    private StackEducationView mStackEduView;
    private StackEducationView.Manager mStackEducationViewManager;
    private ManageEducationView mManageEduView;
    private DismissView mDismissView;

    private ViewGroup mManageMenu;
    private ViewGroup mManageDontBubbleView;
    private ViewGroup mManageSettingsView;
    private ImageView mManageSettingsIcon;
    private TextView mManageSettingsText;
    private boolean mShowingManage = false;
    private boolean mShowedUserEducationInTouchListenerActive = false;
    private PhysicsAnimator.SpringConfig mManageSpringConfig = new PhysicsAnimator.SpringConfig(
            SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
    private BubblePositioner mPositioner;

    @SuppressLint("ClickableViewAccessibility")
    public BubbleStackView(Context context, BubbleStackViewManager bubbleStackViewManager,
            BubblePositioner bubblePositioner, BubbleData data,
            @Nullable SurfaceSynchronizer synchronizer,
            FloatingContentCoordinator floatingContentCoordinator,
            Bubbles.SysuiProxy.Provider sysuiProxyProvider,
            ShellExecutor mainExecutor) {
        super(context);

        mMainExecutor = mainExecutor;
        mManager = bubbleStackViewManager;
        mPositioner = bubblePositioner;
        mBubbleData = data;
        mSysuiProxyProvider = sysuiProxyProvider;

        Resources res = getResources();
        mBubbleSize = res.getDimensionPixelSize(R.dimen.bubble_size);
        mBubbleElevation = mPositioner.getBubbleElevation();
        mBubbleTouchPadding = res.getDimensionPixelSize(R.dimen.bubble_touch_padding);

        mExpandedViewPadding = res.getDimensionPixelSize(R.dimen.bubble_expanded_view_padding);


        final TypedArray ta = mContext.obtainStyledAttributes(
                new int[]{android.R.attr.dialogCornerRadius});
        mCornerRadius = ta.getDimensionPixelSize(0, 0);
        ta.recycle();

        final Runnable onBubbleAnimatedOut = () -> {
            if (getBubbleCount() == 0) {
                mExpandedViewTemporarilyHidden = false;
                mManager.onAllBubblesAnimatedOut();
            }
        };
        mStackAnimationController = new StackAnimationController(
                floatingContentCoordinator, this::getBubbleCount, onBubbleAnimatedOut,
                this::animateShadows /* onStackAnimationFinished */, mPositioner);

        mExpandedAnimationController = new ExpandedAnimationController(mPositioner,
                onBubbleAnimatedOut, this);

        mExpandedViewAnimationController =
                new ExpandedViewAnimationControllerImpl(context, mPositioner);

        mSurfaceSynchronizer = synchronizer != null ? synchronizer : DEFAULT_SURFACE_SYNCHRONIZER;

        // Force LTR by default since most of the Bubbles UI is positioned manually by the user, or
        // is centered. It greatly simplifies translation positioning/animations. Views that will
        // actually lay out differently in RTL, such as the flyout and expanded view, will set their
        // layout direction to LOCALE.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        mBubbleContainer = new PhysicsAnimationLayout(context);
        mBubbleContainer.setActiveController(mStackAnimationController);
        mBubbleContainer.setElevation(mBubbleElevation);
        mBubbleContainer.setClipChildren(false);
        addView(mBubbleContainer, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        mExpandedViewContainer = new FrameLayout(context);
        mExpandedViewContainer.setElevation(mBubbleElevation);
        mExpandedViewContainer.setClipChildren(false);
        addView(mExpandedViewContainer);

        mAnimatingOutSurfaceContainer = new FrameLayout(getContext());
        mAnimatingOutSurfaceContainer.setLayoutParams(
                new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(mAnimatingOutSurfaceContainer);

        mAnimatingOutSurfaceView = new SurfaceView(getContext());
        mAnimatingOutSurfaceView.setZOrderOnTop(true);
        boolean supportsRoundedCorners = ScreenDecorationsUtils.supportsRoundedCornersOnWindows(
                mContext.getResources());
        mAnimatingOutSurfaceView.setCornerRadius(supportsRoundedCorners ? mCornerRadius : 0);
        mAnimatingOutSurfaceView.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
        mAnimatingOutSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {}

            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                mAnimatingOutSurfaceReady = true;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                mAnimatingOutSurfaceReady = false;
            }
        });
        mAnimatingOutSurfaceContainer.addView(mAnimatingOutSurfaceView);

        mAnimatingOutSurfaceContainer.setPadding(
                mExpandedViewContainer.getPaddingLeft(),
                mExpandedViewContainer.getPaddingTop(),
                mExpandedViewContainer.getPaddingRight(),
                mExpandedViewContainer.getPaddingBottom());

        setUpManageMenu();

        setUpFlyout();
        mFlyoutTransitionSpring.setSpring(new SpringForce()
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        mFlyoutTransitionSpring.addEndListener(mAfterFlyoutTransitionSpring);

        setUpDismissView();

        setClipChildren(false);
        setFocusable(true);
        mBubbleContainer.bringToFront();

        mBubbleOverflow = mBubbleData.getOverflow();

        if (Flags.enableOptionalBubbleOverflow()) {
            showOverflow(mBubbleData.hasOverflowBubbles());
        } else {
            mShowingOverflow = true; // if the flags not on this is always true
            setUpOverflow();
        }
        mScrim = new View(getContext());
        mScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mScrim.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));
        addView(mScrim);
        mScrim.setAlpha(0f);

        mManageMenuScrim = new View(getContext());
        mManageMenuScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mManageMenuScrim.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));
        addView(mManageMenuScrim, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mManageMenuScrim.setAlpha(0f);
        mManageMenuScrim.setVisibility(INVISIBLE);

        mOrientationChangedListener =
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mPositioner.update(DeviceConfig.create(mContext, mContext.getSystemService(
                            WindowManager.class)));
                    onDisplaySizeChanged();
                    mExpandedAnimationController.updateResources();
                    mExpandedAnimationController.onOrientationChanged();
                    mStackAnimationController.updateResources();
                    mBubbleOverflow.updateResources();

                    if (!isStackEduVisible() && mRelativeStackPositionBeforeRotation != null) {
                        mStackAnimationController.setStackPosition(
                                mRelativeStackPositionBeforeRotation);
                        mRelativeStackPositionBeforeRotation = null;
                    }

                    if (mIsExpanded) {
                        // update the expanded view and pointer location for the new orientation.
                        hideFlyoutImmediate();
                        mExpandedViewContainer.setAlpha(0f);
                        updateExpandedView();
                        updateOverflowVisibility();
                        updatePointerPosition(false);
                        requestUpdate();
                        if (mShowingManage) {
                            // if we're showing the menu after rotation, post it to the looper
                            // to make sure that the location of the menu button is correct
                            post(() -> showManageMenu(true));
                        } else {
                            showManageMenu(false);
                        }

                        PointF p = mPositioner.getExpandedBubbleXY(getBubbleIndex(mExpandedBubble),
                                getState());
                        final float translationY = mPositioner.getExpandedViewY(mExpandedBubble,
                                mPositioner.showBubblesVertically() ? p.y : p.x);
                        mExpandedViewContainer.setTranslationX(0f);
                        mExpandedViewContainer.setTranslationY(translationY);
                        mExpandedViewContainer.setAlpha(1f);
                    }

                    removeOnLayoutChangeListener(mOrientationChangedListener);
                };
        final float maxDismissSize = getResources().getDimensionPixelSize(
                R.dimen.dismiss_circle_size);
        final float minDismissSize = getResources().getDimensionPixelSize(
                R.dimen.dismiss_circle_small);
        final float sizePercent = minDismissSize / maxDismissSize;
        mDismissBubbleAnimator = ValueAnimator.ofFloat(1f, 0f);
        mDismissBubbleAnimator.addUpdateListener(animation -> {
            final float animatedValue = (float) animation.getAnimatedValue();
            if (mDismissView != null) {
                mDismissView.setPivotX((mDismissView.getRight() - mDismissView.getLeft()) / 2f);
                mDismissView.setPivotY((mDismissView.getBottom() - mDismissView.getTop()) / 2f);
                final float scaleValue = Math.max(animatedValue, sizePercent);
                mDismissView.getCircle().setScaleX(scaleValue);
                mDismissView.getCircle().setScaleY(scaleValue);
            }
            if (mViewBeingDismissed != null) {
                mViewBeingDismissed.setAlpha(Math.max(animatedValue, 0.7f));
            }
        });

        // If the stack itself is clicked, it means none of its touchable views (bubbles, flyouts,
        // TaskView, etc.) were touched. Collapse the stack if it's expanded.
        setOnClickListener(view -> {
            if (mShowingManage) {
                showManageMenu(false /* show */);
            } else if (isManageEduVisible()) {
                mManageEduView.hide();
            } else if (isStackEduVisible()) {
                mStackEduView.hide(false /* isExpanding */);
            } else if (mBubbleData.isExpanded()) {
                mBubbleData.setExpanded(false);
            } else {
                maybeShowStackEdu();
            }
            onDraggingEnded();
        });

        animate()
                .setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED)
                .setDuration(FADE_IN_DURATION);

        mExpandedViewAlphaAnimator.setDuration(EXPANDED_VIEW_ALPHA_ANIMATION_DURATION);
        mExpandedViewAlphaAnimator.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
        mExpandedViewAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                BubbleExpandedView expandedView = getExpandedView();
                if (expandedView != null) {
                    // We need to be Z ordered on top in order for alpha animations to work.
                    expandedView.setSurfaceZOrderedOnTop(true);
                    expandedView.setAnimating(true);
                    mExpandedViewContainer.setVisibility(VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                BubbleExpandedView expandedView = getExpandedView();
                if (expandedView != null
                        // The surface needs to be Z ordered on top for alpha values to work on the
                        // TaskView, and if we're temporarily hidden, we are still on the screen
                        // with alpha = 0f until we animate back. Stay Z ordered on top so the alpha
                        // = 0f remains in effect.
                        && !mExpandedViewTemporarilyHidden) {
                    expandedView.setSurfaceZOrderedOnTop(false);
                    expandedView.setAnimating(false);
                }
            }
        });
        mExpandedViewAlphaAnimator.addUpdateListener(valueAnimator -> {
            BubbleExpandedView expandedView = getExpandedView();
            if (expandedView != null) {
                float alpha = (float) valueAnimator.getAnimatedValue();
                expandedView.setContentAlpha(alpha);
                expandedView.setBackgroundAlpha(alpha);
            }
        });

        mAnimatingOutSurfaceAlphaAnimator.setDuration(EXPANDED_VIEW_ALPHA_ANIMATION_DURATION);
        mAnimatingOutSurfaceAlphaAnimator.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
        mAnimatingOutSurfaceAlphaAnimator.addUpdateListener(valueAnimator -> {
            if (!mExpandedViewTemporarilyHidden) {
                mAnimatingOutSurfaceView.setAlpha((float) valueAnimator.getAnimatedValue());
            }
        });
        mAnimatingOutSurfaceAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                releaseAnimatingOutBubbleBuffer();
            }
        });
    }

    /**
     * Reset state related to dragging.
     */
    private void onDraggingEnded() {
        mIsDraggingStack = false;
        mMagnetizedObject = null;
    }

    /**
     * Sets whether or not the stack should become temporarily invisible by moving off the side of
     * the screen.
     *
     * If a flyout comes in while it's invisible, it will animate back in while the flyout is
     * showing but disappear again when the flyout is gone.
     */
    public void setTemporarilyInvisible(boolean invisible) {
        mTemporarilyInvisible = invisible;

        // If we are animating out, hide immediately if possible so we animate out with the status
        // bar.
        updateTemporarilyInvisibleAnimation(invisible /* hideImmediately */);
    }

    /**
     * Animates the stack to be temporarily invisible, if needed.
     *
     * If we're currently dragging the stack, or a flyout is visible, the stack will remain visible.
     * regardless of the value of {@link #mTemporarilyInvisible}. This method is called on ACTION_UP
     * as well as whenever a flyout hides, so we will animate invisible at that point if needed.
     */
    private void updateTemporarilyInvisibleAnimation(boolean hideImmediately) {
        removeCallbacks(mAnimateTemporarilyInvisibleImmediate);

        if (mIsDraggingStack) {
            // If we're dragging the stack, don't animate it invisible.
            return;
        }

        final boolean shouldHide =
                mTemporarilyInvisible && mFlyout.getVisibility() != View.VISIBLE;

        postDelayed(mAnimateTemporarilyInvisibleImmediate,
                shouldHide && !hideImmediately ? ANIMATE_TEMPORARILY_INVISIBLE_DELAY : 0);
    }

    private final Runnable mAnimateTemporarilyInvisibleImmediate = () -> {
        if (mTemporarilyInvisible && mFlyout.getVisibility() != View.VISIBLE) {
            // To calculate a distance, bubble stack needs to be moved to become hidden,
            // we need to take into account that the bubble stack is positioned on the edge
            // of the available screen rect, which can be offset by system bars and cutouts.
            if (mStackAnimationController.isStackOnLeftSide()) {
                int availableRectOffsetX =
                        mPositioner.getAvailableRect().left - mPositioner.getScreenRect().left;
                mBubbleContainer
                        .animate()
                        .translationX(-(mBubbleSize + availableRectOffsetX))
                        .start();
            } else {
                int availableRectOffsetX =
                        mPositioner.getAvailableRect().right - mPositioner.getScreenRect().right;
                mBubbleContainer.animate().translationX(mBubbleSize - availableRectOffsetX).start();
            }
        } else {
            mBubbleContainer.animate().translationX(0).start();
        }
    };

    /**
     * Animates the bubble stack to stash along the edge of the screen.
     *
     * @param stashImmediately whether the stash should happen immediately or without delay.
     */
    private void animateStashedState(boolean stashImmediately) {
        if (!Flags.enableBubbleStashing()) return;

        removeCallbacks(mAnimateStashedState);

        postDelayed(mAnimateStashedState, stashImmediately ? 0 : ANIMATE_STASH_DELAY);
    }

    private final Runnable mAnimateStashedState = () -> {
        if (mFlyout.getVisibility() != View.VISIBLE
                && !mIsDraggingStack
                && !isExpansionAnimating()
                && !isExpanded()
                && !isStackEduVisible()) {
            // To calculate a distance, bubble stack needs to be moved to become stashed,
            // we need to take into account that the bubble stack is positioned on the edge
            // of the available screen rect, which can be offset by system bars and cutouts.
            final float amountOffscreen = mBubbleSize - (mBubbleSize * PERCENT_HIDDEN_WHEN_STASHED);
            if (mStackAnimationController.isStackOnLeftSide()) {
                int availableRectOffsetX =
                        mPositioner.getAvailableRect().left - mPositioner.getScreenRect().left;
                mBubbleContainer
                        .animate()
                        .translationX(-(amountOffscreen + availableRectOffsetX))
                        .start();
            } else {
                int availableRectOffsetX =
                        mPositioner.getAvailableRect().right - mPositioner.getScreenRect().right;
                mBubbleContainer.animate()
                        .translationX(amountOffscreen - availableRectOffsetX)
                        .start();
            }
        }
    };

    private void setUpOverflow() {
        resetOverflowView();
        mBubbleContainer.addView(mBubbleOverflow.getIconView(),
                mBubbleContainer.getChildCount() /* index */,
                new FrameLayout.LayoutParams(mBubbleSize, mBubbleSize));
        updateOverflow();
        mBubbleOverflow.getIconView().setOnClickListener((View v) -> {
            mBubbleData.setShowingOverflow(true);
            mBubbleData.setSelectedBubble(mBubbleOverflow);
            mBubbleData.setExpanded(true);
        });
    }

    private void setUpDismissView() {
        if (mDismissView != null) {
            removeView(mDismissView);
        }
        mDismissView = new DismissView(getContext());
        DismissViewUtils.setup(mDismissView);
        int elevation = getResources().getDimensionPixelSize(R.dimen.bubble_elevation);

        addView(mDismissView);
        mDismissView.setElevation(elevation);

        final ContentResolver contentResolver = getContext().getContentResolver();
        final int dismissRadius = Settings.Secure.getInt(
                contentResolver, "bubble_dismiss_radius", mBubbleSize * 2 /* default */);

        // Save the MagneticTarget instance for the newly set up view - we'll add this to the
        // MagnetizedObjects when the dismiss view gets shown.
        mMagneticTarget = new MagnetizedObject.MagneticTarget(
                mDismissView.getCircle(), dismissRadius);
        mBubbleContainer.bringToFront();
    }

    // TODO: Create ManageMenuView and move setup / animations there
    private void setUpManageMenu() {
        if (mManageMenu != null) {
            removeView(mManageMenu);
        }

        mManageMenu = (ViewGroup) LayoutInflater.from(getContext()).inflate(
                R.layout.bubble_manage_menu, this, false);
        mManageMenu.setVisibility(View.INVISIBLE);

        final TypedArray ta = mContext.obtainStyledAttributes(new int[]{
                com.android.internal.R.attr.materialColorSurfaceBright});
        final int menuBackgroundColor = ta.getColor(0, Color.WHITE);
        ta.recycle();
        mManageMenu.getBackground().setColorFilter(menuBackgroundColor, PorterDuff.Mode.SRC_IN);

        PhysicsAnimator.getInstance(mManageMenu).setDefaultSpringConfig(mManageSpringConfig);

        mManageMenu.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerRadius);
            }
        });
        mManageMenu.setClipToOutline(true);

        mManageMenu.findViewById(R.id.bubble_manage_menu_dismiss_container).setOnClickListener(
                view -> {
                    showManageMenu(false /* show */);
                    dismissBubbleIfExists(mBubbleData.getSelectedBubble());
                });

        mManageMenu.findViewById(R.id.bubble_manage_menu_dont_bubble_container).setOnClickListener(
                view -> {
                    showManageMenu(false /* show */);
                    mUnbubbleConversationCallback.accept(mBubbleData.getSelectedBubble().getKey());
                });

        mManageDontBubbleView = mManageMenu
                .findViewById(R.id.bubble_manage_menu_dont_bubble_container);

        mManageSettingsView = mManageMenu.findViewById(R.id.bubble_manage_menu_settings_container);
        mManageSettingsView.setOnClickListener(
                view -> {
                    showManageMenu(false /* show */);
                    final BubbleViewProvider bubble = mBubbleData.getSelectedBubble();
                    if (bubble != null && mBubbleData.hasBubbleInStackWithKey(bubble.getKey())) {
                        // If it's in the stack it's a proper Bubble.
                        final Intent intent = ((Bubble) bubble).getSettingsIntent(mContext);
                        mBubbleData.setExpanded(false);
                        mContext.startActivityAsUser(intent, ((Bubble) bubble).getUser());
                        logBubbleEvent(bubble,
                                FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__HEADER_GO_TO_SETTINGS);
                    }
                });

        mManageSettingsIcon = mManageMenu.findViewById(R.id.bubble_manage_menu_settings_icon);
        mManageSettingsText = mManageMenu.findViewById(R.id.bubble_manage_menu_settings_name);

        // The menu itself should respect locale direction so the icons are on the correct side.
        mManageMenu.setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
        addView(mManageMenu);
        updateManageButtonListener();
    }

    /**
     * Whether the selected bubble is conversation bubble
     */
    private boolean isConversationBubble() {
        BubbleViewProvider bubble = mBubbleData.getSelectedBubble();
        return bubble instanceof Bubble && ((Bubble) bubble).isConversation();
    }

    /**
     * Whether the educational view should show for the expanded view "manage" menu.
     */
    private boolean shouldShowManageEdu() {
        if (!isConversationBubble()) {
            // We only show user education for conversation bubbles right now
            return false;
        }
        final boolean seen = getPrefBoolean(ManageEducationView.PREF_MANAGED_EDUCATION);
        final boolean shouldShow = (!seen || BubbleDebugConfig.forceShowUserEducation(mContext))
                && getExpandedView() != null;
        ProtoLog.d(WM_SHELL_BUBBLES, "Show manage edu=%b", shouldShow);
        if (shouldShow && BubbleDebugConfig.neverShowUserEducation(mContext)) {
            Log.w(TAG, "Want to show manage edu, but it is forced hidden");
            return false;
        }
        return shouldShow;
    }

    /**
     * Show manage education if should show and was not showing before.
     */
    private void maybeShowManageEdu() {
        if (!shouldShowManageEdu()) {
            return;
        }
        if (mManageEduView == null) {
            mManageEduView = new ManageEducationView(mContext, mPositioner);
            addView(mManageEduView);
        }
        showManageEdu();
    }

    /**
     * Show manage education if was not showing before.
     */
    private void showManageEdu() {
        BubbleExpandedView expandedView = getExpandedView();
        if (expandedView == null) return;
        mManageEduView.show(expandedView, mStackAnimationController.isStackOnLeftSide());
    }

    @VisibleForTesting
    public boolean isManageEduVisible() {
        return mManageEduView != null && mManageEduView.getVisibility() == VISIBLE;
    }

    /**
     * Whether education view should show for the collapsed stack.
     */
    private boolean shouldShowStackEdu() {
        if (!isConversationBubble()) {
            // We only show user education for conversation bubbles right now
            return false;
        }
        final boolean seen = getPrefBoolean(StackEducationView.PREF_STACK_EDUCATION);
        final boolean shouldShow = !seen || BubbleDebugConfig.forceShowUserEducation(mContext);
        ProtoLog.d(WM_SHELL_BUBBLES, "Show stack edu=%b", shouldShow);
        if (shouldShow && BubbleDebugConfig.neverShowUserEducation(mContext)) {
            Log.w(TAG, "Want to show stack edu, but it is forced hidden");
            return false;
        }
        return shouldShow;
    }

    private boolean getPrefBoolean(String key) {
        return mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE)
                .getBoolean(key, false /* default */);
    }

    /**
     * @return true if education view for collapsed stack should show and was not showing before.
     */
    private boolean maybeShowStackEdu() {
        if (!shouldShowStackEdu() || isExpanded()) {
            return false;
        }
        if (mStackEduView == null) {
            mStackEducationViewManager = mManager::updateWindowFlagsForBackpress;
            mStackEduView =
                    new StackEducationView(mContext, mPositioner, mStackEducationViewManager);
            addView(mStackEduView);
        }
        return showStackEdu();
    }

    /**
     * @return true if education view for the collapsed stack was not showing before.
     */
    private boolean showStackEdu() {
        // Stack appears on top of the education views
        mBubbleContainer.bringToFront();
        // Ensure the stack is in the correct spot
        PointF position = mPositioner.getStartPosition(
                mStackAnimationController.isStackOnLeftSide() ? LEFT : RIGHT);
        // Animate stack to the position
        mStackAnimationController.springStackAfterFling(position.x, position.y);
        return mStackEduView.show(position);
    }

    @VisibleForTesting
    public boolean isStackEduVisible() {
        return mStackEduView != null && mStackEduView.getVisibility() == VISIBLE;
    }

    // Recreates & shows the education views. Call when a theme/config change happens.
    private void updateUserEdu() {
        if (isStackEduVisible() && !mStackEduView.isHiding()) {
            removeView(mStackEduView);
            mStackEducationViewManager = mManager::updateWindowFlagsForBackpress;
            mStackEduView =
                    new StackEducationView(mContext, mPositioner, mStackEducationViewManager);
            addView(mStackEduView);
            showStackEdu();
        }
        if (isManageEduVisible()) {
            removeView(mManageEduView);
            mManageEduView = new ManageEducationView(mContext, mPositioner);
            addView(mManageEduView);
            showManageEdu();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpFlyout() {
        if (mFlyout != null) {
            removeView(mFlyout);
        }
        mFlyout = new BubbleFlyoutView(getContext(), mPositioner);
        mFlyout.setVisibility(GONE);
        mFlyout.setOnClickListener(mFlyoutClickListener);
        mFlyout.setOnTouchListener(mFlyoutTouchListener);
        addView(mFlyout, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    void updateFontScale() {
        setUpManageMenu();
        mFlyout.updateFontSize();
        for (Bubble b : mBubbleData.getBubbles()) {
            if (b.getExpandedView() != null) {
                b.getExpandedView().updateFontSize();
            }
        }
        if (mShowingOverflow && mBubbleOverflow != null
                && mBubbleOverflow.getExpandedView() != null) {
            mBubbleOverflow.getExpandedView().updateFontSize();
        }
    }

    void updateLocale() {
        if (mShowingOverflow && mBubbleOverflow != null
                && mBubbleOverflow.getExpandedView() != null) {
            mBubbleOverflow.getExpandedView().updateLocale();
        }
    }

    private void updateOverflow() {
        mBubbleOverflow.update();
        if (mShowingOverflow) {
            mBubbleContainer.reorderView(mBubbleOverflow.getIconView(),
                    mBubbleContainer.getChildCount() - 1 /* index */);
        }
        updateOverflowVisibility();
    }

    private void updateOverflowVisibility() {
        int visibility = GONE;
        if (mShowingOverflow) {
            if (mIsExpanded || mBubbleData.isShowingOverflow()) {
                visibility = VISIBLE;
            }
        }
        if (Flags.enableRetrievableBubbles()) {
            if (BubbleOverflow.KEY.equals(mBubbleData.getSelectedBubbleKey())
                    && !mBubbleData.hasBubbles()) {
                // Hide overflow bubble icon if it is the only bubble
                visibility = GONE;
            }
        }
        mBubbleOverflow.setVisible(visibility);
    }

    private void updateOverflowDotVisibility(boolean expanding) {
        if (mShowingOverflow && mBubbleOverflow.showDot()) {
            mBubbleOverflow.getIconView().animateDotScale(expanding ? 1 : 0f, () -> {
                mBubbleOverflow.setVisible(expanding ? VISIBLE : GONE);
            });
        }
    }

    /**  Sets whether the overflow should be visible or not. */
    public void showOverflow(boolean showOverflow) {
        if (!Flags.enableOptionalBubbleOverflow()) return;
        if (mShowingOverflow != showOverflow) {
            mShowingOverflow = showOverflow;
            if (showOverflow) {
                setUpOverflow();
            } else if (mBubbleOverflow != null) {
                resetOverflowView();
            }
        }
    }

    /**
     * Handle theme changes.
     */
    public void onThemeChanged() {
        setUpFlyout();
        setUpManageMenu();
        setUpDismissView();
        updateOverflow();
        updateUserEdu();
        updateExpandedViewTheme();
        mScrim.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));
        mManageMenuScrim.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(android.R.color.system_neutral1_1000)));
    }

    /**
     * Respond to the phone being rotated by repositioning the stack and hiding any flyouts.
     * This is called prior to the rotation occurring, any values that should be updated
     * based on the new rotation should occur in {@link #mOrientationChangedListener}.
     */
    public void onOrientationChanged() {
        mRelativeStackPositionBeforeRotation = new RelativeStackPosition(
                mPositioner.getRestingPosition(),
                mPositioner.getAllowableStackPositionRegion(getBubbleCount()));
        addOnLayoutChangeListener(mOrientationChangedListener);
        hideFlyoutImmediate();
    }

    /** Tells the views with locale-dependent layout direction to resolve the new direction. */
    public void onLayoutDirectionChanged(int direction) {
        mManageMenu.setLayoutDirection(direction);
        mFlyout.setLayoutDirection(direction);
        if (mStackEduView != null) {
            mStackEduView.setLayoutDirection(direction);
        }
        if (mManageEduView != null) {
            mManageEduView.setLayoutDirection(direction);
        }
        updateExpandedViewDirection(direction);
    }

    /** Respond to the display size change by recalculating view size and location. */
    public void onDisplaySizeChanged() {
        updateOverflow();
        setUpFlyout();
        setUpDismissView();
        updateUserEdu();
        mBubbleSize = mPositioner.getBubbleSize();
        for (Bubble b : mBubbleData.getBubbles()) {
            if (b.getIconView() == null) {
                Log.w(TAG, "Display size changed. Icon null: " + b);
                continue;
            }
            b.getIconView().setLayoutParams(new LayoutParams(mBubbleSize, mBubbleSize));
            if (b.getExpandedView() != null) {
                b.getExpandedView().updateDimensions();
            }
        }
        if (mShowingOverflow) {
            mBubbleOverflow.getIconView().setLayoutParams(
                    new LayoutParams(mBubbleSize, mBubbleSize));
        }
        mExpandedAnimationController.updateResources();
        mStackAnimationController.updateResources();
        mDismissView.updateResources();
        mMagneticTarget.setMagneticFieldRadiusPx(mBubbleSize * 2);
        if (!isStackEduVisible()) {
            mStackAnimationController.setStackPosition(
                    new RelativeStackPosition(
                            mPositioner.getRestingPosition(),
                            mPositioner.getAllowableStackPositionRegion(getBubbleCount())));
        }
        if (mIsExpanded) {
            updateExpandedView();
        }
        setUpManageMenu();
        if (mShowingManage) {
            // the manage menu location depends on the manage button location which may need a
            // layout pass, so post this to the looper
            post(() -> showManageMenu(true));
        }
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
        inoutInfo.setTouchableInsets(ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION);

        mTempRect.setEmpty();
        getTouchableRegion(mTempRect);
        inoutInfo.touchableRegion.set(mTempRect);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowManager windowManager = mContext.getSystemService(WindowManager.class);
        mPositioner.update(DeviceConfig.create(mContext, Objects.requireNonNull(windowManager)));
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
        getViewTreeObserver().addOnDrawListener(mSystemGestureExcludeUpdater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(mViewUpdater);
        getViewTreeObserver().removeOnDrawListener(mSystemGestureExcludeUpdater);
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
    }

    @Override
    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoInternal(info);
        setupLocalMenu(info);
    }

    void updateExpandedViewTheme() {
        final List<Bubble> bubbles = mBubbleData.getBubbles();
        if (bubbles.isEmpty()) {
            return;
        }
        bubbles.forEach(bubble -> {
            if (bubble.getExpandedView() != null) {
                bubble.getExpandedView().applyThemeAttrs();
            }
        });
    }

    void updateExpandedViewDirection(int direction) {
        final List<Bubble> bubbles = mBubbleData.getBubbles();
        if (bubbles.isEmpty()) {
            return;
        }
        bubbles.forEach(bubble -> {
            if (bubble.getExpandedView() != null) {
                bubble.getExpandedView().setLayoutDirection(direction);
            }
        });
    }

    void setupLocalMenu(AccessibilityNodeInfo info) {
        Resources res = mContext.getResources();

        // Custom local actions.
        AccessibilityAction moveTopLeft = new AccessibilityAction(R.id.action_move_top_left,
                res.getString(R.string.bubble_accessibility_action_move_top_left));
        info.addAction(moveTopLeft);

        AccessibilityAction moveTopRight = new AccessibilityAction(R.id.action_move_top_right,
                res.getString(R.string.bubble_accessibility_action_move_top_right));
        info.addAction(moveTopRight);

        AccessibilityAction moveBottomLeft = new AccessibilityAction(R.id.action_move_bottom_left,
                res.getString(R.string.bubble_accessibility_action_move_bottom_left));
        info.addAction(moveBottomLeft);

        AccessibilityAction moveBottomRight = new AccessibilityAction(R.id.action_move_bottom_right,
                res.getString(R.string.bubble_accessibility_action_move_bottom_right));
        info.addAction(moveBottomRight);

        // Default actions.
        info.addAction(AccessibilityAction.ACTION_DISMISS);
        if (mIsExpanded) {
            info.addAction(AccessibilityAction.ACTION_COLLAPSE);
        } else {
            info.addAction(AccessibilityAction.ACTION_EXPAND);
        }
    }

    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (super.performAccessibilityActionInternal(action, arguments)) {
            return true;
        }
        final RectF stackBounds = mPositioner.getAllowableStackPositionRegion(getBubbleCount());

        // R constants are not final so we cannot use switch-case here.
        if (action == AccessibilityNodeInfo.ACTION_DISMISS) {
            mBubbleData.dismissAll(Bubbles.DISMISS_ACCESSIBILITY_ACTION);
            announceForAccessibility(
                    getResources().getString(R.string.accessibility_bubble_dismissed));
            return true;
        } else if (action == AccessibilityNodeInfo.ACTION_COLLAPSE) {
            mBubbleData.setExpanded(false);
            return true;
        } else if (action == AccessibilityNodeInfo.ACTION_EXPAND) {
            mBubbleData.setExpanded(true);
            return true;
        } else if (action == R.id.action_move_top_left) {
            mStackAnimationController.springStackAfterFling(stackBounds.left, stackBounds.top);
            return true;
        } else if (action == R.id.action_move_top_right) {
            mStackAnimationController.springStackAfterFling(stackBounds.right, stackBounds.top);
            return true;
        } else if (action == R.id.action_move_bottom_left) {
            mStackAnimationController.springStackAfterFling(stackBounds.left, stackBounds.bottom);
            return true;
        } else if (action == R.id.action_move_bottom_right) {
            mStackAnimationController.springStackAfterFling(stackBounds.right, stackBounds.bottom);
            return true;
        }
        return false;
    }

    /**
     * Update content description for a11y TalkBack.
     */
    public void updateContentDescription() {
        if (mBubbleData.getBubbles().isEmpty()) {
            return;
        }

        for (int i = 0; i < mBubbleData.getBubbles().size(); i++) {
            final Bubble bubble = mBubbleData.getBubbles().get(i);
            final String appName = bubble.getAppName();

            String titleStr = bubble.getTitle();
            if (titleStr == null) {
                titleStr = getResources().getString(R.string.notification_bubble_title);
            }

            if (bubble.getIconView() != null) {
                if (mIsExpanded || i > 0) {
                    bubble.getIconView().setContentDescription(getResources().getString(
                            R.string.bubble_content_description_single, titleStr, appName));
                } else {
                    final int moreCount = getBubbleCount();
                    bubble.getIconView().setContentDescription(getResources().getString(
                            R.string.bubble_content_description_stack,
                            titleStr, appName, moreCount));
                }
            }
        }
    }

    /**
     * Update bubbles' icon views accessibility states.
     */
    public void updateBubblesAcessibillityStates() {
        for (int i = 0; i < mBubbleData.getBubbles().size(); i++) {
            Bubble prevBubble = i > 0 ? mBubbleData.getBubbles().get(i - 1) : null;
            Bubble bubble = mBubbleData.getBubbles().get(i);

            View bubbleIconView = bubble.getIconView();
            if (bubbleIconView == null) {
                continue;
            }

            if (mIsExpanded) {
                // when stack is expanded
                // all bubbles are important for accessibility
                bubbleIconView
                        .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

                View prevBubbleIconView = prevBubble != null ? prevBubble.getIconView() : null;

                if (prevBubbleIconView != null) {
                    bubbleIconView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(View v,
                                AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(v, info);
                            info.setTraversalAfter(prevBubbleIconView);
                        }
                    });
                }
            } else {
                // when stack is collapsed, only the top bubble is important for accessibility,
                bubbleIconView.setImportantForAccessibility(
                        i == 0 ? View.IMPORTANT_FOR_ACCESSIBILITY_YES :
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }

        if (mIsExpanded) {
            // make the overflow bubble last in the accessibility traversal order

            View bubbleOverflowIconView =
                    mBubbleOverflow != null ? mBubbleOverflow.getIconView() : null;
            if (mShowingOverflow && bubbleOverflowIconView != null
                    && !mBubbleData.getBubbles().isEmpty()) {
                Bubble lastBubble =
                        mBubbleData.getBubbles().get(mBubbleData.getBubbles().size() - 1);
                View lastBubbleIconView = lastBubble.getIconView();
                if (lastBubbleIconView != null) {
                    bubbleOverflowIconView.setAccessibilityDelegate(
                            new View.AccessibilityDelegate() {
                                @Override
                                public void onInitializeAccessibilityNodeInfo(View v,
                                        AccessibilityNodeInfo info) {
                                    super.onInitializeAccessibilityNodeInfo(v, info);
                                    info.setTraversalAfter(lastBubbleIconView);
                                }
                            });
                }
            }
        }
    }

    private void updateSystemGestureExcludeRects() {
        // Exclude the region occupied by the first BubbleView in the stack
        Rect excludeZone = mSystemGestureExclusionRects.get(0);
        if (getBubbleCount() > 0) {
            View firstBubble = mBubbleContainer.getChildAt(0);
            excludeZone.set(firstBubble.getLeft(), firstBubble.getTop(), firstBubble.getRight(),
                    firstBubble.getBottom());
            excludeZone.offset((int) (firstBubble.getTranslationX() + 0.5f),
                    (int) (firstBubble.getTranslationY() + 0.5f));
            mBubbleContainer.setSystemGestureExclusionRects(mSystemGestureExclusionRects);
        } else {
            excludeZone.setEmpty();
            mBubbleContainer.setSystemGestureExclusionRects(Collections.emptyList());
        }
    }

    /**
     * Sets the listener to notify when the bubble stack is expanded.
     */
    public void setExpandListener(Bubbles.BubbleExpandListener listener) {
        mExpandListener = listener;
    }

    /** Sets the function to call to un-bubble the given conversation. */
    public void setUnbubbleConversationCallback(
            Consumer<String> unbubbleConversationCallback) {
        mUnbubbleConversationCallback = unbubbleConversationCallback;
    }

    /**
     * Whether the stack of bubbles is expanded or not.
     */
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Whether the stack of bubbles is animating to or from expansion.
     */
    public boolean isExpansionAnimating() {
        return mIsExpansionAnimating;
    }

    /**
     * Whether the stack of bubbles is animating a switch between bubbles.
     */
    public boolean isSwitchAnimating() {
        return mIsBubbleSwitchAnimating;
    }

    /**
     * The {@link Bubble} that is expanded, null if one does not exist.
     */
    @VisibleForTesting
    @Nullable
    public BubbleViewProvider getExpandedBubble() {
        return mExpandedBubble;
    }

    @Nullable
    private BubbleExpandedView getExpandedView() {
        return mExpandedBubble != null ? mExpandedBubble.getExpandedView() : null;
    }

    // via BubbleData.Listener
    @SuppressLint("ClickableViewAccessibility")
    void addBubble(Bubble bubble) {
        final boolean firstBubble = getBubbleCount() == 0;

        if (firstBubble && shouldShowStackEdu()) {
            // Override the default stack position if we're showing user education.
            mStackAnimationController.setStackPosition(mPositioner.getDefaultStartPosition());
        }

        if (bubble.getIconView() == null) {
            return;
        }

        if (firstBubble && bubble.isAppBubble() && !mPositioner.hasUserModifiedDefaultPosition()) {
            // TODO (b/294284894): update language around "app bubble" here
            // If it's an app bubble and we don't have a previous resting position, update the
            // controllers to use the default position for the app bubble (it'd be different from
            // the position initialized with the controllers originally).
            PointF startPosition =  mPositioner.getDefaultStartPosition(true /* isAppBubble */);
            mStackOnLeftOrWillBe = mPositioner.isStackOnLeft(startPosition);
            mStackAnimationController.setStackPosition(startPosition);
            mExpandedAnimationController.setCollapsePoint(startPosition);
        } else if (firstBubble) {
            mStackOnLeftOrWillBe = mStackAnimationController.isStackOnLeftSide();
        }

        // Set the view translation x so that this bubble will animate in from the same side they
        // expand / collapse on.
        bubble.getIconView().setTranslationX(mStackAnimationController.getStackPosition().x);

        mBubbleContainer.addView(bubble.getIconView(), 0,
                new FrameLayout.LayoutParams(mPositioner.getBubbleSize(),
                        mPositioner.getBubbleSize()));

        // Set the dot position to the opposite of the side the stack is resting on, since the stack
        // resting slightly off-screen would result in the dot also being off-screen.
        bubble.getIconView().setDotBadgeOnLeft(!mStackOnLeftOrWillBe /* onLeft */);
        bubble.getIconView().setOnClickListener(mBubbleClickListener);
        bubble.getIconView().setOnTouchListener(mBubbleTouchListener);
        updateBubbleShadows(mIsExpanded);
        animateInFlyoutForBubble(bubble);
        requestUpdate();
        logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__POSTED);
    }

    // via BubbleData.Listener
    void removeBubble(Bubble bubble) {
        if (isExpanded() && getBubbleCount() == 1) {
            mRemovingLastBubbleWhileExpanded = true;
            // We're expanded while the last bubble is being removed. Let the scrim animate away
            // and then remove our views (removing the icon view triggers the removal of the
            // bubble window so do that at the end of the animation so we see the scrim animate).
            BadgedImageView iconView = bubble.getIconView();
            showScrim(false, () -> {
                mRemovingLastBubbleWhileExpanded = false;
                bubble.cleanupExpandedView();
                if (iconView != null) {
                    mBubbleContainer.removeView(iconView);
                }
                bubble.cleanupViews(); // cleans up the icon view
                updateExpandedView(); // resets state for no expanded bubble
                mExpandedBubble = null;
            });
            logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__DISMISSED);
            return;
        } else if (getBubbleCount() == 1) {
            mExpandedBubble = null;
        }
        // Remove it from the views
        for (int i = 0; i < getBubbleCount(); i++) {
            View v = mBubbleContainer.getChildAt(i);
            if (v instanceof BadgedImageView
                    && ((BadgedImageView) v).getKey().equals(bubble.getKey())) {
                mBubbleContainer.removeViewAt(i);
                if (mBubbleData.hasOverflowBubbleWithKey(bubble.getKey())) {
                    bubble.cleanupExpandedView();
                } else {
                    bubble.cleanupViews();
                }
                updateExpandedView();
                if (getBubbleCount() == 0 && !isExpanded()) {
                    // This is the last bubble and the stack is collapsed
                    updateStackPosition();
                }
                logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__DISMISSED);
                return;
            }
        }
        // If a bubble is suppressed, it is not attached to the container. Clean it up.
        if (bubble.isSuppressed()) {
            bubble.cleanupViews();
            logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__DISMISSED);
        } else {
            Log.w(TAG, "was asked to remove Bubble, but didn't find the view! " + bubble);
        }
    }

    // via BubbleData.Listener
    void updateBubble(Bubble bubble) {
        animateInFlyoutForBubble(bubble);
        requestUpdate();
        logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__UPDATED);
    }

    /**
     * Update bubble order and pointer position.
     */
    public void updateBubbleOrder(List<Bubble> bubbles, boolean updatePointerPosition) {
        // Don't reorder bubbles in the middle of a gesture because that would remove bubbles from
        // view hierarchy and will cancel all touch events. Instead wait until the gesture is
        // finished and then reorder.
        if (mIsGestureInProgress) {
            mShouldReorderBubblesAfterGestureCompletes = true;
            return;
        }
        updateBubbleOrderInternal(bubbles, updatePointerPosition);
    }

    private void updateBubbleOrderInternal(List<Bubble> bubbles, boolean updatePointerPosition) {
        final Runnable reorder = () -> {
            for (int i = 0; i < bubbles.size(); i++) {
                Bubble bubble = bubbles.get(i);
                mBubbleContainer.reorderView(bubble.getIconView(), i);
            }
        };
        if (mIsExpanded || isExpansionAnimating()) {
            reorder.run();
            updateBadges(false /* setBadgeForCollapsedStack */);
            updateBubbleShadows(true /* isExpanded */);
        } else {
            List<View> bubbleViews = bubbles.stream()
                    .map(b -> b.getIconView()).collect(Collectors.toList());
            mStackAnimationController.animateReorder(bubbleViews, reorder);
        }

        if (updatePointerPosition) {
            updatePointerPosition(false /* forIme */);
        }
    }

    /**
     * Changes the currently selected bubble. If the stack is already expanded, the newly selected
     * bubble will be shown immediately. This does not change the expanded state or change the
     * position of any bubble.
     */
    // via BubbleData.Listener
    public void setSelectedBubble(@Nullable BubbleViewProvider bubbleToSelect) {
        if (bubbleToSelect == null) {
            mBubbleData.setShowingOverflow(false);
            return;
        }

        // Ignore this new bubble only if it is the exact same bubble object. Otherwise, we'll want
        // to re-render it even if it has the same key (equals() returns true). If the currently
        // expanded bubble is removed and instantly re-added, we'll get back a new Bubble instance
        // with the same key (with newly inflated expanded views), and we need to render those new
        // views.
        if (mExpandedBubble == bubbleToSelect) {
            return;
        }

        if (bubbleToSelect.getKey().equals(BubbleOverflow.KEY)) {
            mBubbleData.setShowingOverflow(true);
        } else {
            mBubbleData.setShowingOverflow(false);
        }

        if (mIsExpanded && mIsExpansionAnimating) {
            // If the bubble selection changed during the expansion animation, the expanding bubble
            // probably crashed or immediately removed itself (or, we just got unlucky with a new
            // auto-expanding bubble showing up at just the right time). Cancel the animations so we
            // can start fresh.
            cancelAllExpandCollapseSwitchAnimations();
        }
        showManageMenu(false /* show */);

        // If we're expanded, screenshot the currently expanded bubble (before expanding the newly
        // selected bubble) so we can animate it out.
        BubbleExpandedView expandedView = getExpandedView();
        if (mIsExpanded && expandedView != null && !mExpandedViewTemporarilyHidden) {
            // Before screenshotting, have the real TaskView show on top of other surfaces
            // so that the screenshot doesn't flicker on top of it.
            expandedView.setSurfaceZOrderedOnTop(true);

            try {
                screenshotAnimatingOutBubbleIntoSurface((success) -> {
                    mAnimatingOutSurfaceContainer.setVisibility(
                            success ? View.VISIBLE : View.INVISIBLE);
                    showNewlySelectedBubble(bubbleToSelect);
                });
            } catch (Exception e) {
                showNewlySelectedBubble(bubbleToSelect);
                e.printStackTrace();
            }
        } else {
            showNewlySelectedBubble(bubbleToSelect);
        }
    }

    private void showNewlySelectedBubble(BubbleViewProvider bubbleToSelect) {
        final BubbleViewProvider previouslySelected = mExpandedBubble;
        mExpandedBubble = bubbleToSelect;
        mExpandedViewAnimationController.setExpandedView(getExpandedView());

        if (mIsExpanded) {
            hideCurrentInputMethod();

            if (Flags.enableRetrievableBubbles()) {
                if (mBubbleData.getBubbles().size() == 1) {
                    // First bubble, check if overflow visibility needs to change
                    updateOverflowVisibility();
                }
            }

            // Make the container of the expanded view transparent before removing the expanded view
            // from it. Otherwise a punch hole created by {@link android.view.SurfaceView} in the
            // expanded view becomes visible on the screen. See b/126856255
            mExpandedViewContainer.setAlpha(0.0f);
            mSurfaceSynchronizer.syncSurfaceAndRun(() -> {
                if (previouslySelected != null) {
                    previouslySelected.setTaskViewVisibility(false);
                }

                updateExpandedBubble();
                requestUpdate();

                logBubbleEvent(previouslySelected,
                        FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__COLLAPSED);
                logBubbleEvent(bubbleToSelect,
                        FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__EXPANDED);
                notifyExpansionChanged(previouslySelected, false /* expanded */);
                notifyExpansionChanged(bubbleToSelect, true /* expanded */);
            });
        }
    }

    /**
     * Changes the expanded state of the stack.
     * Don't call this directly, call mBubbleData#setExpanded.
     *
     * @param shouldExpand whether the bubble stack should appear expanded
     */
    // via BubbleData.Listener
    public void setExpanded(boolean shouldExpand) {
        if (!shouldExpand) {
            // If we're collapsing, release the animating-out surface immediately since we have no
            // need for it, and this ensures it cannot remain visible as we collapse.
            releaseAnimatingOutBubbleBuffer();
        }

        if (shouldExpand == mIsExpanded) {
            return;
        }

        boolean wasExpanded = mIsExpanded;

        hideCurrentInputMethod();

        mSysuiProxyProvider.getSysuiProxy().onStackExpandChanged(shouldExpand);

        if (wasExpanded) {
            stopMonitoringSwipeUpGesture();
            animateCollapse();
            showManageMenu(false);
            logBubbleEvent(mExpandedBubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__COLLAPSED);
        } else {
            animateExpansion();
            // TODO: move next line to BubbleData
            logBubbleEvent(mExpandedBubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__EXPANDED);
            logBubbleEvent(mExpandedBubble,
                    FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__STACK_EXPANDED);
            mManager.checkNotificationPanelExpandedState(notifPanelExpanded -> {
                if (!notifPanelExpanded && mIsExpanded) {
                    startMonitoringSwipeUpGesture();
                }
            });
        }
        notifyExpansionChanged(mExpandedBubble, mIsExpanded);
        announceExpandForAccessibility(mExpandedBubble, mIsExpanded);
    }

    /**
     * Check if we only have overflow expanded. Which is the case when we are launching bubbles from
     * background.
     */
    private boolean isOnlyOverflowExpanded() {
        boolean overflowExpanded = mExpandedBubble != null && BubbleOverflow.KEY.equals(
                mExpandedBubble.getKey());
        return overflowExpanded && !mBubbleData.hasBubbles();
    }

    /**
     * Monitor for swipe up gesture that is used to collapse expanded view
     */
    void startMonitoringSwipeUpGesture() {
        stopMonitoringSwipeUpGestureInternal();

        if (isGestureNavEnabled()) {
            mBubblesNavBarGestureTracker = new BubblesNavBarGestureTracker(mContext, mPositioner);
            mBubblesNavBarGestureTracker.start(mSwipeUpListener);
            setOnTouchListener(mContainerSwipeListener);
        }
    }

    private void announceExpandForAccessibility(BubbleViewProvider bubble, boolean expanded) {
        if (bubble instanceof Bubble) {
            String contentDescription = getBubbleContentDescription((Bubble) bubble);
            String message = getResources().getString(
                    expanded
                            ? R.string.bubble_accessibility_announce_expand
                            : R.string.bubble_accessibility_announce_collapse, contentDescription);
            announceForAccessibility(message);
        }
    }

    @NonNull
    private String getBubbleContentDescription(Bubble bubble) {
        final String appName = bubble.getAppName();
        final String title = bubble.getTitle() != null
                ? bubble.getTitle()
                : getResources().getString(R.string.notification_bubble_title);

        if (appName == null || title.equals(appName)) {
            // App bubble title equals the app name, so return only the title to avoid having
            // content description like: `<app> from <app>`.
            return title;
        } else {
            return getResources().getString(
                    R.string.bubble_content_description_single, title, appName);
        }
    }

    private boolean isGestureNavEnabled() {
        return mContext.getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode)
                == WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;
    }

    /**
     * Stop monitoring for swipe up gesture
     */
    void stopMonitoringSwipeUpGesture() {
        stopMonitoringSwipeUpGestureInternal();
    }

    private void stopMonitoringSwipeUpGestureInternal() {
        if (mBubblesNavBarGestureTracker != null) {
            mBubblesNavBarGestureTracker.stop();
            mBubblesNavBarGestureTracker = null;
            setOnTouchListener(null);
        }
    }

    /**
     * Called when back press occurs while bubbles are expanded.
     */
    public void onBackPressed() {
        if (mIsExpanded) {
            if (mShowingManage) {
                showManageMenu(false);
            } else if (isManageEduVisible()) {
                mManageEduView.hide();
            } else {
                mBubbleData.setExpanded(false);
            }
        }
    }

    void setBubbleSuppressed(Bubble bubble, boolean suppressed) {
        if (suppressed) {
            int index = getBubbleIndex(bubble);
            mBubbleContainer.removeViewAt(index);
            updateExpandedView();
        } else {
            if (bubble.getIconView() == null) {
                return;
            }
            if (bubble.getIconView().getParent() != null) {
                Log.e(TAG, "Bubble is already added to parent. Can't unsuppress: " + bubble);
                return;
            }
            int index = mBubbleData.getBubbles().indexOf(bubble);
            // Add the view back to the correct position
            mBubbleContainer.addView(bubble.getIconView(), index,
                    new LayoutParams(mPositioner.getBubbleSize(),
                            mPositioner.getBubbleSize()));
            updateBubbleShadows(mIsExpanded);
            requestUpdate();
        }
    }

    void onSensitiveNotificationProtectionStateChanged(
            boolean sensitiveNotificationProtectionActive) {
        mSensitiveNotificationProtectionActive = sensitiveNotificationProtectionActive;
    }

    /**
     * Asks the BubbleController to hide the IME from anywhere, whether it's focused on Bubbles or
     * not.
     */
    void hideCurrentInputMethod() {
        mManager.hideCurrentInputMethod();
    }

    /** Set the stack position to whatever the positioner says. */
    void updateStackPosition() {
        mStackAnimationController.setStackPosition(mPositioner.getRestingPosition());
        mDismissView.hide();
    }

    private void beforeExpandedViewAnimation() {
        mIsExpansionAnimating = true;
        hideFlyoutImmediate();
        updateExpandedBubble();
        updateExpandedView();
    }

    private void afterExpandedViewAnimation() {
        mIsExpansionAnimating = false;
        updateExpandedView();
        requestUpdate();
    }

    /** Animate the expanded view hidden. This is done while we're dragging out a bubble. */
    private void hideExpandedViewIfNeeded() {
        if (mExpandedViewTemporarilyHidden
                || mExpandedBubble == null
                || mExpandedBubble.getExpandedView() == null) {
            return;
        }

        mExpandedViewTemporarilyHidden = true;

        // Scale down.
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                .spring(AnimatableScaleMatrix.SCALE_X,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(
                                1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT),
                        mScaleOutSpringConfig)
                .spring(AnimatableScaleMatrix.SCALE_Y,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(
                                1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT),
                        mScaleOutSpringConfig)
                .addUpdateListener((target, values) ->
                        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix))
                .start();

        // Animate alpha from 1f to 0f.
        mExpandedViewAlphaAnimator.reverse();
    }

    /**
     * Animate the expanded view visible again. This is done when we're done dragging out a bubble.
     */
    private void showExpandedViewIfNeeded() {
        if (!mExpandedViewTemporarilyHidden) {
            return;
        }

        mExpandedViewTemporarilyHidden = false;

        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                .spring(AnimatableScaleMatrix.SCALE_X,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                        mScaleOutSpringConfig)
                .spring(AnimatableScaleMatrix.SCALE_Y,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                        mScaleOutSpringConfig)
                .addUpdateListener((target, values) ->
                        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix))
                .start();

        mExpandedViewAlphaAnimator.start();
    }

    private void showScrim(boolean show, Runnable after) {
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mScrimAnimation = null;
                if (after != null) {
                    after.run();
                }
            }
        };
        if (mScrimAnimation != null) {
            // Cancel scrim animation if it animates
            mScrimAnimation.cancel();
        }
        if (show) {
            mScrimAnimation = mScrim.animate();
            mScrimAnimation
                    .setInterpolator(ALPHA_IN)
                    .alpha(BUBBLE_EXPANDED_SCRIM_ALPHA)
                    .setListener(listener)
                    .start();
        } else {
            mScrimAnimation = mScrim.animate();
            mScrimAnimation
                    .alpha(0f)
                    .setInterpolator(ALPHA_OUT)
                    .setListener(listener)
                    .start();
        }
    }

    private void animateExpansion() {
        ProtoLog.d(WM_SHELL_BUBBLES, "animateExpansion, expandedBubble=%s",
                mExpandedBubble != null ? mExpandedBubble.getKey() : "null");
        cancelDelayedExpandCollapseSwitchAnimations();

        mIsExpanded = true;
        if (isStackEduVisible()) {
            mStackEduView.hide(true /* fromExpansion */);
        }
        beforeExpandedViewAnimation();

        showScrim(true, null /* runnable */);
        updateBubbleShadows(mIsExpanded);
        mBubbleContainer.setActiveController(mExpandedAnimationController);
        updateOverflowVisibility();

        if (Flags.enableRetrievableBubbles() && isOnlyOverflowExpanded()) {
            animateOverflowExpansion();
        } else {
            animateBubbleExpansion();
        }
    }

    private void animateBubbleExpansion() {
        updateBadges(false /* setBadgeForCollapsedStack */);
        updatePointerPosition(false /* forIme */);
        if (Flags.enableBubbleStashing()) {
            mBubbleContainer.animate().translationX(0).start();
        }
        mExpandedAnimationController.expandFromStack(() -> {
            if (mIsExpanded && getExpandedView() != null) {
                maybeShowManageEdu();
            }
            updateOverflowDotVisibility(true /* expanding */);
        } /* after */);
        int index;
        if (mExpandedBubble != null && BubbleOverflow.KEY.equals(mExpandedBubble.getKey())) {
            index = mBubbleData.getBubbles().size();
        } else {
            index = getBubbleIndex(mExpandedBubble);
        }
        PointF bubbleXY = mPositioner.getExpandedBubbleXY(index, getState());
        final float translationY = mPositioner.getExpandedViewY(mExpandedBubble,
                mPositioner.showBubblesVertically() ? bubbleXY.y : bubbleXY.x);
        mExpandedViewContainer.setTranslationX(0f);
        mExpandedViewContainer.setTranslationY(translationY);
        mExpandedViewContainer.setAlpha(1f);

        final boolean showVertically = mPositioner.showBubblesVertically();
        // How far horizontally the bubble will be animating. We'll wait a bit longer for bubbles
        // that are animating farther, so that the expanded view doesn't move as much.
        final float relevantStackPosition = showVertically
                ? mStackAnimationController.getStackPosition().y
                : mStackAnimationController.getStackPosition().x;
        final float bubbleWillBeAt = showVertically
                ? bubbleXY.y
                : bubbleXY.x;
        final float distanceAnimated = Math.abs(bubbleWillBeAt - relevantStackPosition);

        // Wait for the path animation target to reach its end, and add a small amount of extra time
        // if the bubble is moving a lot horizontally.
        final long startDelay;

        // Should not happen since we lay out before expanding, but just in case...
        if (getWidth() > 0) {
            startDelay = (long)
                    (ExpandedAnimationController.EXPAND_COLLAPSE_TARGET_ANIM_DURATION * 1.2f
                            + (distanceAnimated / getWidth()) * 30);
        } else {
            startDelay = 0L;
        }

        // Set the pivot point for the scale, so the expanded view animates out from the bubble.
        if (showVertically) {
            float pivotX;
            if (mStackOnLeftOrWillBe) {
                pivotX = bubbleXY.x + mBubbleSize + mExpandedViewPadding;
            } else {
                pivotX = bubbleXY.x - mExpandedViewPadding;
            }
            mExpandedViewContainerMatrix.setScale(
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    pivotX,
                    bubbleXY.y + mBubbleSize / 2f);
        } else {
            mExpandedViewContainerMatrix.setScale(
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    bubbleXY.x + mBubbleSize / 2f,
                    bubbleXY.y + mBubbleSize + mExpandedViewPadding);
        }
        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix);

        BubbleExpandedView expandedView = getExpandedView();
        if (expandedView != null) {
            expandedView.setContentAlpha(0f);
            expandedView.setBackgroundAlpha(0f);

            // We'll be starting the alpha animation after a slight delay, so set this flag early
            // here.
            expandedView.setAnimating(true);
        }

        mDelayedAnimation = () -> {
            mExpandedViewAlphaAnimator.start();

            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                    .spring(AnimatableScaleMatrix.SCALE_X,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .spring(AnimatableScaleMatrix.SCALE_Y,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .addUpdateListener((target, values) -> {
                        if (mExpandedBubble == null || mExpandedBubble.getIconView() == null) {
                            return;
                        }
                        float translation = showVertically
                                ? mExpandedBubble.getIconView().getTranslationY()
                                : mExpandedBubble.getIconView().getTranslationX();
                        mExpandedViewContainerMatrix.postTranslate(
                                translation - bubbleWillBeAt,
                                0);
                        mExpandedViewContainer.setAnimationMatrix(
                                mExpandedViewContainerMatrix);
                    })
                    .withEndActions(() -> {
                        mExpandedViewContainer.setAnimationMatrix(null);
                        afterExpandedViewAnimation();
                        BubbleExpandedView expView = getExpandedView();
                        if (expView != null) {
                            expView.setSurfaceZOrderedOnTop(false);
                        }
                    })
                    .start();
        };
        mMainExecutor.executeDelayed(mDelayedAnimation, startDelay);
    }

    /**
     * Animate expansion of overflow view when it is shown from the bubble shortcut.
     * <p>
     * Animates the view with a scale originating from the center of the view.
     */
    private void animateOverflowExpansion() {
        PointF bubbleXY = mPositioner.getExpandedBubbleXY(0, getState());
        final float translationY = mPositioner.getExpandedViewY(mExpandedBubble,
                mPositioner.showBubblesVertically() ? bubbleXY.y : bubbleXY.x);
        mExpandedViewContainer.setTranslationX(0f);
        mExpandedViewContainer.setTranslationY(translationY);
        mExpandedViewContainer.setAlpha(1f);

        boolean stackOnLeft = mPositioner.isStackOnLeft(getStackPosition());
        float width = mPositioner.getTaskViewContentWidth(stackOnLeft);
        float height = mPositioner.getExpandedViewHeight(mExpandedBubble);
        float scale = 1f - OPEN_OVERFLOW_ANIMATE_SCALE_AMOUNT;
        // Scale from the center of the view
        mExpandedViewContainerMatrix.setScale(scale, scale, width / 2f, height / 2f);
        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix);
        mExpandedViewAlphaAnimator.start();
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                .spring(AnimatableScaleMatrix.SCALE_X,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                        mScaleInSpringConfig)
                .spring(AnimatableScaleMatrix.SCALE_Y,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                        mScaleInSpringConfig)
                .addUpdateListener((target, values) -> {
                    mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix);
                }).withEndActions(() -> {
                    mExpandedViewContainer.setAnimationMatrix(null);
                    afterExpandedViewAnimation();
                    BubbleExpandedView expandedView = getExpandedView();
                    if (expandedView != null) {
                        expandedView.setSurfaceZOrderedOnTop(false);
                    }
                }).start();
    }

    private void animateCollapse() {
        cancelDelayedExpandCollapseSwitchAnimations();
        ProtoLog.d(WM_SHELL_BUBBLES, "animateCollapse");
        if (isManageEduVisible()) {
            mManageEduView.hide();
        }

        mIsExpanded = false;
        mIsExpansionAnimating = true;

        if (!mRemovingLastBubbleWhileExpanded) {
            // When we remove the last bubble it animates the scrim.
            showScrim(false, null /* runnable */);
        }

        mBubbleContainer.cancelAllAnimations();

        // If we were in the middle of swapping, the animating-out surface would have been scaling
        // to zero - finish it off.
        PhysicsAnimator.getInstance(mAnimatingOutSurfaceContainer).cancel();
        mAnimatingOutSurfaceContainer.setScaleX(0f);
        mAnimatingOutSurfaceContainer.setScaleY(0f);

        // Let the expanded animation controller know that it shouldn't animate child adds/reorders
        // since we're about to animate collapsed.
        mExpandedAnimationController.notifyPreparingToCollapse();
        final PointF collapsePosition = mStackAnimationController
                .getStackPositionAlongNearestHorizontalEdge();
        updateOverflowDotVisibility(false /* expanding */);
        final Runnable collapseBackToStack = () ->
                mExpandedAnimationController.collapseBackToStack(
                        collapsePosition,
                        /* fadeBubblesDuringCollapse= */ mRemovingLastBubbleWhileExpanded,
                        () -> {
                            mBubbleContainer.setActiveController(mStackAnimationController);
                            updateOverflowVisibility();
                            animateShadows();
                        });

        final Runnable after = () -> {
            final BubbleViewProvider previouslySelected = mExpandedBubble;
            // TODO(b/231350255): investigate why this call is needed here
            beforeExpandedViewAnimation();
            if (mManageEduView != null) {
                mManageEduView.hide();
            }

            updateBadges(true /* setBadgeForCollapsedStack */);
            afterExpandedViewAnimation();
            if (previouslySelected != null) {
                previouslySelected.setTaskViewVisibility(false);
            }
            mExpandedViewAnimationController.reset();
            animateStashedState(false /* stashImmediately */);
        };
        mExpandedViewAnimationController.animateCollapse(collapseBackToStack, after,
                collapsePosition);
        BubbleExpandedView expandedView = getExpandedView();
        if (expandedView != null) {
            // When the animation completes, we should no longer be showing the content.
            // This won't actually update content visibility immediately, if we are currently
            // animating. But updates the internal state for the content to be hidden after
            // animation completes.
            expandedView.setContentVisibility(false);
        }
    }

    private void animateSwitchBubbles() {
        // If we're no longer expanded, this is meaningless.
        if (!mIsExpanded) {
            mIsBubbleSwitchAnimating = false;
            return;
        }

        // The surface contains a screenshot of the animating out bubble, so we just need to animate
        // it out (and then release the GraphicBuffer).
        PhysicsAnimator.getInstance(mAnimatingOutSurfaceContainer).cancel();

        mAnimatingOutSurfaceAlphaAnimator.reverse();
        mExpandedViewAlphaAnimator.start();

        if (mPositioner.showBubblesVertically()) {
            float translationX = mStackAnimationController.isStackOnLeftSide()
                    ? mAnimatingOutSurfaceContainer.getTranslationX() + mBubbleSize * 2
                    : mAnimatingOutSurfaceContainer.getTranslationX();
            PhysicsAnimator.getInstance(mAnimatingOutSurfaceContainer)
                    .spring(DynamicAnimation.TRANSLATION_X, translationX, mTranslateSpringConfig)
                    .start();
        } else {
            PhysicsAnimator.getInstance(mAnimatingOutSurfaceContainer)
                    .spring(DynamicAnimation.TRANSLATION_Y,
                            mAnimatingOutSurfaceContainer.getTranslationY() - mBubbleSize,
                            mTranslateSpringConfig)
                    .start();
        }

        boolean isOverflow = mExpandedBubble != null
                && mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        PointF p = mPositioner.getExpandedBubbleXY(isOverflow
                        ? mBubbleContainer.getChildCount() - 1
                        : mBubbleData.getBubbles().indexOf(mExpandedBubble),
                getState());
        mExpandedViewContainer.setAlpha(1f);
        mExpandedViewContainer.setVisibility(View.VISIBLE);

        if (mPositioner.showBubblesVertically()) {
            float pivotX;
            float pivotY = p.y + mBubbleSize / 2f;
            if (mStackOnLeftOrWillBe) {
                pivotX = p.x + mBubbleSize + mExpandedViewPadding;
            } else {
                pivotX = p.x - mExpandedViewPadding;
            }
            mExpandedViewContainerMatrix.setScale(
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    pivotX, pivotY);
        } else {
            mExpandedViewContainerMatrix.setScale(
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT,
                    p.x + mBubbleSize / 2f,
                    p.y + mBubbleSize + mExpandedViewPadding);
        }

        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix);

        mMainExecutor.executeDelayed(() -> {
            if (!mIsExpanded) {
                mIsBubbleSwitchAnimating = false;
                return;
            }

            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                    .spring(AnimatableScaleMatrix.SCALE_X,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .spring(AnimatableScaleMatrix.SCALE_Y,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .addUpdateListener((target, values) -> {
                        mExpandedViewContainer.setAnimationMatrix(mExpandedViewContainerMatrix);
                    })
                    .withEndActions(() -> {
                        mExpandedViewTemporarilyHidden = false;
                        mIsBubbleSwitchAnimating = false;
                        mExpandedViewContainer.setAnimationMatrix(null);

                        // When a bubble is being dragged, the expanded view is temporarily hidden.
                        // If the motion ends with dismissing the bubble, with multiple bubbles in
                        // the stack, we'll end up here to switch to the new bubble. However, the
                        // expanded view animation might not actually set the z ordering for the
                        // expanded view correctly, because the view may still be temporarily
                        // hidden. So set it again here.
                        BubbleExpandedView expandedView = getExpandedView();
                        if (expandedView != null) {
                            expandedView.setSurfaceZOrderedOnTop(false);
                            expandedView.setAnimating(false);
                        }
                    })
                    .start();
        }, 25);
    }

    /**
     * Cancels any delayed steps for expand/collapse and bubble switch animations, and resets the is
     * animating flags for those animations.
     */
    private void cancelDelayedExpandCollapseSwitchAnimations() {
        mMainExecutor.removeCallbacks(mDelayedAnimation);

        mIsExpansionAnimating = false;
        mIsBubbleSwitchAnimating = false;
    }

    private void cancelAllExpandCollapseSwitchAnimations() {
        cancelDelayedExpandCollapseSwitchAnimations();

        PhysicsAnimator.getInstance(mAnimatingOutSurfaceView).cancel();
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();

        mExpandedViewContainer.setAnimationMatrix(null);
    }

    private void notifyExpansionChanged(BubbleViewProvider bubble, boolean expanded) {
        if (mExpandListener != null && bubble != null) {
            mExpandListener.onBubbleExpandChanged(expanded, bubble.getKey());
        }
    }

    /**
     * Updates the stack based for IME changes. When collapsed it'll move the stack if it
     * overlaps where they IME would be. When expanded it'll shift the expanded bubbles
     * if they might overlap with the IME (this only happens for large screens)
     * and clip the expanded view.
     */
    public void setImeVisible(boolean visible) {
        if ((mIsExpansionAnimating || mIsBubbleSwitchAnimating) && mIsExpanded) {
            // This will update the animation so the bubbles move to position for the IME
            mExpandedAnimationController.expandFromStack(() -> {
                updatePointerPosition(false /* forIme */);
                afterExpandedViewAnimation();
                mExpandedViewContainer.setVisibility(VISIBLE);
                mExpandedViewAnimationController.animateForImeVisibilityChange(visible);
            } /* after */);
            return;
        }

        if (!mIsExpanded && getBubbleCount() > 0) {
            final float stackDestinationY =
                    mStackAnimationController.animateForImeVisibility(visible);

            // How far the stack is animating due to IME, we'll just animate the flyout by that
            // much too.
            final float stackDy =
                    stackDestinationY - mStackAnimationController.getStackPosition().y;

            // If the flyout is visible, translate it along with the bubble stack.
            if (mFlyout.getVisibility() == VISIBLE) {
                PhysicsAnimator.getInstance(mFlyout)
                        .spring(DynamicAnimation.TRANSLATION_Y,
                                mFlyout.getTranslationY() + stackDy,
                                FLYOUT_IME_ANIMATION_SPRING_CONFIG)
                        .start();
            }
        }

        if (mIsExpanded) {
            mExpandedViewAnimationController.animateForImeVisibilityChange(visible);
            BubbleExpandedView expandedView = getExpandedView();
            if (mPositioner.showBubblesVertically() && expandedView != null) {
                float selectedY = mPositioner.getExpandedBubbleXY(getState().selectedIndex,
                        getState()).y;
                float newExpandedViewTop = mPositioner.getExpandedViewY(mExpandedBubble, selectedY);
                expandedView.setImeVisible(visible);
                if (!expandedView.isUsingMaxHeight()) {
                    mExpandedViewContainer.animate().translationY(newExpandedViewTop);
                }
                List<Animator> animList = new ArrayList<>();
                for (int i = 0; i < mBubbleContainer.getChildCount(); i++) {
                    View child = mBubbleContainer.getChildAt(i);
                    float transY = mPositioner.getExpandedBubbleXY(i, getState()).y;
                    ObjectAnimator anim = ObjectAnimator.ofFloat(child, TRANSLATION_Y, transY);
                    animList.add(anim);
                }
                updatePointerPosition(true /* forIme */);
                AnimatorSet set = new AnimatorSet();
                set.playTogether(animList);
                set.start();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN && ev.getActionIndex() != mPointerIndexDown) {
            // Ignore touches from additional pointer indices.
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mPointerIndexDown = ev.getActionIndex();
        } else if (ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            mPointerIndexDown = -1;
        }

        boolean dispatched = super.dispatchTouchEvent(ev);

        // If a new bubble arrives while the collapsed stack is being dragged, it will be positioned
        // at the front of the stack (under the touch position). Subsequent ACTION_MOVE events will
        // then be passed to the new bubble, which will not consume them since it hasn't received an
        // ACTION_DOWN yet. Work around this by passing MotionEvents directly to the touch handler
        // until the current gesture ends with an ACTION_UP event.
        if (!dispatched && !mIsExpanded && mIsGestureInProgress) {
            dispatched = mBubbleTouchListener.onTouch(this /* view */, ev);
        }

        mIsGestureInProgress =
                ev.getAction() != MotionEvent.ACTION_UP
                        && ev.getAction() != MotionEvent.ACTION_CANCEL;

        // If there is a deferred reorder action, and the gesture is over, run it now.
        if (mShouldReorderBubblesAfterGestureCompletes && !mIsGestureInProgress) {
            mShouldReorderBubblesAfterGestureCompletes = false;
            updateBubbleOrderInternal(mBubbleData.getBubbles(), false);
        }

        return dispatched;
    }

    void setFlyoutStateForDragLength(float deltaX) {
        // This shouldn't happen, but if it does, just wait until the flyout lays out. This method
        // is continually called.
        if (mFlyout.getWidth() <= 0) {
            return;
        }

        final boolean onLeft = mStackAnimationController.isStackOnLeftSide();
        mFlyoutDragDeltaX = deltaX;

        final float collapsePercent =
                onLeft ? -deltaX / mFlyout.getWidth() : deltaX / mFlyout.getWidth();
        mFlyout.setCollapsePercent(Math.min(1f, Math.max(0f, collapsePercent)));

        // Calculate how to translate the flyout if it has been dragged too far in either direction.
        float overscrollTranslation = 0f;
        if (collapsePercent < 0f || collapsePercent > 1f) {
            // Whether we are more than 100% transitioned to the dot.
            final boolean overscrollingPastDot = collapsePercent > 1f;

            // Whether we are overscrolling physically to the left - this can either be pulling the
            // flyout away from the stack (if the stack is on the right) or pushing it to the left
            // after it has already become the dot.
            final boolean overscrollingLeft =
                    (onLeft && collapsePercent > 1f) || (!onLeft && collapsePercent < 0f);
            overscrollTranslation =
                    (overscrollingPastDot ? collapsePercent - 1f : collapsePercent * -1)
                            * (overscrollingLeft ? -1 : 1)
                            * (mFlyout.getWidth() / (FLYOUT_OVERSCROLL_ATTENUATION_FACTOR
                            // Attenuate the smaller dot less than the larger flyout.
                            / (overscrollingPastDot ? 2 : 1)));
        }

        mFlyout.setTranslationX(mFlyout.getRestingTranslationX() + overscrollTranslation);
    }

    /** Passes the MotionEvent to the magnetized object and returns true if it was consumed. */
    private boolean passEventToMagnetizedObject(MotionEvent event) {
        return mMagnetizedObject != null && mMagnetizedObject.maybeConsumeMotionEvent(event);
    }

    private void dismissBubbleIfExists(@Nullable BubbleViewProvider bubble) {
        if (bubble != null && mBubbleData.hasBubbleInStackWithKey(bubble.getKey())) {
            if (mIsExpanded && mBubbleData.getBubbles().size() > 1
                    && Objects.equals(bubble, mExpandedBubble)) {
                // If we have more than 1 bubble and it's the current bubble being dismissed,
                // we will perform the switch animation
                mIsBubbleSwitchAnimating = true;
            }
            mBubbleData.dismissBubbleWithKey(bubble.getKey(), Bubbles.DISMISS_USER_GESTURE);
        }
    }

    /** Prepares and starts the dismiss animation on the bubble stack. */
    private void animateDismissBubble(View targetView, boolean applyAlpha) {
        mViewBeingDismissed = targetView;

        if (mViewBeingDismissed == null) {
            return;
        }
        if (applyAlpha) {
            mDismissBubbleAnimator.removeAllListeners();
            mDismissBubbleAnimator.start();
        } else {
            mDismissBubbleAnimator.removeAllListeners();
            mDismissBubbleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    resetDismissAnimator();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    resetDismissAnimator();
                }
            });
            mDismissBubbleAnimator.reverse();
        }
    }

    private void resetDismissAnimator() {
        mDismissBubbleAnimator.removeAllListeners();
        mDismissBubbleAnimator.cancel();

        if (mViewBeingDismissed != null) {
            mViewBeingDismissed.setAlpha(1f);
            mViewBeingDismissed = null;
        }
        if (mDismissView != null) {
            mDismissView.getCircle().setScaleX(1f);
            mDismissView.getCircle().setScaleY(1f);
        }
    }

    /** Animates the flyout collapsed (to dot), or the reverse, starting with the given velocity. */
    private void animateFlyoutCollapsed(boolean collapsed, float velX) {
        final boolean onLeft = mStackAnimationController.isStackOnLeftSide();
        // If the flyout was tapped, we want a higher stiffness for the collapse animation so it's
        // faster.
        mFlyoutTransitionSpring.getSpring().setStiffness(
                (mBubbleToExpandAfterFlyoutCollapse != null)
                        ? SpringForce.STIFFNESS_MEDIUM
                        : SpringForce.STIFFNESS_LOW);
        mFlyoutTransitionSpring
                .setStartValue(mFlyoutDragDeltaX)
                .setStartVelocity(velX)
                .animateToFinalPosition(collapsed
                        ? (onLeft ? -mFlyout.getWidth() : mFlyout.getWidth())
                        : 0f);
    }

    private boolean shouldShowFlyout(Bubble bubble) {
        Bubble.FlyoutMessage flyoutMessage = bubble.getFlyoutMessage();
        final BadgedImageView bubbleView = bubble.getIconView();
        if (flyoutMessage == null
                || flyoutMessage.message == null
                || !bubble.showFlyout()
                || isStackEduVisible()
                || isExpanded()
                || mIsExpansionAnimating
                || mIsGestureInProgress
                || mSensitiveNotificationProtectionActive
                || mBubbleToExpandAfterFlyoutCollapse != null
                || bubbleView == null) {
            if (bubbleView != null && mFlyout.getVisibility() != VISIBLE) {
                bubbleView.removeDotSuppressionFlag(BadgedImageView.SuppressionFlag.FLYOUT_VISIBLE);
            }
            // Skip the message if none exists, we're expanded or animating expansion, or we're
            // about to expand a bubble from the previous tapped flyout, or if bubble view is null.
            return false;
        }
        return true;
    }

    /**
     * Animates in the flyout for the given bubble, if available, and then hides it after some time.
     */
    @VisibleForTesting
    void animateInFlyoutForBubble(Bubble bubble) {
        if (!shouldShowFlyout(bubble)) {
            return;
        }
        ProtoLog.d(WM_SHELL_BUBBLES, "animateFlyout=%s", bubble.getKey());
        mFlyoutDragDeltaX = 0f;
        clearFlyoutOnHide();
        mAfterFlyoutHidden = () -> {
            // Null it out to ensure it runs once.
            mAfterFlyoutHidden = null;

            if (mBubbleToExpandAfterFlyoutCollapse != null) {
                // User tapped on the flyout and we should expand
                mBubbleData.setSelectedBubble(mBubbleToExpandAfterFlyoutCollapse);
                mBubbleData.setExpanded(true);
                mBubbleToExpandAfterFlyoutCollapse = null;
            }

            // Stop suppressing the dot now that the flyout has morphed into the dot.
            if (bubble.getIconView() != null) {
                bubble.getIconView().removeDotSuppressionFlag(
                        BadgedImageView.SuppressionFlag.FLYOUT_VISIBLE);
            }
            // Hide the stack after a delay, if needed.
            updateTemporarilyInvisibleAnimation(false /* hideImmediately */);
            animateStashedState(true /* stashImmediately */);
        };

        // Suppress the dot when we are animating the flyout.
        bubble.getIconView().addDotSuppressionFlag(
                BadgedImageView.SuppressionFlag.FLYOUT_VISIBLE);

        // Start flyout expansion. Post in case layout isn't complete and getWidth returns 0.
        post(() -> {
            // An auto-expanding bubble could have been posted during the time it takes to
            // layout.
            if (isExpanded() || bubble.getIconView() == null) {
                return;
            }
            final Runnable expandFlyoutAfterDelay = () -> {
                mAnimateInFlyout = () -> {
                    mFlyout.setVisibility(VISIBLE);
                    updateTemporarilyInvisibleAnimation(false /* hideImmediately */);
                    mFlyoutDragDeltaX =
                            mStackAnimationController.isStackOnLeftSide()
                                    ? -mFlyout.getWidth()
                                    : mFlyout.getWidth();
                    animateFlyoutCollapsed(false /* collapsed */, 0 /* velX */);
                    mFlyout.postDelayed(mHideFlyout, FLYOUT_HIDE_AFTER);
                };
                mFlyout.postDelayed(mAnimateInFlyout, 200);
            };


            if (mFlyout.getVisibility() == View.VISIBLE) {
                mFlyout.animateUpdate(bubble.getFlyoutMessage(),
                        mStackAnimationController.getStackPosition(), !bubble.showDot(),
                        bubble.getIconView().getDotCenter(),
                        mAfterFlyoutHidden /* onHide */);
            } else {
                mFlyout.setVisibility(INVISIBLE);
                mFlyout.setupFlyoutStartingAsDot(bubble.getFlyoutMessage(),
                        mStackAnimationController.getStackPosition(),
                        mStackAnimationController.isStackOnLeftSide(),
                        bubble.getIconView().getDotColor() /* dotColor */,
                        expandFlyoutAfterDelay /* onLayoutComplete */,
                        mAfterFlyoutHidden /* onHide */,
                        bubble.getIconView().getDotCenter(),
                        !bubble.showDot());
            }
            mFlyout.bringToFront();
        });
        mFlyout.removeCallbacks(mHideFlyout);
        mFlyout.postDelayed(mHideFlyout, FLYOUT_HIDE_AFTER);
        logBubbleEvent(bubble, FrameworkStatsLog.BUBBLE_UICHANGED__ACTION__FLYOUT);
    }

    /** Hide the flyout immediately and cancel any pending hide runnables. */
    private void hideFlyoutImmediate() {
        clearFlyoutOnHide();
        mFlyout.removeCallbacks(mAnimateInFlyout);
        mFlyout.removeCallbacks(mHideFlyout);
        mFlyout.hideFlyout();
    }

    private void clearFlyoutOnHide() {
        mFlyout.removeCallbacks(mAnimateInFlyout);
        if (mAfterFlyoutHidden == null) {
            return;
        }
        mAfterFlyoutHidden.run();
        mAfterFlyoutHidden = null;
    }

    /**
     * Fills the Rect with the touchable region of the bubbles. This will be used by WindowManager
     * to decide which touch events go to Bubbles.
     *
     * Bubbles is below the status bar/notification shade but above application windows. If you're
     * trying to get touch events from the status bar or another higher-level window layer, you'll
     * need to re-order TYPE_BUBBLES in WindowManagerPolicy so that we have the opportunity to steal
     * them.
     */
    public void getTouchableRegion(Rect outRect) {
        if (isStackEduVisible()) {
            // When user education shows then capture all touches
            outRect.set(0, 0, getWidth(), getHeight());
            return;
        }

        if (!mIsExpanded) {
            if (getBubbleCount() > 0 || mBubbleData.isShowingOverflow()) {
                mBubbleContainer.getChildAt(0).getBoundsOnScreen(outRect);
                // Increase the touch target size of the bubble
                outRect.top -= mBubbleTouchPadding;
                outRect.left -= mBubbleTouchPadding;
                outRect.right += mBubbleTouchPadding;
                outRect.bottom += mBubbleTouchPadding;
                if (Flags.enableBubbleStashing()) {
                    if (mStackOnLeftOrWillBe) {
                        outRect.right += mBubbleTouchPadding;
                    } else {
                        outRect.left -= mBubbleTouchPadding;
                    }
                }
            }
        } else {
            mBubbleContainer.getBoundsOnScreen(outRect);
            // Account for the IME in the touchable region so that the touchable region of the
            // Bubble window doesn't obscure the IME. The touchable region affects which areas
            // of the screen can be excluded by lower windows (IME is just above the embedded task)
            outRect.bottom -= mPositioner.getImeHeight();
        }

        if (mFlyout.getVisibility() == View.VISIBLE) {
            final Rect flyoutBounds = new Rect();
            mFlyout.getBoundsOnScreen(flyoutBounds);
            outRect.union(flyoutBounds);
        }
    }

    private void requestUpdate() {
        if (mViewUpdatedRequested || mIsExpansionAnimating) {
            return;
        }
        mViewUpdatedRequested = true;
        getViewTreeObserver().addOnPreDrawListener(mViewUpdater);
        invalidate();
    }

    /** Hide or show the manage menu for the currently expanded bubble. */
    @VisibleForTesting
    public void showManageMenu(boolean show) {
        if ((mManageMenu.getVisibility() == VISIBLE) == show) return;
        ProtoLog.d(WM_SHELL_BUBBLES, "showManageMenu=%b for bubble=%s",
                show, (mExpandedBubble != null ? mExpandedBubble.getKey() : "null"));

        mShowingManage = show;

        // This should not happen, since the manage menu is only visible when there's an expanded
        // bubble. If we end up in this state, just hide the menu immediately.
        BubbleExpandedView expandedView = getExpandedView();
        if (expandedView == null) {
            mManageMenu.setVisibility(View.INVISIBLE);
            mManageMenuScrim.setVisibility(INVISIBLE);
            mSysuiProxyProvider.getSysuiProxy().onManageMenuExpandChanged(false /* show */);
            return;
        }
        if (show) {
            mManageMenuScrim.setVisibility(VISIBLE);
            mManageMenuScrim.setTranslationZ(mManageMenu.getElevation() - 1f);
        }
        Runnable endAction = () -> {
            if (!show) {
                mManageMenuScrim.setVisibility(INVISIBLE);
                mManageMenuScrim.setTranslationZ(0f);
            }
        };

        mSysuiProxyProvider.getSysuiProxy().onManageMenuExpandChanged(show);
        mManageMenuScrim.animate()
                .setInterpolator(show ? ALPHA_IN : ALPHA_OUT)
                .alpha(show ? BUBBLE_EXPANDED_SCRIM_ALPHA : 0f)
                .withEndAction(endAction)
                .start();

        // If available, update the manage menu's settings option with the expanded bubble's app
        // name and icon.
        if (show) {
            final Bubble bubble = mBubbleData.getBubbleInStackWithKey(mExpandedBubble.getKey());
            if (bubble != null && !bubble.isAppBubble()) {
                // Setup options for non app bubbles
                mManageDontBubbleView.setVisibility(VISIBLE);
                mManageSettingsIcon.setImageBitmap(bubble.getRawAppBadge());
                mManageSettingsText.setText(getResources().getString(
                        R.string.bubbles_app_settings, bubble.getAppName()));
                mManageSettingsView.setVisibility(VISIBLE);
            } else {
                // Setup options for app bubbles
                // App bubbles have no conversations
                // so we don't show the option to not bubble conversation
                mManageDontBubbleView.setVisibility(GONE);
                // App bubbles are not notification based
                // so we don't show the option to go to notification settings
                mManageSettingsView.setVisibility(GONE);
            }
        }

        if (expandedView.getTaskView() != null) {
            expandedView.getTaskView().setObscuredTouchRect(mShowingManage
                    ? new Rect(0, 0, getWidth(), getHeight())
                    : null);
        }

        final boolean isLtr =
                getResources().getConfiguration().getLayoutDirection() == LAYOUT_DIRECTION_LTR;

        // When the menu is open, it should be at these coordinates. The menu pops out to the right
        // in LTR and to the left in RTL.
        expandedView.getManageButtonBoundsOnScreen(mTempRect);
        final float margin = expandedView.getManageButtonMargin();
        final float targetX = isLtr
                ? mTempRect.left - margin
                : mTempRect.right + margin - mManageMenu.getWidth();
        final float menuHeight = getVisibleManageMenuHeight();
        final float targetY = mTempRect.bottom - menuHeight;

        final float xOffsetForAnimation = (isLtr ? 1 : -1) * mManageMenu.getWidth() / 4f;
        if (show) {
            mManageMenu.setScaleX(0.5f);
            mManageMenu.setScaleY(0.5f);
            mManageMenu.setTranslationX(targetX - xOffsetForAnimation);
            mManageMenu.setTranslationY(targetY + menuHeight / 4f);
            mManageMenu.setAlpha(0f);

            PhysicsAnimator.getInstance(mManageMenu)
                    .spring(DynamicAnimation.ALPHA, 1f)
                    .spring(DynamicAnimation.SCALE_X, 1f)
                    .spring(DynamicAnimation.SCALE_Y, 1f)
                    .spring(DynamicAnimation.TRANSLATION_X, targetX)
                    .spring(DynamicAnimation.TRANSLATION_Y, targetY)
                    .withEndActions(() -> {
                        View child = mManageMenu.getChildAt(0);
                        child.requestAccessibilityFocus();
                        BubbleExpandedView expView = getExpandedView();
                        if (expView != null) {
                            // Update the AV's obscured touchable region for the new state.
                            expView.updateObscuredTouchableRegion();
                        }
                    })
                    .start();

            mManageMenu.setVisibility(View.VISIBLE);
        } else {
            PhysicsAnimator.getInstance(mManageMenu)
                    .spring(DynamicAnimation.ALPHA, 0f)
                    .spring(DynamicAnimation.SCALE_X, 0.5f)
                    .spring(DynamicAnimation.SCALE_Y, 0.5f)
                    .spring(DynamicAnimation.TRANSLATION_X, targetX - xOffsetForAnimation)
                    .spring(DynamicAnimation.TRANSLATION_Y, targetY + menuHeight / 4f)
                    .withEndActions(() -> {
                        mManageMenu.setVisibility(View.INVISIBLE);
                        BubbleExpandedView expView = getExpandedView();
                        if (expView != null) {
                            // Update the AV's obscured touchable region for the new state.
                            expView.updateObscuredTouchableRegion();
                        }
                    })
                    .start();
        }
    }

    /**
     * Checks whether manage menu don't bubble conversation action is available and visible
     * Used for testing
     */
    @VisibleForTesting
    public boolean isManageMenuDontBubbleVisible() {
        return mManageDontBubbleView != null && mManageDontBubbleView.getVisibility() == VISIBLE;
    }

    /**
     * Checks whether manage menu notification settings action is available and visible
     * Used for testing
     */
    @VisibleForTesting
    public boolean isManageMenuSettingsVisible() {
        return mManageSettingsView != null && mManageSettingsView.getVisibility() == VISIBLE;
    }

    private void updateExpandedBubble() {
        mExpandedViewContainer.removeAllViews();
        BubbleExpandedView bev = getExpandedView();
        if (mIsExpanded && bev != null) {
            bev.setContentVisibility(false);
            bev.setAnimating(!mIsExpansionAnimating);
            mExpandedViewContainerMatrix.setScaleX(0f);
            mExpandedViewContainerMatrix.setScaleY(0f);
            mExpandedViewContainerMatrix.setTranslate(0f, 0f);
            mExpandedViewContainer.setVisibility(View.INVISIBLE);
            mExpandedViewContainer.setAlpha(0f);
            mExpandedViewContainer.addView(bev);

            postDelayed(() -> {
                // Set the Manage button click handler from postDelayed. This appears to resolve
                // a race condition with adding the BubbleExpandedView view to the expanded view
                // container. Due to the race condition the click handler sometimes is not set up
                // correctly and is never called.
                updateManageButtonListener();
            }, 0);

            if (!mIsExpansionAnimating) {
                mIsBubbleSwitchAnimating = true;
                mSurfaceSynchronizer.syncSurfaceAndRun(() -> {
                    post(this::animateSwitchBubbles);
                });
            }
        }
    }

    private void updateManageButtonListener() {
        BubbleExpandedView bev = getExpandedView();
        if (mIsExpanded && bev != null) {
            bev.setManageClickListener((view) -> {
                showManageMenu(true /* show */);
            });
        }
    }

    /**
     * Requests a snapshot from the currently expanded bubble's TaskView and displays it in a
     * SurfaceView. This allows us to load a newly expanded bubble's Activity into the TaskView,
     * while animating the (screenshot of the) previously selected bubble's content away.
     *
     * @param onComplete Callback to run once we're done here - called with 'false' if something
     *                   went wrong, or 'true' if the SurfaceView is now showing a screenshot of the
     *                   expanded bubble.
     */
    private void screenshotAnimatingOutBubbleIntoSurface(Consumer<Boolean> onComplete) {
        final BubbleExpandedView animatingOutExpandedView = getExpandedView();
        if (!mIsExpanded || animatingOutExpandedView == null) {
            // You can't animate null.
            onComplete.accept(false);
            return;
        }

        // Release the previous screenshot if it hasn't been released already.
        if (mAnimatingOutBubbleBuffer != null) {
            releaseAnimatingOutBubbleBuffer();
        }

        try {
            mAnimatingOutBubbleBuffer = animatingOutExpandedView.snapshotActivitySurface();
        } catch (Exception e) {
            // If we fail for any reason, print the stack trace and then notify the callback of our
            // failure. This is not expected to occur, but it's not worth crashing over.
            Log.wtf(TAG, e);
            onComplete.accept(false);
        }

        if (mAnimatingOutBubbleBuffer == null
                || mAnimatingOutBubbleBuffer.getHardwareBuffer() == null) {
            // While no exception was thrown, we were unable to get a snapshot.
            onComplete.accept(false);
            return;
        }

        // Make sure the surface container's properties have been reset.
        PhysicsAnimator.getInstance(mAnimatingOutSurfaceContainer).cancel();
        mAnimatingOutSurfaceContainer.setScaleX(1f);
        mAnimatingOutSurfaceContainer.setScaleY(1f);
        final float translationX = mPositioner.showBubblesVertically() && mStackOnLeftOrWillBe
                ? mExpandedViewContainer.getPaddingLeft() + mPositioner.getPointerSize()
                : mExpandedViewContainer.getPaddingLeft();
        mAnimatingOutSurfaceContainer.setTranslationX(translationX);
        mAnimatingOutSurfaceContainer.setTranslationY(0);

        final int[] taskViewLocation = animatingOutExpandedView.getTaskViewLocationOnScreen();
        final int[] surfaceViewLocation = mAnimatingOutSurfaceView.getLocationOnScreen();

        // Translate the surface to overlap the real TaskView.
        mAnimatingOutSurfaceContainer.setTranslationY(
                taskViewLocation[1] - surfaceViewLocation[1]);

        // Set the width/height of the SurfaceView to match the snapshot.
        mAnimatingOutSurfaceView.getLayoutParams().width =
                mAnimatingOutBubbleBuffer.getHardwareBuffer().getWidth();
        mAnimatingOutSurfaceView.getLayoutParams().height =
                mAnimatingOutBubbleBuffer.getHardwareBuffer().getHeight();
        mAnimatingOutSurfaceView.requestLayout();

        // Post to wait for layout.
        post(() -> {
            // The buffer might have been destroyed if the user is mashing on bubbles, that's okay.
            if (mAnimatingOutBubbleBuffer == null
                    || mAnimatingOutBubbleBuffer.getHardwareBuffer() == null
                    || mAnimatingOutBubbleBuffer.getHardwareBuffer().isClosed()) {
                onComplete.accept(false);
                return;
            }

            if (!mIsExpanded || !mAnimatingOutSurfaceReady) {
                onComplete.accept(false);
                return;
            }

            // Attach the buffer! We're now displaying the snapshot.
            mAnimatingOutSurfaceView.getHolder().getSurface().attachAndQueueBufferWithColorSpace(
                    mAnimatingOutBubbleBuffer.getHardwareBuffer(),
                    mAnimatingOutBubbleBuffer.getColorSpace());

            mAnimatingOutSurfaceView.setAlpha(1f);
            mExpandedViewContainer.setVisibility(View.INVISIBLE);

            mSurfaceSynchronizer.syncSurfaceAndRun(() -> {
                post(() -> {
                    onComplete.accept(true);
                });
            });
        });
    }

    /**
     * Releases the buffer containing the screenshot of the animating-out bubble, if it exists and
     * isn't yet destroyed.
     */
    private void releaseAnimatingOutBubbleBuffer() {
        if (mAnimatingOutBubbleBuffer != null
                && !mAnimatingOutBubbleBuffer.getHardwareBuffer().isClosed()) {
            mAnimatingOutBubbleBuffer.getHardwareBuffer().close();
        }
    }

    private void updateExpandedView() {
        boolean isOverflowExpanded = mExpandedBubble != null
                && BubbleOverflow.KEY.equals(mExpandedBubble.getKey());
        int[] paddings = mPositioner.getExpandedViewContainerPadding(
                mStackAnimationController.isStackOnLeftSide(), isOverflowExpanded);
        mExpandedViewContainer.setPadding(paddings[0], paddings[1], paddings[2], paddings[3]);
        BubbleExpandedView expandedView = getExpandedView();
        if (expandedView != null) {
            PointF p = mPositioner.getExpandedBubbleXY(getBubbleIndex(mExpandedBubble),
                    getState());
            mExpandedViewContainer.setTranslationY(mPositioner.getExpandedViewY(mExpandedBubble,
                    mPositioner.showBubblesVertically() ? p.y : p.x));
            mExpandedViewContainer.setTranslationX(0f);
            expandedView.updateTaskViewContentWidth();
            expandedView.updateView(mExpandedViewContainer.getLocationOnScreen());
            updatePointerPosition(false /* forIme */);
        }

        mStackOnLeftOrWillBe = mStackAnimationController.isStackOnLeftSide();
    }

    /**
     * Updates whether each of the bubbles should show shadows. When collapsed & resting, only the
     * visible bubbles (top 2) will show a shadow. When the stack is being dragged, everything
     * shows a shadow. When an individual bubble is dragged out, it should show a shadow.
     * The bubble overflow is a special case and never has a shadow as it's ordered below the
     * rest of the bubbles and isn't visible unless the stack is expanded.
     *
     * @param isExpanded whether the stack will be expanded or not when the shadows are applied.
     */
    private void updateBubbleShadows(boolean isExpanded) {
        final int childCount = mBubbleContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final BadgedImageView bv = (BadgedImageView) mBubbleContainer.getChildAt(i);
            final boolean isOverflow = BubbleOverflow.KEY.equals(bv.getKey());
            final boolean isDraggedOut = mMagnetizedObject != null
                    && mMagnetizedObject.getUnderlyingObject().equals(bv);
            if (isDraggedOut) {
                // If it's dragged out, it's above all the other bubbles
                bv.setZ((mPositioner.getMaxBubbles() * mBubbleElevation) + 1);
            } else {
                bv.setZ(mPositioner.getZTranslation(i, isOverflow, isExpanded));
            }
        }
    }

    /**
     * When the bubbles are flung and then rest, the shadows stack up for the bubbles hidden
     * beneath the top two bubbles, to avoid this we animate the Z translations once the stack
     * is resting so that they fade away nicely.
     */
    private void animateShadows() {
        int bubbleCount = getBubbleCount();
        for (int i = 0; i < bubbleCount; i++) {
            BadgedImageView bv = (BadgedImageView) mBubbleContainer.getChildAt(i);
            boolean fullShadow = i < NUM_VISIBLE_WHEN_RESTING;
            if (!fullShadow) {
                bv.animate().translationZ(0).start();
            }
        }
    }

    private void updateBadges(boolean setBadgeForCollapsedStack) {
        int bubbleCount = getBubbleCount();
        for (int i = 0; i < bubbleCount; i++) {
            BadgedImageView bv = (BadgedImageView) mBubbleContainer.getChildAt(i);
            if (mIsExpanded) {
                // If we're not displaying vertically, we always show the badge on the left.
                boolean onLeft = mPositioner.showBubblesVertically() && !mStackOnLeftOrWillBe;
                bv.showDotAndBadge(onLeft);
            } else if (setBadgeForCollapsedStack) {
                if (i == 0) {
                    bv.showDotAndBadge(!mStackOnLeftOrWillBe);
                } else {
                    bv.hideDotAndBadge(!mStackOnLeftOrWillBe);
                }
            }
        }
    }

    /**
     * Updates the position of the pointer based on the expanded bubble.
     *
     * @param forIme whether the position is being updated due to the ime appearing, in this case
     *               the pointer is animated to the location.
     */
    private void updatePointerPosition(boolean forIme) {
        BubbleExpandedView expandedView = getExpandedView();
        if (mExpandedBubble == null || expandedView == null) {
            return;
        }
        int index = getBubbleIndex(mExpandedBubble);
        if (index == -1) {
            return;
        }
        PointF position = mPositioner.getExpandedBubbleXY(index, getState());
        float bubblePosition = mPositioner.showBubblesVertically()
                ? position.y
                : position.x;
        expandedView.setPointerPosition(bubblePosition,
                mStackOnLeftOrWillBe, forIme /* animate */);
    }

    /**
     * @return the number of bubbles in the stack view.
     */
    public int getBubbleCount() {
        final int childCount = mBubbleContainer.getChildCount();
        // Subtract 1 for the overflow button if it's showing.
        return mShowingOverflow ? childCount - 1 : childCount;
    }

    /**
     * Finds the bubble index within the stack.
     *
     * @param provider the bubble view provider with the bubble to look up.
     * @return the index of the bubble view within the bubble stack. The range of the position
     * is between 0 and the bubble count minus 1.
     */
    int getBubbleIndex(@Nullable BubbleViewProvider provider) {
        if (provider == null) {
            return -1;
        }
        return mBubbleContainer.indexOfChild(provider.getIconView());
    }

    /**
     * Menu height calculated for animation
     * It takes into account view visibility to get the correct total height
     */
    private float getVisibleManageMenuHeight() {
        float menuHeight = 0;

        for (int i = 0; i < mManageMenu.getChildCount(); i++) {
            View subview = mManageMenu.getChildAt(i);

            if (subview.getVisibility() == VISIBLE) {
                menuHeight += subview.getHeight();
            }
        }

        return menuHeight;
    }

    /**
     * @return the normalized x-axis position of the bubble stack rounded to 4 decimal places.
     */
    public float getNormalizedXPosition() {
        int width = mPositioner.getAvailableRect().width();
        float stackPosition = width > 0 ? getStackPosition().x / width : 0;
        return new BigDecimal(stackPosition)
                .setScale(4, RoundingMode.CEILING.HALF_UP)
                .floatValue();
    }

    /**
     * @return the normalized y-axis position of the bubble stack rounded to 4 decimal places.
     */
    public float getNormalizedYPosition() {
        int height = mPositioner.getAvailableRect().height();
        float stackPosition = height > 0 ? getStackPosition().y / height : 0;
        return new BigDecimal(stackPosition)
                .setScale(4, RoundingMode.CEILING.HALF_UP)
                .floatValue();
    }

    /** @return the position of the bubble stack. */
    public PointF getStackPosition() {
        return mStackAnimationController.getStackPosition();
    }

    /**
     * Logs the bubble UI event.
     *
     * @param provider the bubble view provider that is being interacted on. Null value indicates
     *                 that the user interaction is not specific to one bubble.
     * @param action   the user interaction enum.
     */
    private void logBubbleEvent(@Nullable BubbleViewProvider provider, int action) {
        final String packageName =
                (provider != null && provider instanceof Bubble)
                        ? ((Bubble) provider).getPackageName()
                        : "null";
        mBubbleData.logBubbleEvent(provider,
                action,
                packageName,
                getBubbleCount(),
                getBubbleIndex(provider),
                getNormalizedXPosition(),
                getNormalizedYPosition());
    }

    /** For debugging only */
    List<Bubble> getBubblesOnScreen() {
        List<Bubble> bubbles = new ArrayList<>();
        for (int i = 0; i < getBubbleCount(); i++) {
            View child = mBubbleContainer.getChildAt(i);
            if (child instanceof BadgedImageView) {
                String key = ((BadgedImageView) child).getKey();
                Bubble bubble = mBubbleData.getBubbleInStackWithKey(key);
                bubbles.add(bubble);
            }
        }
        return bubbles;
    }

    /** @return the current stack state. */
    public StackViewState getState() {
        mStackViewState.numberOfBubbles = mBubbleContainer.getChildCount();
        mStackViewState.selectedIndex = getBubbleIndex(mExpandedBubble);
        mStackViewState.onLeft = mStackOnLeftOrWillBe;
        return mStackViewState;
    }

    /**
     * Handles vertical offset changes, e.g. when one handed mode is switched on/off.
     *
     * @param offset new vertical offset.
     */
    void onVerticalOffsetChanged(int offset) {
        // adjust dismiss view vertical position, so that it is still visible to the user
        ViewGroup.LayoutParams lp = mDismissView.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) lp;
            layoutParams.bottomMargin = offset;
            mDismissView.setLayoutParams(layoutParams);
        }
        mMagneticTarget.setScreenVerticalOffset(offset);
        mMagneticTarget.updateLocationOnScreen();
    }

    /**
     * Removes the overflow view from the stack. This allows for re-adding it later to a new stack.
     */
    void resetOverflowView() {
        BadgedImageView overflowIcon = mBubbleOverflow.getIconView();
        if (overflowIcon != null) {
            PhysicsAnimationLayout parent = (PhysicsAnimationLayout) overflowIcon.getParent();
            if (parent != null) {
                parent.removeViewNoAnimation(overflowIcon);
            }
        }
    }

    /**
     * Holds some commonly queried information about the stack.
     */
    public static class StackViewState {
        // Number of bubbles (including the overflow itself) in the stack.
        public int numberOfBubbles;
        // The selected index if the stack is expanded.
        public int selectedIndex;
        // Whether the stack is resting on the left or right side of the screen when collapsed.
        public boolean onLeft;
    }

    /**
     * Representation of stack position that uses relative properties rather than absolute
     * coordinates. This is used to maintain similar stack positions across configuration changes.
     */
    public static class RelativeStackPosition {
        /** Whether to place the stack at the leftmost allowed position. */
        private boolean mOnLeft;

        /**
         * How far down the vertically allowed region to place the stack. For example, if the stack
         * allowed region is between y = 100 and y = 1100 and this is 0.2f, we'll place the stack at
         * 100 + (0.2f * 1000) = 300.
         */
        private float mVerticalOffsetPercent;

        public RelativeStackPosition(boolean onLeft, float verticalOffsetPercent) {
            mOnLeft = onLeft;
            mVerticalOffsetPercent = clampVerticalOffsetPercent(verticalOffsetPercent);
        }

        /** Constructs a relative position given a region and a point in that region. */
        public RelativeStackPosition(PointF position, RectF region) {
            mOnLeft = position.x < region.width() / 2;
            mVerticalOffsetPercent =
                    clampVerticalOffsetPercent((position.y - region.top) / region.height());
        }

        /** Ensures that the offset percent is between 0f and 1f. */
        private float clampVerticalOffsetPercent(float offsetPercent) {
            return Math.max(0f, Math.min(1f, offsetPercent));
        }

        /**
         * Given an allowable stack position region, returns the point within that region
         * represented by this relative position.
         */
        public PointF getAbsolutePositionInRegion(RectF region) {
            return new PointF(
                    mOnLeft ? region.left : region.right,
                    region.top + mVerticalOffsetPercent * region.height());
        }
    }
}
