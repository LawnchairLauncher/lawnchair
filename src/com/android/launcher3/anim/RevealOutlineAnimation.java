package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A {@link ViewOutlineProvider} that has helper functions to create reveal animations.
 * This class should be extended so that subclasses can define the reveal shape as the
 * animation progresses from 0 to 1.
 */
public abstract class RevealOutlineAnimation extends ViewOutlineProvider {
    protected Rect mOutline;
    protected float mOutlineRadius;

    public RevealOutlineAnimation() {
        mOutline = new Rect();
    }

    /** Returns whether elevation should be removed for the duration of the reveal animation. */
    abstract boolean shouldRemoveElevationDuringAnimation();
    /** Sets the progress, from 0 to 1, of the reveal animation. */
    abstract void setProgress(float progress);

    /**
     * @see #createRevealAnimator(View, boolean, float) where startProgress is set to 0.
     */
    public ValueAnimator createRevealAnimator(final View revealView, boolean isReversed) {
        return createRevealAnimator(revealView, isReversed, 0f /* startProgress */);
    }

    /**
     * Animates the given View's ViewOutline according to {@link #setProgress(float)}.
     * @param revealView The View whose outline we are animating.
     * @param isReversed Whether we are hiding rather than revealing the View.
     * @param startProgress The progress at which to start the newly created animation. Useful if
     * the previous reveal animation was cancelled and we want to create a new animation where it
     * left off. Note that if isReversed=true, we start at 1 - startProgress (and go to 0).
     * @return The Animator, which the caller must start.
     */
    public ValueAnimator createRevealAnimator(final View revealView, boolean isReversed,
            float startProgress) {
        ValueAnimator va = isReversed
                ? ValueAnimator.ofFloat(1f - startProgress, 0f)
                : ValueAnimator.ofFloat(startProgress, 1f);
        final float elevation = revealView.getElevation();

        va.addListener(new AnimatorListenerAdapter() {
            private boolean mIsClippedToOutline;
            private ViewOutlineProvider mOldOutlineProvider;

            public void onAnimationStart(Animator animation) {
                mIsClippedToOutline = revealView.getClipToOutline();
                mOldOutlineProvider = revealView.getOutlineProvider();

                revealView.setOutlineProvider(RevealOutlineAnimation.this);
                revealView.setClipToOutline(true);
                if (shouldRemoveElevationDuringAnimation()) {
                    revealView.setTranslationZ(-elevation);
                }
            }

            public void onAnimationEnd(Animator animation) {
                revealView.setOutlineProvider(mOldOutlineProvider);
                revealView.setClipToOutline(mIsClippedToOutline);
                if (shouldRemoveElevationDuringAnimation()) {
                    revealView.setTranslationZ(0);
                }
            }

        });

        va.addUpdateListener(v -> {
            float progress = (Float) v.getAnimatedValue();
            setProgress(progress);
            revealView.invalidateOutline();
        });
        return va;
    }

    @Override
    public void getOutline(View v, Outline outline) {
        outline.setRoundRect(mOutline, mOutlineRadius);
    }

    public float getRadius() {
        return mOutlineRadius;
    }

    public void getOutline(Rect out) {
        out.set(mOutline);
    }
}
