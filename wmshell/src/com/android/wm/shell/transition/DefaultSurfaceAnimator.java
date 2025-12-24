/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.TransactionPool;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

public class DefaultSurfaceAnimator {

    /** Builds an animator for the surface and adds it to the `animations` list. */
    static void buildSurfaceAnimation(@NonNull ArrayList<Animator> animations,
            @NonNull Animation anim, @NonNull SurfaceControl leash,
            @NonNull Runnable finishCallback, @NonNull TransactionPool pool,
            @NonNull ShellExecutor mainExecutor, @Nullable Point position, float cornerRadius,
            @Nullable Rect clipRect,
            @Nullable TransitionAnimationHelper.RoundedContentPerDisplay roundedBounds) {
        final DefaultAnimationAdapter adapter = new DefaultAnimationAdapter(anim, leash,
                position, clipRect, cornerRadius, roundedBounds);
        buildSurfaceAnimation(animations, anim, finishCallback, pool, mainExecutor, adapter);
    }

    /** Builds an animator for the surface and adds it to the `animations` list. */
    static void buildSurfaceAnimation(@NonNull ArrayList<Animator> animations,
            @NonNull Animation anim, @NonNull Runnable finishCallback,
            @NonNull TransactionPool pool, @NonNull ShellExecutor mainExecutor,
            @NonNull AnimationAdapter updateListener) {
        final SurfaceControl.Transaction transaction = pool.acquire();
        updateListener.setTransaction(transaction);
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        // Animation length is already expected to be scaled.
        va.overrideDurationScale(1.0f);
        va.setDuration(anim.computeDurationHint());
        setupValueAnimator(va, updateListener, (vanim) -> {
            pool.release(transaction);
            mainExecutor.execute(() -> {
                animations.remove(vanim);
                finishCallback.run();
            });
        });
        animations.add(va);
    }

    /** The animation adapter for buildSurfaceAnimation. */
    abstract static class AnimationAdapter implements ValueAnimator.AnimatorUpdateListener {
        @NonNull final SurfaceControl mLeash;
        @NonNull SurfaceControl.Transaction mTransaction;
        private Choreographer mChoreographer;

        AnimationAdapter(@NonNull SurfaceControl leash) {
            mLeash = Objects.requireNonNull(leash, "leash is null in AnimationAdapter constructor");
        }

        void setTransaction(@NonNull SurfaceControl.Transaction transaction) {
            mTransaction = transaction;
        }

        @Override
        public void onAnimationUpdate(@NonNull ValueAnimator animator) {
            // The finish callback in buildSurfaceAnimation will ensure that the animation ends
            // with fraction 1.
            final long currentPlayTime = animator.getAnimatedFraction() >= 1f
                    ? animator.getDuration()
                    : Math.min(animator.getDuration(), animator.getCurrentPlayTime());
            applyTransformation(animator, currentPlayTime);
            if (mChoreographer == null) {
                mChoreographer = Choreographer.getInstance();
            }
            mTransaction.setFrameTimelineVsync(mChoreographer.getVsyncId());
            mTransaction.apply();
        }

        abstract void applyTransformation(@NonNull ValueAnimator animator, long currentPlayTime);
    }

    private static class DefaultAnimationAdapter extends AnimationAdapter {
        final Transformation mTransformation = new Transformation();
        final float[] mMatrix = new float[9];
        @NonNull final Animation mAnim;
        @Nullable final Point mPosition;
        @Nullable final Rect mClipRect;
        @Nullable private final Rect mAnimClipRect;
        final float mCornerRadius;
        final int mWindowBottom;

        /**
         * Inset changes aren't synchronized with transitions, so use a "provider" to track the
         * bottom of the display content during the animation.
         */
        @Nullable final TransitionAnimationHelper.RoundedContentPerDisplay mRoundedContentBounds;

        DefaultAnimationAdapter(@NonNull Animation anim, @NonNull SurfaceControl leash,
                @Nullable Point position, @Nullable Rect clipRect, float cornerRadius,
                TransitionAnimationHelper.RoundedContentPerDisplay roundedBounds) {
            super(leash);
            mAnim = anim;
            mPosition = (position != null && (position.x != 0 || position.y != 0))
                    ? position : null;
            mClipRect = (clipRect != null && !clipRect.isEmpty()) ? clipRect : null;
            mAnimClipRect = mClipRect != null ? new Rect() : null;
            mCornerRadius = cornerRadius;
            mWindowBottom = clipRect != null ? clipRect.bottom : 0;
            mRoundedContentBounds = roundedBounds;
        }

        @Override
        void applyTransformation(@NonNull ValueAnimator animator, long currentPlayTime) {
            final Transformation transformation = mTransformation;
            final SurfaceControl.Transaction t = mTransaction;
            final SurfaceControl leash = mLeash;
            transformation.clear();
            mAnim.getTransformation(currentPlayTime, transformation);
            if (mPosition != null) {
                transformation.getMatrix().postTranslate(mPosition.x, mPosition.y);
            }
            t.setMatrix(leash, transformation.getMatrix(), mMatrix);
            t.setAlpha(leash, transformation.getAlpha());

            if (mClipRect != null) {
                boolean needCrop = false;
                if (mRoundedContentBounds != null) {
                    mClipRect.bottom = Math.min(mRoundedContentBounds.mBounds.bottom,
                            mWindowBottom);
                }

                mAnimClipRect.set(mClipRect);
                if (transformation.hasClipRect()) {
                    mAnimClipRect.intersectUnchecked(transformation.getClipRect());
                    needCrop = true;
                }
                final Insets extensionInsets = Insets.min(transformation.getInsets(), Insets.NONE);
                if (!extensionInsets.equals(Insets.NONE)) {
                    // Clip out any overflowing edge extension.
                    mAnimClipRect.inset(extensionInsets);
                    needCrop = true;
                }
                if (mCornerRadius > 0 && mAnim.hasRoundedCorners()) {
                    // Rounded corner can only be applied if a crop is set.
                    t.setCornerRadius(leash, mCornerRadius);
                    needCrop = true;
                }
                if (needCrop) {
                    t.setWindowCrop(leash, mAnimClipRect);
                }
            }
        }
    }

    /**
     * Setup some callback logic on a value-animator. This helper ensures that a value animator
     * finishes at its final fraction (1f) and that relevant callbacks are only called once.
     */
    public static ValueAnimator setupValueAnimator(ValueAnimator animator,
            ValueAnimator.AnimatorUpdateListener updateListener,
            Consumer<ValueAnimator> afterFinish) {
        animator.addUpdateListener(updateListener);
        animator.addListener(new AnimatorListenerAdapter() {
            // It is possible for the end/cancel to be called more than once, which may cause
            // issues if the animating surface has already been released. Track the finished
            // state here to skip duplicate callbacks. See b/252872225.
            private boolean mFinished;

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                onFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onFinish();
            }

            private void onFinish() {
                if (mFinished) return;
                mFinished = true;
                // Apply transformation of end state in case the animation is canceled.
                if (animator.getAnimatedFraction() < 1f) {
                    animator.setCurrentFraction(1f);
                }
                afterFinish.accept(animator);
                // The update listener can continue to be called after the animation has ended if
                // end() is called manually again before the finisher removes the animation.
                // Remove it manually here to prevent animating a released surface.
                // See b/252872225.
                animator.removeUpdateListener(updateListener);
            }
        });
        return animator;
    }
}
