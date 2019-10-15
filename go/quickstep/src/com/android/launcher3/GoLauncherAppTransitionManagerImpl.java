package com.android.launcher3;

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.quickstep.views.IconRecentsView.CONTENT_ALPHA;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.view.View;

import com.android.quickstep.views.IconRecentsView;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
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
    RemoteAnimationRunnerCompat getWallpaperOpenRunner(boolean fromUnlock) {
        return new GoWallpaperOpenLauncherAnimationRunner(mHandler,
                false /* startAtFrontOfQueue */, fromUnlock);
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

    /**
     * Remote animation runner for animation from app to Launcher. For Go, when going to recents,
     * we need to ensure that the recents view is ready for remote animation before starting.
     */
    private final class GoWallpaperOpenLauncherAnimationRunner extends
            WallpaperOpenLauncherAnimationRunner {
        public GoWallpaperOpenLauncherAnimationRunner(Handler handler, boolean startAtFrontOfQueue,
                boolean fromUnlock) {
            super(handler, startAtFrontOfQueue, fromUnlock);
        }

        @Override
        public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                AnimationResult result) {
            boolean isGoingToRecents =
                    taskIsATargetWithMode(targetCompats, mLauncher.getTaskId(), MODE_OPENING)
                    && (mLauncher.getStateManager().getState() == LauncherState.OVERVIEW);
            if (isGoingToRecents) {
                IconRecentsView recentsView = mLauncher.getOverviewPanel();
                if (!recentsView.isReadyForRemoteAnim()) {
                    recentsView.setOnReadyForRemoteAnimCallback(() ->
                        postAsyncCallback(mHandler, () -> onCreateAnimation(targetCompats, result))
                    );
                    return;
                }
            }
            super.onCreateAnimation(targetCompats, result);
        }
    }
}
