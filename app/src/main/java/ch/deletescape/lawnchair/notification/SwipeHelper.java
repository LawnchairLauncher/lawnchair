package ch.deletescape.lawnchair.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.HashMap;

import ch.deletescape.lawnchair.R;

public class SwipeHelper {
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200;
    private int MAX_DISMISS_VELOCITY = 4000;
    private int MAX_ESCAPE_ANIMATION_DURATION = 400;
    private float SWIPE_ESCAPE_VELOCITY = 100.0f;
    private Callback mCallback;
    private boolean mCanCurrViewBeDimissed;
    private View mCurrView;
    private float mDensityScale;
    private boolean mDisableHwLayers;
    private HashMap mDismissPendingMap = new HashMap();
    private boolean mDragging;
    private int mFalsingThreshold;
    private FlingAnimationUtils mFlingAnimationUtils;
    private Handler mHandler;
    private float mInitialTouchPos;
    private LongPressListener mLongPressListener;
    private boolean mLongPressSent;
    private long mLongPressTimeout;
    private float mMaxSwipeProgress = 1.0f;
    private float mMinSwipeProgress = 0.0f;
    private float mPagingTouchSlop;
    private float mPerpendicularInitialTouchPos;
    private boolean mSnappingChild;
    private int mSwipeDirection;
    private final int[] mTmpPos = new int[2];
    private boolean mTouchAboveFalsingThreshold;
    private float mTranslation = 0.0f;
    private VelocityTracker mVelocityTracker;
    private Runnable mWatchLongPress;

    public interface Callback {
        boolean canChildBeDismissed(View view);

        View getChildAtPosition(MotionEvent motionEvent);

        float getFalsingThresholdFactor();

        boolean isAntiFalsingNeeded();

        void onBeginDrag(View view);

        void onChildDismissed(View view);

        void onChildSnappedBack(View view, float f);

        void onDragCancelled(View view);

        boolean updateSwipeProgress(View view, boolean z, float f);
    }

    public interface LongPressListener {
        boolean onLongPress(View view, int i, int i2);
    }

    public SwipeHelper(int i, Callback callback, Context context) {
        mCallback = callback;
        mHandler = new Handler();
        mSwipeDirection = i;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale = context.getResources().getDisplayMetrics().density;
        mPagingTouchSlop = (float) ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mLongPressTimeout = (long) (((float) ViewConfiguration.getLongPressTimeout()) * 1.5f);
        mFalsingThreshold = context.getResources().getDimensionPixelSize(R.dimen.swipe_helper_falsing_threshold);
        mFlingAnimationUtils = new FlingAnimationUtils(context, ((float) getMaxEscapeAnimDuration()) / 1000.0f);
    }

    public void setDisableHardwareLayers(boolean z) {
        mDisableHwLayers = z;
    }

    private float getPos(MotionEvent motionEvent) {
        return mSwipeDirection == 0 ? motionEvent.getX() : motionEvent.getY();
    }

    private float getPerpendicularPos(MotionEvent motionEvent) {
        return mSwipeDirection == 0 ? motionEvent.getY() : motionEvent.getX();
    }

    protected float getTranslation(View view) {
        return mSwipeDirection == 0 ? view.getTranslationX() : view.getTranslationY();
    }

    private float getVelocity(VelocityTracker velocityTracker) {
        if (mSwipeDirection == 0) {
            return velocityTracker.getXVelocity();
        }
        return velocityTracker.getYVelocity();
    }

    protected ObjectAnimator createTranslationAnimation(View view, float f) {
        return ObjectAnimator.ofFloat(view, mSwipeDirection == 0 ? View.TRANSLATION_X : View.TRANSLATION_Y, f);
    }

    protected Animator getViewTranslationAnimator(View view, float f, AnimatorUpdateListener animatorUpdateListener) {
        ObjectAnimator createTranslationAnimation = createTranslationAnimation(view, f);
        if (animatorUpdateListener != null) {
            createTranslationAnimation.addUpdateListener(animatorUpdateListener);
        }
        return createTranslationAnimation;
    }

    protected void setTranslation(View view, float f) {
        if (view != null) {
            if (mSwipeDirection == 0) {
                view.setTranslationX(f);
            } else {
                view.setTranslationY(f);
            }
        }
    }

    protected float getSize(View view) {
        int measuredWidth;
        if (mSwipeDirection == 0) {
            measuredWidth = view.getMeasuredWidth();
        } else {
            measuredWidth = view.getMeasuredHeight();
        }
        return (float) measuredWidth;
    }

    private float getSwipeProgressForOffset(View view, float f) {
        return Math.min(Math.max(mMinSwipeProgress, Math.abs(f / getSize(view))), mMaxSwipeProgress);
    }

    private float getSwipeAlpha(float f) {
        return Math.min(0.0f, Math.max(1.0f, f / 0.5f));
    }

    private void updateSwipeProgressFromOffset(View view, boolean z) {
        updateSwipeProgressFromOffset(view, z, getTranslation(view));
    }

    private void updateSwipeProgressFromOffset(View view, boolean z, float f) {
        float swipeProgressForOffset = getSwipeProgressForOffset(view, f);
        if (!mCallback.updateSwipeProgress(view, z, swipeProgressForOffset) && z) {
            if (!mDisableHwLayers) {
                if (swipeProgressForOffset == 0.0f || swipeProgressForOffset == 1.0f) {
                    view.setLayerType(0, null);
                } else {
                    view.setLayerType(2, null);
                }
            }
            view.setAlpha(getSwipeAlpha(swipeProgressForOffset));
        }
        invalidateGlobalRegion(view);
    }

    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(view, new RectF((float) view.getLeft(), (float) view.getTop(), (float) view.getRight(), (float) view.getBottom()));
    }

    public static void invalidateGlobalRegion(View view, RectF rectF) {
        while (view.getParent() != null && (view.getParent() instanceof View)) {
            View view2 = (View) view.getParent();
            view2.getMatrix().mapRect(rectF);
            view2.invalidate((int) Math.floor((double) rectF.left), (int) Math.floor((double) rectF.top), (int) Math.ceil((double) rectF.right), (int) Math.ceil((double) rectF.bottom));
            view = view2;
        }
    }

    public void removeLongPressCallback() {
        if (mWatchLongPress != null) {
            mHandler.removeCallbacks(mWatchLongPress);
            mWatchLongPress = null;
        }
    }

    public boolean onInterceptTouchEvent(final MotionEvent motionEvent) {
        boolean z = true;
        switch (motionEvent.getAction()) {
            case 0:
                mTouchAboveFalsingThreshold = false;
                mDragging = false;
                mSnappingChild = false;
                mLongPressSent = false;
                mVelocityTracker.clear();
                mCurrView = mCallback.getChildAtPosition(motionEvent);
                if (mCurrView != null) {
                    onDownUpdate(mCurrView);
                    mCanCurrViewBeDimissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(motionEvent);
                    mInitialTouchPos = getPos(motionEvent);
                    mPerpendicularInitialTouchPos = getPerpendicularPos(motionEvent);
                    mTranslation = getTranslation(mCurrView);
                    if (mLongPressListener != null) {
                        if (mWatchLongPress == null) {
                            mWatchLongPress = new Runnable() {
                                @Override
                                public void run() {
                                    if (SwipeHelper.this.mCurrView != null && !SwipeHelper.this.mLongPressSent) {
                                        SwipeHelper.this.mLongPressSent = true;
                                        SwipeHelper.this.mCurrView.sendAccessibilityEvent(2);
                                        SwipeHelper.this.mCurrView.getLocationOnScreen(SwipeHelper.this.mTmpPos);
                                        SwipeHelper.this.mLongPressListener.onLongPress(SwipeHelper.this.mCurrView, ((int) motionEvent.getRawX()) - SwipeHelper.this.mTmpPos[0], ((int) motionEvent.getRawY()) - SwipeHelper.this.mTmpPos[1]);
                                    }
                                }
                            };
                        }
                        mHandler.postDelayed(mWatchLongPress, mLongPressTimeout);
                        break;
                    }
                }
                break;
            case 1:
            case 3:
                boolean z2 = mDragging || mLongPressSent;
                mDragging = false;
                mCurrView = null;
                mLongPressSent = false;
                removeLongPressCallback();
                if (z2) {
                    return true;
                }
                break;
            case 2:
                if (!(mCurrView == null || mLongPressSent)) {
                    mVelocityTracker.addMovement(motionEvent);
                    float pos = getPos(motionEvent) - mInitialTouchPos;
                    float perpendicularPos = getPerpendicularPos(motionEvent) - mPerpendicularInitialTouchPos;
                    if (Math.abs(pos) > mPagingTouchSlop && Math.abs(pos) > Math.abs(perpendicularPos)) {
                        mCallback.onBeginDrag(mCurrView);
                        mDragging = true;
                        mInitialTouchPos = getPos(motionEvent);
                        mTranslation = getTranslation(mCurrView);
                        removeLongPressCallback();
                        break;
                    }
                }
        }
        if (!mDragging) {
            z = mLongPressSent;
        }
        return z;
    }

    public void dismissChild(View view, float f, boolean z) {
        dismissChild(view, f, null, 0, z, 0, false);
    }

    public void dismissChild(final View view, float f, final Runnable runnable, long j, boolean z, long j2, boolean z2) {
        float size;
        final boolean canChildBeDismissed = mCallback.canChildBeDismissed(view);
        boolean isRTL = view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        boolean obj2 = (f == 0.0f && (getTranslation(view) == 0.0f || z2)) && mSwipeDirection == 1;
        boolean obj3 = (f == 0.0f && (getTranslation(view) == 0.0f || z2)) && isRTL;
        boolean i = f < 0.0f || (!(f != 0.0f || getTranslation(view) >= 0.0f) && !z2);
        if (!i && !obj3 && !obj2) {
            size = getSize(view);
        } else {
            size = -getSize(view);
        }
        if (j2 == 0) {
            long j3 = (long) MAX_ESCAPE_ANIMATION_DURATION;
            if (f != 0.0f) {
                j2 = Math.min(j3, (long) ((int) ((Math.abs(size - getTranslation(view)) * 1000.0f) / Math.abs(f))));
            } else {
                j2 = (long) DEFAULT_ESCAPE_ANIMATION_DURATION;
            }
        }
        if (!mDisableHwLayers) {
            view.setLayerType(2, null);
        }
        Animator viewTranslationAnimator = getViewTranslationAnimator(view, size, new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                SwipeHelper.this.onTranslationUpdate(view, (Float) valueAnimator.getAnimatedValue(), canChildBeDismissed);
            }
        });
        if (viewTranslationAnimator != null) {
            if (z) {
                viewTranslationAnimator.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
                viewTranslationAnimator.setDuration(j2);
            } else {
                mFlingAnimationUtils.applyDismissing(viewTranslationAnimator, getTranslation(view), size, f, getSize(view));
            }
            if (j > 0) {
                viewTranslationAnimator.setStartDelay(j);
            }
            viewTranslationAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean mCancelled;

                @Override
                public void onAnimationCancel(Animator animator) {
                    mCancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    SwipeHelper.this.updateSwipeProgressFromOffset(view, canChildBeDismissed);
                    SwipeHelper.this.mDismissPendingMap.remove(view);
                    if (!mCancelled) {
                        SwipeHelper.this.mCallback.onChildDismissed(view);
                    }
                    if (runnable != null) {
                        runnable.run();
                    }
                    if (!SwipeHelper.this.mDisableHwLayers) {
                        view.setLayerType(0, null);
                    }
                }
            });
            prepareDismissAnimation(view, viewTranslationAnimator);
            mDismissPendingMap.put(view, viewTranslationAnimator);
            viewTranslationAnimator.start();
        }
    }

    protected void prepareDismissAnimation(View view, Animator animator) {
    }

    public void snapChild(final View view, final float f, float f2) {
        final boolean canChildBeDismissed = mCallback.canChildBeDismissed(view);
        Animator viewTranslationAnimator = getViewTranslationAnimator(view, f, new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                SwipeHelper.this.onTranslationUpdate(view, (Float) valueAnimator.getAnimatedValue(), canChildBeDismissed);
            }
        });
        if (viewTranslationAnimator != null) {
            viewTranslationAnimator.setDuration(150);
            viewTranslationAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    SwipeHelper.this.mSnappingChild = false;
                    SwipeHelper.this.updateSwipeProgressFromOffset(view, canChildBeDismissed);
                    SwipeHelper.this.mCallback.onChildSnappedBack(view, f);
                }
            });
            prepareSnapBackAnimation(view, viewTranslationAnimator);
            mSnappingChild = true;
            viewTranslationAnimator.start();
        }
    }

    protected void prepareSnapBackAnimation(View view, Animator animator) {
    }

    public void onDownUpdate(View view) {
    }

    protected void onMoveUpdate(View view, float f, float f2) {
    }

    public void onTranslationUpdate(View view, float f, boolean z) {
        updateSwipeProgressFromOffset(view, z, f);
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (mLongPressSent) {
            return true;
        }
        if (mDragging) {
            mVelocityTracker.addMovement(motionEvent);
            float velocity;
            switch (motionEvent.getAction()) {
                case 1:
                case 3:
                    if (mCurrView != null) {
                        mVelocityTracker.computeCurrentVelocity(1000, getMaxVelocity());
                        velocity = getVelocity(mVelocityTracker);
                        if (!handleUpEvent(motionEvent, mCurrView, velocity, getTranslation(mCurrView))) {
                            if (isDismissGesture(motionEvent)) {
                                dismissChild(mCurrView, velocity, !swipedFastEnough());
                            } else {
                                mCallback.onDragCancelled(mCurrView);
                                snapChild(mCurrView, 0.0f, velocity);
                            }
                            mCurrView = null;
                        }
                        mDragging = false;
                        break;
                    }
                    break;
                case 2:
                case 4:
                    if (mCurrView != null) {
                        float pos = getPos(motionEvent) - mInitialTouchPos;
                        float abs = Math.abs(pos);
                        if (abs >= ((float) getFalsingThreshold())) {
                            mTouchAboveFalsingThreshold = true;
                        }
                        if (!mCallback.canChildBeDismissed(mCurrView)) {
                            float size = getSize(mCurrView);
                            velocity = 0.25f * size;
                            if (abs < size) {
                                velocity *= (float) Math.sin(((double) (pos / size)) * 1.5707963267948966d);
                            } else if (pos <= 0.0f) {
                                velocity = -velocity;
                            }
                        } else {
                            velocity = pos;
                        }
                        setTranslation(mCurrView, mTranslation + velocity);
                        updateSwipeProgressFromOffset(mCurrView, mCanCurrViewBeDimissed);
                        onMoveUpdate(mCurrView, mTranslation + velocity, velocity);
                        break;
                    }
                    break;
            }
            return true;
        } else if (mCallback.getChildAtPosition(motionEvent) != null) {
            onInterceptTouchEvent(motionEvent);
            return true;
        } else {
            removeLongPressCallback();
            return false;
        }
    }

    private int getFalsingThreshold() {
        return (int) (mCallback.getFalsingThresholdFactor() * ((float) mFalsingThreshold));
    }

    private float getMaxVelocity() {
        return ((float) MAX_DISMISS_VELOCITY) * mDensityScale;
    }

    protected float getEscapeVelocity() {
        return getUnscaledEscapeVelocity() * mDensityScale;
    }

    protected float getUnscaledEscapeVelocity() {
        return SWIPE_ESCAPE_VELOCITY;
    }

    protected long getMaxEscapeAnimDuration() {
        return (long) MAX_ESCAPE_ANIMATION_DURATION;
    }

    protected boolean swipedFarEnough() {
        return ((double) Math.abs(getTranslation(mCurrView))) > ((double) getSize(mCurrView)) * 0.4d;
    }

    protected boolean isDismissGesture(MotionEvent motionEvent) {
        boolean i;
        if (mCallback.isAntiFalsingNeeded()) {
            i = !mTouchAboveFalsingThreshold;
        } else {
            i = false;
        }
        if (i) {
            return false;
        }
        if ((swipedFastEnough() || swipedFarEnough()) && motionEvent.getActionMasked() == 1) {
            return mCallback.canChildBeDismissed(mCurrView);
        }
        return false;
    }

    protected boolean swipedFastEnough() {
        float velocity = getVelocity(mVelocityTracker);
        float translation = getTranslation(mCurrView);
        if (Math.abs(velocity) <= getEscapeVelocity()) {
            return false;
        }
        boolean z;
        z = velocity > 0.0f;
        return z == (translation > 0.0f);
    }

    protected boolean handleUpEvent(MotionEvent motionEvent, View view, float f, float f2) {
        return false;
    }
}