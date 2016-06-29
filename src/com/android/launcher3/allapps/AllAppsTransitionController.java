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
import com.android.launcher3.R;
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

    private static final float ANIMATION_DURATION = 1200;
    public static final float ALL_APPS_FINAL_ALPHA = .9f;

    private static final float PARALLAX_COEFFICIENT = .125f;

    private AllAppsContainerView mAppsView;
    private Workspace mWorkspace;
    private Hotseat mHotseat;
    private float mHotseatBackgroundAlpha;

    private float mStatusBarHeight;

    private final Launcher mLauncher;
    private final VerticalPullDetector mDetector;

    // Animation in this class is controlled by a single variable {@link mShiftCurrent}.
    // Visually, it represents top y coordinate of the all apps container. Using the
    // {@link mShiftRange} as the denominator, this fraction value ranges in [0, 1].
    //
    // When {@link mShiftCurrent} is 0, all apps container is pulled up.
    // When {@link mShiftCurrent} is {@link mShirtRange}, all apps container is pulled down.
    private float mShiftStart;      // [0, mShiftRange]
    private float mShiftCurrent;    // [0, mShiftRange]
    private float mShiftRange;      // changes depending on the orientation


    private static final float RECATCH_REJECTION_FRACTION = .0875f;

    private int mBezelSwipeUpHeight;
    private long mAnimationDuration;


    private AnimatorSet mCurrentAnimation;
    private boolean mNoIntercept;

    private boolean mLightStatusBar;

    public AllAppsTransitionController(Launcher launcher) {
        mLauncher = launcher;
        mDetector = new VerticalPullDetector(launcher);
        mDetector.setListener(this);
        mBezelSwipeUpHeight = launcher.getResources().getDimensionPixelSize(
                R.dimen.all_apps_bezel_swipe_height);
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
                // Now figure out which direction scroll events the controller will start
                // calling the callbacks.
                int conditionsToReportScroll = 0;

                if (mDetector.isRestingState()) {
                    if (mLauncher.isAllAppsVisible()) {
                        conditionsToReportScroll |= VerticalPullDetector.THRESHOLD_DOWN;
                    } else {
                        conditionsToReportScroll |= VerticalPullDetector.THRESHOLD_UP;
                    }
                } else {
                    if (isInDisallowRecatchBottomZone()) {
                        conditionsToReportScroll |= VerticalPullDetector.THRESHOLD_UP;
                    } else if (isInDisallowRecatchTopZone()) {
                        conditionsToReportScroll |= VerticalPullDetector.THRESHOLD_DOWN;
                    } else {
                        conditionsToReportScroll |= VerticalPullDetector.THRESHOLD_ONLY;
                    }
                }
                mDetector.setDetectableScrollConditions(conditionsToReportScroll);
            }
        }
        if (mNoIntercept) {
            return false;
        }
        mDetector.onTouchEvent(ev);
        if (mDetector.isScrollingState() && (isInDisallowRecatchBottomZone() || isInDisallowRecatchTopZone())) {
            return false;
        }
        return mDetector.shouldIntercept();
    }

    private boolean shouldPossiblyIntercept(MotionEvent ev) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (mDetector.isRestingState()) {
            if (grid.isVerticalBarLayout()) {
                if (ev.getY() > mLauncher.getDeviceProfile().heightPx - mBezelSwipeUpHeight) {
                    return true;
                }
            } else {
                if ((mLauncher.getDragLayer().isEventOverHotseat(ev)
                        || mLauncher.getDragLayer().isEventOverPageIndicator(ev))
                        && !grid.isVerticalBarLayout()) {
                    return true;
                }
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
        return mShiftCurrent / mShiftRange < RECATCH_REJECTION_FRACTION;
    }

    private boolean isInDisallowRecatchBottomZone() {
        return mShiftCurrent / mShiftRange > 1 - RECATCH_REJECTION_FRACTION;
    }

    @Override
    public void onScrollStart(boolean start) {
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        mShiftStart = mAppsView.getTranslationY();
        preparePull(start);
    }

    @Override
    public boolean onScroll(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }
        if (0 <= mShiftStart + displacement && mShiftStart + displacement < mShiftRange) {
            setProgress(mShiftStart + displacement);
        }
        return true;
    }

    @Override
    public void onScrollEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        if (fling) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());

                if (!mLauncher.isAllAppsVisible()) {
                    mLauncher.showAppsView(true, true, false, false);
                } else {
                    animateToAllApps(mCurrentAnimation, mAnimationDuration, true);
                }
            } else {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                if (mLauncher.isAllAppsVisible()) {
                    mLauncher.showWorkspace(true);
                } else {
                    animateToWorkspace(mCurrentAnimation, mAnimationDuration, true);
                }
            }
            // snap to top or bottom using the release velocity
        } else {
            if (mAppsView.getTranslationY() > mShiftRange / 2) {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                if (mLauncher.isAllAppsVisible()) {
                    mLauncher.showWorkspace(true);
                } else {
                    animateToWorkspace(mCurrentAnimation, mAnimationDuration, true);
                }
            } else {
                calculateDuration(velocity, Math.abs(mAppsView.getTranslationY()));
                if (!mLauncher.isAllAppsVisible()) {
                    mLauncher.showAppsView(true, true, false, false);
                } else {
                    animateToAllApps(mCurrentAnimation, mAnimationDuration, true);
                }

            }
        }
    }
    /**
     * @param start {@code true} if start of new drag.
     */
    public void preparePull(boolean start) {
        if (start) {
            // Initialize values that should not change until #onScrollEnd
            mStatusBarHeight = mLauncher.getDragLayer().getInsets().top;
            mHotseat.setVisibility(View.VISIBLE);
            mHotseat.bringToFront();
            if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
                mShiftRange = mHotseat.getTop();
            } else {
                mShiftRange = mHotseat.getBottom();
            }
            if (!mLauncher.isAllAppsVisible()) {
                mLauncher.tryAndUpdatePredictedApps();

                mHotseatBackgroundAlpha = mHotseat.getBackgroundDrawableAlpha() / 255f;
                mHotseat.setBackgroundTransparent(true /* transparent */);
                mAppsView.setVisibility(View.VISIBLE);
                mAppsView.getContentView().setVisibility(View.VISIBLE);
                mAppsView.getContentView().setBackground(null);
                mAppsView.getRevealView().setVisibility(View.VISIBLE);
                mAppsView.getRevealView().setAlpha(mHotseatBackgroundAlpha);

                DeviceProfile grid= mLauncher.getDeviceProfile();
                if (!grid.isVerticalBarLayout()) {
                    mShiftRange = mHotseat.getTop();
                } else {
                    mShiftRange = mHotseat.getBottom();
                }
                mAppsView.getRevealView().setAlpha(mHotseatBackgroundAlpha);
                setProgress(mShiftRange);
            } else {
                View child = ((CellLayout) mWorkspace.getChildAt(mWorkspace.getNextPage()))
                        .getShortcutsAndWidgets();
                child.setVisibility(View.VISIBLE);
                child.setAlpha(1f);
            }
        } else {
            setProgress(mShiftCurrent);
        }
    }

    private void updateLightStatusBar(float progress) {
        boolean enable = (progress < mStatusBarHeight / 2);
        // Do not modify status bar on landscape as all apps is not full bleed.
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            return;
        }
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
                    & ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));

        }
        mLightStatusBar = enable;
    }

    /**
     * @param progress y value of the border between hotseat and all apps
     */
    public void setProgress(float progress) {
        updateLightStatusBar(progress);
        mShiftCurrent = progress;
        float alpha = calcAlphaAllApps(progress);
        float workspaceHotseatAlpha = 1 - alpha;

        mAppsView.getRevealView().setAlpha(Math.min(ALL_APPS_FINAL_ALPHA, Math.max(mHotseatBackgroundAlpha,
                mDecelInterpolator.getInterpolation(alpha))));
        mAppsView.getContentView().setAlpha(alpha);
        mAppsView.setTranslationY(progress);
        mWorkspace.setWorkspaceTranslation(Direction.Y,
                PARALLAX_COEFFICIENT * (-mShiftRange + progress),
                mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mWorkspace.setHotseatTranslation(Direction.Y, -mShiftRange + progress,
                    mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
        } else {
            mWorkspace.setHotseatTranslation(Direction.Y,
                    PARALLAX_COEFFICIENT * (-mShiftRange + progress),
                    mAccelInterpolator.getInterpolation(workspaceHotseatAlpha));
        }
    }

    public float getProgress() {
        return mShiftCurrent;
    }

    private float calcAlphaAllApps(float progress) {
        return ((mShiftRange - progress)/ mShiftRange);
    }

    private void calculateDuration(float velocity, float disp) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(1.5f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, disp / mShiftRange);
        mAnimationDuration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", mAnimationDuration, velocity, disp));
        }
    }

    public void animateToAllApps(AnimatorSet animationOut, long duration, boolean start) {
        if (animationOut == null){
            return;
        }
        if (mDetector.isRestingState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mShiftStart = mAppsView.getTranslationY();
        }
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
        if (start) {
            mCurrentAnimation.start();
        }
    }

    public void animateToWorkspace(AnimatorSet animationOut, long duration, boolean start) {
        if (animationOut == null){
            return;
        }
        if(mDetector.isRestingState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mShiftStart = mAppsView.getTranslationY();
        }
        final float fromAllAppsTop = mAppsView.getTranslationY();
        final float toAllAppsTop = mShiftRange;

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
                 setProgress(mShiftCurrent);
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
        if (start) {
            mCurrentAnimation.start();
        }
    }

    private void finishPullUp() {
        mHotseat.setVisibility(View.INVISIBLE);
        setProgress(0f);
    }

    public void finishPullDown() {
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackgroundTransparent(false /* transparent */);
        mHotseat.setVisibility(View.VISIBLE);
        setProgress(mShiftRange);
    }

    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.setDuration(0);
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
