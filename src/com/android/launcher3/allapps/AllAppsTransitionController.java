package com.android.launcher3.allapps;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.android.launcher3.pageindicators.CaretDrawable;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.Workspace.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.TouchController;

/**
 * Handles AllApps view transition.
 * 1) Slides all apps view using direct manipulation
 * 2) When finger is released, animate to either top or bottom accordingly.
 * <p/>
 * Algorithm:
 * If release velocity > THRES1, snap according to the direction of movement.
 * If release velocity < THRES1, snap according to either top or bottom depending on whether it's
 * closer to top or closer to the page indicator.
 */
public class AllAppsTransitionController implements TouchController, VerticalPullDetector.Listener,
        View.OnLayoutChangeListener {

    private static final String TAG = "AllAppsTrans";
    private static final boolean DBG = false;

    private final Interpolator mAccelInterpolator = new AccelerateInterpolator(2f);
    private final Interpolator mDecelInterpolator = new DecelerateInterpolator(1f);

    private static final float ANIMATION_DURATION = 1200;

    private static final float PARALLAX_COEFFICIENT = .125f;

    private AllAppsContainerView mAppsView;
    private int mAllAppsBackgroundColor;
    private Workspace mWorkspace;
    private Hotseat mHotseat;
    private int mHotseatBackgroundColor;

    private ObjectAnimator mCaretAnimator;
    private final long mCaretAnimationDuration;
    private final Interpolator mCaretInterpolator;

    private float mStatusBarHeight;

    private final Launcher mLauncher;
    private final VerticalPullDetector mDetector;
    private final ArgbEvaluator mEvaluator;

    // Animation in this class is controlled by a single variable {@link mShiftCurrent}.
    // Visually, it represents top y coordinate of the all apps container. Using the
    // {@link mShiftRange} as the denominator, this fraction value ranges in [0, 1].
    //
    // When {@link mShiftCurrent} is 0, all apps container is pulled up.
    // When {@link mShiftCurrent} is {@link mShirtRange}, all apps container is pulled down.
    private float mShiftStart;      // [0, mShiftRange]
    private float mShiftCurrent;    // [0, mShiftRange]
    private float mShiftRange;      // changes depending on the orientation

    private static final float DEFAULT_SHIFT_RANGE = 10;

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
        mShiftCurrent = mShiftRange = DEFAULT_SHIFT_RANGE;
        mBezelSwipeUpHeight = launcher.getResources().getDimensionPixelSize(
                R.dimen.all_apps_bezel_swipe_height);

        mCaretAnimationDuration = launcher.getResources().getInteger(
                R.integer.config_caretAnimationDuration);
        mCaretInterpolator = AnimationUtils.loadInterpolator(launcher,
                R.interpolator.caret_animation_interpolator);
        mEvaluator = new ArgbEvaluator();
        mAllAppsBackgroundColor = launcher.getColor(R.color.all_apps_container_color);
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
                int directionsToDetectScroll = 0;
                boolean ignoreSlopWhenSettling = false;

                if (mDetector.isIdleState()) {
                    if (mLauncher.isAllAppsVisible()) {
                        directionsToDetectScroll |= VerticalPullDetector.DIRECTION_DOWN;
                    } else {
                        directionsToDetectScroll |= VerticalPullDetector.DIRECTION_UP;
                    }
                } else {
                    if (isInDisallowRecatchBottomZone()) {
                        directionsToDetectScroll |= VerticalPullDetector.DIRECTION_UP;
                    } else if (isInDisallowRecatchTopZone()) {
                        directionsToDetectScroll |= VerticalPullDetector.DIRECTION_DOWN;
                    } else {
                        directionsToDetectScroll |= VerticalPullDetector.DIRECTION_BOTH;
                        ignoreSlopWhenSettling = true;
                    }
                }
                mDetector.setDetectableScrollConditions(directionsToDetectScroll,
                        ignoreSlopWhenSettling);
            }
        }
        if (mNoIntercept) {
            return false;
        }
        mDetector.onTouchEvent(ev);
        if (mDetector.isSettlingState() && (isInDisallowRecatchBottomZone() || isInDisallowRecatchTopZone())) {
            return false;
        }
        return mDetector.shouldIntercept();
    }

    private boolean shouldPossiblyIntercept(MotionEvent ev) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (mDetector.isIdleState()) {
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
    public void onDragStart(boolean start) {
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        mShiftStart = mAppsView.getTranslationY();
        preparePull(start);
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }
        float progress = Math.min(Math.max(0, mShiftStart + displacement), mShiftRange);
        setProgress(progress);
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        if (fling) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());

                if (!mLauncher.isAllAppsVisible()) {
                    mLauncher.getUserEventDispatcher().logActionOnContainer(
                            LauncherLogProto.Action.FLING,
                            LauncherLogProto.Action.UP,
                            LauncherLogProto.HOTSEAT);
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
                    mLauncher.getUserEventDispatcher().logActionOnContainer(
                            LauncherLogProto.Action.SWIPE,
                            LauncherLogProto.Action.UP,
                            LauncherLogProto.HOTSEAT);
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
            // Initialize values that should not change until #onDragEnd
            mStatusBarHeight = mLauncher.getDragLayer().getInsets().top;
            mHotseat.setVisibility(View.VISIBLE);
            mHotseat.bringToFront();
            if (!mLauncher.isAllAppsVisible()) {
                mLauncher.tryAndUpdatePredictedApps();
                mHotseatBackgroundColor = mHotseat.getBackgroundDrawableColor();
                mHotseat.setBackgroundTransparent(true /* transparent */);
                mAppsView.setVisibility(View.VISIBLE);
                mAppsView.getContentView().setVisibility(View.VISIBLE);
                mAppsView.getContentView().setBackground(null);
                mAppsView.getRevealView().setVisibility(View.VISIBLE);
                mAppsView.setRevealDrawableColor(mHotseatBackgroundColor);
            }
        } else {
            setProgress(mShiftCurrent);
        }
    }

    private void updateLightStatusBar(float progress) {
        boolean enable = progress <= mStatusBarHeight / 2;
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
        float interpolation = mAccelInterpolator.getInterpolation(workspaceHotseatAlpha);

        int color = (Integer) mEvaluator.evaluate(mDecelInterpolator.getInterpolation(alpha),
                mHotseatBackgroundColor, mAllAppsBackgroundColor);
        mAppsView.setRevealDrawableColor(color);
        mAppsView.getContentView().setAlpha(alpha);
        mAppsView.setTranslationY(progress);
        mWorkspace.setWorkspaceYTranslationAndAlpha(
                PARALLAX_COEFFICIENT * (-mShiftRange + progress),
                interpolation);
        if (mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mWorkspace.setHotseatTranslationAndAlpha(Direction.Y,
                    PARALLAX_COEFFICIENT * (-mShiftRange + progress), interpolation);
        } else {
            mWorkspace.setHotseatTranslationAndAlpha(Direction.Y,
                    -mShiftRange + progress, interpolation);
        }
    }

    public float getProgress() {
        return mShiftCurrent;
    }

    private float calcAlphaAllApps(float progress) {
        return ((mShiftRange - progress) / mShiftRange);
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
        if (animationOut == null) {
            return;
        }
        if (mDetector.isIdleState()) {
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
            }
        });
        mCurrentAnimation = animationOut;
        if (start) {
            mCurrentAnimation.start();
        }
    }

    public void animateToWorkspace(AnimatorSet animationOut, long duration, boolean start) {
        if (animationOut == null) {
            return;
        }
        if (mDetector.isIdleState()) {
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
                    finishPullDown(true);
                    cleanUpAnimation();
                    mDetector.finishedScrolling();
                }
            }
        });
        mCurrentAnimation = animationOut;
        if (start) {
            mCurrentAnimation.start();
        }
    }

    public void finishPullUp() {
        mHotseat.setVisibility(View.INVISIBLE);
        setProgress(0f);
        animateCaret();
    }

    public void finishPullDown(boolean animated) {
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackgroundTransparent(false /* transparent */);
        mHotseat.setVisibility(View.VISIBLE);
        mAppsView.reset();
        setProgress(mShiftRange);
        if (animated) {
            animateCaret();
        } else {
            mWorkspace.getPageIndicator().getCaretDrawable()
                    .setLevel(CaretDrawable.LEVEL_CARET_POINTING_UP);
        }
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

    private void animateCaret() {
        if (mCaretAnimator.isRunning()) {
            mCaretAnimator.cancel(); // stop the animator in its tracks
        }

        if (mLauncher.isAllAppsVisible()) {
            mCaretAnimator.setIntValues(CaretDrawable.LEVEL_CARET_POINTING_DOWN);
        } else {
            mCaretAnimator.setIntValues(CaretDrawable.LEVEL_CARET_POINTING_UP);
        }

        mCaretAnimator.start();
    }

    public void setupViews(AllAppsContainerView appsView, Hotseat hotseat, Workspace workspace) {
        mAppsView = appsView;
        mHotseat = hotseat;
        mWorkspace = workspace;
        mCaretAnimator = ObjectAnimator.ofInt(mWorkspace.getPageIndicator().getCaretDrawable(),
                "level", CaretDrawable.LEVEL_CARET_POINTING_UP); // we will set values later
        mCaretAnimator.setDuration(mCaretAnimationDuration);
        mCaretAnimator.setInterpolator(mCaretInterpolator);
        mHotseat.addOnLayoutChangeListener(this);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        float prevShiftRatio = mShiftCurrent / mShiftRange;
        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mShiftRange = top;
        } else {
            mShiftRange = bottom;
        }
        setProgress(mShiftRange * prevShiftRatio);
    }
}
