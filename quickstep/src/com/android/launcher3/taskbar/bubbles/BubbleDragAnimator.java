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

package com.android.launcher3.taskbar.bubbles;

import static androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_LOW_BOUNCY;
import static androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW;

import android.content.res.Resources;
import android.graphics.PointF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

import com.android.launcher3.R;
import com.android.wm.shell.common.bubbles.DismissCircleView;
import com.android.wm.shell.common.bubbles.DismissView;
import com.android.wm.shell.shared.animation.PhysicsAnimator;

/**
 * The animator performs the bubble animations while dragging and coordinates bubble and dismiss
 * view animations when it gets magnetized, released or dismissed.
 */
public class BubbleDragAnimator {
    private static final float SCALE_BUBBLE_FOCUSED = 1.2f;
    private static final float SCALE_BUBBLE_CAPTURED = 0.9f;
    private static final float SCALE_BUBBLE_BAR_FOCUSED = 1.1f;
    // 400f matches to MEDIUM_LOW spring stiffness
    private static final float TRANSLATION_SPRING_STIFFNESS = 400f;

    private final PhysicsAnimator.SpringConfig mDefaultConfig =
            new PhysicsAnimator.SpringConfig(STIFFNESS_LOW, DAMPING_RATIO_LOW_BOUNCY);
    private final PhysicsAnimator.SpringConfig mTranslationConfig =
            new PhysicsAnimator.SpringConfig(TRANSLATION_SPRING_STIFFNESS,
                    DAMPING_RATIO_LOW_BOUNCY);
    @NonNull
    private final View mView;
    @NonNull
    private final PhysicsAnimator<View> mBubbleAnimator;
    @Nullable
    private DismissView mDismissView;
    @Nullable
    private PhysicsAnimator<DismissCircleView> mDismissAnimator;
    private final float mBubbleFocusedScale;
    private final float mBubbleCapturedScale;
    private final float mDismissCapturedScale;

    /**
     * Should be initialised for each dragged view
     *
     * @param view the dragged view to animate
     */
    public BubbleDragAnimator(@NonNull View view) {
        mView = view;
        mBubbleAnimator = PhysicsAnimator.getInstance(view);
        mBubbleAnimator.setDefaultSpringConfig(mDefaultConfig);

        Resources resources = view.getResources();
        final int collapsedSize = resources.getDimensionPixelSize(
                R.dimen.bubblebar_dismiss_target_small_size);
        final int expandedSize = resources.getDimensionPixelSize(
                R.dimen.bubblebar_dismiss_target_size);
        mDismissCapturedScale = (float) collapsedSize / expandedSize;

        if (view instanceof BubbleBarView) {
            mBubbleFocusedScale = SCALE_BUBBLE_BAR_FOCUSED;
            mBubbleCapturedScale = mDismissCapturedScale;
        } else {
            mBubbleFocusedScale = SCALE_BUBBLE_FOCUSED;
            mBubbleCapturedScale = SCALE_BUBBLE_CAPTURED;
        }
    }

    /**
     * Sets dismiss view to be animated alongside the dragged bubble
     */
    public void setDismissView(@NonNull DismissView dismissView) {
        mDismissView = dismissView;
        mDismissAnimator = PhysicsAnimator.getInstance(dismissView.getCircle());
        mDismissAnimator.setDefaultSpringConfig(mDefaultConfig);
    }

    /**
     * Animates the focused state of the bubble when the dragging starts
     */
    public void animateFocused() {
        mBubbleAnimator.cancel();
        mBubbleAnimator
                .spring(DynamicAnimation.SCALE_X, mBubbleFocusedScale)
                .spring(DynamicAnimation.SCALE_Y, mBubbleFocusedScale)
                .start();
    }

    /**
     * Animates the dragged bubble movement back to the initial position.
     *
     * @param restingPosition the position to animate to
     * @param velocity        the initial velocity to use for the spring animation
     * @param endActions      gets called when the animation completes or gets cancelled
     */
    public void animateToRestingState(@NonNull PointF restingPosition, @NonNull PointF velocity,
            @Nullable Runnable endActions) {
        mBubbleAnimator.cancel();
        mBubbleAnimator
                .spring(DynamicAnimation.SCALE_X, 1f)
                .spring(DynamicAnimation.SCALE_Y, 1f)
                .spring(BubbleDragController.DRAG_TRANSLATION_X, restingPosition.x, velocity.x,
                        mTranslationConfig)
                .spring(DynamicAnimation.TRANSLATION_Y, restingPosition.y, velocity.y,
                        mTranslationConfig)
                .addEndListener((View target, @NonNull FloatPropertyCompat<? super View> property,
                        boolean wasFling, boolean canceled, float finalValue, float finalVelocity,
                        boolean allRelevantPropertyAnimationsEnded) -> {
                    if (canceled || allRelevantPropertyAnimationsEnded) {
                        resetAnimatedViews(restingPosition);
                        if (endActions != null) {
                            endActions.run();
                        }
                    }
                })
                .start();
    }

    /**
     * Animates the dragged view alongside the dismiss view when it gets captured in the dismiss
     * target area.
     */
    public void animateDismissCaptured() {
        mBubbleAnimator.cancel();
        mBubbleAnimator
                .spring(DynamicAnimation.SCALE_X, mBubbleCapturedScale)
                .spring(DynamicAnimation.SCALE_Y, mBubbleCapturedScale)
                .spring(DynamicAnimation.ALPHA, mDismissCapturedScale)
                .start();

        if (mDismissAnimator != null) {
            mDismissAnimator.cancel();
            mDismissAnimator
                    .spring(DynamicAnimation.SCALE_X, mDismissCapturedScale)
                    .spring(DynamicAnimation.SCALE_Y, mDismissCapturedScale)
                    .start();
        }
    }

    /**
     * Animates the dragged view alongside the dismiss view when it gets released from the dismiss
     * target area.
     */
    public void animateDismissReleased() {
        mBubbleAnimator.cancel();
        mBubbleAnimator
                .spring(DynamicAnimation.SCALE_X, mBubbleFocusedScale)
                .spring(DynamicAnimation.SCALE_Y, mBubbleFocusedScale)
                .spring(DynamicAnimation.ALPHA, 1f)
                .start();

        if (mDismissAnimator != null) {
            mDismissAnimator.cancel();
            mDismissAnimator
                    .spring(DynamicAnimation.SCALE_X, 1f)
                    .spring(DynamicAnimation.SCALE_Y, 1f)
                    .start();
        }
    }

    /**
     * Animates the dragged bubble dismiss when it's released in the dismiss target area.
     *
     * @param initialPosition the initial position to move the bubble too after animation finishes
     * @param endActions      gets called when the animation completes or gets cancelled
     */
    public void animateDismiss(@NonNull PointF initialPosition, @Nullable Runnable endActions) {
        float dismissHeight = mDismissView != null ? mDismissView.getHeight() : 0f;
        float translationY = mView.getTranslationY() + dismissHeight;
        mBubbleAnimator
                .spring(DynamicAnimation.TRANSLATION_Y, translationY)
                .spring(DynamicAnimation.SCALE_X, 0f)
                .spring(DynamicAnimation.SCALE_Y, 0f)
                .spring(DynamicAnimation.ALPHA, 0f)
                .addEndListener((View target, @NonNull FloatPropertyCompat<? super View> property,
                        boolean wasFling, boolean canceled, float finalValue, float finalVelocity,
                        boolean allRelevantPropertyAnimationsEnded) -> {
                    if (canceled || allRelevantPropertyAnimationsEnded) {
                        resetAnimatedViews(initialPosition);
                        if (endActions != null) endActions.run();
                    }
                })
                .start();
    }

    /**
     * Reset the animated views to the initial state
     *
     * @param initialPosition position of the bubble
     */
    private void resetAnimatedViews(@NonNull PointF initialPosition) {
        mView.setScaleX(1f);
        mView.setScaleY(1f);
        mView.setAlpha(1f);
        mView.setTranslationX(initialPosition.x);
        mView.setTranslationY(initialPosition.y);

        if (mDismissView != null) {
            mDismissView.getCircle().setScaleX(1f);
            mDismissView.getCircle().setScaleY(1f);
        }
    }
}
