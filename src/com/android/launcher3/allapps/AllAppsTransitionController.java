package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.support.animation.SpringAnimation;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.GradientView;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
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
public class AllAppsTransitionController implements TouchController, SwipeDetector.Listener,
         SearchUiManager.OnScrollRangeChangeListener {

    private static final Property<AllAppsTransitionController, Float> PROGRESS =
            new Property<AllAppsTransitionController, Float>(Float.class, "progress") {

        @Override
        public Float get(AllAppsTransitionController controller) {
            return controller.mProgress;
        }

        @Override
        public void set(AllAppsTransitionController controller, Float progress) {
            controller.setProgress(progress);
        }
    };

    // Spring values used when the user has not flung all apps.
    private static final float SPRING_MAX_RELEASE_VELOCITY = 10000;
    // The delay (as a % of the animation duration) to start the springs.
    private static final float SPRING_DELAY = 0.3f;

    private final Interpolator mWorkspaceAccelnterpolator = Interpolators.ACCEL_2;
    private final Interpolator mHotseatAccelInterpolator = Interpolators.ACCEL_1_5;
    private final Interpolator mFastOutSlowInInterpolator = Interpolators.FAST_OUT_SLOW_IN;

    private static final float PARALLAX_COEFFICIENT = .125f;
    private static final int SINGLE_FRAME_MS = 16;

    private AllAppsContainerView mAppsView;
    private Workspace mWorkspace;
    private Hotseat mHotseat;

    private AllAppsCaretController mCaretController;

    private final Launcher mLauncher;
    private final SwipeDetector mDetector;
    private final boolean mIsDarkTheme;

    // Animation in this class is controlled by a single variable {@link mProgress}.
    // Visually, it represents top y coordinate of the all apps container if multiplied with
    // {@link mShiftRange}.

    // When {@link mProgress} is 0, all apps container is pulled up.
    // When {@link mProgress} is 1, all apps container is pulled down.
    private float mShiftStart;      // [0, mShiftRange]
    private float mShiftRange;      // changes depending on the orientation
    private float mProgress;        // [0, 1], mShiftRange * mProgress = shiftCurrent

    // Velocity of the container. Unit is in px/ms.
    private float mContainerVelocity;

    private static final float DEFAULT_SHIFT_RANGE = 10;

    private static final float RECATCH_REJECTION_FRACTION = .0875f;

    private long mAnimationDuration;

    private boolean mNoIntercept;
    private boolean mTouchEventStartedOnHotseat;

    // Used in discovery bounce animation to provide the transition without workspace changing.
    private boolean mIsTranslateWithoutWorkspace = false;
    private Animator mDiscoBounceAnimation;
    private GradientView mGradientView;

    private SpringAnimation mSearchSpring;
    private SpringAnimationHandler mSpringAnimationHandler;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, SwipeDetector.VERTICAL);
        mShiftRange = DEFAULT_SHIFT_RANGE;
        mProgress = 1f;

        mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            mTouchEventStartedOnHotseat = mLauncher.getDragLayer().isEventOverHotseat(ev);
            if (!mLauncher.isInState(ALL_APPS) && !mLauncher.isInState(NORMAL)) {
                mNoIntercept = true;
            } else if (mLauncher.isInState(ALL_APPS) &&
                    !mAppsView.shouldContainerScroll(ev)) {
                mNoIntercept = true;
            } else if (AbstractFloatingView.getTopOpenView(mLauncher) != null) {
                mNoIntercept = true;
            } else {
                // Now figure out which direction scroll events the controller will start
                // calling the callbacks.
                int directionsToDetectScroll = 0;
                boolean ignoreSlopWhenSettling = false;

                if (mDetector.isIdleState()) {
                    if (mLauncher.isInState(ALL_APPS)) {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_NEGATIVE;
                    } else {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_POSITIVE;
                    }
                } else {
                    if (isInDisallowRecatchBottomZone()) {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_POSITIVE;
                    } else if (isInDisallowRecatchTopZone()) {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_NEGATIVE;
                    } else {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_BOTH;
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

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.addMovement(ev);
        }
        return mDetector.onTouchEvent(ev);
    }

    private boolean isInDisallowRecatchTopZone() {
        return mProgress < RECATCH_REJECTION_FRACTION;
    }

    private boolean isInDisallowRecatchBottomZone() {
        return mProgress > 1 - RECATCH_REJECTION_FRACTION;
    }

    @Override
    public void onDragStart(boolean start) {
        mCaretController.onDragStart();
        mLauncher.getStateManager().cancelAnimation();
        cancelDiscoveryAnimation();
        mShiftStart = mAppsView.getTranslationY();
        onProgressAnimationStart();
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.skipToEnd();
        }
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }

        mContainerVelocity = velocity;

        float shift = Math.min(Math.max(0, mShiftStart + displacement), mShiftRange);
        setProgress(shift / mShiftRange);

        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        final int containerType = mTouchEventStartedOnHotseat
                ? ContainerType.HOTSEAT : ContainerType.WORKSPACE;
        if (fling) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());
                if (!mLauncher.isInState(ALL_APPS)) {
                    logSwipeOnContainer(Touch.FLING, Direction.UP, containerType);
                }
                mLauncher.getStateManager().goToState(ALL_APPS);
                if (hasSpringAnimationHandler()) {
                    mSpringAnimationHandler.add(mSearchSpring, true /* setDefaultValues */);
                    // The icons are moving upwards, so we go to 0 from 1. (y-axis 1 is below 0.)
                    mSpringAnimationHandler.animateToFinalPosition(0 /* pos */, 1 /* startValue */);
                }
            } else {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                if (mLauncher.isInState(ALL_APPS)) {
                    logSwipeOnContainer(Touch.FLING, Direction.DOWN, ContainerType.ALLAPPS);
                }
                mLauncher.getStateManager().goToState(NORMAL);
            }
            // snap to top or bottom using the release velocity
        } else {
            if (mAppsView.getTranslationY() > mShiftRange / 2) {
                calculateDuration(velocity, Math.abs(mShiftRange - mAppsView.getTranslationY()));
                if (mLauncher.isInState(ALL_APPS)) {
                    logSwipeOnContainer(Touch.SWIPE, Direction.DOWN, ContainerType.ALLAPPS);
                }
                mLauncher.getStateManager().goToState(NORMAL);
            } else {
                calculateDuration(velocity, Math.abs(mAppsView.getTranslationY()));
                if (!mLauncher.isInState(ALL_APPS)) {
                    logSwipeOnContainer(Touch.SWIPE, Direction.UP, containerType);
                }
                mLauncher.getStateManager().goToState(ALL_APPS);
            }
        }
    }

    /**
     * Important, make sure that this method is called only when actual launcher state transition
     * happen and not when user swipes in one direction only to cancel that swipe seconds later.
     *
     * @param touchType Swipe or Fling
     * @param direction Up or Down
     * @param containerType Workspace or Allapps
     */
    private void logSwipeOnContainer(int touchType, int direction, int containerType) {
        mLauncher.getUserEventDispatcher().logActionOnContainer(
                touchType, direction, containerType,
                mLauncher.getWorkspace().getCurrentPage());
    }

    public boolean isTransitioning() {
        return mDetector.isDraggingOrSettling();
    }

    private void onProgressAnimationStart() {
        // Initialize values that should not change until #onDragEnd
        mHotseat.setVisibility(View.VISIBLE);
        mAppsView.setVisibility(View.VISIBLE);
    }

    private void updateLightStatusBar(float shift) {
        // Use a light system UI (dark icons) if all apps is behind at least half of the status bar.
        boolean forceChange = shift <= mShiftRange / 4;
        if (forceChange) {
            mLauncher.getSystemUiController().updateUiState(
                    SystemUiController.UI_STATE_ALL_APPS, !mIsDarkTheme);
        } else {
            mLauncher.getSystemUiController().updateUiState(
                    SystemUiController.UI_STATE_ALL_APPS, 0);
        }
    }

    private void updateAllAppsBg(float progress) {
        // gradient
        if (mGradientView == null) {
            mGradientView = mLauncher.findViewById(R.id.gradient_bg);
        }
        mGradientView.setProgress(progress);
    }

    /**
     * Note this method should not be called outside this class. This is public because it is used
     * in xml-based animations which also handle updating the appropriate UI.
     *
     * @param progress value between 0 and 1, 0 shows all apps and 1 shows workspace
     *
     * @see #setFinalProgress(float)
     * @see #animateToFinalProgress(float, boolean, AnimatorSet, AnimationConfig)
     */
    public void setProgress(float progress) {
        float shiftPrevious = mProgress * mShiftRange;
        mProgress = progress;
        float shiftCurrent = progress * mShiftRange;

        float workspaceHotseatAlpha = Utilities.boundToRange(progress, 0f, 1f);
        float alpha = 1 - workspaceHotseatAlpha;
        float workspaceAlpha = mWorkspaceAccelnterpolator.getInterpolation(workspaceHotseatAlpha);
        float hotseatAlpha = mHotseatAccelInterpolator.getInterpolation(workspaceHotseatAlpha);

        updateAllAppsBg(alpha);
        mAppsView.setAlpha(alpha);
        mAppsView.setTranslationY(shiftCurrent);

        if (!mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            mWorkspace.setHotseatTranslationAndAlpha(Workspace.Direction.Y, -mShiftRange + shiftCurrent,
                    hotseatAlpha);
        } else {
            mWorkspace.setHotseatTranslationAndAlpha(Workspace.Direction.Y,
                    PARALLAX_COEFFICIENT * (-mShiftRange + shiftCurrent),
                    hotseatAlpha);
        }

        if (mIsTranslateWithoutWorkspace) {
            return;
        }
        mWorkspace.setWorkspaceYTranslationAndAlpha(
                PARALLAX_COEFFICIENT * (-mShiftRange + shiftCurrent), workspaceAlpha);

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
        mAnimationDuration = SwipeDetector.calculateDuration(velocity, disp / mShiftRange);
    }

    /**
     * Sets the vertical transition progress to {@param progress} and updates all the dependent UI
     * accordingly.
     */
    public void setFinalProgress(float progress) {
        setProgress(progress);
        onProgressAnimationEnd();
    }

    /**
     * Creates an animation which updates the vertical transition progress and updates all the
     * dependent UI using various animation events
     *
     * @param progress the final vertical progress at the end of the animation
     * @param addSpring should there be an addition spring animation for the sub-views
     * @param animationOut the target AnimatorSet where this animation should be added
     * @param outConfig an in/out configuration which can be shared with other animations
     */
    public void animateToFinalProgress(float progress, boolean addSpring,
            AnimatorSet animationOut, AnimationConfig outConfig) {
        if (Float.compare(mProgress, progress) == 0) {
            // Fail fast
            onProgressAnimationEnd();
            return;
        }

        outConfig.shouldPost = true;
        Interpolator interpolator;
        if (mDetector.isIdleState()) {
            mAnimationDuration = LauncherAnimUtils.ALL_APPS_TRANSITION_MS;
            mShiftStart = mAppsView.getTranslationY();
            interpolator = mFastOutSlowInInterpolator;
        } else {
            interpolator = scrollInterpolatorForVelocity(mContainerVelocity);
            mProgress = Utilities.boundToRange(
                    mProgress + mContainerVelocity * SINGLE_FRAME_MS / mShiftRange, 0f, 1f);
            outConfig.shouldPost = false;
        }

        outConfig.duration = mAnimationDuration;
        ObjectAnimator anim = ObjectAnimator.ofFloat(this, PROGRESS, mProgress, progress);
        anim.setDuration(mAnimationDuration);
        anim.setInterpolator(interpolator);
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                onProgressAnimationEnd();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                onProgressAnimationStart();
            }
        });

        animationOut.play(anim);
        if (addSpring) {
            ValueAnimator springAnim = ValueAnimator.ofFloat(0, 1);
            springAnim.setDuration((long) (mAnimationDuration * SPRING_DELAY));
            springAnim.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (!mSpringAnimationHandler.isRunning()) {
                        float velocity = mProgress * SPRING_MAX_RELEASE_VELOCITY;
                        mSpringAnimationHandler.animateToPositionWithVelocity(0, 1, velocity);
                    }
                }
            });
            animationOut.play(anim);
        }
    }

    public void showDiscoveryBounce() {
        // cancel existing animation in case user locked and unlocked at a super human speed.
        cancelDiscoveryAnimation();

        // assumption is that this variable is always null
        mDiscoBounceAnimation = AnimatorInflater.loadAnimator(mLauncher,
                R.animator.discovery_bounce);
        mDiscoBounceAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                mIsTranslateWithoutWorkspace = true;
                onProgressAnimationStart();
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                onProgressAnimationEnd();
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

    public void cancelDiscoveryAnimation() {
        if (mDiscoBounceAnimation == null) {
            return;
        }
        mDiscoBounceAnimation.cancel();
        mDiscoBounceAnimation = null;
    }

    public void setupViews(AllAppsContainerView appsView, Hotseat hotseat, Workspace workspace) {
        mAppsView = appsView;
        mHotseat = hotseat;
        mWorkspace = workspace;
        mHotseat.bringToFront();
        mCaretController = new AllAppsCaretController(
                mWorkspace.getPageIndicator().getCaretDrawable(), mLauncher);
        mAppsView.getSearchUiManager().addOnScrollRangeChangeListener(this);
        mSpringAnimationHandler = mAppsView.getSpringAnimationHandler();
        mSearchSpring = mAppsView.getSearchUiManager().getSpringForFling();
    }

    private boolean hasSpringAnimationHandler() {
        return FeatureFlags.LAUNCHER3_PHYSICS && mSpringAnimationHandler != null;
    }

    @Override
    public void onScrollRangeChanged(int scrollRange) {
        mShiftRange = scrollRange;
        setProgress(mProgress);
    }

    /**
     * Set the final view states based on the progress.
     * TODO: This logic should go in {@link LauncherState}
     */
    private void onProgressAnimationEnd() {
        if (Float.compare(mProgress, 1f) == 0) {
            mAppsView.setVisibility(View.INVISIBLE);
            mHotseat.setVisibility(View.VISIBLE);
            mAppsView.reset();
        } else if (Float.compare(mProgress, 0f) == 0) {
            mHotseat.setVisibility(View.INVISIBLE);
            mAppsView.setVisibility(View.VISIBLE);
        }
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.remove(mSearchSpring);
            mSpringAnimationHandler.reset();
        }

        // TODO: This call should no longer be needed once caret stops animating.
        setProgress(mProgress);
        mDetector.finishedScrolling();
    }
}
