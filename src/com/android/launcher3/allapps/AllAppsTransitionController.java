package com.android.launcher3.allapps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.PagedView;
import com.android.launcher3.Workspace;
import com.android.launcher3.Workspace.Direction;
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

    private final Interpolator mAccelInterpolator = new AccelerateInterpolator(2f);
    private final Interpolator mDecelInterpolator = new DecelerateInterpolator(1f);

    private static final float ANIMATION_DURATION = 2000;
    public static final float ALL_APPS_FINAL_ALPHA = .8f;

    private static final float PARALLAX_COEFFICIENT = .125f;

    private AllAppsContainerView mAppsView;
    private Workspace mWorkspace;
    private Hotseat mHotseat;
    private float mHotseatBackgroundAlpha;

    private float mStatusBarHeight;

    private final Launcher mLauncher;
    private final VerticalPullDetector mDetector;

    // Animation in this class is controlled by a single variable {@link mProgressTransY}.
    // Visually, it represents top y coordinate of the all apps container. Using the
    // {@link mTranslation} as the denominator, this fraction value ranges in [0, 1].
    private float mProgressTransY;   // numerator
    private float mTranslation = -1; // denominator

    private static final float RECATCH_REJECTION_FRACTION = .0875f;

    // Used in landscape.
    private static final float BAZEL_PULL_UP_HEIGHT = 60;

    private long mAnimationDuration;
    private float mCurY;

    private AnimatorSet mCurrentAnimation;
    private boolean mNoIntercept;

    private boolean mLightStatusBar;

    // At the end of scroll settling, this class also sets the state of the launcher.
    // If it's already set,do not call the #mLauncher.setXXX method.
    private boolean mStateAlreadyChanged;

    public AllAppsTransitionController(Launcher launcher) {
        mLauncher = launcher;
        mDetector = new VerticalPullDetector(launcher);
        mDetector.setListener(this);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            if (mLauncher.getWorkspace().isInOverviewMode() || mLauncher.isWidgetsViewVisible()) {
                mNoIntercept = true;
            } else if (mLauncher.isAllAppsVisible() &&
                    !mAppsView.shouldContainerScroll(ev.getX(), ev.getY())) {
                mNoIntercept = true;
            } else if (!mLauncher.isAllAppsVisible() && !shouldPossiblyIntercept(ev)) {
                mNoIntercept = true;
            } else {
                mDetector.setDetectableScrollConditions(mLauncher.isAllAppsVisible() /* down */,
                        isInDisallowRecatchTopZone(), isInDisallowRecatchBottomZone());
            }
        }
        if (mNoIntercept) {
            return false;
        }
        mDetector.onTouchEvent(ev);
        return mDetector.shouldIntercept();
    }

    private boolean shouldPossiblyIntercept(MotionEvent ev) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (mDetector.isRestingState()) {
            if (mLauncher.getDragLayer().isEventOverHotseat(ev) && !grid.isLandscape) {
                return true;
            }
            if (ev.getY() > mLauncher.getDeviceProfile().heightPx - BAZEL_PULL_UP_HEIGHT &&
                    grid.isLandscape) {
                return true;
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev);
    }

    private boolean isInDisallowRecatchTopZone() {
        return mProgressTransY / mTranslation < RECATCH_REJECTION_FRACTION;
    }

    private boolean isInDisallowRecatchBottomZone() {
        return mProgressTransY / mTranslation > 1 - RECATCH_REJECTION_FRACTION;
    }

    @Override
    public void onScrollStart(boolean start) {
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        preparePull(start);
    }

    /**
     * @param start {@code true} if start of new drag.
     */
    public void preparePull(boolean start) {
        // Initialize values that should not change until #onScrollEnd
        mCurY = mAppsView.getTranslationY();
        mStatusBarHeight = mLauncher.getDragLayer().getInsets().top;
        mHotseat.setVisibility(View.VISIBLE);
        mHotseat.bringToFront();
        if (start) {
            if (!mLauncher.isAllAppsVisible()) {
                mLauncher.tryAndUpdatePredictedApps();
                mHotseatBackgroundAlpha = mHotseat.getBackground().getAlpha() / 255f;
                mHotseat.setBackgroundTransparent(true /* transparent */);
                mAppsView.setVisibility(View.VISIBLE);
                mAppsView.getContentView().setVisibility(View.VISIBLE);
                mAppsView.getContentView().setBackground(null);
                mAppsView.getRevealView().setVisibility(View.VISIBLE);
                mAppsView.getRevealView().setAlpha(mHotseatBackgroundAlpha);

                DeviceProfile grid= mLauncher.getDeviceProfile();
                if (!grid.isLandscape) {
                    mTranslation = mHotseat.getTop();
                } else {
                    mTranslation = mHotseat.getBottom();
                }
                setProgress(mTranslation);
            } else {
                // TODO: get rid of this workaround to override state change by workspace transition
                mWorkspace.onLauncherTransitionPrepare(mLauncher, false, false);
                View child = ((CellLayout) mWorkspace.getChildAt(mWorkspace.getNextPage()))
                        .getShortcutsAndWidgets();
                child.setVisibility(View.VISIBLE);
                child.setAlpha(1f);
            }
        }
    }

    private void updateLightStatusBar(float progress) {
        boolean enable = (progress < mStatusBarHeight / 2);
        // Already set correctly
        if (mLightStatusBar == enable) {
            return;
        }
        int systemUiFlags = mLauncher.getWindow().getDecorView().getSystemUiVisibility();
        if (enable) {
            mLauncher.getWindow().getDecorView().setSystemUiVisibility(systemUiFlags
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        } else {
            mLauncher.getWindow().getDecorView().setSystemUiVisibility(systemUiFlags
                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        }
        mLightStatusBar = enable;
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
        updateLightStatusBar(progress);
        mProgressTransY = progress;
        float alpha = calcAlphaAllApps(progress);
        float workspaceHotseatAlpha = 1 - alpha;

        mAppsView.getRevealView().setAlpha(Math.min(ALL_APPS_FINAL_ALPHA, Math.max(mHotseatBackgroundAlpha,
                mDecelInterpolator.getInterpolation(alpha))));
        mAppsView.getContentView().setAlpha(alpha);
        mAppsView.setTranslationY(progress);
        mWorkspace.setWorkspaceTranslation(Direction.Y,
                PARALLAX_COEFFICIENT *(-mTranslation + progress),
                mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
        mWorkspace.setHotseatTranslation(Direction.Y, -mTranslation + progress,
                mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
    }

    public float getProgress() {
        return mProgressTransY;
    }

    private float calcAlphaAllApps(float progress) {
        return ((mTranslation - progress)/mTranslation);
    }

    @Override
    public void onScrollEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        if (fling) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());
                animateToAllApps(mCurrentAnimation, mAnimationDuration);
            } else {
                calculateDuration(velocity, Math.abs(mTranslation - mAppsView.getTranslationY()));
                animateToWorkspace(mCurrentAnimation, mAnimationDuration);
            }
            // snap to top or bottom using the release velocity
        } else {
            if (mAppsView.getTranslationY() > mTranslation / 2) {
                calculateDuration(velocity, Math.abs(mTranslation - mAppsView.getTranslationY()));
                animateToWorkspace(mCurrentAnimation, mAnimationDuration);
            } else {
                calculateDuration(velocity, Math.abs(mAppsView.getTranslationY()));
                animateToAllApps(mCurrentAnimation, mAnimationDuration);
            }
        }
        mCurrentAnimation.start();
    }

    private void calculateDuration(float velocity, float disp) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(1.5f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, disp / mTranslation);
        mAnimationDuration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", mAnimationDuration, velocity, disp));
        }
    }

    public void animateToAllApps(AnimatorSet animationOut, long duration) {
        if (animationOut == null){
            return;
        }
        if (mDetector.isRestingState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mStateAlreadyChanged = true;
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

    public void animateToWorkspace(AnimatorSet animationOut, long duration) {
        if (animationOut == null){
            return;
        }
        if(mDetector.isRestingState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mStateAlreadyChanged = true;
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

    private void finishPullUp() {
        mHotseat.setVisibility(View.INVISIBLE);
        setProgress(0f);
        if (!mStateAlreadyChanged) {
            mLauncher.showAppsView(false /* animated */, true /* resetListToTop */,
                    false /* updatePredictedApps */, false /* focusSearchBar */);
        }
        mStateAlreadyChanged = false;
    }

    public void finishPullDown() {
        if (mHotseat.getBackground() != null) {
            return;
        }
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackgroundTransparent(false /* transparent */);
        mHotseat.setVisibility(View.VISIBLE);
        setProgress(mTranslation);
        if (!mStateAlreadyChanged) {
            mLauncher.showWorkspace(false);
        }
        mStateAlreadyChanged = false;
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

    public void setupViews(AllAppsContainerView appsView, Hotseat hotseat, Workspace workspace) {
        mAppsView = appsView;
        mHotseat = hotseat;
        mWorkspace = workspace;
    }
}
