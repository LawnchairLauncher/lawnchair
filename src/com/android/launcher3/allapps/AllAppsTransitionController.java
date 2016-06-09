package com.android.launcher3.allapps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.PagedView;
import com.android.launcher3.util.TouchController;

/**
 * Handles AllApps view transition.
 * 1) Slides all apps view using direct manipulation
 * 2) When finger is released, animate to either top or bottom accordingly.
 *
 * Algorithm:
 * If release velocity > THRES1, snap according to the direction of movement.
 * If release velocity < THRES1, snap according to either top or bottom depending on whether it's
 *     closer to top or closer to the page indicator.
 */
public class AllAppsTransitionController implements TouchController, VerticalPullDetector.Listener {

    private static final String TAG = "AllAppsTrans";
    private static final boolean DBG = false;

     private final Interpolator mAccelInterpolator = new AccelerateInterpolator(1f);

    private static final float ANIMATION_DURATION = 2000;
    private static final float FINAL_ALPHA = .6f;

    private AllAppsContainerView mAppsView;
    private Hotseat mHotseat;
    private Drawable mHotseatBackground;
    private float mHotseatAlpha;
    private View mWorkspaceCurPage;

    private final Launcher mLauncher;
    private final VerticalPullDetector mDetector;

    // Animation in this class is controlled by a single variable {@link mProgressTransY}.
    // Visually, it represents top y coordinate of the all apps container. Using the
    // {@link mTranslation} as the denominator, this fraction value ranges in [0, 1].
    private float mProgressTransY;   // numerator
    private float mTranslation = -1; // denominator

    private long mAnimationDuration;
    private float mCurY;

    private AnimatorSet mCurrentAnimation;

    public AllAppsTransitionController(Launcher launcher) {
        mLauncher = launcher;
        mDetector = new VerticalPullDetector(launcher);
        mDetector.setListener(this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        init();
        if (mLauncher.getWorkspace().isInOverviewMode() ||
                mLauncher.isWidgetsViewVisible()) {
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDetector.setAllAppsState(mLauncher.isAllAppsVisible(), mAppsView.isScrollAtTop());
        }
        mDetector.onTouchEvent(ev);
        return mDetector.mScrolling;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    private void init() {
        if (mAppsView != null) {
            return;
        }
        mAppsView = mLauncher.getAppsView();
        mHotseat = mLauncher.getHotseat();

        if (mHotseatBackground == null) {
            mHotseatBackground = mHotseat.getBackground();
            mHotseatAlpha = mHotseatBackground.getAlpha() / 255f;
        }
    }

    @Override
    public void onScrollStart(boolean start) {
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        preparePull(start);
        mCurY = mAppsView.getTranslationY();
    }

    /**
     * @param start {@code true} if start of new drag.
     */
    public void preparePull(boolean start) {
        // TODO: create a method inside workspace to fetch this easily.
        mWorkspaceCurPage = mLauncher.getWorkspace().getChildAt(
                mLauncher.getWorkspace().getNextPage());
        mHotseat.setVisibility(View.VISIBLE);
        mHotseat.bringToFront();
        if (start) {
            if (!mLauncher.isAllAppsVisible()) {
                mHotseat.setBackground(null);
                mAppsView.setVisibility(View.VISIBLE);
                mAppsView.getContentView().setVisibility(View.VISIBLE);
                mAppsView.getContentView().setBackground(null);
                mAppsView.getRevealView().setVisibility(View.VISIBLE);
                mAppsView.getRevealView().setAlpha(mHotseatAlpha);
                mAppsView.setSearchBarVisible(false);

                if (mTranslation < 0) {
                    mTranslation = mHotseat.getTop();
                    setProgress(mTranslation);
                }
            } else {
                mLauncher.getWorkspace().onLauncherTransitionPrepare(mLauncher, false, false);
                mWorkspaceCurPage.setVisibility(View.VISIBLE);
                ((CellLayout) mWorkspaceCurPage).getShortcutsAndWidgets().setVisibility(View.VISIBLE);
                ((CellLayout) mWorkspaceCurPage).getShortcutsAndWidgets().setAlpha(1f);
                mAppsView.setSearchBarVisible(false);
                setLightStatusBar(false);
            }
        }
    }

    private void setLightStatusBar(boolean enable) {
        int systemUiFlags = mLauncher.getWindow().getDecorView().getSystemUiVisibility();
        if (enable) {
            mLauncher.getWindow().getDecorView().setSystemUiVisibility(systemUiFlags
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        } else {
            mLauncher.getWindow().getDecorView().setSystemUiVisibility(systemUiFlags
                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        }
    }

    @Override
    public boolean onScroll(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }
        if (0 <= mCurY + displacement && mCurY + displacement < mTranslation) {
            setProgress(mCurY + displacement);
        }
        return true;
    }

    /**
     * @param progress y value of the border between hotseat and all apps
     */
    public void setProgress(float progress) {
        mProgressTransY = progress;
        float alpha = calcAlphaAllApps(progress);
        float workspaceHotseatAlpha = 1 - alpha;

        mAppsView.getRevealView().setAlpha(Math.min(FINAL_ALPHA, Math.max(mHotseatAlpha, alpha)));
        mAppsView.getContentView().setAlpha(alpha);
        mAppsView.setTranslationY(progress);
        setTransAndAlpha(mWorkspaceCurPage, -mTranslation + progress, mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
        setTransAndAlpha(mHotseat, -mTranslation + progress, workspaceHotseatAlpha);
    }

    public float getProgress() {
        return mProgressTransY;
    }

    private float calcAlphaAllApps(float progress) {
        return ((mTranslation - progress)/mTranslation);
    }

    private void setTransAndAlpha(View v, float transY, float alpha) {
        if (v != null) {
            v.setTranslationY(transY);
            v.setAlpha(alpha);
        }
    }

    @Override
    public void onScrollEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        if (fling) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());
                showAppsView(); // Flinging in UP direction
            } else {
                calculateDuration(velocity, Math.abs(mTranslation - mAppsView.getTranslationY()));
                showWorkspace(); // Flinging in DOWN direction
            }
            // snap to top or bottom using the release velocity
        } else {
            if (mAppsView.getTranslationY() > mTranslation / 2) {
                calculateDuration(velocity, Math.abs(mTranslation - mAppsView.getTranslationY()));
                showWorkspace(); // Released in the bottom half
            } else {
                calculateDuration(velocity, Math.abs(mAppsView.getTranslationY()));
                showAppsView(); // Released in the top half
            }
        }
    }

    private void calculateDuration(float velocity, float disp) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(1.5f, Math.abs(0.25f * velocity));
        float travelDistance = Math.max(0.2f, disp / mTranslation);
        mAnimationDuration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", mAnimationDuration, velocity, disp));
        }
    }

    /**
     * Depending on the current state of the launcher, either just
     * 1) animate
     * 2) animate and do all the state updates.
     */
    private void showAppsView() {
        if (mLauncher.isAllAppsVisible()) {
            animateToAllApps(mCurrentAnimation, mAnimationDuration);
            mCurrentAnimation.start();
        } else {
            mLauncher.showAppsView(true /* animated */, true /* resetListToTop */,
                    true /* updatePredictedApps */, false /* focusSearchBar */);
        }
    }

    /**
     * Depending on the current state of the launcher, either just
     * 1) animate
     * 2) animate and do all the state updates.
     */
    private void showWorkspace() {
        if (mLauncher.isAllAppsVisible()) {
            mLauncher.showWorkspace(true /* animated */);
        } else {
            animateToWorkspace(mCurrentAnimation, mAnimationDuration);
            mCurrentAnimation.start();
        }
    }

    public void animateToAllApps(AnimatorSet animationOut, long duration) {
        if ((mAppsView = mLauncher.getAppsView()) == null || animationOut == null){
            return;
        }
        if (!mDetector.mScrolling) {
            preparePull(true);
            mAnimationDuration = duration;
        }
        mCurY = mAppsView.getTranslationY();
        final float fromAllAppsTop = mAppsView.getTranslationY();
        final float toAllAppsTop = 0;

        ObjectAnimator driftAndAlpha = ObjectAnimator.ofFloat(this, "progress",
                fromAllAppsTop, toAllAppsTop);
        driftAndAlpha.setDuration(mAnimationDuration);
        driftAndAlpha.setInterpolator(new PagedView.ScrollInterpolator());
        animationOut.play(driftAndAlpha);

        animationOut.addListener(new AnimatorListenerAdapter() {
            boolean canceled = false;
            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) {
                    return;
                } else {
                    finishPullUp();
                    cleanUpAnimation();
                    mDetector.finishedScrolling();
                }
            }});
        mCurrentAnimation = animationOut;
    }

    private void finishPullUp() {
        mAppsView.setSearchBarVisible(true);
        mHotseat.setVisibility(View.INVISIBLE);
        setProgress(0f);
        setLightStatusBar(true);
    }

    public void animateToWorkspace(AnimatorSet animationOut, long duration) {
        if ((mAppsView = mLauncher.getAppsView()) == null || animationOut == null){
            return;
        }
        if(!mDetector.mScrolling) {
            preparePull(true);
            mAnimationDuration = duration;
        }
        final float fromAllAppsTop = mAppsView.getTranslationY();
        final float toAllAppsTop = mTranslation;

        ObjectAnimator driftAndAlpha = ObjectAnimator.ofFloat(this, "progress",
                fromAllAppsTop, toAllAppsTop);
        driftAndAlpha.setDuration(mAnimationDuration);
        driftAndAlpha.setInterpolator(new PagedView.ScrollInterpolator());
        animationOut.play(driftAndAlpha);

        animationOut.addListener(new AnimatorListenerAdapter() {
             boolean canceled = false;
             @Override
             public void onAnimationCancel(Animator animation) {
                 canceled = true;
             }

             @Override
             public void onAnimationEnd(Animator animation) {
                 if (canceled) {
                     return;
                 } else {
                     finishPullDown();
                     cleanUpAnimation();
                     mDetector.finishedScrolling();
                 }
             }});
        mCurrentAnimation = animationOut;
    }

    public void finishPullDown() {
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackground(mHotseatBackground);
        mHotseat.setVisibility(View.VISIBLE);
        setProgress(mTranslation);
        setLightStatusBar(false);
    }

    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
    }

    private void cleanUpAnimation() {
        mCurrentAnimation = null;
    }
}
