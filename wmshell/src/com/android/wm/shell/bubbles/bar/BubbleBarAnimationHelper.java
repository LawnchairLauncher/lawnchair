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
import static android.view.View.SCALE_X;
import static android.view.View.SCALE_Y;
import static android.view.View.TRANSLATION_X;
import static android.view.View.TRANSLATION_Y;
import static android.view.View.VISIBLE;
import static android.view.View.X;
import static android.view.View.Y;

import static com.android.wm.shell.animation.Interpolators.EMPHASIZED;
import static com.android.wm.shell.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.wm.shell.bubbles.bar.BubbleBarExpandedView.CORNER_RADIUS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.util.Size;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.bubbles.BubbleOverflow;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.bubbles.BubbleViewProvider;
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix;
import com.android.wm.shell.common.magnetictarget.MagnetizedObject.MagneticTarget;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

/**
 * Helper class to animate a {@link BubbleBarExpandedView} on a bubble.
 */
public class BubbleBarAnimationHelper {

    private static final String TAG = BubbleBarAnimationHelper.class.getSimpleName();

    private static final float EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT = 0.1f;
    private static final float EXPANDED_VIEW_ANIMATE_OUT_SCALE_AMOUNT = .75f;
    private static final int EXPANDED_VIEW_ALPHA_ANIMATION_DURATION = 150;
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

    /** Spring config for the expanded view scale-in animation. */
    private final PhysicsAnimator.SpringConfig mScaleInSpringConfig =
            new PhysicsAnimator.SpringConfig(300f, 0.9f);

    /** Spring config for the expanded view scale-out animation. */
    private final PhysicsAnimator.SpringConfig mScaleOutSpringConfig =
            new PhysicsAnimator.SpringConfig(900f, 1f);

    /** Matrix used to scale the expanded view container with a given pivot point. */
    private final AnimatableScaleMatrix mExpandedViewContainerMatrix = new AnimatableScaleMatrix();

    /** Animator for animating the expanded view's alpha (including the TaskView inside it). */
    private final ValueAnimator mExpandedViewAlphaAnimator = ValueAnimator.ofFloat(0f, 1f);

    @Nullable
    private Animator mRunningDragAnimator;

    private final Context mContext;
    private final BubbleBarLayerView mLayerView;
    private final BubblePositioner mPositioner;
    private final int[] mTmpLocation = new int[2];

    private BubbleViewProvider mExpandedBubble;
    private boolean mIsExpanded = false;

    public BubbleBarAnimationHelper(Context context,
            BubbleBarLayerView bubbleBarLayerView,
            BubblePositioner positioner) {
        mContext = context;
        mLayerView = bubbleBarLayerView;
        mPositioner = positioner;

        mExpandedViewAlphaAnimator.setDuration(EXPANDED_VIEW_ALPHA_ANIMATION_DURATION);
        mExpandedViewAlphaAnimator.setInterpolator(Interpolators.PANEL_CLOSE_ACCELERATED);
        mExpandedViewAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                BubbleBarExpandedView bbev = getExpandedView();
                if (bbev != null) {
                    // We need to be Z ordered on top in order for alpha animations to work.
                    bbev.setSurfaceZOrderedOnTop(true);
                    bbev.setAnimating(true);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                BubbleBarExpandedView bbev = getExpandedView();
                if (bbev != null) {
                    // The surface needs to be Z ordered on top for alpha values to work on the
                    // TaskView, and if we're temporarily hidden, we are still on the screen
                    // with alpha = 0f until we animate back. Stay Z ordered on top so the alpha
                    // = 0f remains in effect.
                    if (mIsExpanded) {
                        bbev.setSurfaceZOrderedOnTop(false);
                    }

                    bbev.setContentVisibility(mIsExpanded);
                    bbev.setAnimating(false);
                }
            }
        });
        mExpandedViewAlphaAnimator.addUpdateListener(valueAnimator -> {
            BubbleBarExpandedView bbev = getExpandedView();
            if (bbev != null) {
                float alpha = (float) valueAnimator.getAnimatedValue();
                bbev.setTaskViewAlpha(alpha);
                bbev.setAlpha(alpha);
            }
        });
    }

    /**
     * Animates the provided bubble's expanded view to the expanded state.
     */
    public void animateExpansion(BubbleViewProvider expandedBubble,
            @Nullable Runnable afterAnimation) {
        mExpandedBubble = expandedBubble;
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            return;
        }
        mIsExpanded = true;

        mExpandedViewContainerMatrix.setScaleX(0f);
        mExpandedViewContainerMatrix.setScaleY(0f);

        updateExpandedView();
        bbev.setAnimating(true);
        bbev.setContentVisibility(false);
        bbev.setAlpha(0f);
        bbev.setTaskViewAlpha(0f);
        bbev.setVisibility(VISIBLE);

        setScaleFromBubbleBar(mExpandedViewContainerMatrix,
                1f - EXPANDED_VIEW_ANIMATE_SCALE_AMOUNT);

        bbev.setAnimationMatrix(mExpandedViewContainerMatrix);

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
                    bbev.setAnimationMatrix(mExpandedViewContainerMatrix);
                })
                .withEndActions(() -> {
                    bbev.setAnimationMatrix(null);
                    updateExpandedView();
                    bbev.setSurfaceZOrderedOnTop(false);
                    if (afterAnimation != null) {
                        afterAnimation.run();
                    }
                })
                .start();
    }

    /**
     * Collapses the currently expanded bubble.
     *
     * @param endRunnable a runnable to run at the end of the animation.
     */
    public void animateCollapse(Runnable endRunnable) {
        mIsExpanded = false;
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate collapse without a bubble");
            return;
        }
        bbev.setScaleX(1f);
        bbev.setScaleY(1f);

        setScaleFromBubbleBar(mExpandedViewContainerMatrix, 1f);

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
                    if (endRunnable != null) {
                        endRunnable.run();
                    }
                })
                .start();
        mExpandedViewAlphaAnimator.reverse();
    }

    private void setScaleFromBubbleBar(AnimatableScaleMatrix matrix, float scale) {
        // Set the pivot point for the scale, so the view animates out from the bubble bar.
        Rect availableRect = mPositioner.getAvailableRect();
        float pivotX = mPositioner.isBubbleBarOnLeft() ? availableRect.left : availableRect.right;
        float pivotY = mPositioner.getBubbleBarTopOnScreen();
        matrix.setScale(scale, scale, pivotX, pivotY);
    }

    /**
     * Animate the expanded bubble when it is being dragged
     */
    public void animateStartDrag() {
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate start drag without a bubble");
            return;
        }
        setDragPivot(bbev);
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
        startNewDragAnimation(animatorSet);
    }

    /**
     * Animates dismissal of currently expanded bubble
     *
     * @param endRunnable a runnable to run at the end of the animation
     */
    public void animateDismiss(Runnable endRunnable) {
        mIsExpanded = false;
        final BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate dismiss without a bubble");
            return;
        }

        int[] location = bbev.getLocationOnScreen();
        int diffFromBottom = mPositioner.getScreenRect().bottom - location[1];

        cancelAnimations();
        bbev.animate()
                // 2x distance from bottom so the view flies out
                .translationYBy(diffFromBottom * 2)
                .setDuration(EXPANDED_VIEW_DISMISS_DURATION)
                .withEndAction(endRunnable)
                .start();
    }

    /**
     * Animate current expanded bubble back to its rest position
     */
    public void animateToRestPosition() {
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to animate expanded view to rest position without a bubble");
            return;
        }
        Point restPoint = getExpandedViewRestPosition(getExpandedViewSize());

        AnimatorSet contentAnim = new AnimatorSet();
        contentAnim.playTogether(
                ObjectAnimator.ofFloat(bbev, X, restPoint.x),
                ObjectAnimator.ofFloat(bbev, Y, restPoint.y),
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
            }
        });
        startNewDragAnimation(animatorSet);
    }

    /**
     * Animates currently expanded bubble into the given {@link MagneticTarget}.
     *
     * @param target magnetic target to snap to
     * @param endRunnable a runnable to run at the end of the animation
     */
    public void animateIntoTarget(MagneticTarget target, @Nullable Runnable endRunnable) {
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
                super.onAnimationEnd(animation);
                if (endRunnable != null) {
                    endRunnable.run();
                }
            }
        });
        startNewDragAnimation(animatorSet);
    }

    /**
     * Animate currently expanded view when it is released from dismiss view
     */
    public void animateUnstuckFromDismissView(MagneticTarget target) {
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
        startNewDragAnimation(animatorSet);
    }

    /**
     * Cancel current animations
     */
    public void cancelAnimations() {
        PhysicsAnimator.getInstance(mExpandedViewContainerMatrix).cancel();
        mExpandedViewAlphaAnimator.cancel();
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev != null) {
            bbev.animate().cancel();
        }
        if (mRunningDragAnimator != null) {
            mRunningDragAnimator.cancel();
            mRunningDragAnimator = null;
        }
    }

    private @Nullable BubbleBarExpandedView getExpandedView() {
        BubbleViewProvider bubble = mExpandedBubble;
        if (bubble != null) {
            return bubble.getBubbleBarExpandedView();
        }
        return null;
    }

    private void updateExpandedView() {
        BubbleBarExpandedView bbev = getExpandedView();
        if (bbev == null) {
            Log.w(TAG, "Trying to update the expanded view without a bubble");
            return;
        }

        final Size size = getExpandedViewSize();
        Point position = getExpandedViewRestPosition(size);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bbev.getLayoutParams();
        lp.width = size.getWidth();
        lp.height = size.getHeight();
        bbev.setLayoutParams(lp);
        bbev.setX(position.x);
        bbev.setY(position.y);
        bbev.updateLocation();
        bbev.maybeShowOverflow();
    }

    private Point getExpandedViewRestPosition(Size size) {
        final int padding = mPositioner.getBubbleBarExpandedViewPadding();
        Point point = new Point();
        if (mPositioner.isBubbleBarOnLeft()) {
            point.x = mPositioner.getInsets().left + padding;
        } else {
            point.x = mPositioner.getAvailableRect().width() - size.getWidth() - padding;
        }
        point.y = mPositioner.getExpandedViewBottomForBubbleBar() - size.getHeight();
        return point;
    }

    private Size getExpandedViewSize() {
        boolean isOverflowExpanded = mExpandedBubble.getKey().equals(BubbleOverflow.KEY);
        final int width = mPositioner.getExpandedViewWidthForBubbleBar(isOverflowExpanded);
        final int height = mPositioner.getExpandedViewHeightForBubbleBar(isOverflowExpanded);
        return new Size(width, height);
    }

    private void startNewDragAnimation(Animator animator) {
        cancelAnimations();
        mRunningDragAnimator = animator;
        animator.start();
    }

    private static void setDragPivot(BubbleBarExpandedView bbev) {
        bbev.setPivotX(bbev.getWidth() / 2f);
        bbev.setPivotY(0f);
    }

    private class DragAnimatorListenerAdapter extends AnimatorListenerAdapter {

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
            mRunningDragAnimator = null;
        }
    }
}
