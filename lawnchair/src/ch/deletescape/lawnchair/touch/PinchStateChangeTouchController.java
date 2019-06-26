package ch.deletescape.lawnchair.touch;

import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LogAccelerateInterpolator;
import com.android.launcher3.LogDecelerateInterpolator;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.util.PendingAnimation;
import com.android.launcher3.util.TouchController;

public class PinchStateChangeTouchController extends AnimatorListenerAdapter implements
        TouchController, OnScaleGestureListener {
    private static final float FLING_VELOCITY = 0.003f;
    private static final float SUCCESS_TRANSITION_PROGRESS = 0.5f;
    private static final String TAG = "PinchStateChangeTouchController";
    private static final int THRESHOLD = 6;
    private AnimatorPlaybackController mCurrentAnimation;
    private final ScaleGestureDetector mDetector;
    private LauncherState mFromState;
    private TimeInterpolator mInterpolator;
    private final Launcher mLauncher;
    private PendingAnimation mPendingAnimation;
    private boolean mPinchStarted;
    private long mPreviousTimeMillis;
    private float mProgressDelta;
    private float mProgressMultiplier;
    private long mTimeDelta;
    private LauncherState mToState;
    private final Workspace mWorkspace;

    public PinchStateChangeTouchController(Launcher launcher) {
        mLauncher = launcher;
        mWorkspace = launcher.getWorkspace();
        mDetector = new ScaleGestureDetector(mLauncher, this);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent motionEvent) {
        mDetector.onTouchEvent(motionEvent);
        return mPinchStarted;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent motionEvent) {
        return mPinchStarted && motionEvent.getPointerCount() <= 2 && mDetector.onTouchEvent(motionEvent);
    }

    private LauncherState getTargetState(LauncherState launcherState) {
        if (launcherState == LauncherState.NORMAL) {
            return LauncherState.OPTIONS;
        }
        return launcherState == LauncherState.OPTIONS ? LauncherState.NORMAL : launcherState;
    }

    private float initCurrentAnimation() {
        float height = (float) mLauncher.getDragLayer().getHeight();
        mCurrentAnimation = mLauncher.getStateManager().createAnimationToNewWorkspace(mToState, (long) (2 * height));
        return 1 / height;
    }

    private boolean reInitCurrentAnimation() {
        LauncherState state = mFromState == null ? mLauncher.getStateManager().getState() : mFromState;
        LauncherState targetState = getTargetState(state);
        if ((state == mFromState && targetState == mToState) || state == targetState) {
            return false;
        }
        mFromState = state;
        mToState = targetState;
        mPreviousTimeMillis = System.currentTimeMillis();
        mInterpolator = mLauncher.isInState(LauncherState.OPTIONS) ? new LogDecelerateInterpolator(100, 0) : new LogAccelerateInterpolator(100, 0);
        mProgressMultiplier = initCurrentAnimation();
        mCurrentAnimation.getTarget().addListener(this);
        mCurrentAnimation.dispatchOnStart();
        mPinchStarted = true;
        return true;
    }

    public boolean onScaleBegin(ScaleGestureDetector r4) {
        if (mCurrentAnimation != null) return true;

        if (mLauncher.isInState(LauncherState.NORMAL) || mLauncher.isInState(LauncherState.OPTIONS)) {
            if (mLauncher.isWorkspaceLocked()) return false;
            if (mWorkspace.isSwitchingState()) return false;
            if (mWorkspace.duringScrollInteraction()) return false;
            if (AbstractFloatingView.getTopOpenView(mLauncher) != null) return false;

            mToState = mFromState = null;
            reInitCurrentAnimation();
            return true;
        }
        return false;
    }

    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        if (mCurrentAnimation == null) {
            Log.d(TAG, "onScale# No animation.");
            return false;
        }
        float currentSpan = scaleGestureDetector.getCurrentSpan() - scaleGestureDetector.getPreviousSpan();
        int width = mWorkspace.getWidth() * 6;
        float f = LauncherState.OPTIONS.getWorkspaceScaleAndTranslation(mLauncher)[0];
        currentSpan = mInterpolator.getInterpolation((Math.max(f,
                Math.min(mFromState.getWorkspaceScaleAndTranslation(mLauncher)[0] + currentSpan /  width, 1)) - f) / (1 - f));
        if (mToState == LauncherState.OPTIONS) {
            currentSpan = 1.0f - currentSpan;
        }
        updateProgress(currentSpan);
        mProgressDelta = currentSpan - mCurrentAnimation.getProgressFraction();
        mTimeDelta = System.currentTimeMillis() - mPreviousTimeMillis;
        mPreviousTimeMillis = System.currentTimeMillis();
        return false;
    }

    private void updateProgress(float f) {
        mCurrentAnimation.setPlayFraction(f);
    }

    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
        if (mCurrentAnimation == null) {
            Log.d(TAG, "onScaleEnd# No animation.");
            clearState();
            mPinchStarted = false;
            return;
        }
        int i;
        LauncherState launcherState;
        float f;
        float progressFraction = mCurrentAnimation.getProgressFraction();
        float f2 = mProgressDelta / ((float) mTimeDelta);
        boolean z = (mLauncher.isInState(LauncherState.OPTIONS) && f2 >= FLING_VELOCITY) || (!mLauncher.isInState(LauncherState.OPTIONS) && f2 <= -0.003f);
        if (z) {
            i = 4;
            launcherState = Float.compare(Math.signum(f2), Math.signum(mProgressMultiplier)) == 0 ? mToState : mFromState;
        } else {
            i = 5;
            launcherState = progressFraction > 0.5f ? mToState : mFromState;
        }
        LauncherState launcherState2 = launcherState;
        long j = 0;
        float f3 = 0;
        if (launcherState2 == this.mToState) {
            if (progressFraction >= 1) {
                f = 1;
                f3 = f;
            } else {
                f = Utilities.boundToRange(((16 * f2) * mProgressMultiplier) + progressFraction, 0, 1);
                j = SwipeDetector.calculateDuration(f2, 1 - Math.max(progressFraction, 0));
                f3 = 1;
            }
        } else if (progressFraction <= 0) {
            f = 0;
        } else {
            f = Utilities.boundToRange(((16 * f2) * mProgressMultiplier) + progressFraction, 0, 1);
            j = SwipeDetector.calculateDuration(f2, Math.min(progressFraction, 1) - 0);
        }
        mCurrentAnimation.setEndAction(() -> onPinchInteractionCompleted(launcherState2, i));
        ValueAnimator animationPlayer = mCurrentAnimation.getAnimationPlayer();
        animationPlayer.setFloatValues(f, f3);
        animationPlayer
                .setDuration(j).setInterpolator(Interpolators.scrollInterpolatorForVelocity(f2));
        animationPlayer.start();
        mPinchStarted = false;
    }

    private int getDirectionForLog() {
        return mToState.ordinal > mFromState.ordinal ? 5 : 6;
    }

    private void onPinchInteractionCompleted(LauncherState launcherState, int i) {
        clearState();
        boolean i2 = true;
        if (mPendingAnimation != null) {
            boolean z = mToState == launcherState;
            mPendingAnimation.finish(z, i);
            mPendingAnimation = null;
            i2 = !z;
        }
        if (i2) {
            if (launcherState != mFromState) {
                mLauncher.getUserEventDispatcher().logStateChangeAction(i, getDirectionForLog(), 6,
                        mFromState.containerType, mToState.containerType, mLauncher.getWorkspace().getCurrentPage());
            }
            mLauncher.getStateManager().goToState(launcherState, false);
        }
    }

    private void clearState() {
        mCurrentAnimation = null;
    }
}