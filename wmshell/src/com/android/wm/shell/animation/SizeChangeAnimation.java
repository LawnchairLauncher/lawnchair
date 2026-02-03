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

package com.android.wm.shell.animation;

import static com.android.wm.shell.transition.DefaultSurfaceAnimator.setupValueAnimator;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.Nullable;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ClipRectAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;

import com.android.wm.shell.shared.animation.Interpolators;

import java.util.function.Consumer;

/**
 * Animation implementation for size-changing window container animations. Ported from
 * {@link com.android.server.wm.WindowChangeAnimationSpec}.
 * <p>
 * This animation behaves slightly differently depending on whether the window is growing
 * or shrinking:
 * <ul>
 * <li>If growing, it will do a clip-reveal after quicker fade-out/scale of the smaller (old)
 * snapshot.
 * <li>If shrinking, it will do an opposite clip-reveal on the old snapshot followed by a quicker
 * fade-out of the bigger (old) snapshot while simultaneously shrinking the new window into
 * place.
 * </ul>
 */
public class SizeChangeAnimation {
    private final Rect mTmpRect = new Rect();
    final Transformation mTmpTransform = new Transformation();
    final Matrix mTmpMatrix = new Matrix();
    final float[] mTmpFloats = new float[9];
    final float[] mTmpVecs = new float[4];

    private final Animation mAnimation;
    private final Animation mSnapshotAnim;

    private final ValueAnimator mAnimator = ValueAnimator.ofFloat(0f, 1f);

    /**
     * The maximum of stretching applied to any surface during interpolation (since the animation
     * is a combination of stretching/cropping/fading).
     */
    private static final float DEFAULT_SCALE_FACTOR = 0.7f;

    /**
     * Since this animation is made of several sub-animations, we want to pre-arrange the
     * sub-animations on a "virtual timeline" and then drive the overall progress in lock-step.
     *
     * To do this, we have a single value-animator which animates progress from 0-1 with an
     * arbitrary duration and interpolator. Then we convert the progress to a frame in our virtual
     * timeline to get the interpolated transforms.
     *
     * The APIs for arranging the sub-animations use integral frame numbers, so we need to pick
     * an integral "duration" for our virtual timeline. That's what this constant specifies. It
     * is effectively an animation "resolution" since it divides-up the 0-1 interpolation-space.
     */
    private static final int ANIMATION_RESOLUTION = 1000;

    /**
     * Initialize a size-change animation from start to end bounds
     */
    public SizeChangeAnimation(Rect startBounds, Rect endBounds) {
        this(startBounds, endBounds, 1f, DEFAULT_SCALE_FACTOR);
    }

    /**
     * Initialize a size-change animation from start to end bounds.
     * <p>
     * Allows specifying the initial scale factor, {@code initialScale}, that is applied to the
     * start bounds. This can be useful for example when a task is scaled down when the size change
     * animation starts.
     * <p>
     * By default the max scale applied to any surface is {@link #DEFAULT_SCALE_FACTOR}. Use
     * {@code scaleFactor} to override it.
     */
    public SizeChangeAnimation(Rect startBounds, Rect endBounds, float initialScale,
            float scaleFactor) {
        mAnimation = buildContainerAnimation(startBounds, endBounds, initialScale, scaleFactor);
        mSnapshotAnim = buildSnapshotAnimation(startBounds, endBounds, scaleFactor);
    }

    /**
     * Initialize a size-change animation for a container leash.
     */
    public void initialize(SurfaceControl leash, SurfaceControl snapshot,
            SurfaceControl.Transaction startT) {
        startT.reparent(snapshot, leash);
        startT.setPosition(snapshot, 0, 0);
        startT.show(snapshot);
        startT.show(leash);
        apply(startT, leash, snapshot, 0.f);
    }

    /**
     * Initialize a size-change animation for a view containing the leash surface(s).
     *
     * Note that this **will** apply {@param startToApply}!
     */
    public void initialize(View view, SurfaceControl leash, SurfaceControl snapshot,
            SurfaceControl.Transaction startToApply) {
        startToApply.reparent(snapshot, leash);
        startToApply.setPosition(snapshot, 0, 0);
        startToApply.show(snapshot);
        startToApply.show(leash);
        apply(view, startToApply, leash, snapshot, 0.f);
    }

    private ValueAnimator buildAnimatorInner(ValueAnimator.AnimatorUpdateListener updater,
            SurfaceControl leash, SurfaceControl snapshot, Consumer<Animator> onFinish,
            SurfaceControl.Transaction transaction, @Nullable View view) {
        return setupValueAnimator(mAnimator, updater, (anim) -> {
            transaction.reparent(snapshot, null);
            if (view != null) {
                view.setClipBounds(null);
                view.setAnimationMatrix(null);
                transaction.setCrop(leash, null);
            }
            transaction.apply();
            transaction.close();
            onFinish.accept(anim);
        });
    }

    /**
     * Build an animator which works on a pair of surface controls (where the snapshot is assumed
     * to be a child of the main leash).
     *
     * @param onFinish Called when animation finishes. This is called on the anim thread!
     */
    public ValueAnimator buildAnimator(SurfaceControl leash, SurfaceControl snapshot,
            Consumer<Animator> onFinish) {
        final SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        Choreographer choreographer = Choreographer.getInstance();
        return buildAnimatorInner(animator -> {
            // The finish callback in buildSurfaceAnimation will ensure that the animation ends
            // with fraction 1.
            final float progress = Math.clamp(animator.getAnimatedFraction(), 0.f, 1.f);
            apply(transaction, leash, snapshot, progress);
            transaction.setFrameTimelineVsync(choreographer.getVsyncId());
            transaction.apply();
        }, leash, snapshot, onFinish, transaction, null /* view */);
    }

    /**
     * Build an animator which works on a view that contains a pair of surface controls (where
     * the snapshot is assumed to be a child of the main leash).
     *
     * @param onFinish Called when animation finishes. This is called on the anim thread!
     */
    public ValueAnimator buildViewAnimator(View view, SurfaceControl leash,
            SurfaceControl snapshot, Consumer<Animator> onFinish) {
        final SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        return buildAnimatorInner(animator -> {
            // The finish callback in buildSurfaceAnimation will ensure that the animation ends
            // with fraction 1.
            final float progress = Math.clamp(animator.getAnimatedFraction(), 0.f, 1.f);
            apply(view, transaction, leash, snapshot, progress);
        }, leash, snapshot, onFinish, transaction, view);
    }

    /** Animation for the whole container (snapshot is inside this container). */
    private static AnimationSet buildContainerAnimation(Rect startBounds, Rect endBounds,
            float initialScale, float scaleFactor) {
        final long duration = ANIMATION_RESOLUTION;
        boolean growing = endBounds.width() - startBounds.width()
                + endBounds.height() - startBounds.height() >= 0;
        long scalePeriod = (long) (duration * scaleFactor);
        float startScaleX = scaleFactor * ((float) startBounds.width()) / endBounds.width()
                + (1.f - scaleFactor);
        float startScaleY = scaleFactor * ((float) startBounds.height()) / endBounds.height()
                + (1.f - scaleFactor);
        final AnimationSet animSet = new AnimationSet(true);
        // Use a linear interpolator so the driving ValueAnimator sets the interpolation
        animSet.setInterpolator(Interpolators.LINEAR);

        final Animation scaleAnim = new ScaleAnimation(startScaleX, 1, startScaleY, 1);
        scaleAnim.setDuration(scalePeriod);
        long scaleStartOffset = 0;
        if (!growing) {
            scaleStartOffset = duration - scalePeriod;
        }
        scaleAnim.setStartOffset(scaleStartOffset);
        animSet.addAnimation(scaleAnim);

        if (initialScale != 1f) {
            final Animation initialScaleAnim = new ScaleAnimation(initialScale, 1f, initialScale,
                    1f);
            initialScaleAnim.setDuration(scalePeriod);
            initialScaleAnim.setStartOffset(scaleStartOffset);
            animSet.addAnimation(initialScaleAnim);
        }

        final Animation translateAnim = new TranslateAnimation(startBounds.left,
                endBounds.left, startBounds.top, endBounds.top);
        translateAnim.setDuration(duration);
        animSet.addAnimation(translateAnim);
        Rect startClip = new Rect(startBounds);
        startClip.scale(initialScale);
        Rect endClip = new Rect(endBounds);
        startClip.offsetTo(0, 0);
        endClip.offsetTo(0, 0);
        final Animation clipAnim = new ClipRectAnimation(startClip, endClip);
        clipAnim.setDuration(duration);
        animSet.addAnimation(clipAnim);
        animSet.initialize(startBounds.width(), startBounds.height(),
                endBounds.width(), endBounds.height());
        return animSet;
    }

    /** The snapshot surface is assumed to be a child of the container surface. */
    private static AnimationSet buildSnapshotAnimation(Rect startBounds, Rect endBounds,
            float scaleFactor) {
        final long duration = ANIMATION_RESOLUTION;
        boolean growing = endBounds.width() - startBounds.width()
                + endBounds.height() - startBounds.height() >= 0;
        long scalePeriod = (long) (duration * scaleFactor);
        float endScaleX = 1.f / (scaleFactor * ((float) startBounds.width()) / endBounds.width()
                + (1.f - scaleFactor));
        float endScaleY = 1.f / (scaleFactor * ((float) startBounds.height()) / endBounds.height()
                + (1.f - scaleFactor));

        AnimationSet snapAnimSet = new AnimationSet(true);
        // Use a linear interpolator so the driving ValueAnimator sets the interpolation
        snapAnimSet.setInterpolator(Interpolators.LINEAR);
        // Animation for the "old-state" snapshot that is atop the task.
        final Animation snapAlphaAnim = new AlphaAnimation(1.f, 0.f);
        snapAlphaAnim.setDuration(scalePeriod);
        if (!growing) {
            snapAlphaAnim.setStartOffset(duration - scalePeriod);
        }
        snapAnimSet.addAnimation(snapAlphaAnim);
        final Animation snapScaleAnim =
                new ScaleAnimation(endScaleX, endScaleX, endScaleY, endScaleY);
        snapScaleAnim.setDuration(duration);
        snapAnimSet.addAnimation(snapScaleAnim);
        snapAnimSet.initialize(startBounds.width(), startBounds.height(),
                endBounds.width(), endBounds.height());
        return snapAnimSet;
    }

    private void calcCurrentClipBounds(Rect outClip, Transformation fromTransform) {
        // The following applies an inverse scale to the clip-rect so that it crops "after" the
        // scale instead of before.
        mTmpVecs[1] = mTmpVecs[2] = 0;
        mTmpVecs[0] = mTmpVecs[3] = 1;
        fromTransform.getMatrix().mapVectors(mTmpVecs);

        mTmpVecs[0] = 1.f / mTmpVecs[0];
        mTmpVecs[3] = 1.f / mTmpVecs[3];
        final Rect clipRect = fromTransform.getClipRect();
        outClip.left = (int) (clipRect.left * mTmpVecs[0] + 0.5f);
        outClip.right = (int) (clipRect.right * mTmpVecs[0] + 0.5f);
        outClip.top = (int) (clipRect.top * mTmpVecs[3] + 0.5f);
        outClip.bottom = (int) (clipRect.bottom * mTmpVecs[3] + 0.5f);
    }

    private void apply(SurfaceControl.Transaction t, SurfaceControl leash, SurfaceControl snapshot,
            float progress) {
        long currentPlayTime = (long) (((float) ANIMATION_RESOLUTION) * progress);
        // update thumbnail surface
        mSnapshotAnim.getTransformation(currentPlayTime, mTmpTransform);
        t.setMatrix(snapshot, mTmpTransform.getMatrix(), mTmpFloats);
        t.setAlpha(snapshot, mTmpTransform.getAlpha());

        // update container surface
        mAnimation.getTransformation(currentPlayTime, mTmpTransform);
        final Matrix matrix = mTmpTransform.getMatrix();
        t.setMatrix(leash, matrix, mTmpFloats);

        calcCurrentClipBounds(mTmpRect, mTmpTransform);
        t.setCrop(leash, mTmpRect);
    }

    private void apply(View view, SurfaceControl.Transaction tmpT, SurfaceControl leash,
            SurfaceControl snapshot, float progress) {
        long currentPlayTime = (long) (((float) ANIMATION_RESOLUTION) * progress);
        // update thumbnail surface
        mSnapshotAnim.getTransformation(currentPlayTime, mTmpTransform);
        tmpT.setMatrix(snapshot, mTmpTransform.getMatrix(), mTmpFloats);
        tmpT.setAlpha(snapshot, mTmpTransform.getAlpha());

        // update container surface
        mAnimation.getTransformation(currentPlayTime, mTmpTransform);
        final Matrix matrix = mTmpTransform.getMatrix();
        mTmpMatrix.set(matrix);
        // animationMatrix is applied after getTranslation, so "move" the translate to the end.
        mTmpMatrix.preTranslate(-view.getTranslationX(), -view.getTranslationY());
        mTmpMatrix.postTranslate(view.getTranslationX(), view.getTranslationY());
        view.setAnimationMatrix(mTmpMatrix);

        calcCurrentClipBounds(mTmpRect, mTmpTransform);
        tmpT.setCrop(leash, mTmpRect);
        view.setClipBounds(mTmpRect);

        // this takes stuff out of mTmpT so mTmpT can be re-used immediately
        view.getViewRootImpl().applyTransactionOnDraw(tmpT);
    }
}
