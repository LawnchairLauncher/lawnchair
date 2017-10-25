package ch.deletescape.lawnchair.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;

import ch.deletescape.lawnchair.Utilities;

public abstract class RevealOutlineAnimation extends ViewOutlineProvider {
    protected Rect mOutline = new Rect();
    protected float mOutlineRadius;

    abstract void setProgress(float f);

    abstract boolean shouldRemoveElevationDuringAnimation();

    public ValueAnimator createRevealAnimator(View view) {
        return createRevealAnimator(view, false);
    }

    public ValueAnimator createRevealAnimator(final View view, boolean z) {
        ValueAnimator animator = z ? ValueAnimator.ofFloat(1.0f, 0.0f) : ValueAnimator.ofFloat(0.0f, 1.0f);
        final float elevation = view.getElevation();
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean mWasCanceled = false;

            public void onAnimationStart(Animator animator) {
                view.setOutlineProvider(RevealOutlineAnimation.this);
                view.setClipToOutline(true);
                if (shouldRemoveElevationDuringAnimation()) {
                    view.setTranslationZ(-elevation);
                }
            }

            public void onAnimationCancel(Animator animator) {
                mWasCanceled = true;
            }

            public void onAnimationEnd(Animator animator) {
                if (!mWasCanceled) {
                    view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                    view.setClipToOutline(false);
                    if (shouldRemoveElevationDuringAnimation()) {
                        view.setTranslationZ(0.0f);
                    }
                }
            }
        });
        animator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                setProgress((Float) valueAnimator.getAnimatedValue());
                view.invalidateOutline();
                if (!Utilities.ATLEAST_LOLLIPOP_MR1) {
                    view.invalidate();
                }
            }
        });
        return animator;
    }

    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(mOutline, mOutlineRadius);
    }
}