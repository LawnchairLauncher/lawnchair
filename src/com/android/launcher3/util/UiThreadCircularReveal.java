package com.android.launcher3.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.launcher3.Utilities;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class UiThreadCircularReveal {

    public static ValueAnimator createCircularReveal(View v, int x, int y, float r0, float r1) {
        return createCircularReveal(v, x, y, r0, r1, ViewOutlineProvider.BACKGROUND);
    }

    public static ValueAnimator createCircularReveal(View v, int x, int y, float r0, float r1,
            final ViewOutlineProvider originalProvider) {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);

        final View revealView = v;
        final RevealOutlineProvider outlineProvider = new RevealOutlineProvider(x, y, r0, r1);
        final float elevation = v.getElevation();

        va.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animation) {
                revealView.setOutlineProvider(outlineProvider);
                revealView.setClipToOutline(true);
                revealView.setTranslationZ(-elevation);
            }

            public void onAnimationEnd(Animator animation) {
                revealView.setOutlineProvider(originalProvider);
                revealView.setClipToOutline(false);
                revealView.setTranslationZ(0);
            }

        });

        va.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                float progress = arg0.getAnimatedFraction();
                outlineProvider.setProgress(progress);
                revealView.invalidateOutline();
                if (!Utilities.isLmpMR1OrAbove()) {
                    revealView.invalidate();
                }
            }
        });
        return va;
    }
}
