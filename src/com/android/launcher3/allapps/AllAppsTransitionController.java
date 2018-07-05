package com.android.launcher3.allapps;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.support.animation.SpringAnimation;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.SpringAnimationHandler;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.GradientView;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TouchController;

import java.lang.reflect.InvocationTargetException;

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

    private static final String TAG = "AllAppsTrans";
    private static final boolean DBG = false;

    private final Interpolator mWorkspaceAccelnterpolator = new AccelerateInterpolator(2f);
    private final Interpolator mHotseatAccelInterpolator = new AccelerateInterpolator(1.5f);
    private final Interpolator mDecelInterpolator = new DecelerateInterpolator(3f);
    private final Interpolator mFastOutSlowInInterpolator = new FastOutSlowInInterpolator();
    private final SwipeDetector.ScrollInterpolator mScrollInterpolator
            = new SwipeDetector.ScrollInterpolator();

    private static final float PARALLAX_COEFFICIENT = .125f;
    private static final int SINGLE_FRAME_MS = 16;

    private AllAppsContainerView mAppsView;
    private int mAllAppsBackgroundColor;
    private Workspace mWorkspace;
    private Hotseat mHotseat;
    private int mHotseatBackgroundColor;

    private AllAppsCaretController mCaretController;

    private float mStatusBarHeight;

    private final Launcher mLauncher;
    private final SwipeDetector mDetector;
    private final ArgbEvaluator mEvaluator;
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

    private AnimatorSet mCurrentAnimation;
    private boolean mNoIntercept;
    private boolean mTouchEventStartedOnHotseat;

    // Used in discovery bounce animation to provide the transition without workspace changing.
    private boolean mIsTranslateWithoutWorkspace = false;
    private Animator mDiscoBounceAnimation;
    private GradientView mGradientView;

    private SpringAnimation mSearchSpring;
    private SpringAnimationHandler mSpringAnimationHandler;

    private final static float NOTIFICATION_OPEN_VELOCITY = 2.25f;
    private final static float NOTIFICATION_CLOSE_VELOCITY = -0.35f;
    enum NotificationState {
        Locked,
        Free,
        Opened,
        Closed
    }
    private NotificationState mNotificationState;

    public AllAppsTransitionController(Launcher l) {
        mLauncher = l;
        mDetector = new SwipeDetector(l, this, SwipeDetector.VERTICAL);
        mShiftRange = DEFAULT_SHIFT_RANGE;
        mProgress = 1f;

        mEvaluator = new ArgbEvaluator();
        mAllAppsBackgroundColor = Themes.getAttrColor(l, android.R.attr.colorPrimary);
        mIsDarkTheme = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = false;
            mTouchEventStartedOnHotseat = mLauncher.getDragLayer().isEventOverHotseat(ev);
            if (!mLauncher.isAllAppsVisible() && mLauncher.getWorkspace().workspaceInModalState()) {
                mNoIntercept = true;
            } else if (mLauncher.isAllAppsVisible() &&
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
                    if (mLauncher.isAllAppsVisible()) {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_NEGATIVE;
                    } else {
                        directionsToDetectScroll |= SwipeDetector.DIRECTION_BOTH;
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
        cancelAnimation();
        mCurrentAnimation = LauncherAnimUtils.createAnimatorSet();
        mShiftStart = mAppsView.getTranslationY();
        preparePull(start);
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.skipToEnd();
        }
        mNotificationState = NotificationState.Free;
    }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        if (mAppsView == null) {
            return false;   // early termination.
        }

        //Locked means do not use any notification code
        if (mNotificationState != NotificationState.Locked) {
            if (mProgress < 1f) {
                //Apps list is being opened, disable notification code
                mNotificationState = NotificationState.Locked;
            } else {
                //Disable code when access to the hidden APIs returns an error
                if (velocity > NOTIFICATION_OPEN_VELOCITY &&
                        (mNotificationState == NotificationState.Free || mNotificationState == NotificationState.Closed)) {
                    mNotificationState = openNotifications() ?
                            NotificationState.Opened :
                            NotificationState.Locked;
                } else if (velocity < NOTIFICATION_CLOSE_VELOCITY &&
                        mNotificationState == NotificationState.Opened) {
                    mNotificationState = closeNotifications() ?
                            NotificationState.Closed :
                            NotificationState.Locked;
                }

                //Don't open all apps when notification shade is being used
                if (mNotificationState == NotificationState.Opened || mNotificationState == NotificationState.Closed) {
                    return true;
                }
            }
        }

        mContainerVelocity = velocity;

        float shift = Math.min(Math.max(0, mShiftStart + displacement), mShiftRange);
        setProgress(shift / mShiftRange);

        return true;
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private boolean openNotifications() {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(mLauncher.getSystemService("statusbar"));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return false;
        }
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private boolean closeNotifications() {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("collapsePanels")
                    .invoke(mLauncher.getSystemService("statusbar"));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return false;
        }
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if (mAppsView == null) {
            return; // early termination.
        }

        final int containerType = mTouchEventStartedOnHotseat
                ? ContainerType.HOTSEAT : ContainerType.WORKSPACE;

        if (fling && mNotificationState != NotificationState.Opened && mNotificationState != NotificationState.Closed) {
            if (velocity < 0) {
                calculateDuration(velocity, mAppsView.getTranslationY());

                if (!mLauncher.isAllAppsVisible()) {
                    mLauncher.getUserEventDispatcher().logActionOnContainer(
                            Action.Touch.FLING,
                            Action.Direction.UP,
                            containerType);
                }
                mLauncher.showAppsView(true /* animated */,
                        false /* updatePredictedApps */,
                        false /* focusSearchBar */);
                if (hasSpringAnimationHandler()) {
                    mSpringAnimationHandler.add(mSearchSpring, true /* setDefaultValues */);
                    // The icons are moving upwards, so we go to 0 from 1. (y-axis 1 is below 0.)
                    mSpringAnimationHandler.animateToFinalPosition(0 /* pos */, 1 /* startValue */);
                }
            } else {
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
                if (!mLauncher.isAllAppsVisible()) {
                    mLauncher.getUserEventDispatcher().logActionOnContainer(
                            Action.Touch.SWIPE,
                            Action.Direction.UP,
                            containerType);
                }
                mLauncher.showAppsView(true, /* animated */
                        false /* updatePredictedApps */,
                        false /* focusSearchBar */);
            }
        }
    }

    public boolean isTransitioning() {
        return mDetector.isDraggingOrSettling();
    }

    public boolean isDragging() {
        return mDetector.isDraggingState();
    }

    /**
     * @param start {@code true} if start of new drag.
     */
    public void preparePull(boolean start) {
        if (start) {
            ((InputMethodManager) mLauncher.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(mLauncher.getAppsView().getWindowToken(), 0);
            // Initialize values that should not change until #onDragEnd
            mStatusBarHeight = mLauncher.getDragLayer().getInsets().top;
            mHotseat.setVisibility(View.VISIBLE);
            mHotseatBackgroundColor = mHotseat.getBackgroundDrawableColor();
            mHotseat.setBackgroundTransparent(true /* transparent */);
            if (!mLauncher.isAllAppsVisible()) {
                mLauncher.tryAndUpdatePredictedApps();
                mAppsView.reset();
                mAppsView.setVisibility(View.VISIBLE);
                if (!FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
                    mAppsView.setRevealDrawableColor(mHotseatBackgroundColor);
                }
            }
        }
    }

    private void updateLightStatusBar(float shift) {
        // Do not modify status bar on landscape as all apps is not full bleed.
        if (!FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS
                && mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            return;
        }

        // Use a light system UI (dark icons) if all apps is behind at least half of the status bar.
        boolean forceChange = FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS ?
                shift <= mShiftRange / 4 :
                shift <= mStatusBarHeight / 2;
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
            mGradientView = (GradientView) mLauncher.findViewById(R.id.gradient_bg);
            mGradientView.setVisibility(View.VISIBLE);
            mGradientView.setShiftScrim(!Utilities.ATLEAST_MARSHMALLOW);
        }
        mGradientView.setProgress(progress);
    }

    /**
     * @param progress       value between 0 and 1, 0 shows all apps and 1 shows workspace
     */
    public void setProgress(float progress) {
        float shiftPrevious = mProgress * mShiftRange;
        mProgress = progress;
        float shiftCurrent = progress * mShiftRange;

        float workspaceHotseatAlpha = Utilities.boundToRange(progress, 0f, 1f);
        float alpha = 1 - workspaceHotseatAlpha;
        float workspaceAlpha = mWorkspaceAccelnterpolator.getInterpolation(workspaceHotseatAlpha);
        float hotseatAlpha = mHotseatAccelInterpolator.getInterpolation(workspaceHotseatAlpha);

        int color = (Integer) mEvaluator.evaluate(mDecelInterpolator.getInterpolation(alpha),
                mHotseatBackgroundColor, mAllAppsBackgroundColor);
        int bgAlpha = Color.alpha((int) mEvaluator.evaluate(alpha,
                mHotseatBackgroundColor, mAllAppsBackgroundColor));

        if (FeatureFlags.LAUNCHER3_GRADIENT_ALL_APPS) {
            updateAllAppsBg(alpha);
        } else {
            mAppsView.setRevealDrawableColor(ColorUtils.setAlphaComponent(color, bgAlpha));
        }

        mAppsView.getContentView().setAlpha(alpha);
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
        return shouldPost;
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
                if (canceled) {
                    return;
                } else {
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
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.remove(mSearchSpring);
            mSpringAnimationHandler.reset();
        }
        setProgress(0f);
    }

    public void finishPullDown() {
        mAppsView.setVisibility(View.INVISIBLE);
        mHotseat.setBackgroundTransparent(false /* transparent */);
        mHotseat.setVisibility(View.VISIBLE);
        mAppsView.reset();
        if (hasSpringAnimationHandler()) {
            mSpringAnimationHandler.reset();
        }
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
}
