package ch.deletescape.lawnchair.allapps;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import ch.deletescape.lawnchair.CellLayout;
import ch.deletescape.lawnchair.Hotseat;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.LauncherAppWidgetHostView;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.ShortcutAndWidgetContainer;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.Workspace;
import ch.deletescape.lawnchair.allapps.theme.IAllAppsThemer;
import ch.deletescape.lawnchair.anim.SpringAnimationHandler;
import ch.deletescape.lawnchair.blur.BlurWallpaperProvider;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.util.TouchController;

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

    private final Interpolator mAccelInterpolator = new AccelerateInterpolator(2f);
    private final Interpolator mDecelInterpolator = new DecelerateInterpolator(3f);
    private final Interpolator mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
    private final ScrollInterpolator mScrollInterpolator = new ScrollInterpolator();

    private static final float ANIMATION_DURATION = 1200;
    private static final float PARALLAX_COEFFICIENT = .125f;
    private static final float FAST_FLING_PX_MS = 10;
    private static final int SINGLE_FRAME_MS = 16;
    private final boolean mTransparentHotseat;
    private boolean mLightStatusBar;

    private AllAppsContainerView mAppsView;
    private final IAllAppsThemer mTheme;
    private int mAllAppsBackgroundColor;
    private int mAllAppsBackgroundColorBlur;
    private Workspace mWorkspace;
    private Hotseat mHotseat;
    private int mHotseatBackgroundColor;

    private AllAppsCaretController mCaretController;

    private float mStatusBarHeight;

    private final Launcher mLauncher;
    private final VerticalPullDetector mDetector;
    private final ArgbEvaluator mEvaluator;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftStart;      // [0, mShiftRange]
    private float mShiftRange;      // changes depending on the orientation
    private SpringAnimationHandler<AllAppsGridAdapter.ViewHolder> mSpringAnimationHandler;
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent
    private int mPullDownState;

    // Velocity of the container. Unit is in px/ms.
    private float mContainerVelocity;

    private static final float DEFAULT_SHIFT_RANGE = 10;

    private static final float RECATCH_REJECTION_FRACTION = .0875f;

    private long mAnimationDuration;

    private AnimatorSet mCurrentAnimation;
    private boolean mNoIntercept;

    // Used in discovery bounce animation to provide the transition without workspace changing.
    private boolean mIsTranslateWithoutWorkspace = false;
    private AnimatorSet mDiscoBounceAnimation;

    private int allAppsAlpha;

    private int mPullDownAction;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mDetector = new VerticalPullDetector(l);
        mDetector.setListener(this);
        mShiftRange = DEFAULT_SHIFT_RANGE;
        mProgress = 1f;
        mEvaluator = new ArgbEvaluator();
        mTheme = Utilities.getThemer().allAppsTheme(l);
        mAllAppsBackgroundColor = mTheme.getBackgroundColor();
        mAllAppsBackgroundColorBlur = mTheme.getBackgroundColorBlur();
        mTransparentHotseat = Utilities.getPrefs(l).getTransparentHotseat();
        mLightStatusBar = Utilities.getPrefs(l).getLightStatusBar();
        initPullDown(l);
    }

    public void initPullDown(Context context) {
        mPullDownAction = FeatureFlags.INSTANCE.pullDownAction(context);
    }

    public void updateLightStatusBar(Context context) {
        mLightStatusBar = Utilities.getPrefs(context).getLightStatusBar();
        updateLightStatusBar(mProgress * mShiftRange);
    }

    public void setAllAppsAlpha(int allAppsAlpha) {
        this.allAppsAlpha = allAppsAlpha;
        mAppsView.setAppIconTextStyle(
                mTheme.iconTextColor(allAppsAlpha),
                mTheme.getIconTextLines());
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            if (!mLauncher.isAllAppsVisible() && mLauncher.getWorkspace().workspaceInModalState()) {
                mNoIntercept = true;
            } else if (mLauncher.isAllAppsVisible() &&
                    !mAppsView.shouldContainerScroll(ev)) {
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
                        if (mPullDownAction != 0)
                            directionsToDetectScroll |= VerticalPullDetector.DIRECTION_DOWN;
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
        return mDetector.isDraggingOrSettling();
    }

    private boolean shouldPossiblyIntercept(MotionEvent ev) {
        CellLayout cl = mLauncher.getWorkspace().getCurrentDropLayout();
        if (cl != null) {
            ShortcutAndWidgetContainer c = cl.getShortcutsAndWidgets();
            int x = (int) ev.getX();
            int y = (int) ev.getY();
            Rect outRect = new Rect();
            int count = c.getChildCount();
            for (int i = 0; i < count; i++) {
                View v = c.getChildAt(i);
                if (v instanceof LauncherAppWidgetHostView) {
                    v.getGlobalVisibleRect(outRect);
                    if (outRect.contains(x, y)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    private boolean isInDisallowRecatchTopZone() {
        return mProgress < RECATCH_REJECTION_FRACTION;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (hasSpringAnimationHandler())
            mSpringAnimationHandler.addMovement(ev);
        return mDetector.onTouchEvent(ev);
    }

    private boolean isInDisallowRecatchBottomZone() {
        return mProgress > 1 - RECATCH_REJECTION_FRACTION;
    }

    @Override
    public void onDragStart(boolean start) {
        mCaretController.onDragStart();
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        mShiftStart = mAppsView.getTranslationY();
        if (mPullDownState != 4)
            mPullDownState = (mPullDownAction != 0 && mProgress == 1) ? 1 : 0;
        preparePull(start);
        if (hasSpringAnimationHandler())
            mSpringAnimationHandler.skipToEnd();
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }

        if (mProgress == 1) {
            boolean notOpenedYet = mPullDownState == 1;
            if (notOpenedYet || mPullDownState == 2) {
                if (velocity > 2.4) {
                    mPullDownState = 3;
                    mLauncher.onPullDownAction(mPullDownAction);
                    if (mPullDownAction != FeatureFlags.PULLDOWN_NOTIFICATIONS)
                        mPullDownState = 4;
                } else if (notOpenedYet && velocity < 0) {
                    mPullDownState = 0;
                }
            } else if (mPullDownState == 3 && velocity < -0.5) {
                mPullDownState = 2;
                mLauncher.closeNotifications();
            }
        }

        if (mPullDownState < 2) {
            mContainerVelocity = velocity;

            float shift = Math.min(Math.max(0, mShiftStart + displacement), mShiftRange);
            setProgress(shift / mShiftRange);
        }

        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        if (fling) {
            if (velocity < 0 && mPullDownState < 2) {
                calculateDuration(velocity, mAppsView.getTranslationY());
                mLauncher.showAppsView(true /* animated */, false /* focusSearchBar */);
                if (hasSpringAnimationHandler())
                    mSpringAnimationHandler.animateToFinalPosition(0, 1);
            } else if (mPullDownAction != FeatureFlags.PULLDOWN_APPS_SEARCH || mPullDownState < 2) {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                mLauncher.showWorkspace(true);
            }
            // snap to top or bottom using the release velocity
        } else {
            if (mAppsView.getTranslationY() > mShiftRange / 2) {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                mLauncher.showWorkspace(true);
            } else {
                calculateDuration(velocity, Math.abs(mAppsView.getTranslationY()));
                mLauncher.showAppsView(true /* animated */, false /* focusSearchBar */);
            }
        }
        mPullDownState = mPullDownAction != 0 ? 1 : 0;
    }

    public boolean isTransitioning() {
        return mDetector.isDraggingOrSettling();
    }

    /**
     * @param start {@code true} if start of new drag.
     */
    public void preparePull(boolean start) {
        if (start) {
            // Initialize values that should not change until #onDragEnd
            mStatusBarHeight = mLauncher.getDragLayer().getInsets().top;
            mHotseat.setVisibility(View.VISIBLE);
            mHotseatBackgroundColor = mHotseat.getBackgroundDrawableColor();
            mHotseat.setBackgroundTransparent(true /* transparent */);
            if (!mLauncher.isAllAppsVisible()) {
                mAppsView.setVisibility(View.VISIBLE);
                mAppsView.setRevealDrawableColor(mHotseatBackgroundColor);
            }
        }
    }

    private void updateLightStatusBar(float shift) {
        boolean useDarkTheme = FeatureFlags.INSTANCE.useDarkTheme(FeatureFlags.DARK_ALLAPPS);
        boolean darkStatusBar = useDarkTheme ||
                (BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS) &&
                        !mLauncher.getExtractedColors().isLightStatusBar() &&
                        allAppsAlpha < 52);

        boolean darkNavigationBar = useDarkTheme ||
                (BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS) &&
                        !mLauncher.getExtractedColors().isLightNavigationBar() &&
                        allAppsAlpha < 52);

        if (Utilities.ATLEAST_MARSHMALLOW) {
            // Use a light status bar (dark icons) if all apps is behind at least half of the status
            // bar. If the status bar is already light due to wallpaper extraction, keep it that way.
            boolean activate = !mLauncher.getDeviceProfile().isVerticalBarLayout() && shift <= mStatusBarHeight / 2;
            mLauncher.activateLightStatusBar(activate ? !darkStatusBar : mLightStatusBar);
            mLauncher.activateLightNavigationBar(!darkNavigationBar && activate);
        } else {
            mAppsView.setStatusBarHeight(darkStatusBar ? 0 : Math.max(mStatusBarHeight - shift, 0));
        }
    }

    /**
     * @param progress value between 0 and 1, 0 shows all apps and 1 shows workspace
     */
    public void setProgress(float progress) {
        float shiftPrevious = mProgress * mShiftRange;
        mProgress = progress;
        float shiftCurrent = progress * mShiftRange;

        float alpha = 1f - progress;
        float interpolation = mAccelInterpolator.getInterpolation(progress);

        int allAppsBg = ColorUtils.setAlphaComponent(mAllAppsBackgroundColor, allAppsAlpha);
        int allAppsBgBlur = mAllAppsBackgroundColorBlur + (allAppsAlpha << 24);
        boolean blurEnabled = BlurWallpaperProvider.Companion.isEnabled(BlurWallpaperProvider.BLUR_ALLAPPS);
        int color = (int) mEvaluator.evaluate(
                Math.max(mDecelInterpolator.getInterpolation(alpha), 0),
                blurEnabled ? mAllAppsBackgroundColorBlur : mHotseatBackgroundColor,
                blurEnabled ? allAppsBgBlur : allAppsBg);
        if (mTransparentHotseat) {
            mAppsView.setRevealDrawableColor(blurEnabled ? allAppsBgBlur : color);
            mAppsView.setBlurOpacity((int) (alpha * 255));
        } else {
            mAppsView.setRevealDrawableColor(color);
        }
        if (blurEnabled) {
            mAppsView.setWallpaperTranslation(shiftCurrent);
            mHotseat.setWallpaperTranslation(shiftCurrent);
        }
        mAppsView.getContentView().setAlpha(alpha);
        mAppsView.setTranslationY(shiftCurrent);

        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mWorkspace.setHotseatTranslationAndAlpha(Workspace.Direction.Y, -mShiftRange + shiftCurrent,
                    interpolation);
        } else {
            mWorkspace.setHotseatTranslationAndAlpha(Workspace.Direction.Y,
                    PARALLAX_COEFFICIENT * (-mShiftRange + shiftCurrent),
                    interpolation);
        }

        if (mIsTranslateWithoutWorkspace) {
            return;
        }
        mWorkspace.setWorkspaceYTranslationAndAlpha(
                PARALLAX_COEFFICIENT * (-mShiftRange + shiftCurrent), interpolation);

        if (!mDetector.isDraggingState()) {
            mContainerVelocity = mDetector.computeVelocity(shiftCurrent - shiftPrevious,
                    System.currentTimeMillis());
        }

        mCaretController.updateCaret(progress, mContainerVelocity, mDetector.isDraggingState());
        updateLightStatusBar(shiftCurrent);
    }

    public float getProgress() {
        return mProgress;
    }

    private void calculateDuration(float velocity, float disp) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(2f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, disp / mShiftRange);
        mAnimationDuration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
    }

    public boolean animateToAllApps(AnimatorSet animationOut, long duration) {
        boolean shouldPost = true;
        if (animationOut == null) {
            return shouldPost;
        }
        Interpolator interpolator;
        if (mDetector.isIdleState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mShiftStart = mAppsView.getTranslationY();
            interpolator = mFastOutSlowInInterpolator;
        } else {
            mScrollInterpolator.setVelocityAtZero(Math.abs(mContainerVelocity));
            interpolator = mScrollInterpolator;
            float nextFrameProgress = mProgress + mContainerVelocity * SINGLE_FRAME_MS / mShiftRange;
            if (nextFrameProgress >= 0f) {
                mProgress = nextFrameProgress;
            }
            shouldPost = false;
        }


        ObjectAnimator driftAndAlpha = ObjectAnimator.ofFloat(this, "progress",
                mProgress, 0f);
        driftAndAlpha.setDuration(mAnimationDuration);
        driftAndAlpha.setInterpolator(interpolator);
        animationOut.play(driftAndAlpha);

        animationOut.addListener(new AnimatorListenerAdapter() {
            boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled) {
                    finishPullUp();
                    cleanUpAnimation();
                    mDetector.finishedScrolling();
                }
            }
        });
        mCurrentAnimation = animationOut;
        return shouldPost;
    }

    public void showDiscoveryBounce() {
        // cancel existing animation in case user locked and unlocked at a super human speed.
        cancelDiscoveryAnimation();

        // assumption is that this variable is always null
        mDiscoBounceAnimation = (AnimatorSet) AnimatorInflater.loadAnimator(mLauncher,
                R.animator.discovery_bounce);
        mDiscoBounceAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                mIsTranslateWithoutWorkspace = true;
                preparePull(true);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                finishPullDown();
                mDiscoBounceAnimation = null;
                mIsTranslateWithoutWorkspace = false;
            }
        });
        mDiscoBounceAnimation.setTarget(this);
        mAppsView.post(new Runnable() {
            @Override
            public void run() {
                if (mDiscoBounceAnimation == null) {
                    return;
                }
                mDiscoBounceAnimation.start();
            }
        });
    }

    public boolean animateToWorkspace(AnimatorSet animationOut, long duration) {
        boolean shouldPost = true;
        if (animationOut == null) {
            return shouldPost;
        }
        Interpolator interpolator;
        if (mDetector.isIdleState()) {
            preparePull(true);
            mAnimationDuration = duration;
            mShiftStart = mAppsView.getTranslationY();
            interpolator = mFastOutSlowInInterpolator;
        } else {
            mScrollInterpolator.setVelocityAtZero(Math.abs(mContainerVelocity));
            interpolator = mScrollInterpolator;
            float nextFrameProgress = mProgress + mContainerVelocity * SINGLE_FRAME_MS / mShiftRange;
            if (nextFrameProgress <= 1f) {
                mProgress = nextFrameProgress;
            }
            shouldPost = false;
        }

        ObjectAnimator driftAndAlpha = ObjectAnimator.ofFloat(this, "progress",
                mProgress, 1f);
        driftAndAlpha.setDuration(mAnimationDuration);
        driftAndAlpha.setInterpolator(interpolator);
        animationOut.play(driftAndAlpha);

        animationOut.addListener(new AnimatorListenerAdapter() {
            boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled) {
                    finishPullDown();
                    cleanUpAnimation();
                    mDetector.finishedScrolling();
                }
            }
        });
        mCurrentAnimation = animationOut;
        return shouldPost;
    }

    public void finishPullUp() {
        mHotseat.setVisibility(View.INVISIBLE);
        if (hasSpringAnimationHandler())
            mSpringAnimationHandler.reset();
        setProgress(0f);
    }

    public void finishPullDown() {
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackgroundTransparent(false /* transparent */);
        mHotseat.setVisibility(View.VISIBLE);
        mAppsView.reset();
        if (hasSpringAnimationHandler())
            mSpringAnimationHandler.reset();
        setProgress(1f);
    }

    private void cancelAnimation() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }
        cancelDiscoveryAnimation();
    }

    public void cancelDiscoveryAnimation() {
        if (mDiscoBounceAnimation == null) {
            return;
        }
        mDiscoBounceAnimation.cancel();
        mDiscoBounceAnimation = null;
    }

    private void cleanUpAnimation() {
        mCurrentAnimation = null;
    }

    public void setupViews(AllAppsContainerView appsView, Hotseat hotseat, Workspace workspace) {
        mAppsView = appsView;
        mHotseat = hotseat;
        mWorkspace = workspace;
        mHotseat.addOnLayoutChangeListener(this);
        mHotseat.bringToFront();
        mCaretController = new AllAppsCaretController(
        mWorkspace.getPageIndicator().getCaretDrawable(), mLauncher);
        mSpringAnimationHandler = mAppsView.getSpringAnimationHandler();
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mShiftRange = top;
        } else {
            mShiftRange = bottom;
        }
        setProgress(mProgress);
    }

    private boolean hasSpringAnimationHandler() {
        return mSpringAnimationHandler != null;
    }


    static class ScrollInterpolator implements Interpolator {

        boolean mSteeper;

        public void setVelocityAtZero(float velocity) {
            mSteeper = velocity > FAST_FLING_PX_MS;
        }

        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            float output = t * t * t;
            if (mSteeper) {
                output *= t * t; // Make interpolation initial slope steeper
            }
            return output + 1;
        }
    }
}
