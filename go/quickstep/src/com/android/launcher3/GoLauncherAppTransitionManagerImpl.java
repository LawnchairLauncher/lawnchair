package com.android.launcher3;

import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.views.IconRecentsView.CONTENT_ALPHA;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.view.View;

import com.android.quickstep.views.IconRecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

/**
 * A {@link QuickstepAppTransitionManagerImpl} with recents-specific app transitions based off
 * {@link com.android.quickstep.views.IconRecentsView}.
 */
public final class GoLauncherAppTransitionManagerImpl extends QuickstepAppTransitionManagerImpl {

    public GoLauncherAppTransitionManagerImpl(Context context) {
        super(context);
    }

    @Override
    protected boolean isLaunchingFromRecents(View v, RemoteAnimationTargetCompat[] targets) {
        return mLauncher.getStateManager().getState().overviewUi;
    }

    @Override
    protected void composeRecentsLaunchAnimator(AnimatorSet anim, View v,
            RemoteAnimationTargetCompat[] targets, boolean launcherClosing) {
        // Stubbed. Recents launch animation will come from the recents view itself and will not
        // use remote animations.
    }

    @Override
    protected Runnable composeViewContentAnimator(AnimatorSet anim, float[] alphas, float[] trans) {
        IconRecentsView overview = mLauncher.getOverviewPanel();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(overview,
                CONTENT_ALPHA, alphas);
        alpha.setDuration(CONTENT_ALPHA_DURATION);
        alpha.setInterpolator(LINEAR);
        anim.play(alpha);

        ObjectAnimator transY = ObjectAnimator.ofFloat(overview, View.TRANSLATION_Y, trans);
        transY.setInterpolator(AGGRESSIVE_EASE);
        transY.setDuration(CONTENT_TRANSLATION_DURATION);
        anim.play(transY);

        return mLauncher.getStateManager()::reapplyState;
    }
}
