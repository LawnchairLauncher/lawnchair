/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles.bar;

import static android.view.View.ALPHA;
import static android.view.View.INVISIBLE;
import static android.view.View.SCALE_X;
import static android.view.View.SCALE_Y;
import static android.view.View.TRANSLATION_X;
import static android.view.View.TRANSLATION_Y;
import static android.view.View.VISIBLE;
import static android.view.View.X;
import static android.view.View.Y;

import static com.android.wm.shell.bubbles.bar.BubbleBarExpandedView.CORNER_RADIUS;
import static com.android.wm.shell.bubbles.bar.BubbleBarExpandedView.TASK_VIEW_ALPHA;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;
import static com.android.wm.shell.shared.animation.Interpolators.EMPHASIZED;
import static com.android.wm.shell.shared.animation.Interpolators.EMPHASIZED_DECELERATE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceControl;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

import com.android.app.animation.Interpolators;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.animation.SizeChangeAnimation;
import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.bubbles.BubbleOverflow;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleViewProvider;
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix;
import com.android.wm.shell.shared.animation.PhysicsAnimator;
import com.android.wm.shell.shared.magnetictarget.MagnetizedObject.MagneticTarget;

/**
 * Helper class to animate a {@link BubbleBarExpandedView} on a bubble.
 */
public class BubbleBarAnimationHelper {

    private static final String TAG = BubbleBarAnimationHelper.class.getSimpleName();

    private static final float EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT = 0.1f;
    private static final float EXPANDED_VIEW_ANIMATE_OUT_SCALE_AMOUNT = .75f;
    private static final int EXPANDED_VIEW_EXPAND_ALPHA_DURATION = 150;
    private static final int EXPANDED_VIEW_SNAP_TO_DISMISS_DURATION = 400;
    private static final int EXPANDED_VIEW_ANIMATE_TO_REST_DURATION = 400;
    private static final int EXPANDED_VIEW_DISMISS_DURATION = 250;
    private static final int EXPANDED_VIEW_DRAG_ANIMATION_DURATION = 400;
    /**
     * Additional scale applied to expanded view when it is positioned inside a magnetic target.
     */
    private static final float EXPANDED_VIEW_IN_TARGET_SCALE = 0.2f;
    private static final float EXPANDED_VIEW_DRAG_SCALE = 0.4f;
    private static final float DISMISS_VIEW_SCALE = 1.25f;
    private static final int HANDLE_ALPHA_ANIMATION_DURATION = 100;

    private static final float SWITCH_OUT_SCALE = 0.97f;
    private static final long SWITCH_OUT_SCALE_DURATION = 200L;
    private static final long SWITCH_OUT_ALPHA_DURATION = 100L;
    private static final long SWITCH_OUT_HANDLE_ALPHA_DURATION = 50L;
    private static final long SWITCH_IN_ANIM_DELAY = 50L;
    private static final long SWITCH_IN_TX_DURATION = 350L;
    private static final long SWITCH_IN_ALPHA_DURATION = 50L;
    // Keep this handle alpha delay at least as long as alpha animation for both expanded views.
    private static final long SWITCH_IN_HANDLE_ALPHA_DELAY = 150L;
    private static final long SWITCH_IN_HANDLE_ALPHA_DURATION = 100L;

    /** Spring config for the expanded view scale-in animation. */
    private final PhysicsAnimator.SpringConfig mScaleInSpringConfig =
            new PhysicsAnimator.SpringConfig(300f, 0.9f);

    /** Spring config for the expanded view scale-out animation. */
    private final PhysicsAnimator.SpringConfig mScaleOutSpringConfig =
            new PhysicsAnimator.SpringConfig(900f, 1f);

    private final int mSwitchAnimPositionOffset;

    /** Matrix used to scale the expanded view container with a given pivot point. */
    private final AnimatableScaleMatrix mExpandedViewContainerMatrix = new AnimatableScaleMatrix();

    @Nullable
    private Animator mRunningAnimator;

    private final BubblePositioner mPositioner;
    private final int[] mTmpLocation = new int[2];

    // TODO(b/381936992): remove expanded bubble state from this helper class
    private BubbleViewProvider mExpandedBubble;

    public BubbleBarAnimationHelper(Context context, BubblePositioner positioner) {
        mPositioner = positioner;
        mSwitchAnimPositionOffset = context.getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_switch_offset);
    }

    /**
     * Animates the provided bubble's expanded view to the expanded state.
     *
     * @param endRunnable a runnable to run at the end of the animation (even if the animation is
     *                    canceled)
     */
    public void animateExpansion(BubbleViewProvider expandedBubble,
            @Nullable Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateExpansion()");
        mExpandedBubble = expandedBubble;
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            return;
        }

        mExpandedViewContainerMatrix.setScaleX(0f);
        mExpandedViewContainerMatrix.setScaleY(0f);

        prepareForAnimateIn(bbev);

        setScaleFromBubbleBar(mExpandedViewContainerMatrix,
                1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT);

        bbev.setAnimationMatrix(mExpandedViewContainerMatrix);

        bbev.animateExpansionWhenTaskViewVisible(() -> {
            bbev.getHandleView().setAlpha(1);
            ObjectAnimator alphaAnim = createAlphaAnimator(bbev, /* visible= */ true);
            alphaAnim.setDuration(EXPANDED_VIEW_EXPAND_ALPHA_DURATION);
            alphaAnim.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
            alphaAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    bbev.setAnimating(false);
                }
            });
            startNewAnimator(alphaAnim);

            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
            PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                    .spring(AnimatableScaleMatrix.SCALE_X,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .spring(AnimatableScaleMatrix.SCALE_Y,
                            AnimatableScaleMatrix.getAnimatableValueForScaleFactor(1f),
                            mScaleInSpringConfig)
                    .addUpdateListener((target, values) -> {
                        bbev.setAnimationMatrix(mExpandedViewContainerMatrix);
                    })
                    .withEndActions(() -> {
                        bbev.setAnimationMatrix(null);
                        updateExpandedView(bbev);
                    })
                    .withEndOrCancelActions(() -> {
                        ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                                "BBAnimationHelper.animateExpansion(): finished");
                        if (endRunnable != null) {
                            endRunnable.run();
                        }
                    })
                    .start();
        });
    }

    private void prepareForAnimateIn(BubbleBarExpandedView bbev) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.prepareForAnimateIn()");
        bbev.setAnimating(true);
        updateExpandedView(bbev);
        // We need to be Z ordered on top in order for taskView alpha to work.
        // It is also set when the alpha animation starts, but needs to be set here to too avoid
        // flickers.
        bbev.setSurfaceZOrderedOnTop(true);
        bbev.setTaskViewAlpha(0f);
        bbev.setContentVisibility(false);
        bbev.setVisibility(VISIBLE);
    }

    /**
     * Collapses the currently expanded bubble.
     *
     * @param endRunnable a runnable to run at the end of the animation (even if the animation is
     *                    canceled)
     */
    public void animateCollapse(Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateCollapse()");
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate collapse without a bubble");
            return;
        }
        bbev.setScaleX(1f);
        bbev.setScaleY(1f);

        setScaleFromBubbleBar(mExpandedViewContainerMatrix, 1f);

        bbev.setAnimating(true);

        ObjectAnimator alphaAnim = createAlphaAnimator(bbev, /* visible= */ false);
        alphaAnim.setDuration(EXPANDED_VIEW_EXPAND_ALPHA_DURATION);
        alphaAnim.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
        alphaAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bbev.setAnimating(false);
            }
        });
        startNewAnimator(alphaAnim);

        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix)
                .spring(AnimatableScaleMatrix.SCALE_X,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(
                                EXPANDED_VIEW_ANIMATE_OUT_SCALE_AMOUNT),
                        mScaleOutSpringConfig)
                .spring(AnimatableScaleMatrix.SCALE_Y,
                        AnimatableScaleMatrix.getAnimatableValueForScaleFactor(
                                EXPANDED_VIEW_ANIMATE_OUT_SCALE_AMOUNT),
                        mScaleOutSpringConfig)
                .addUpdateListener((target, values) -> {
                    bbev.setAnimationMatrix(mExpandedViewContainerMatrix);
                })
                .withEndActions(() -> {
                    bbev.setAnimationMatrix(null);
                    bbev.resetBottomClip();
                })
                .withEndOrCancelActions(() -> {
                    ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                            "BBAnimationHelper.animateCollapse(): finished");
                    if (endRunnable != null) {
                        endRunnable.run();
                    }
                })
                .start();
    }

    private void setScaleFromBubbleBar(AnimatableScaleMatrix matrix, float scale) {
        // Set the pivot point for the scale, so the view animates out from the bubble bar.
        Rect availableRect = mPositioner.getAvailableRect();
        float pivotX = mPositioner.isBubbleBarOnLeft() ? availableRect.left : availableRect.right;
        float pivotY = mPositioner.getBubbleBarTopOnScreen();
        matrix.setScale(scale, scale, pivotX, pivotY);
    }

    /**
     * Animate between two bubble views using a switch animation
     *
     * @param fromBubble bubble to hide
     * @param toBubble bubble to show
     * @param endRunnable optional runnable after animation finishes (even if the animation is
     *                    canceled)
     */
    public void animateSwitch(BubbleViewProvider fromBubble, BubbleViewProvider toBubble,
            @Nullable Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateSwitch(): from=%s to=%s",
                fromBubble.getKey(), toBubble.getKey());
        /*
         * Switch animation
         *
         * |.....................fromBubble scale to 0.97.....................|
         * |fromBubble handle alpha 0|....fromBubble alpha to 0.....|         |
         * 0-------------------------50-----------------------100---150--------200----------250--400
         *                           |..toBubble alpha to 1...|     |toBubble handle alpha 1|    |
         *                           |................toBubble position +/-48 to 0...............|
         */

        mExpandedBubble = toBubble;
        final BubbleBarExpandedView toBbev = toBubble.getBubbleBarExpandedView();
        final BubbleBarExpandedView fromBbev = fromBubble.getBubbleBarExpandedView();
        if (toBbev == null || fromBbev == null) {
            return;
        }

        fromBbev.setAnimating(true);

        prepareForAnimateIn(toBbev);
        final float endTx = toBbev.getTranslationX();
        final float startTx = getSwitchAnimationInitialTx(endTx);
        toBbev.setTranslationX(startTx);
        toBbev.getHandleView().setAlpha(0f);
        toBbev.getHandleView().setHandleInitialColor(fromBbev.getHandleView().getHandleColor());

        toBbev.animateExpansionWhenTaskViewVisible(() -> {
            AnimatorSet switchAnim = new AnimatorSet();
            switchAnim.playTogether(switchOutAnimator(fromBbev), switchInAnimator(toBbev, endTx));
            switchAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                            "BBAnimationHelper.animateSwitch(): finished");
                    if (endRunnable != null) {
                        endRunnable.run();
                    }
                }
            });
            startNewAnimator(switchAnim);
        });
    }

    private float getSwitchAnimationInitialTx(float endTx) {
        if (mPositioner.isBubbleBarOnLeft()) {
            return endTx - mSwitchAnimPositionOffset;
        } else {
            return endTx + mSwitchAnimPositionOffset;
        }
    }

    private Animator switchOutAnimator(BubbleBarExpandedView bbev) {
        setPivotToCenter(bbev);
        AnimatorSet scaleAnim = new AnimatorSet();
        scaleAnim.playTogether(
                ObjectAnimator.ofFloat(bbev, SCALE_X, SWITCH_OUT_SCALE),
                ObjectAnimator.ofFloat(bbev, SCALE_Y, SWITCH_OUT_SCALE)
        );
        scaleAnim.setInterpolator(Interpolators.ACCELERATE);
        scaleAnim.setDuration(SWITCH_OUT_SCALE_DURATION);

        ObjectAnimator alphaAnim = createAlphaAnimator(bbev, /* visible= */ false);
        alphaAnim.setStartDelay(SWITCH_OUT_HANDLE_ALPHA_DURATION);
        alphaAnim.setDuration(SWITCH_OUT_ALPHA_DURATION);

        ObjectAnimator handleAlphaAnim = ObjectAnimator.ofFloat(bbev.getHandleView(), ALPHA, 0f)
                .setDuration(SWITCH_OUT_HANDLE_ALPHA_DURATION);

        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(scaleAnim, alphaAnim, handleAlphaAnim);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                bbev.setAnimating(false);
            }
        });
        return animator;
    }

    private Animator switchInAnimator(BubbleBarExpandedView bbev, float restingTx) {
        ObjectAnimator positionAnim = ObjectAnimator.ofFloat(bbev, TRANSLATION_X, restingTx);
        positionAnim.setInterpolator(Interpolators.EMPHASIZED_DECELERATE);
        positionAnim.setStartDelay(SWITCH_IN_ANIM_DELAY);
        positionAnim.setDuration(SWITCH_IN_TX_DURATION);

        // Animate alpha directly to have finer control over surface z-ordering
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(bbev, TASK_VIEW_ALPHA, 1f);
        alphaAnim.setStartDelay(SWITCH_IN_ANIM_DELAY);
        alphaAnim.setDuration(SWITCH_IN_ALPHA_DURATION);
        alphaAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                bbev.setSurfaceZOrderedOnTop(true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                bbev.setContentVisibility(true);
                // The outgoing expanded view alpha animation is still in progress.
                // Do not reset the surface z-order as otherwise the outgoing expanded view is
                // placed on top.
            }
        });

        ObjectAnimator handleAlphaAnim = ObjectAnimator.ofFloat(bbev.getHandleView(), ALPHA, 1f);
        handleAlphaAnim.setStartDelay(SWITCH_IN_HANDLE_ALPHA_DELAY);
        handleAlphaAnim.setDuration(SWITCH_IN_HANDLE_ALPHA_DURATION);
        handleAlphaAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                bbev.setSurfaceZOrderedOnTop(false);
                bbev.setAnimating(false);
            }
        });

        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(positionAnim, alphaAnim, handleAlphaAnim);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                updateExpandedView(bbev);
            }
        });
        return animator;
    }

    /**
     * Animate the expanded bubble when it is being dragged
     */
    public void animateStartDrag() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateStartDrag()");
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate start drag without a bubble");
            return;
        }
        setDragPivot(bbev);
        bbev.setDragging(true);
        // Corner radius gets scaled, apply the reverse scale to ensure we have the desired radius
        final float cornerRadius = bbev.getDraggedCornerRadius() / EXPANDED_VIEW_DRAG_SCALE;

        AnimatorSet contentAnim = new AnimatorSet();
        contentAnim.playTogether(
                ObjectAnimator.ofFloat(bbev, SCALE_X, EXPANDED_VIEW_DRAG_SCALE),
                ObjectAnimator.ofFloat(bbev, SCALE_Y, EXPANDED_VIEW_DRAG_SCALE),
                ObjectAnimator.ofFloat(bbev, CORNER_RADIUS, cornerRadius)
        );
        contentAnim.setDuration(EXPANDED_VIEW_DRAG_ANIMATION_DURATION).setInterpolator(EMPHASIZED);

        ObjectAnimator handleAnim = ObjectAnimator.ofFloat(bbev.getHandleView(), ALPHA, 0f)
                .setDuration(HANDLE_ALPHA_ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(contentAnim, handleAnim);
        animatorSet.addListener(new DragAnimatorListenerAdapter(bbev));
        startNewAnimator(animatorSet);
    }

    /**
     * Animates dismissal of currently expanded bubble
     *
     * @param endRunnable a runnable to run at the end of the animation (even if the animation is
     *                    canceled)
     */
    public void animateDismiss(Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateDismiss()");
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate dismiss without a bubble");
            return;
        }

        int[] location = bbev.getLocationOnScreen();
        int diffFromBottom = mPositioner.getScreenRect().bottom - location[1];

        ObjectAnimator animator = ObjectAnimator.ofFloat(
                bbev, TRANSLATION_Y, bbev.getTranslationY() + (diffFromBottom * 2));
        animator.setDuration(EXPANDED_VIEW_DISMISS_DURATION);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateDismiss(): finished");
                if (endRunnable != null) {
                    endRunnable.run();
                }
            }
        });
        startNewAnimator(animator);
    }

    /**
     * Animate current expanded bubble back to its rest position
     */
    public void animateToRestPosition() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateToRestPosition()");
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate expanded view to rest position without a bubble");
            return;
        }
        final boolean isOverflow = mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        final Rect rect = new Rect();
        mPositioner.getBubbleBarExpandedViewBounds(mPositioner.isBubbleBarOnLeft(),
                isOverflow, rect);

        AnimatorSet contentAnim = new AnimatorSet();
        contentAnim.playTogether(
                ObjectAnimator.ofFloat(bbev, X, rect.left),
                ObjectAnimator.ofFloat(bbev, Y, rect.top),
                ObjectAnimator.ofFloat(bbev, SCALE_X, 1f),
                ObjectAnimator.ofFloat(bbev, SCALE_Y, 1f),
                ObjectAnimator.ofFloat(bbev, CORNER_RADIUS, bbev.getRestingCornerRadius())
        );
        contentAnim.setDuration(EXPANDED_VIEW_ANIMATE_TO_REST_DURATION).setInterpolator(EMPHASIZED);

        ObjectAnimator handleAlphaAnim = ObjectAnimator.ofFloat(bbev.getHandleView(), ALPHA, 1f)
                .setDuration(HANDLE_ALPHA_ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(contentAnim, handleAlphaAnim);
        animatorSet.addListener(new DragAnimatorListenerAdapter(bbev) {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                bbev.resetPivot();
                bbev.setDragging(false);
                updateExpandedView(bbev);
            }
        });
        startNewAnimator(animatorSet);
    }

    /**
     * Animates currently expanded bubble into the given {@link MagneticTarget}.
     *
     * @param target magnetic target to snap to
     * @param endRunnable a runnable to run at the end of the animation (even if the animation is
     *                    canceled)
     */
    public void animateIntoTarget(MagneticTarget target, @Nullable Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateIntoTarget()");
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to snap the expanded view to target without a bubble");
            return;
        }

        setDragPivot(bbev);

        // When the view animates into the target, it is scaled down with the pivot at center top.
        // Find the point on the view that would be the center of the view at its final scale.
        // Once we know that, we can calculate x and y distance from the center of the target view
        // and use that for the translation animation to ensure that the view at final scale is
        // placed at the center of the target.

        // Set mTmpLocation to the current location of the view on the screen, taking into account
        // any scale applied.
        bbev.getLocationOnScreen(mTmpLocation);
        // Since pivotX is at the center of the x-axis, even at final scale, center of the view on
        // x-axis will be the same as the center of the view at current size.
        // Get scaled width of the view and adjust mTmpLocation so that point on x-axis is at the
        // center of the view at its current size.
        float currentWidth = bbev.getWidth() * bbev.getScaleX();
        mTmpLocation[0] += (int) (currentWidth / 2f);
        // Since pivotY is at the top of the view, at final scale, top coordinate of the view
        // remains the same.
        // Get height of the view at final scale and adjust mTmpLocation so that point on y-axis is
        // moved down by half of the height at final scale.
        float targetHeight = bbev.getHeight() * EXPANDED_VIEW_IN_TARGET_SCALE;
        mTmpLocation[1] += (int) (targetHeight / 2f);
        // mTmpLocation is now set to the point on the view that will be the center of the view once
        // scale is applied.

        // Calculate the difference between the target's center coordinates and mTmpLocation
        float xDiff = target.getCenterOnScreen().x - mTmpLocation[0];
        float yDiff = target.getCenterOnScreen().y - mTmpLocation[1];

        // Corner radius gets scaled, apply the reverse scale to ensure we have the desired radius
        final float cornerRadius = bbev.getDraggedCornerRadius() / EXPANDED_VIEW_IN_TARGET_SCALE;

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                // Move expanded view to the center of dismiss view
                ObjectAnimator.ofFloat(bbev, TRANSLATION_X, bbev.getTranslationX() + xDiff),
                ObjectAnimator.ofFloat(bbev, TRANSLATION_Y, bbev.getTranslationY() + yDiff),
                // Scale expanded view down
                ObjectAnimator.ofFloat(bbev, SCALE_X, EXPANDED_VIEW_IN_TARGET_SCALE),
                ObjectAnimator.ofFloat(bbev, SCALE_Y, EXPANDED_VIEW_IN_TARGET_SCALE),
                // Update corner radius for expanded view
                ObjectAnimator.ofFloat(bbev, CORNER_RADIUS, cornerRadius),
                // Scale dismiss view up
                ObjectAnimator.ofFloat(target.getTargetView(), SCALE_X, DISMISS_VIEW_SCALE),
                ObjectAnimator.ofFloat(target.getTargetView(), SCALE_Y, DISMISS_VIEW_SCALE)
        );
        animatorSet.setDuration(EXPANDED_VIEW_SNAP_TO_DISMISS_DURATION).setInterpolator(
                EMPHASIZED_DECELERATE);
        animatorSet.addListener(new DragAnimatorListenerAdapter(bbev) {
            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                        "BBAnimationHelper.animateIntoTarget(): finished");
                super.onAnimationEnd(animation);
                if (endRunnable != null) {
                    endRunnable.run();
                }
            }
        });
        startNewAnimator(animatorSet);
    }

    /**
     * Animate currently expanded view when it is released from dismiss view
     */
    public void animateUnstuckFromDismissView(MagneticTarget target) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateUnstuckFromDismissView()");
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to unsnap the expanded view from dismiss without a bubble");
            return;
        }
        setDragPivot(bbev);
        // Corner radius gets scaled, apply the reverse scale to ensure we have the desired radius
        final float cornerRadius = bbev.getDraggedCornerRadius() / EXPANDED_VIEW_DRAG_SCALE;
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(bbev, SCALE_X, EXPANDED_VIEW_DRAG_SCALE),
                ObjectAnimator.ofFloat(bbev, SCALE_Y, EXPANDED_VIEW_DRAG_SCALE),
                ObjectAnimator.ofFloat(bbev, CORNER_RADIUS, cornerRadius),
                ObjectAnimator.ofFloat(target.getTargetView(), SCALE_X, 1f),
                ObjectAnimator.ofFloat(target.getTargetView(), SCALE_Y, 1f)
        );
        animatorSet.setDuration(EXPANDED_VIEW_SNAP_TO_DISMISS_DURATION).setInterpolator(
                EMPHASIZED_DECELERATE);
        animatorSet.addListener(new DragAnimatorListenerAdapter(bbev));
        startNewAnimator(animatorSet);
    }

    /**
     * Animates converting of a non-bubble task into an expanded bubble view.
     *
     * @param endRunnable a runnable to run at the end of the animation (even if the animation is
     *                    canceled)
     */
    public void animateConvert(BubbleViewProvider expandedBubble,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull Rect origBounds,
            float origScale,
            @NonNull SurfaceControl snapshot,
            @NonNull SurfaceControl taskLeash,
            @Nullable Runnable endRunnable) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateConvert()");
        mExpandedBubble = expandedBubble;
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            return;
        }

        bbev.setTaskViewAlpha(1f);
        SurfaceControl tvSf = ((Bubble) mExpandedBubble).getTaskView().getSurfaceControl();

        final boolean isOverflow = mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        final Rect restBounds = new Rect();
        mPositioner.getBubbleBarExpandedViewBounds(mPositioner.isBubbleBarOnLeft(),
                isOverflow, restBounds);

        Rect startBounds = new Rect(origBounds.left - restBounds.left,
                origBounds.top - restBounds.top,
                origBounds.right - restBounds.left,
                origBounds.bottom - restBounds.top);
        Rect endBounds = new Rect(0, 0, restBounds.width(), restBounds.height());
        final SizeChangeAnimation sca = new SizeChangeAnimation(startBounds, endBounds,
                origScale, /* scaleFactor= */ 1f);
        sca.initialize(bbev, taskLeash, snapshot, startT);

        Animator a = sca.buildViewAnimator(bbev, tvSf, snapshot, /* onFinish */ (va) -> {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.animateConvert(): finished");
            updateExpandedView(bbev);
            snapshot.release();
            bbev.setSurfaceZOrderedOnTop(false);
            bbev.setAnimating(false);
            if (endRunnable != null) {
                endRunnable.run();
            }
        });

        bbev.setSurfaceZOrderedOnTop(true);
        a.setDuration(EXPANDED_VIEW_ANIMATE_TO_REST_DURATION);
        a.setInterpolator(EMPHASIZED);
        a.start();
    }

    /**
     * Cancel current animations
     */
    public void cancelAnimations() {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "BBAnimationHelper.cancelAnimations(): "
                + "hasRunningAnimator=%b",
                (mRunningAnimator != null && mRunningAnimator.isRunning()));
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
        if (mRunningAnimator != null) {
            if (mRunningAnimator.isRunning()) {
                mRunningAnimator.cancel();
            }
            mRunningAnimator = null;
        }
    }

    /** Handles IME position changes. */
    public void onImeTopChanged(int imeTop) {
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Bubble bar expanded view was null when IME top changed");
            return;
        }
        bbev.onImeTopChanged(imeTop);
    }

    private @Nullable BubbleBarExpandedView getExpandedView() {
        BubbleViewProvider bubble = mExpandedBubble;
        if (bubble != null) {
            return bubble.getBubbleBarExpandedView();
        }
        return null;
    }

    private void updateExpandedView(BubbleBarExpandedView bbev) {
        if (bbev == null) {
            Log.w(TAG, "Trying to update the expanded view without a bubble");
            return;
        }
        final boolean isOverflow = mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        final Rect rect = new Rect();
        mPositioner.getBubbleBarExpandedViewBounds(mPositioner.isBubbleBarOnLeft(),
                isOverflow, rect);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bbev.getLayoutParams();
        lp.width = rect.width();
        lp.height = rect.height();
        bbev.setLayoutParams(lp);
        bbev.setX(rect.left);
        bbev.setY(rect.top);
        bbev.setScaleX(1f);
        bbev.setScaleY(1f);
        bbev.updateLocation();
        bbev.maybeShowOverflow();
    }

    private void startNewAnimator(Animator animator) {
        cancelAnimations();
        mRunningAnimator = animator;
        animator.start();
    }

    /**
     * Animate the alpha of the expanded view between visible (1) and invisible (0).
     * {@link BubbleBarExpandedView} requires
     * {@link com.android.wm.shell.bubbles.BubbleExpandedView#setSurfaceZOrderedOnTop(boolean)} to
     * be called before alpha can be applied.
     * Only supports alpha of 1 or 0. Otherwise we can't reset surface z-order at the end.
     */
    private ObjectAnimator createAlphaAnimator(BubbleBarExpandedView bubbleBarExpandedView,
            boolean visible) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(bubbleBarExpandedView, TASK_VIEW_ALPHA,
                visible ? 1f : 0f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Move task view to the top of the window so alpha can be applied to it
                bubbleBarExpandedView.setSurfaceZOrderedOnTop(true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                bubbleBarExpandedView.setContentVisibility(visible);
                if (!visible) {
                    // Hide the expanded view before we reset the z-ordering
                    bubbleBarExpandedView.setVisibility(INVISIBLE);
                }
                bubbleBarExpandedView.setSurfaceZOrderedOnTop(false);
            }
        });
        return animator;
    }

    private static void setDragPivot(BubbleBarExpandedView bbev) {
        bbev.setPivotX(bbev.getWidth() / 2f);
        bbev.setPivotY(0f);
    }

    private static void setPivotToCenter(BubbleBarExpandedView bbev) {
        bbev.setPivotX(bbev.getWidth() / 2f);
        bbev.setPivotY(bbev.getHeight() / 2f);
    }

    private static class DragAnimatorListenerAdapter extends AnimatorListenerAdapter {

        private final BubbleBarExpandedView mBubbleBarExpandedView;

        DragAnimatorListenerAdapter(BubbleBarExpandedView bbev) {
            mBubbleBarExpandedView = bbev;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            mBubbleBarExpandedView.setAnimating(true);
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            mBubbleBarExpandedView.setAnimating(false);
        }
    }
}
